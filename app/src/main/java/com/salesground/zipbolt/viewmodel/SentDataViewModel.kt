package com.salesground.zipbolt.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesground.zipbolt.model.DataToTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SentDataViewModel : ViewModel() {

    private val sentDataItemsNormalList: MutableList<DataToTransfer> = mutableListOf()
    private val _sentDataItems = MutableLiveData<MutableList<DataToTransfer>>(mutableListOf())
    val sentDataItems: LiveData<MutableList<DataToTransfer>>
        get() = _sentDataItems

    private val _updatedSentDataItemIndex = MutableLiveData(-1)
    val updatedSentDataItemIndex: LiveData<Int>
        get() = _updatedSentDataItemIndex

    private val _canceledSentDataItemIndex = MutableLiveData(-1)
    val canceledSentDataItemIndex: LiveData<Int>
        get() = _canceledSentDataItemIndex


    fun addCollectionOfDataToTransferToSentDataItems(collectionOfDataToTransfer: MutableList<DataToTransfer>) {
        collectionOfDataToTransfer.map {
            it.transferStatus = DataToTransfer.TransferStatus.TRANSFER_ONGOING
        }
        sentDataItemsNormalList.addAll(collectionOfDataToTransfer)
        viewModelScope.launch(Dispatchers.Main) {
            _sentDataItems.value = sentDataItemsNormalList
        }
    }


    fun dataTransferCompleted(dataToTransfer: DataToTransfer) {
        sentDataItemsNormalList.find {
            it.dataUri == dataToTransfer.dataUri
        }?.let {
            it.transferStatus = DataToTransfer.TransferStatus.TRANSFER_COMPLETE
            viewModelScope.launch(Dispatchers.Main) {
                _updatedSentDataItemIndex.value = sentDataItemsNormalList.indexOf(it)
            }
        }
    }


    fun cancelDataTransfer(dataToTransfer: DataToTransfer) {
        sentDataItemsNormalList.find {
            it.dataUri == dataToTransfer.dataUri
        }?.let {
            val removedIndex = sentDataItemsNormalList.indexOf(it)
            sentDataItemsNormalList.remove(it)
            viewModelScope.launch(Dispatchers.Main) {
                _canceledSentDataItemIndex.value = removedIndex
            }
        }
    }

}