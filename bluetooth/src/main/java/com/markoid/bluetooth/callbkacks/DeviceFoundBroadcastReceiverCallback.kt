package com.markoid.bluetooth.callbkacks

import android.bluetooth.BluetoothDevice

interface DeviceFoundBroadcastReceiverCallback {
    fun onShowLoading(boolean: Boolean)
    fun onNewDeviceFound(device: BluetoothDevice)
}