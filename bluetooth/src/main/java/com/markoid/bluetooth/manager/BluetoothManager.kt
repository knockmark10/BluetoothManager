package com.markoid.bluetooth.manager

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import com.android.permissionlibrary.callbacks.PermissionCallback
import com.android.permissionlibrary.managers.PermissionManager
import com.markoid.bluetooth.callbkacks.BluetoothManagerCallback
import com.markoid.bluetooth.callbkacks.DeviceFoundBroadcastReceiverCallback
import com.markoid.bluetooth.receivers.DeviceFoundBroadcastReceiver
import com.markoid.bluetooth.services.BluetoothService
import com.markoid.bluetooth.states.ScheduleState
import com.markoid.bluetooth.states.ServiceState
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.ReplaySubject
import java.util.concurrent.TimeUnit

/**
 * References: https://developer.android.com/guide/topics/connectivity/bluetooth#kotlin
 */
class BluetoothManager
private constructor(
    private val mActivity: Activity
) : PermissionCallback, DeviceFoundBroadcastReceiverCallback {

    companion object {
        const val REQUEST_ENABLE_BT = 1203
    }

    private var SCAN_TIME = 120L //120 seconds (2 minutes)

    private var DISCOVERABLE_TIME = 300L //300 seconds (5 minutes)

    private var mNotifyScanModeChange = false

    private var mLoopScan = false

    private var mLoopDiscovery = false

    private var isScanReady = false

    private var mListener: BluetoothManagerCallback? = null

    private val mServiceStateListener by lazy { ReplaySubject.create<ServiceState>() }

    private val mReadMessageListener by lazy { ReplaySubject.create<String>() }

    private val mNewDevicesList = mutableSetOf<BluetoothDevice>()

    private val disposables = mutableListOf<Pair<ScheduleState, Disposable>>()

    private val mDeviceFoundBroadcastReceiver by lazy { DeviceFoundBroadcastReceiver() }

    private val mPermissionManager by lazy { PermissionManager(this.mActivity, this) }

    private val mBluetoothAdapter: BluetoothAdapter? by lazy { getDefaultAdapter() }

    private val mBluetoothService by lazy { BluetoothService(this.mBluetoothAdapter) }

    init {
        setup()
    }

    // <-----------------------------PUBLIC METHODS ----------------------------------------------->

    /**
     * Make device visible for some time.
     */
    fun makeDeviceVisibleForOtherDevices() {
        val discoverableIntent = Intent(ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_TIME)
        }
        this.mActivity.startActivity(discoverableIntent)
        this.scheduleTask(DISCOVERABLE_TIME, TimeUnit.SECONDS, ScheduleState.DISCOVERY) {
            if (this.mLoopDiscovery) makeDeviceVisibleForOtherDevices()
        }
    }

    /**
     * Call it from onStop inside your activity's lifecycle
     */
    fun onStop() {
        this.cancelDiscovery()
        this.mBluetoothService.stop()
        clearDisposables()
        this.mDeviceFoundBroadcastReceiver.unregisterReceiver(this.mActivity)
    }

    /**
     * Perform discovery operation, is scan is available and every set up is ready.
     */
    fun scanDevices() {
        if (this.isScanReady) {
            val newDeviceFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            this.mDeviceFoundBroadcastReceiver.registerReceiver(this.mActivity, newDeviceFilter)
            this.mBluetoothAdapter?.startDiscovery()
            showLoading(true)
            this.scheduleTask(this.SCAN_TIME, TimeUnit.SECONDS, ScheduleState.SCAN) {
                cancelDiscovery()
                if (this.mLoopScan) scanDevices()
            }
        }
    }

    /**
     * Retrieve a list of synchronized devices.
     */
    fun getSynchronizedDevices(): List<BluetoothDevice> {
        checkBluetoothCompatibility()
        return (this.mBluetoothAdapter?.bondedDevices ?: emptySet()).toList()
    }

    fun connectDevice(device: BluetoothDevice) {
        this.mBluetoothService.connect(device)
    }

    /**
     * Send message to connected device
     */
    fun sendMessage(message: String) {
        val send = message.toByteArray()
        this.mBluetoothService.write(send)
    }

    // <-----------------------------INTERNAL CALLS ----------------------------------------------->
    /**
     * Set up basic configuration, such as bluetooth availability, enablement, location
     * permissions and other necessary configurations.
     */
    private fun setup() {
        if (!this.mPermissionManager.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            this.mPermissionManager.requestSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            setupBluetoothConfigurations()
        }
    }

    private fun setupBluetoothConfigurations() {
        if (checkBluetoothCompatibility()) {
            startBluetoothService()
            checkBluetoothEnablement()
            setupNewDevicesList()
            setupConnectState()
            setReadMessageListener()
            setBroadcastListener()
        }
    }

    private fun setupConnectState() {
        this.mBluetoothService.setStateListener(this.mServiceStateListener)
        val disposable = this.mServiceStateListener
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { this.mListener?.onServiceStateChanged(it) }
        addDisposable(ScheduleState.SERVICE_CHANGE, disposable)
    }

    private fun setReadMessageListener() {
        this.mBluetoothService.setReadMessageListener(this.mReadMessageListener)
        val disposable = this.mReadMessageListener
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { this.mListener?.onMessageReceived(it) }
        addDisposable(ScheduleState.READ_MESSAGE, disposable)
    }

    private fun setBroadcastListener() {
        this.mDeviceFoundBroadcastReceiver.setBroadcastListener(this)
    }

    private fun setupNewDevicesList() {
        this.mNewDevicesList.clear()
        this.isScanReady = true
    }

    /**
     * Checks if bluetooth is enabled
     */
    private fun checkBluetoothEnablement() {
        if (this.mBluetoothAdapter?.isEnabled == false) {
            showBluetoothEnableDialog()
        } else {
            this.mListener?.onBluetoothAvailable()
        }
    }

    /**
     * Cancels bluetooth's discovery operation
     */
    private fun cancelDiscovery() {
        val isDiscovering = this.mBluetoothAdapter?.isDiscovering ?: false
        if (checkBluetoothCompatibility() && isDiscovering) this.mBluetoothAdapter?.cancelDiscovery()
    }

    /**
     * Show dialog to require permission to enable bluetooth
     */
    private fun showBluetoothEnableDialog() {
        val enableIntent = Intent(ACTION_REQUEST_ENABLE)
        this.mActivity.startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
    }

    private fun startBluetoothService() {
        this.mBluetoothService.start()
    }

    private fun checkBluetoothCompatibility(): Boolean = if (this.mBluetoothAdapter == null) {
        this.mListener?.onBluetoothNotCompatible()
        false
    } else {
        true
    }

    private fun scheduleTask(
        time: Long,
        timeUnit: TimeUnit,
        state: ScheduleState,
        task: () -> Unit
    ) {
        val disposable = Completable.timer(time, timeUnit)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { task() }
        addDisposable(state, disposable)
    }

    private fun addDisposable(state: ScheduleState, disposable: Disposable) {
        this.disposables.firstOrNull { it.first == state }?.let { removeDisposable(state) }
        this.disposables.add(Pair(state, disposable))
    }

    private fun removeDisposable(state: ScheduleState) {
        this.disposables.firstOrNull { it.first == state }?.let {
            dispose(it.second)
            this.disposables.remove(it)
        }
    }

    private fun dispose(disposable: Disposable) {
        if (!disposable.isDisposed) disposable.dispose()
    }

    private fun clearDisposables() {
        this.disposables.forEach { dispose(it.second) }
        this.disposables.clear()
    }

    private fun showLoading(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        this.mListener?.onScanProgressVisibility(visibility)
    }

    // <--------------------------------BUILDER --------------------------------------------------->
    object Builder {

        private var loopScan = false

        private var discoverableTime = 300L

        private var loopDiscovery = false

        private var notifyScanModeChange = false

        private var listener: BluetoothManagerCallback? = null

        fun notifyScanModeChange(notify: Boolean): Builder {
            this.notifyScanModeChange = notify
            return this
        }

        fun useScanLooper(useLooper: Boolean): Builder {
            this.loopScan = useLooper
            return this
        }

        fun setDiscoverableTime(seconds: Long, useLooper: Boolean): Builder {
            this.discoverableTime = seconds
            this.loopDiscovery = useLooper
            return this
        }

        fun setBluetoothListener(listener: BluetoothManagerCallback): Builder {
            this.listener = listener
            return this
        }

        fun build(activity: Activity) = BluetoothManager(activity).apply {
            this.mLoopScan = loopScan
            this.DISCOVERABLE_TIME = discoverableTime
            this.mLoopDiscovery = loopDiscovery
            this.mListener = listener
            this.mNotifyScanModeChange = notifyScanModeChange
        }
    }

    // <------------------------------OVERRIDES --------------------------------------------------->

    override fun onPermissionDenied(permission: String) {
        this.mListener?.onBluetoothAvailable()
    }

    override fun onPermissionGranted(permissions: String) {
        setupBluetoothConfigurations()
    }

    override fun onShowLoading(boolean: Boolean) {
        showLoading(boolean)
    }

    override fun onNewDeviceFound(device: BluetoothDevice) {
        this.mNewDevicesList.add(device)
        this.mListener?.onNewDeviceFound(this.mNewDevicesList.toList())
    }

}