package com.trimble.ttm.formlibrary.utils.ext

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING

fun Context.openActivityIfItsAlreadyInBackStack(activityName: String) {
    val activityManager: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    activityManager.appTasks.forEach {
        if ((it.taskInfo.topActivity?.className
                ?: EMPTY_STRING) == activityName || (it.taskInfo.baseActivity?.className
                ?: EMPTY_STRING) == activityName
        ) {
            startActivity(Intent(it.taskInfo.baseIntent))
        }
    }
}