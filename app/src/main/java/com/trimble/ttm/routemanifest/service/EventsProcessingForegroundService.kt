package com.trimble.ttm.routemanifest.service

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.GEOFENCE_EVENT_PROCESS
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MemoryLogger
import com.trimble.ttm.commons.logger.TRIP
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.EVENTS_PROCESSING_FOREGROUND_SERVICE_CHANNEL_ID
import com.trimble.ttm.commons.utils.EVENTS_PROCESSING_FOREGROUND_SERVICE_ID
import com.trimble.ttm.commons.utils.createNotification
import com.trimble.ttm.commons.utils.toSafeString
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.Utils.customGetSerializable
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.model.GeofenceTrigger
import com.trimble.ttm.routemanifest.utils.CPIK_EVENT_TYPE_KEY
import com.trimble.ttm.routemanifest.utils.CPIK_GEOFENCE_EVENT
import com.trimble.ttm.routemanifest.utils.KEY_GEOFENCE_EVENT
import com.trimble.ttm.routemanifest.utils.KEY_GEOFENCE_NAME
import com.trimble.ttm.routemanifest.utils.KEY_GEOFENCE_TIME
import com.trimble.ttm.routemanifest.utils.KEY_GEOFENCE_TRIGGER
import com.trimble.ttm.routemanifest.utils.KEY_IS_NEW_LAUNCHER
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private const val GEOFENCE_MAP_EMPTINESS_THRESHOLD = 3
private const val GEOFENCE_MAP_EMPTINESS_FALLBACK_THRESHOLD = 30
class EventsProcessingForegroundService : LifecycleService(), KoinComponent {

    private val tag = "EventsProcessingService"
    private val serviceManager: ServiceManager by inject()
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val coroutineDispatcherProvider: DispatcherProvider by inject()
    private var geofenceMapEmptinessCount = 0
    private var isProcessing = false
    private val geofenceTriggerConcurrentMap = ConcurrentHashMap<String, String>()
    private var shouldProcessGeofenceEventPeriodically = true
    private var geofenceProcessJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.logLifecycle(TRIP + tag, "$tag onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startNotificationForForeground()
        Log.logLifecycle(TRIP + tag, "$tag onStartCommand")
        isProcessing = true
        geofenceMapEmptinessCount = 0

        startRouteManifestForegroundServiceIfNotStartedPreviously()
        intent?.extras?.let { data ->
            handleEvents(data)
        }

        if(intent == null || intent.extras == null){
            stopEventsProcessingForegroundService(reason = "Invalid intent or intent extras. Intent: $intent IntentExtras: ${intent?.extras}")
        }
        return START_NOT_STICKY
    }

    private fun handleEvents(data: Bundle){
        Log.d(tag, "Inside handleEvents")
        data.getString(CPIK_EVENT_TYPE_KEY)?.let { eventType ->
            when(eventType){
                CPIK_GEOFENCE_EVENT -> {
                    Log.d(tag, "Calling handleGeofenceEvents()")
                    handleGeofenceEvents(data)
                }
                else ->{
                    stopEventsProcessingForegroundService(reason = "Invalid eventType $eventType")
                }
            }
        }
    }

