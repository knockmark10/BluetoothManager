package com.markoid.bluetoothmanager

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.markoid.bluetooth.callbkacks.BluetoothManagerCallback
import com.markoid.bluetooth.manager.BluetoothManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), BluetoothManagerCallback {

    private val bluetoothManager by lazy {
        BluetoothManager.Builder
            .setDiscoverableTime(120L, false)
            .setScanTime(10L, false)
            .build(this)
            ?.requestLocationPermission()
            ?.requestBluetoothEnabling()
            ?.setBluetoothListener(this)
    }

    private val attachedDevicesAdapter by lazy { DevicesAdapter() }

    private val newFoundDevicesAdapter by lazy { DevicesAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        setupAttachedDevicesList()

        setupNewFoundDevicesList()

        setClickListener()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BluetoothManager.REQUEST_ENABLE_BT) {
            setupAttachedDevicesList()
        }
    }

    private fun setupAttachedDevicesList() {
        val devices = bluetoothManager?.getSynchronizedDevices() ?: emptyList()
        val visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
        this.devices_attached_title.visibility = visibility
        this.devices_attached_list.visibility = visibility

        this.devices_attached_list.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        this.devices_attached_list.adapter = attachedDevicesAdapter
        this.attachedDevicesAdapter.setData(devices)
        this.attachedDevicesAdapter.setClickListener { this.bluetoothManager?.connectDevice(it) }
    }

    private fun setupNewFoundDevicesList() {
        this.devices_found_list.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        this.devices_found_list.adapter = newFoundDevicesAdapter
        this.newFoundDevicesAdapter.setClickListener { this.bluetoothManager?.connectDevice(it) }
    }

    private fun setClickListener() {
        this.devices_scan.setOnClickListener {
            this.bluetoothManager?.scanDevices()
        }

        this.devices_make_discoverable.setOnClickListener {
            this.bluetoothManager?.makeDeviceVisibleForOtherDevices()
        }
    }

    override fun onNewDeviceFound(devices: List<BluetoothDevice>) {
        val visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
        this.devices_found_title.visibility = visibility
        this.devices_found_loading.visibility = visibility
        this.devices_found_list.visibility = visibility
        this.newFoundDevicesAdapter.setData(devices)
    }

    override fun onBluetoothAvailable() {

    }

    override fun onScanProgressVisibility(visibility: Int) {
        this.devices_found_loading.visibility = visibility
    }

}
