package com.trimble.ttm.workfloweventscommunication.utils

/*
 * *
 *  * Copyright Trimble Inc., 2023 All rights reserved.
 *  *
 *  * Licensed Software Confidential and Proprietary Information of Trimble Inc.,
 *   made available under Non-Disclosure Agreement OR License as applicable.
 *
 *   Product Name: TTM - Driver Workflow
 *
 *   Author: Koushik Kumar V
 *
 *   Created On: 29-11-2023
 *
 *   Abstract: Utils file
 * *
 */

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

inline fun <reified T> fromJsonString(json: String): T? {
    return try {
        Gson().fromJson(json, object : TypeToken<T>() {}.type)
    }catch (e : Exception){
        Log.e(WORKFLOW_EVENTS_COMMUNICATION, "Failed to convert JSON string to the specified type. JSON: $json, Exception: ${e.stackTraceToString()}")
        null
    }
}

fun PackageManager.getAppLabel(packageName: String, flag: Int) = getApplicationLabel(getAppInfo(packageName, flag)).toString()

fun PackageManager.getAppInfo(packageName: String, flag: Int) = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flag.toLong()))
} else {
    getApplicationInfo(packageName, flag)
}