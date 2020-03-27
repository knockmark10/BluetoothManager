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

    private var mScanTime = 120L

    private var mDiscoverableTime = 120L

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

    private val mBluetoothAdapter: BluetoothAdapter by lazy { getDefaultAdapter() }

    private val mBluetoothService by lazy { BluetoothService(this.mBluetoothAdapter) }

    // <-----------------------------PUBLIC METHODS ----------------------------------------------->

    init {
        setupBluetoothConfigurations()
    }

    /**
     * Make device visible for some time.
     */
    fun makeDeviceVisibleForOtherDevices() {
        val discoverableIntent = Intent(ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(EXTRA_DISCOVERABLE_DURATION, mDiscoverableTime)
        }
        this.mActivity.startActivity(discoverableIntent)
        this.scheduleTask(mDiscoverableTime, TimeUnit.SECONDS, ScheduleState.DISCOVERY) {
            if (this.mLoopDiscovery) makeDeviceVisibleForOtherDevices()
        }
    }

    fun setBluetoothListener(listener: BluetoothManagerCallback): BluetoothManager {
        this.mListener = listener
        return this
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
     * Is can be scheduled to perform active scanning infinitely
     */
    fun scanDevices() {
        if (this.isScanReady) {
            this.mNewDevicesList.clear()
            val newDeviceFilter = IntentFilter()
            newDeviceFilter.addAction(BluetoothDevice.ACTION_FOUND)
            newDeviceFilter.addAction(ACTION_DISCOVERY_FINISHED)
            this.mDeviceFoundBroadcastReceiver.registerReceiver(this.mActivity, newDeviceFilter)
            this.mBluetoothAdapter.startDiscovery()
            showLoading(true)
            this.scheduleTask(this.mScanTime, TimeUnit.SECONDS, ScheduleState.SCAN) {
                cancelDiscovery()
            }
        }
    }

    /**
     * Stops scanning operation
     */
    fun stopScan() {
        this.cancelDiscovery()
    }

    /**
     * Retrieve a list of synchronized devices.
     */
    fun getSynchronizedDevices(): List<BluetoothDevice> {
        return (this.mBluetoothAdapter.bondedDevices ?: emptySet()).toList()
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

    fun requestLocationPermission(): BluetoothManager {
        if (!this.mPermissionManager.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            this.mPermissionManager.requestSinglePermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return this
    }

    fun requestBluetoothEnabling(): BluetoothManager {
        if (!this.mBluetoothAdapter.isEnabled) {
            showBluetoothEnableDialog()
        } else {
            this.mListener?.onBluetoothAvailable()
        }
        return this
    }

    fun isLocationGranted(): Boolean =
        this.mPermissionManager.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun isBluetoothEnabled(): Boolean =
        this.mBluetoothAdapter.isEnabled

    // <-----------------------------INTERNAL CALLS ----------------------------------------------->
    private fun setupBluetoothConfigurations() {
        checkBluetoothEnablement()
        setupNewDevicesList()
        setBroadcastListener()
        startBluetoothService()
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
        if (!this.mBluetoothAdapter.isEnabled) {
            showBluetoothEnableDialog()
        } else {
            this.mListener?.onBluetoothAvailable()
        }
    }

    /**
     * Cancels bluetooth's discovery operation
     */
    private fun cancelDiscovery() {
        this.mBluetoothAdapter.cancelDiscovery()
    }

    /**
     * Sets up everything for starting
     */
    private fun startBluetoothService() {
        this.mBluetoothService.start()
        setupConnectState()
        setReadMessageListener()
    }

    /**
     * Show dialog to require permission to enable bluetooth
     */
    private fun showBluetoothEnableDialog() {
        val enableIntent = Intent(ACTION_REQUEST_ENABLE)
        this.mActivity.startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
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

        private var scanTime = 120L

        private var discoverableTime = 120L

        private var loopDiscovery = false

        private var listener: BluetoothManagerCallback? = null

        /**
         * Set time for scanning operation, while setting its maximum time to 5 minutes
         */
        fun setScanTime(seconds: Long, useLooper: Boolean): Builder {
            val fixedTime = if (seconds > 300L) 300L else seconds
            this.scanTime = fixedTime
            this.loopScan = useLooper
            return this
        }

        /**
         * Set time for scanning operation, while setting its maximum time to 2 minutes
         */
        fun setDiscoverableTime(seconds: Long, useLooper: Boolean): Builder {
            val fixedTime = if (seconds > 120) 120 else seconds
            this.discoverableTime = fixedTime
            this.loopDiscovery = useLooper
            return this
        }

        /**
         * Returns an instance of BluetoothManager
         * If device doesn't support bluetooth, null object will be returned
         */
        fun build(activity: Activity): BluetoothManager? =
            if (getDefaultAdapter() == null) null else BluetoothManager(activity).apply {
                this.mLoopScan = loopScan
                this.mDiscoverableTime = discoverableTime
                this.mLoopDiscovery = loopDiscovery
                this.mListener = listener
                this.mScanTime = scanTime
            }
    }

    // <------------------------------OVERRIDES --------------------------------------------------->

    override fun onPermissionDenied(permission: String) {
        this.mListener?.onLocationPermissionRejected()
    }

    override fun onPermissionGranted(permissions: String) {
        this.mListener?.onLocationPermissionAccepted()
        setupBluetoothConfigurations()
    }

    override fun onScanOperationFinished() {
        this.mListener?.onScanFinished()
        showLoading(false)
        if (this.mLoopScan) scanDevices()
    }

    override fun onNewDeviceFound(device: BluetoothDevice) {
        this.mNewDevicesList.add(device)
        this.mListener?.onNewDeviceFound(this.mNewDevicesList.toList())
    }

}