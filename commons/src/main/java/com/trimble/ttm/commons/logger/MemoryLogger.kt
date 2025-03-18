package com.trimble.ttm.commons.logger

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants

data class CpuMetric(val numberOfCores: Long, val cpuClockSpeed: Long, val deviceUpTime: Double)

object MemoryLogger {

    private const val tag = "MemoryDetails"

    enum class Scenario {
        APPLICATION_START,
        APPLICATION_TRIM,
        RM_FOREGROUND_SERVICE_START,
        RM_FOREGROUND_SERVICE_TRIM,
        EVENT_PROCESS_SERVICE_TRIM,
        TRIP_PANEL_SERVICE_TRIM,
        AUTHENTICATION_SERVICE_TRIM,
    }

    fun onTrimMemory(level: Int, applicationContext: Context, scenario: Scenario) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                /*
                Levels above this are either not yet being put on the list
                or are at the beginning of the LRU list
                */
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(tag, "Application about to be terminated. Level: $level Scenario: ${scenario.name}")
                logMemoryAndCpuDetails(applicationContext, scenario)
            }
        }
    }

    fun logMemoryAndCpuDetails(applicationContext: Context, scenario: Scenario) {
        try {
            val memoryInfo = getAvailableMemory(applicationContext)
            val runtime = Runtime.getRuntime()
            val jvmTotalHeapSize = runtime.totalMemory() / 1024
            val jvmFreeHeapSize = runtime.freeMemory() / 1024
            val cpuMetrics = getCpuMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Log.n(
                    tag, "Memory Statistics",
                    null, "JVM heap free (KB)" to jvmFreeHeapSize,
                    "JVM heap total (KB)" to jvmTotalHeapSize,
                    "JVM heap memory allocation - [Total heap - Free heap] (KB)" to jvmTotalHeapSize - jvmFreeHeapSize,
                    "RAM Memory available (KB)" to memoryInfo.availMem/1024,
                    "RAM Memory threshold (KB)" to memoryInfo.threshold/1024,
                    "RAM Memory total (KB)" to memoryInfo.totalMem/1024,
                    "Is RAM having low memory" to memoryInfo.lowMemory,
                    "Available RAM memory percentage" to memoryInfo.availMem / memoryInfo.totalMem.toDouble() * 100.0,
                    "Cpu cores" to cpuMetrics.numberOfCores,
                    "Cpu clock speed (Hz)" to cpuMetrics.cpuClockSpeed,
                    "Device Up time (Secs)" to cpuMetrics.deviceUpTime,
                    "Process name" to Application.getProcessName(),
                    "Scenario" to scenario.name
                )
            } else {
                Log.n(
                    tag, "Memory Statistics",
                    null, "JVM heap free (KB)" to jvmFreeHeapSize,
                    "JVM heap total (KB)" to jvmTotalHeapSize,
                    "JVM heap memory allocation - [Total heap - Free heap] (KB)" to jvmTotalHeapSize - jvmFreeHeapSize,
                    "RAM Memory available (KB)" to memoryInfo.availMem/1024,
                    "RAM Memory threshold (KB)" to memoryInfo.threshold/1024,
                    "RAM Memory total (KB)" to memoryInfo.totalMem/1024,
                    "Is RAM having low memory" to memoryInfo.lowMemory,
                    "Available RAM memory percentage" to memoryInfo.availMem / memoryInfo.totalMem.toDouble() * 100.0,
                    "Cpu cores" to cpuMetrics.numberOfCores,
                    "Cpu clock speed (Hz)" to cpuMetrics.cpuClockSpeed,
                    "Device Up time (Secs)" to cpuMetrics.deviceUpTime,
                    "Scenario" to scenario.name
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error logging JVM memory. ${e.message}", e)
        }
    }

    private fun getAvailableMemory(applicationContext: Context): ActivityManager.MemoryInfo {
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager?.getMemoryInfo(memoryInfo) ?: Log.w(tag, "Error getting activity service")
        }
    }

    private fun getCpuMetrics(): CpuMetric {
        val numOfCpuCores = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF)
        val cpuClockSpeedHz = Os.sysconf(OsConstants._SC_CLK_TCK)
        val deviceUpTimeInSecs = SystemClock.elapsedRealtime() / 1000.0
        return CpuMetric(numberOfCores = numOfCpuCores, cpuClockSpeed = cpuClockSpeedHz, deviceUpTime = deviceUpTimeInSecs)
    }

}