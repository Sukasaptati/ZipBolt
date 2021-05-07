package com.salesground.zipbolt.ui.fragments

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.salesground.zipbolt.MainActivity
import com.salesground.zipbolt.R
import com.salesground.zipbolt.databinding.FragmentImageBinding
import com.salesground.zipbolt.model.DataToTransfer
import com.salesground.zipbolt.model.ui.ImagesDisplayModel
import com.salesground.zipbolt.ui.customviews.ChipsLayout
import com.salesground.zipbolt.ui.recyclerview.imagefragment.DeviceImagesDisplayRecyclerViewAdapter
import com.salesground.zipbolt.ui.recyclerview.imagefragment.DeviceImagesDisplayViewHolderType
import com.salesground.zipbolt.viewmodel.ImagesViewModel

class ImageFragment : Fragment() {
    private val imagesViewModel: ImagesViewModel by activityViewModels()
    private lateinit var dAdapter: DeviceImagesDisplayRecyclerViewAdapter
    private lateinit var chipsLayout: ChipsLayout
    private var selectedCategory: String = "All"
    private val bucketNames = mutableListOf<String>()
    private var mainActivity: MainActivity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.let {
            mainActivity = it as MainActivity
        }
        dAdapter = DeviceImagesDisplayRecyclerViewAdapter(
            onImageClicked = {
                if (imagesViewModel.collectionOfClickedImages.contains(it)
                    && it is ImagesDisplayModel.DeviceImageDisplay
                ) {
                    // remove image
                    mainActivity?.removeFromDataToTransferList(it.deviceImage)
                } else if (it is ImagesDisplayModel.DeviceImageDisplay) {
                    // add image
                    mainActivity?.addToDataToTransferList(it.deviceImage)
                }
                imagesViewModel.onImageClicked(it)
            },
            imagesClicked = imagesViewModel.collectionOfClickedImages
        )
        observeViewModelLiveData()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = FragmentImageBinding.inflate(
            inflater, container,
            false
        )
        rootView.apply {
            chipsLayout = imagesCategoryChipsLayout
            fragmentImageRecyclerview.apply {
                setHasFixedSize(true)

                val spanCount: Int = when (resources.configuration.orientation) {
                    Configuration.ORIENTATION_PORTRAIT -> {
                        if (resources.displayMetrics.density > 3.1 || resources.configuration.densityDpi < 245) {
                            3
                        } else {
                            4
                        }
                    }
                    else -> {
                        if (resources.displayMetrics.density > 3.1 || resources.configuration.densityDpi < 245) {
                            5
                        } else {
                            7
                        }
                    }
                }
                val gridLayoutManager = GridLayoutManager(context, spanCount)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (dAdapter.getItemViewType(
                            position
                        )) {
                            DeviceImagesDisplayViewHolderType.IMAGE.type -> 1
                            DeviceImagesDisplayViewHolderType.GROUP_HEADER.type -> spanCount
                            else -> 1
                        }
                    }
                }
                adapter = dAdapter
                layoutManager = gridLayoutManager
            }
            return rootView.root
        }
    }

    private fun observeViewModelLiveData() {
        imagesViewModel.deviceImagesGroupedByDateModified.observe(this) {
            dAdapter.submitList(it)
        }
        imagesViewModel.deviceImagesBucketName.observe(this) {
            it?.let { it ->

                selectedCategory = imagesViewModel.chosenBucket.value ?: selectedCategory

                val buckets =
                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        it.take(14)
                    } else it.take(25)


                buckets.forEach { bucket ->
                    bucketNames.add(bucket.bucketName)
                }

                bucketNames.forEach { bucketName ->
                    val layout =
                        layoutInflater.inflate(R.layout.category_chip, chipsLayout, false)
                    val chip = layout.findViewById<Chip>(R.id.category_chip)
                    chip.text = when {
                        bucketName.length > 13 -> {
                            "${bucketName.take(10)}..."
                        }
                        bucketName.length < 4 -> {
                            " $bucketName "
                        }
                        else -> {
                            bucketName
                        }
                    }
                    chip.setOnClickListener {

                        chip.isChecked = true
                        if (bucketName != imagesViewModel.chosenBucket.value) {
                            imagesViewModel.filterDeviceImages(bucketName = bucketName)
                            try {
                                val indexOfLastSelectedBucket =
                                    bucketNames.indexOf(selectedCategory)
                                chipsLayout.refresh(indexOfLastSelectedBucket)
                            } catch (noSuchElementException: Exception) {

                            }
                            selectedCategory =
                                imagesViewModel.chosenBucket.value ?: selectedCategory
                        }
                    }
                    if (bucketName == imagesViewModel.chosenBucket.value) {
                        chip.isChecked = true
                    }
                    chipsLayout.addView(chip)
                }
            }
        }
    }
}