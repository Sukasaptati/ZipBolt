package com.salesground.zipbolt.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import com.salesground.zipbolt.model.MediaModel
import com.salesground.zipbolt.repository.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val
    imageRepository: ImageRepository
) : ViewModel() {

    var allImagesOnDevice = mutableStateOf<MutableList<MediaModel>>(mutableListOf())
        private set

    private var _allImagesFetchedOnce = MutableLiveData<MutableList<MediaModel>>()
    val allImagesFetchedOnce: LiveData<MutableList<MediaModel>> = _allImagesFetchedOnce

    private var _selectedImagesForTransfer = MutableLiveData<MutableList<MediaModel>>(mutableListOf())
    val selectedImagesForTransfer : LiveData<MutableList<MediaModel>> = _selectedImagesForTransfer

    private val imagesList: MutableList<MediaModel> = mutableListOf()

    init {
        fetchAllImagesOnDeviceOnce()
        addImages()
    }

    fun imageSelected(imageSelected : MediaModel){
        _selectedImagesForTransfer.value?.add(imageSelected)
    }

    private fun fetchAllImagesOnDeviceOnce() {
        viewModelScope.launch(Dispatchers.IO) {
            val imagesOnDevice = imageRepository.fetchAllImagesOnDeviceOnce()
            withContext(Dispatchers.Main) {
                _allImagesFetchedOnce.value = imagesOnDevice
            }
        }
    }

    private fun addImages() {
        viewModelScope.launch {
            imageRepository.fetchAllImagesOnDevice().collect {
                imagesList.add(it)
                allImagesOnDevice.value = imagesList
            }
        }
    }
}