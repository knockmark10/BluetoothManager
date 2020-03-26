package com.markoid.bluetooth.receivers

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.markoid.bluetooth.callbkacks.DeviceFoundBroadcastReceiverCallback

class DeviceFoundBroadcastReceiver : BroadcastReceiver() {

    private var isRegistered = false

    private var mListener: DeviceFoundBroadcastReceiverCallback? = null

    override fun onReceive(p0: Context?, intent: Intent?) {
        when (intent?.action ?: "") {
            BluetoothDevice.ACTION_FOUND -> {
                intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    ?.let { device -> this.mListener?.onNewDeviceFound(device) }
            }
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> this.mListener?.onShowLoading(false)
        }
    }


    fun registerReceiver(activity: Activity, filter: IntentFilter): Intent? = try {
        if (!this.isRegistered) activity.registerReceiver(this, filter) else null
    } finally {
        this.isRegistered = true
    }

    fun unregisterReceiver(activity: Activity): Boolean =
        this.isRegistered && this.performUnregisterReceiver(activity)

    private fun performUnregisterReceiver(activity: Activity): Boolean {
        activity.unregisterReceiver(this)
        this.isRegistered = false
        return true
    }

    fun setBroadcastListener(listener: DeviceFoundBroadcastReceiverCallback) {
        this.mListener = listener
    }

}