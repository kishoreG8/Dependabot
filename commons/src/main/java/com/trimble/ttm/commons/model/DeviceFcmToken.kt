package com.trimble.ttm.commons.model

/* This data class is used to hold Device specific FCM token in Datastore*/
data class DeviceFcmToken(
    val fcmToken: String,
    val truckNumber: String
)
