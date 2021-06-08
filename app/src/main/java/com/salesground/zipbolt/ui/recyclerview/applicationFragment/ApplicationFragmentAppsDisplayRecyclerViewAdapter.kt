package com.salesground.zipbolt.ui.recyclerview.applicationFragment

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesground.zipbolt.model.DataToTransfer
import com.salesground.zipbolt.ui.recyclerview.DataToTransferRecyclerViewItemClickListener

class ApplicationFragmentAppsDisplayRecyclerViewAdapter(
    private val dataToTransferRecyclerViewItemClickListener:
    DataToTransferRecyclerViewItemClickListener,
    private val clickedApplications: MutableList<DataToTransfer>
) : ListAdapter<DataToTransfer, RecyclerView.ViewHolder>(
    ApplicationFragmentAppsDisplayRecyclerViewAdapterDiffUtil
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ApplicationLayoutItemViewHolder.createViewHolder(
            parent,
            dataToTransferRecyclerViewItemClickListener
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ApplicationLayoutItemViewHolder) {
            val currentItem = getItem(position)
            holder.bindApplicationDetails(
                currentItem,
                clickedApplications.contains(currentItem)
            )
        }
    }

    object ApplicationFragmentAppsDisplayRecyclerViewAdapterDiffUtil :
        DiffUtil.ItemCallback<DataToTransfer>() {
        override fun areItemsTheSame(
            oldItem: DataToTransfer,
            newItem: DataToTransfer
        ): Boolean {
            return oldItem.dataUri == newItem.dataUri
        }

        override fun areContentsTheSame(
            oldItem: DataToTransfer,
            newItem: DataToTransfer
        ): Boolean {
            return oldItem == newItem
        }
    }
}

