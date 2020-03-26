package com.markoid.bluetoothmanager

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class HeaderRecyclerViewAdapter<HEADERVIEW : RecyclerView.ViewHolder, ITEMVIEW : RecyclerView.ViewHolder> :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val HEADER_TYPE = 234
        const val ITEM_TYPE = 591
    }

    abstract fun getHeaderViewHolder(parent: ViewGroup): HEADERVIEW

    abstract fun getItemViewHolder(parent: ViewGroup): ITEMVIEW

    abstract fun getElementsCount(): Int

    abstract fun onBindHeaderView(holder: HEADERVIEW, position: Int)

    abstract fun onBindItemView(holder: ITEMVIEW, position: Int)

    abstract fun getHeaderViewType(position: Int): Boolean

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            HEADER_TYPE -> getHeaderViewHolder(parent)
            else -> getItemViewHolder(parent)
        }

    override fun getItemCount(): Int =
        this.getElementsCount()

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
        if (holder.itemViewType == HEADER_TYPE) {
            (holder as? HEADERVIEW)?.let {
                this.onBindHeaderView(it, position)
            } ?: throw IllegalStateException()
        } else {
            (holder as? ITEMVIEW)?.let {
                this.onBindItemView(it, position)
            } ?: throw IllegalStateException()
        }

    override fun getItemViewType(position: Int): Int = if (getHeaderViewType(position)) {
        HEADER_TYPE
    } else {
        ITEM_TYPE
    }

}