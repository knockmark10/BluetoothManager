package com.markoid.bluetooth.callbkacks

import android.bluetooth.BluetoothDevice
import com.markoid.bluetooth.states.ServiceState

interface BluetoothManagerCallback {
    fun onNewDeviceFound(devices: List<BluetoothDevice>)
    fun onBluetoothNotCompatible()
    fun onBluetoothAvailable()
    fun onScanProgressVisibility(visibility: Int) {}
    fun onServiceStateChanged(state: ServiceState) {}
    fun onMessageReceived(message: String) {}
}