package com.trimble.ttm.formlibrary.manager

import android.app.Application
import android.app.NotificationManager
import android.content.Context

fun getNotificationManager(application: Application): NotificationManager =
    application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager