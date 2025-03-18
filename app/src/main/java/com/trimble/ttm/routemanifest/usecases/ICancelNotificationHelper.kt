package com.trimble.ttm.routemanifest.usecases

/**
 * Encapsulates all different scenarios for canceling notifications
 * */
interface ICancelNotificationHelper {

    /**
     * Cancel a notification from a Creation or Edition Trip
     * */
    fun cancelEditOrCreationTripNotification()

}