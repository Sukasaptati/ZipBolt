package com.salesground.zipbolt.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.salesground.zipbolt.model.DataToTransfer

class DataToTransferViewModel : ViewModel() {

    private var _collectionOfDataToTransfer: MutableList<DataToTransfer> = mutableListOf()
    val collectionOfDataToTransfer: MutableList<DataToTransfer>
        get() = _collectionOfDataToTransfer

    fun addDataToTransfer(dataToTransfer: DataToTransfer) {
        collectionOfDataToTransfer.add(dataToTransfer)
    }

    fun removeDataFromDataToTransfer(dataToTransfer: DataToTransfer) {
        collectionOfDataToTransfer.remove(dataToTransfer)
    }

    fun clearCollectionOfDataToTransfer() {
        _collectionOfDataToTransfer = mutableListOf()
    }
}