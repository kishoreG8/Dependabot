package com.trimble.ttm.commons.repo

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Token Handler class used to retrieve fcm token for a particular device.
 * It will be used to send firebase push notifications to a specific device.
 * The Retrieved token will be stored in the firestore, which will be later used by cloud functions
 * to send fcm push notifications.
 */
class FCMDeviceTokenHandler {
    suspend fun fetchDeviceSpecificFcmTokenFromFirebase(): String = FirebaseMessaging.getInstance().token.await()
}