    private fun handleGeofenceEvents(data: Bundle) {
        val isNewLauncher = data.getBoolean(KEY_IS_NEW_LAUNCHER, false)
        var geofenceEventType = EMPTY_STRING
        var geofenceEventTime = EMPTY_STRING
        Log.d(
            GEOFENCE_EVENT_PROCESS,
            "Inside handleGeofenceEvents() method, isNewLauncher:$isNewLauncher"
        )
        if (isNewLauncher) {
            val geofenceTrigger = Utils.fromJsonString<GeofenceTrigger>(
                data.getString(KEY_GEOFENCE_TRIGGER) ?: EMPTY_STRING
            )
            geofenceTrigger?.let {
                geofenceEventType = it.geofenceEvent
                geofenceEventTime = it.geofenceTime?.toString() ?: EMPTY_STRING
                geofenceTriggerConcurrentMap[it.geofenceName] = it.dispatchId
                Log.logGeoFenceEventFlow(
                    GEOFENCE_EVENT_PROCESS,
                    "GeofenceEventReceived: GeofenceName: ${it.geofenceName} GeofenceEventType: $geofenceEventType GeofenceEventTime: $geofenceEventTime"
                )
                processGeofenceEventEach5Seconds()
            }
        } else {
            data.getString(KEY_GEOFENCE_EVENT)?.let { eventType -> geofenceEventType = eventType }
            data.customGetSerializable<Date>(KEY_GEOFENCE_TIME)?.let { eventTime ->
                geofenceEventTime = eventTime.toSafeString()
            }
            data.getString(KEY_GEOFENCE_NAME)?.let { geofenceSetName ->
                geofenceTriggerConcurrentMap[geofenceSetName] = EMPTY_STRING
                Log.logGeoFenceEventFlow(
                    GEOFENCE_EVENT_PROCESS,
                    "GeofenceEventReceived: GeofenceName: $geofenceSetName GeofenceEventType: $geofenceEventType GeofenceEventTime: $geofenceEventTime"
                )
                processGeofenceEventEach5Seconds()
            }
        }
    }

    private fun processGeofenceEventEach5Seconds() {
        geofenceProcessJob?.cancel()
        geofenceProcessJob = lifecycleScope.launch(coroutineDispatcherProvider.io()) {
            while (shouldProcessGeofenceEventPeriodically) {
                delay(5.seconds)
                processGeofenceEventsForActiveDispatch()
            }
        }
        geofenceProcessJob?.invokeOnCompletion {
            Log.logGeoFenceEventFlow(GEOFENCE_EVENT_PROCESS, "processGeofenceEventEach5Seconds Job completed. ${it?.stackTraceToString()}")
        }
    }

