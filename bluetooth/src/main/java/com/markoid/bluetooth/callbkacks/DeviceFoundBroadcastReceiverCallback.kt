package com.markoid.bluetooth.callbkacks

import android.bluetooth.BluetoothDevice

interface DeviceFoundBroadcastReceiverCallback {
    fun onScanOperationFinished()
    fun onNewDeviceFound(device: BluetoothDevice)
}