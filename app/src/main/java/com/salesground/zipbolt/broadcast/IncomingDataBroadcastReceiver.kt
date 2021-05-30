package com.salesground.zipbolt.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.salesground.zipbolt.model.DataToTransfer


class IncomingDataBroadcastReceiver(private val dataReceiveListener: DataReceiveListener) :
    BroadcastReceiver() {

    interface DataReceiveListener {
        fun onDataReceive(
            dataDisplayName: String,
            dataUri: Uri?,
            dataSize: Long,
            dataType: Int,
            percentTransferred: Float = 0f,
            transferStatus: Int = DataToTransfer.TransferStatus.TRANSFER_WAITING.value
        )
    }

    companion object {
        const val INCOMING_DATA_BYTES_RECEIVED_ACTION =
            "com.salesground.speedforce.INCOMING_DATA_BYTES_RECEIVED_ACTION"
        const val INCOMING_FILE_NAME = "IncomingFileName"
        const val PERCENTAGE_OF_DATA_RECEIVED = "PercentageOfDataReceived"
        const val INCOMING_FILE_URI = "IncomingFileURI"
        const val INCOMING_FILE_SIZE = "IncomingFileSize"
        const val INCOMING_FILE_MIME_TYPE = "IncomingFileMimeType"
        const val INCOMING_FILE_TRANSFER_STATUS = "IncomingFileTransferStatus"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            when (intent.action) {
                INCOMING_DATA_BYTES_RECEIVED_ACTION -> {
                    val fileName = intent.getStringExtra(INCOMING_FILE_NAME) ?: ""
                    val percentageOfDataReceived = intent.getFloatExtra(PERCENTAGE_OF_DATA_RECEIVED, 0f)
                    val fileUri = intent.getParcelableExtra<Uri?>(INCOMING_FILE_URI)
                    val fileSize = intent.getLongExtra(INCOMING_FILE_SIZE, 0)
                    val fileType = intent.getIntExtra(
                        INCOMING_FILE_MIME_TYPE,
                        DataToTransfer.MediaType.IMAGE.value
                    )
                    val fileReceiveStatus = intent.getIntExtra(
                        INCOMING_FILE_TRANSFER_STATUS,
                        0
                    )
                    dataReceiveListener.onDataReceive(
                        fileName,
                        fileUri,
                        fileSize,
                        fileType,
                        percentageOfDataReceived,
                        fileReceiveStatus
                    )
                }
                else -> {

                }
            }
        }
    }
}