    private fun startRouteManifestForegroundServiceIfNotStartedPreviously() {
        startForegroundServiceIfNotStartedPreviously(RouteManifestForegroundService::class.java)
    }
    private fun stopEventsProcessingForegroundService(reason: String) {
        Log.logGeoFenceEventFlow(GEOFENCE_EVENT_PROCESS, "Stopping EventsProcessingForegroundService. $reason")
        shouldProcessGeofenceEventPeriodically = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startNotificationForForeground() {
        startForeground(
            EVENTS_PROCESSING_FOREGROUND_SERVICE_ID,
            this.createNotification(
                EVENTS_PROCESSING_FOREGROUND_SERVICE_CHANNEL_ID,
                getString(R.string.events_processing_foreground_service_channel_name),
                getString(R.string.app_name),
                getString(R.string.events_processing_foreground_notification_content_text),
                R.mipmap.ic_app_icon,
            )
        )
    }

    private suspend fun processGeofenceEventsForActiveDispatch() =
        withContext(coroutineDispatcherProvider.io()) {
            val activeDispatchId =
                appModuleCommunicator.getCurrentWorkFlowId("processGeofenceEvent")
            clearGeoFenceMapIfThereIsNoActiveTrip(activeDispatchId = activeDispatchId)
            if (geofenceTriggerConcurrentMap.isNotEmpty()) {
                Log.logGeoFenceEventFlow(
                    GEOFENCE_EVENT_PROCESS,
                    "GeofenceEventTriggerSize: D$activeDispatchId T${geofenceTriggerConcurrentMap.keys}"
                )
                iterateGeoFenceMapAndProcessGeofenceEvents(activeDispatchId = activeDispatchId)
            } else {
                geofenceMapEmptinessCount++
                Log.d(GEOFENCE_EVENT_PROCESS, "Incrementing geofenceEmptiness count: $geofenceMapEmptinessCount")
                stopServiceUponGeofenceProcessCompletion()
            }
            stopServiceIfGeofenceEmptyThresholdExceeds()
        }

    /**
     * Driver has an active trip, Vehicle is in motion and Admin removes all the stops from the active trip.
     * The active trip will be completed in the Driver Workflow. But, the route will persist in maps (part of MAPP-8085 implementation).
     * For some reason, if the geofence trigger is received after trip completion, it is neglected. (By clearing the geofenceTriggerConcurrentMap)
     */
    private fun clearGeoFenceMapIfThereIsNoActiveTrip(activeDispatchId: String) {
        if (geofenceTriggerConcurrentMap.isNotEmpty() && activeDispatchId.isEmpty()) {
            Log.w(
                GEOFENCE_EVENT_PROCESS,
                "GeofenceEventDispatchEmpty There is no Active Dispatch. So Clearing the UnProcessed GeoFence Events. ${geofenceTriggerConcurrentMap.keys}"
            )
            geofenceTriggerConcurrentMap.clear()
            isProcessing = false
        } else {
            val geofenceTriggerOfDifferentDispatch =
                geofenceTriggerConcurrentMap.filterValues { it != activeDispatchId && it != EMPTY_STRING }
            if (geofenceTriggerOfDifferentDispatch.isNotEmpty()) {
                Log.w(
                    GEOFENCE_EVENT_PROCESS,
                    "GeofenceEventDispatchIdMismatch Active DispatchId:$activeDispatchId and Geofence Triggered DispatchId's ${geofenceTriggerOfDifferentDispatch.values} are different. So Clearing the UnProcessed GeoFence Events. ${geofenceTriggerOfDifferentDispatch.entries}",
                    null,
                    "Active Dispatch Id" to activeDispatchId,
                    "Triggered DispatchId" to geofenceTriggerOfDifferentDispatch.values
                )
                geofenceTriggerOfDifferentDispatch.forEach { mapEntry ->
                    geofenceTriggerConcurrentMap.remove(
                        mapEntry.key,
                        mapEntry.value
                    )
                }
                if (geofenceTriggerConcurrentMap.isEmpty()) {
                    isProcessing = false
                }
            }
        }
    }

    private fun iterateGeoFenceMapAndProcessGeofenceEvents(activeDispatchId: String) {
        try {
            val iterator = geofenceTriggerConcurrentMap.iterator()
            while (iterator.hasNext()) {
                val (geofenceSetName, _) = iterator.next()
                serviceManager. processGeoFenceEvents(
                    activeDispatchId,
                    geofenceSetName,
                    // This context is used to update the trip widget. So, it should out live service death. We are passing appCtx for that reason
                    this@EventsProcessingForegroundService.applicationContext,
                    true
                )
                iterator.remove()
            }
            isProcessing = false
        } catch (e: Exception) {
            Log.e(GEOFENCE_EVENT_PROCESS, "Exception in EventsProcessingService ${e.message}", e)
        }
    }

    private fun stopServiceUponGeofenceProcessCompletion(){
        if (geofenceMapEmptinessCount > GEOFENCE_MAP_EMPTINESS_THRESHOLD) {
            Log.d(GEOFENCE_EVENT_PROCESS, "Going to Stop service on the completion of the processing of geofence events. isProcessing: $isProcessing")
            if (isProcessing.not()) {
                stopEventsProcessingForegroundService(reason = "Geofence events process completed.")
            }
        }
    }

    /**
     * This is a fallback. This service was running indefinitely when we checked on logs.
     * That was because of isProcessing flag. The geofence trigger data from AL is immediately filled in geofenceTriggerConcurrentMap
     * That is considered as source of truth here
     */
    private fun stopServiceIfGeofenceEmptyThresholdExceeds(){
        if (geofenceTriggerConcurrentMap.isEmpty() && geofenceMapEmptinessCount > GEOFENCE_MAP_EMPTINESS_FALLBACK_THRESHOLD) {
            stopEventsProcessingForegroundService(reason = "Stopping service in fallback")
        }
    }

    override fun onDestroy() {
        Log.logLifecycle(TRIP + tag, "$tag Service OnDestroy")
        geofenceProcessJob?.cancel()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryLogger.onTrimMemory(level, applicationContext, MemoryLogger.Scenario.EVENT_PROCESS_SERVICE_TRIM)
    }
}