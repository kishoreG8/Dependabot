package com.trimble.ttm.routemanifest.utils

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.clientVersionStalenessDays
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.totalBytesToDownload
import com.google.android.play.core.ktx.updatePriority
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


fun checkAppUpdate(context: Context, coroutineScope: CoroutineScope, coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()) {
    val logTag = "APP_UPDATE"
    coroutineScope.launch(coroutineDispatcherProvider.io()) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(context)
            val updateInfo = appUpdateManager.requestAppUpdateInfo()
            if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                Log.n(
                    logTag, "Update Availability values",
                    null,
                    "IsAvailable" to "available",
                    "StalenessDays" to updateInfo.clientVersionStalenessDays,
                    "UpdatePriority" to updateInfo.updatePriority,
                    "AvailableVersionCode" to updateInfo.availableVersionCode(),
                    "TotalBytesToDownload" to updateInfo.totalBytesToDownload,
                    "FlexibleUpdateTypeAllowed" to updateInfo.isFlexibleUpdateAllowed,
                    "ImmediateUpdateTypeAllowed" to updateInfo.isImmediateUpdateAllowed
                )
            } else {
                Log.i(logTag, "Update Availability values", null, "IsAvailable" to "unavailable")
            }
        }catch (e: Exception){
            /*Logging as debug, since exception will happen only on debug builds*/
            Log.d(logTag, e.stackTraceToString())
        }
    }
}