package com.markoid.bluetooth.callbkacks

import android.bluetooth.BluetoothDevice
import com.markoid.bluetooth.states.ServiceState

interface BluetoothManagerCallback {
    fun onNewDeviceFound(devices: List<BluetoothDevice>)
    fun onBluetoothAvailable()
    fun onLocationPermissionAccepted() {}
    fun onLocationPermissionRejected() {}
    fun onScanProgressVisibility(visibility: Int) {}
    fun onScanFinished() {}
    fun onServiceStateChanged(state: ServiceState) {}
    fun onMessageReceived(message: String) {}
}