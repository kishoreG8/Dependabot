package com.trimble.ttm.routemanifest.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.routemanifest.service.AuthenticationForegroundService
import com.trimble.ttm.routemanifest.utils.APP_UPDATE_TRACKER_TAG
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action.equals("android.intent.action.MY_PACKAGE_REPLACED").not()) return
        Log.i(APP_UPDATE_TRACKER_TAG, "App has been updated!")
        // On app update start RouteManifestForegroundService
        // to register geofence broadcast receiver and establish trip
        // panel connection
        context?.startForegroundServiceIfNotStartedPreviously(AuthenticationForegroundService::class.java)
    }
}