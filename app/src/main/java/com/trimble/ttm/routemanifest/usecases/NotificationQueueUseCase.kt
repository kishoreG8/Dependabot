package com.trimble.ttm.routemanifest.usecases

import androidx.datastore.preferences.core.Preferences
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.FcmData
import com.trimble.ttm.routemanifest.utils.Utils

class NotificationQueueUseCase(val dataStoreManager: DataStoreManager) {

    suspend fun getEnqueuedNotificationsList(notificationType: Preferences.Key<String>): List<FcmData> {
        // This code will see if there is any notification stored on the data store, we fetch the fcmData and return the list with them
        val notificationListString = dataStoreManager.getValue(
            notificationType,
            ""
        )
        var fcmDataList: MutableList<FcmData> = mutableListOf()
        if (notificationListString.isNotEmpty()) {
            fcmDataList =
                Utils.fromJsonString(notificationListString) ?: fcmDataList
            dataStoreManager.removeItem(notificationType)
        }
        return fcmDataList
    }

    suspend fun enqueueNotifications(fcmData: FcmData, notificationType: Preferences.Key<String>) {
        var fcmDataList = mutableListOf<FcmData>()
        val currentNotificationData =
            dataStoreManager.getValue(notificationType, "")
        // We check if there are any notification saved already
        if (currentNotificationData.isEmpty()) {
            // If there is none, we save the notification that we just received
            fcmDataList.add(fcmData)
            dataStoreManager.setValue(
                notificationType,
                Utils.toPrettyJsonString(fcmDataList)
            )
        } else {
            // Otherwise, we fetch the previous notification data, add the new one
            // to the list and save it again.
            fcmDataList = Utils.fromJsonString(currentNotificationData) ?: mutableListOf()
            fcmDataList.add(fcmData)
            dataStoreManager.setValue(
                notificationType,
                Utils.toPrettyJsonString(fcmDataList)
            )
        }
    }

}