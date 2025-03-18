package com.trimble.ttm.routemanifest.service

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MemoryLogger
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.createNotification
import com.trimble.ttm.formlibrary.usecases.UpdateInspectionInformationUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.usecases.TripCacheUseCase
import com.trimble.ttm.routemanifest.usecases.WorkFlowApplicationUseCase
import com.trimble.ttm.routemanifest.utils.Utils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

private const val FOREGROUND_SERVICE_CHANNEL_ID = "0000"
private const val ROUTE_MANIFEST_FOREGROUND_SERVICE_ID = 111

class RouteManifestForegroundService : LifecycleService(), KoinComponent {
    private val tag = "RMForegroundService"
    private val coroutineDispatcherProvider: DispatcherProvider by inject()

    private val dataStoreManager: DataStoreManager by inject()

    private val backboneUseCase: BackboneUseCase by inject()
    private var motionAndTriggerPanelJob: Job? = null
    private var shouldUpdateTripPanelMessagesPeriodically = true
    private val tripCacheUseCase: TripCacheUseCase by inject()
    private val updateInspectionInformationUseCase: UpdateInspectionInformationUseCase by inject()
    private val appModuleCommunicator: AppModuleCommunicator by inject()

    private val serviceManager: ServiceManager by inject()
    private val workFlowApplicationUseCase : WorkFlowApplicationUseCase by inject()

    override fun onCreate() {
        super.onCreate()
        startNotificationForForeground()
        Log.logLifecycle(tag, "$tag onCreate")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.logLifecycle(tag, "$tag onStartCommand")
        startNotificationForForeground()
        lifecycleScope.launch(CoroutineName(tag) + coroutineDispatcherProvider.io()) {
            setCrashReportWithBackboneData()
            if (appModuleCommunicator.isFirebaseAuthenticated()) {
                registerLiveListeners()
            }
        }
        MemoryLogger.logMemoryAndCpuDetails(applicationContext, MemoryLogger.Scenario.RM_FOREGROUND_SERVICE_START)
        return START_STICKY
    }

    private fun registerLiveListeners() {
        appModuleCommunicator.listenForFeatureFlagDocumentUpdates()
        triggerOrUpdatePanelMessagesPeriodicallyOnMotionChanges()
        listenMotionChangesToTriggerOrUpdatePanelMessages()
        lifecycleScope.launch(coroutineDispatcherProvider.io() + CoroutineName(tag)) {
            if (dataStoreManager.containsKey(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY).not())
                dataStoreManager.setValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING)
            workFlowApplicationUseCase.listenEdvirSettings()
        }
    }

    private fun triggerOrUpdatePanelMessagesPeriodicallyOnMotionChanges() {
        motionAndTriggerPanelJob?.cancel()
        motionAndTriggerPanelJob =
            lifecycleScope.launch(coroutineDispatcherProvider.io() + CoroutineName("periodicTripPanelMessagesUpdateOnMotion")) {
                while (shouldUpdateTripPanelMessagesPeriodically) {
                    delay(5.seconds)
                    triggerOrUpdatePanelMessages(backboneUseCase.fetchEngineMotion("periodicTripPanelMessagesUpdateOnMotion"))
                }
            }
    }

    private suspend fun triggerOrUpdatePanelMessages(it: Boolean?) {
        if (it == null) {
            serviceManager.sendTripPanelMessageIfTruckStopped(
                isTruckMoving = false,
                updateInspectionInformationUseCase
            )
        } else {
            serviceManager.sendTripPanelMessageIfTruckStopped(
                isTruckMoving = it,
                updateInspectionInformationUseCase
            )
        }
    }

    private fun listenMotionChangesToTriggerOrUpdatePanelMessages() =
        lifecycleScope.launch(coroutineDispatcherProvider.io() + CoroutineName("monitorMotion")) {
            backboneUseCase.monitorMotion().collect { motionState ->
                Log.d(tag, "Motion state is changed: $motionState", throwable = null, "motionState" to motionState)
                triggerOrUpdatePanelMessages(motionState)
            }
        }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryLogger.onTrimMemory(level, applicationContext, MemoryLogger.Scenario.RM_FOREGROUND_SERVICE_TRIM)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(tag, "$tag Task removed")
    }

    override fun onDestroy() {
        Log.logLifecycle(tag, "$tag onDestroy")
        try {
            if(motionAndTriggerPanelJob?.isActive == true) {
                motionAndTriggerPanelJob?.cancel()
            }
            shouldUpdateTripPanelMessagesPeriodically = false
            tripCacheUseCase.clearDisposable()
        } catch (e: Exception) {
            Log.e(tag, e.message, e)
        }
        super.onDestroy()
    }

    private fun startNotificationForForeground() {
        startForeground(
            ROUTE_MANIFEST_FOREGROUND_SERVICE_ID,
            this.createNotification(
                FOREGROUND_SERVICE_CHANNEL_ID,
                getString(R.string.foreground_service_channel_name),
                getString(R.string.app_name),
                getString(R.string.foreground_notification_content_text),
                R.mipmap.ic_app_icon
            )
        )
    }

    private suspend fun setCrashReportWithBackboneData() {
        try {
            Utils.setCrashReportIdentifierAfterBackboneDataCache(
                appModuleCommunicator
            )
        } catch (e: Exception) {
            Log.e(tag, e.message, e)
        }
    }
}