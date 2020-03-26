package com.markoid.bluetoothmanager

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_devices.view.*

class DevicesAdapter :
    HeaderRecyclerViewAdapter<DevicesAdapter.HeaderViewHolder, DevicesAdapter.ItemViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()

    private var mListener: ((device: BluetoothDevice) -> Unit)? = null

    override fun getHeaderViewHolder(parent: ViewGroup): HeaderViewHolder =
        HeaderViewHolder(parent.inflate(R.layout.item_devices))

    override fun getItemViewHolder(parent: ViewGroup): ItemViewHolder =
        ItemViewHolder(parent.inflate(R.layout.item_devices))

    override fun getElementsCount(): Int = this.devices.size + 1

    override fun getHeaderViewType(position: Int): Boolean = position == 0

    override fun onBindHeaderView(holder: HeaderViewHolder, position: Int) {
    }

    override fun onBindItemView(holderItem: ItemViewHolder, position: Int) = with(holderItem) {
        setData()
        setClickListener()
    }

    fun setClickListener(listener: (device: BluetoothDevice) -> Unit) {
        this.mListener = listener
    }

    fun setData(data: List<BluetoothDevice>) {
        this.devices.clear()
        this.devices.addAll(data)
        notifyDataSetChanged()
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class ItemViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {

        private val context = this.view.context

        private val device: BluetoothDevice
            get() = devices[adapterPosition - 1]

        fun setData() = with(this.view) {
            val name: String =
                if (device.name != null && device.name.isNotEmpty()) device.name else "Unknown"
            this.device_name.text = name
            this.device_address.text = device.address
            this.device_status.text = "Disconnected"
            this.device_status.setTextColor(getColor(R.color.disconnected))
        }

        fun setClickListener() {
            this.itemView.setOnClickListener {
                mListener?.let { it(device) }
            }
        }

        private fun getColor(color: Int) =
            ContextCompat.getColor(context, color)

    }

}

private fun ViewGroup.inflate(layout: Int): View =
    LayoutInflater.from(this.context).inflate(layout, this, false)
