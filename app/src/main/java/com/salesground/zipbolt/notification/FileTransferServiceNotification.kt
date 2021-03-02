package com.salesground.zipbolt.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat


const val FILE_TRANSFER_SERVICE_NOTIFICATION_ID = "FileTransferServiceNotificationID"
const val FILE_TRANSFER_SERVICE_CHANNEL_NAME = "ZipBolt File Transfer Service Notification"

class FileTransferServiceNotification(private val notificationManager: NotificationManager) {


    fun createFTSNotificationChannel() {
        val ftsChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(FILE_TRANSFER_SERVICE_NOTIFICATION_ID, )
                .apply {
                    this.setName("ZipBolt File Transfer Service Notification")
                    setDescription("This notification channel is responsible for alerting you that ZipBolt is sharing files on the background")
                    setShowBadge(false)
                }.build()
        } else {
            TODO("VERSION.SDK_INT < O")
        }

        notificationManager.createNotificationChannel(ftsChannel)

    }
}