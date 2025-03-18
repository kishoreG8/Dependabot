package com.trimble.ttm.routemanifest.macrobenchmark.utils

import android.Manifest
import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry

internal fun provideDisplayOverlayPermissionAndStartApp(macrobenchmarkScope: MacrobenchmarkScope) {
    val systemAlertPermission = Manifest.permission.SYSTEM_ALERT_WINDOW
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant $PACKAGE_NAME $systemAlertPermission")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val postNotificationPermission = Manifest.permission.POST_NOTIFICATIONS
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant $PACKAGE_NAME $postNotificationPermission")
    }

    macrobenchmarkScope.startActivityAndWait()
}