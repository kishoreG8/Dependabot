package com.trimble.ttm.routemanifest.utils.ext

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_FORM
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormActivityIntentActionData
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.routemanifest.ui.activities.ComposeFormActivity
import com.trimble.ttm.routemanifest.ui.activities.DispatchDetailActivity
import com.trimble.ttm.routemanifest.ui.activities.FormActivity
import com.trimble.ttm.routemanifest.ui.activities.StopDetailActivity
import me.drakeet.support.toast.ToastCompat

fun Context.makeLongLivingToast(message: CharSequence): Toast {
    return ToastCompat.makeText(this, message, Toast.LENGTH_LONG)
}

fun Context.startForegroundServiceIfNotStartedPreviously(serviceClass: Class<*>) {
    if (this.isServiceRunningInForeground(serviceClass).not()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            this.startService(Intent(this, serviceClass))
        else
            this.startForegroundService(Intent(this, serviceClass))
    }
}

/**
 * getRunningServices function is deprecated since Android O version.
 * From android version O onwards this method returns the running services of the caller
 * and does not return running of other applications */
fun Context.isServiceRunningInForeground(serviceClass: Class<*>): Boolean {
    val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
        if (serviceClass.name == service.service.className) {
            return service.foreground
        }
    }
    return false
}

fun PackageManager.getPackageInfoList(): List<PackageInfo> {
    return if (Build.VERSION.SDK_INT >= 33) {
        this.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
    } else {
        this.getInstalledPackages(0)
    }
}

fun Context.startDispatchFormActivity(
    isComposeEnabled: Boolean,
    path: String,
    dispatchFormPath: DispatchFormPath,
    isManualArrival: Boolean,
    isFormResponseSentToServer: Boolean
) {
    Log.n(TRIP_FORM,"OpenForm C:$isComposeEnabled isManual:$isManualArrival isSent:$isFormResponseSentToServer DispatchFromPath values stopName:${dispatchFormPath.stopName}, stopId:${dispatchFormPath.stopId}, actionId:${dispatchFormPath.actionId}, formId:${dispatchFormPath.formId}, formClass:${dispatchFormPath.formClass}")

    val isFormDestinationClass = if (!isComposeEnabled) {
        this@startDispatchFormActivity is FormActivity
    }  else {
        this@startDispatchFormActivity is ComposeFormActivity
    }

    val formFromDispatchOrStopDetail = (this@startDispatchFormActivity is DispatchDetailActivity
            || this@startDispatchFormActivity is StopDetailActivity
            || isFormDestinationClass)

    this.startActivity(
        FormActivityIntentActionData(
            isComposeEnabled = isComposeEnabled,
            containsStopData = formFromDispatchOrStopDetail,
            customerId = null,
            formId = null,
            formResponse = FormResponse(),
            driverFormPath = dispatchFormPath,
            isSecondForm = false,
            dispatchFormSavePath = path,
            isFormFromTripPanel = isManualArrival.not(),
            isFormResponseSentToServer = isFormResponseSentToServer
        ).buildIntent().apply {
            `package` = packageName
        }
    )
}

