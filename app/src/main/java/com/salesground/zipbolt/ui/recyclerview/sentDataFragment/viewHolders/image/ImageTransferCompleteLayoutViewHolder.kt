package com.salesground.zipbolt.ui.recyclerview.ongoingDataTransferRecyclerViewComponents.viewHolders.image

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.salesground.zipbolt.databinding.ImageTransferLayoutItemBinding
import com.salesground.zipbolt.model.DataToTransfer
import com.salesground.zipbolt.ui.recyclerview.receivedDataFragment.viewHolders.image.ImageReceiveCompleteLayoutViewHolder

class ImageTransferCompleteLayoutViewHolder(
    private val imageTransferLayoutItemBinding: ImageTransferLayoutItemBinding
) : RecyclerView.ViewHolder(imageTransferLayoutItemBinding.root) {

    fun bindImageData(dataToTransfer: DataToTransfer) {
        imageTransferLayoutItemBinding.run {
            Glide.with(imageWaitingForTransferLayoutItemImageView)
                .load(dataToTransfer.dataUri)
                .into(imageWaitingForTransferLayoutItemImageView)

            imageTransferLayoutItemLoadingImageShimmer.run{
                stopShimmer()
                hideShimmer()
            }
        }
    }

    companion object {
        fun createViewHolder(parent: ViewGroup): ImageReceiveCompleteLayoutViewHolder {
            val layoutBinding = ImageTransferLayoutItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )

            return ImageReceiveCompleteLayoutViewHolder(layoutBinding)
        }
    }
}