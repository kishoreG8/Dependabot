package com.trimble.ttm.commons.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
const val CUSTOM_WORKFLOW_FOREGROUND_SERVICE_CHANNEL_ID = "0002"
const val CUSTOM_WORK_FLOW_FOREGROUND_SERVICE_ID = 113

const val EVENTS_PROCESSING_FOREGROUND_SERVICE_CHANNEL_ID = "0001"
const val EVENTS_PROCESSING_FOREGROUND_SERVICE_ID = 112

const val TRIP_PANEL_NEGATIVE_ACTION_FOREGROUND_SERVICE_CHANNEL_ID = "0003"
const val TRIP_PANEL_NEGATIVE_ACTION_FOREGROUND_SERVICE_ID = 114

const val AUTHENTICATION_FOREGROUND_SERVICE_CHANNEL_ID = "0004"
const val AUTHENTICATION_FOREGROUND_SERVICE_ID = 115

@RequiresApi(Build.VERSION_CODES.O)
fun Context.createNotification(
    channelId: String,
    channelName: String,
    contentTitle: String,
    contentText: String,
    icon:Int,
    pendingIntent: PendingIntent? = null,
): Notification {
    val notificationChannel =
        NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
    notificationChannel.description = channelId
    notificationChannel.setSound(null, null)
    notificationChannel.lightColor = Color.BLUE
    notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

    val notificationManager =
        this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(notificationChannel)

    return Notification.Builder(this, channelId).let { builder ->
        builder.setContentTitle(contentTitle)
        builder.setContentText(contentText)
        builder.setSmallIcon(icon)
        builder.setOngoing(true)
        pendingIntent?.let { builder.setContentIntent(it) }
        builder.build()
    }
}