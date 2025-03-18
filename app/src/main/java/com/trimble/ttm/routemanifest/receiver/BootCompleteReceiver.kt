package com.trimble.ttm.routemanifest.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.routemanifest.service.AuthenticationForegroundService
import com.trimble.ttm.routemanifest.utils.BOOT_COMPLETE_TRACKER_TAG
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action.equals("android.intent.action.BOOT_COMPLETED").not()) return
        Log.i(BOOT_COMPLETE_TRACKER_TAG, "Device is rebooted!")
        // On device reboot start RouteManifestForegroundService
        context?.startForegroundServiceIfNotStartedPreviously(AuthenticationForegroundService::class.java)
    }
}