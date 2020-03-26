package com.markoid.bluetooth.services

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import com.markoid.bluetooth.states.ServiceState
import io.reactivex.subjects.ReplaySubject
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
class BluetoothService(
    private val mBluetoothAdapter: BluetoothAdapter?,
    private val handler: Handler = Handler()
) {

    private var mState: ServiceState

    private var mAcceptThread: AcceptThread? = null

    private var mConnectThread: ConnectThread? = null

    private var mConnectedThread: ConnectedThread? = null

    private var stateListener: ReplaySubject<ServiceState>? = null

    private var readMessageListener: ReplaySubject<String>? = null

    /**
     * @param set - Sets the current state of the chat connection
     * @param get - Return the current connection state.
     * @param state An integer defining the current connection state
     */
    @get:Synchronized
    @set:Synchronized
    var state: ServiceState
        get() = mState
        private set(state) {
            stateListener?.onNext(state)
            mState = state
        }

    companion object {
        // Name for the SDP record when creating server socket
        private const val NAME = "BluetoothChat"

        // Unique UUID for this application
        private val MY_UUID =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")

        // Constants that indicate the current connection state
        const val STATE_NONE = 0 // we're doing nothing
        const val STATE_LISTEN = 1 // now listening for incoming connections
        const val STATE_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_CONNECTED = 3 // now connected to a remote device
    }

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    init {
        mState = ServiceState.NONE
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
    fun start() {
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread?.cancel()
            mConnectThread = null
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread?.cancel()
            mConnectedThread = null
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (mAcceptThread == null) {
            mAcceptThread = AcceptThread()
            mAcceptThread?.start()
        }
        state = ServiceState.LISTEN
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    @Synchronized
    fun connect(device: BluetoothDevice) {
        // Cancel any thread attempting to make a connection
        if (mState == ServiceState.CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread?.cancel()
                mConnectThread = null
            }
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread?.cancel()
            mConnectedThread = null
        }
        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device)
        mConnectThread?.start()
        state = ServiceState.CONNECTING
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(socket: BluetoothSocket?, device: BluetoothDevice) {
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread?.cancel()
            mConnectThread = null
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread?.cancel()
            mConnectedThread = null
        }
        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {
            mAcceptThread?.cancel()
            mAcceptThread = null
        }
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket)
        mConnectedThread?.start()
        state = ServiceState.CONNECTED
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        if (mConnectThread != null) {
            mConnectThread?.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread?.cancel()
            mConnectedThread = null
        }
        if (mAcceptThread != null) {
            mAcceptThread?.cancel()
            mAcceptThread = null
        }
        state = ServiceState.NONE
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray?) {
        // Create temporary object
        var connectedThread: ConnectedThread?
        // Synchronize a copy of the ConnectedThread
        synchronized(this@BluetoothService) {
            if (mState != ServiceState.CONNECTED) return
            connectedThread = mConnectedThread
        }
        // Perform the write unsynchronized
        connectedThread?.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed() {
        state = ServiceState.LISTEN
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        state = ServiceState.LISTEN
    }

    fun setStateListener(listener: ReplaySubject<ServiceState>) {
        this.stateListener = listener
    }

    fun setReadMessageListener(listener: ReplaySubject<String>) {
        this.readMessageListener = listener
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private inner class AcceptThread : Thread() {

        // The local server socket
        private val mmServerSocket: BluetoothServerSocket?

        init {
            var tmp: BluetoothServerSocket? = null
            // Create a new listening server socket
            try {
                tmp = mBluetoothAdapter?.listenUsingRfcommWithServiceRecord(NAME, MY_UUID)
            } catch (e: IOException) {
            }
            mmServerSocket = tmp
        }


        override fun run() {
            name = "AcceptThread"
            var socket: BluetoothSocket? = null
            // Listen to the server socket if we're not connected
            while (mState != ServiceState.CONNECTED) {
                socket = try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    break
                }
                // If a connection was accepted
                if (socket != null) {
                    synchronized(this@BluetoothService) {
                        when (mState) {
                            // Situation normal. Start the connected thread.
                            ServiceState.LISTEN, ServiceState.CONNECTING ->
                                connected(socket, socket.remoteDevice)
                            // Either not ready or already connected. Terminate new socket.
                            ServiceState.NONE, ServiceState.CONNECTED ->
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                }
                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket?

        init {
            var tmp: BluetoothSocket? = null
            /**
             * Get a BluetoothSocket for a connection with the given BluetoothDevice
             */
            try {
                tmp =
                    mmDevice.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
            }
            mmSocket = tmp
        }

        override fun run() {
            name = "ConnectThread"
            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter?.cancelDiscovery()
            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket?.connect()
            } catch (e: IOException) {
                connectionFailed()
                // Close the socket
                try {
                    mmSocket?.close()
                } catch (e2: IOException) {
                }
                // Start the service over to restart listening mode
                this@BluetoothService.start()
                return
            }
            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothService) { mConnectThread = null }
            // Start the connected thread
            connected(mmSocket, mmDevice)
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket?) : Thread() {

        private val inputStream: InputStream?

        private val outputStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmSocket?.inputStream
                tmpOut = mmSocket?.outputStream
            } catch (e: IOException) {
            }
            inputStream = tmpIn
            outputStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    read(buffer)
                } catch (e: IOException) {
                    connectionLost()
                    break
                }
            }
        }

        // Read from the InputStream
        fun read(buffer: ByteArray?) {
            buffer?.let {
                val bytes = inputStream?.read(buffer) ?: -1
                val message = handler.obtainMessage(0, bytes, -1, buffer)
                val readBuf = message.obj as ByteArray
                val readMessage = String(readBuf, 0, message.arg1)
                readMessageListener?.onNext(readMessage)
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        fun write(buffer: ByteArray?) {
            buffer?.let {
                try {
                    outputStream?.write(it)
                } catch (e: IOException) {
                    Timber.e(e.localizedMessage)
                }
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Timber.e(e.localizedMessage)
            }
        }
    }

}