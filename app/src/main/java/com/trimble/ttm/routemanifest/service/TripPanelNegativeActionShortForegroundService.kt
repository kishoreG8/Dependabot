package com.trimble.ttm.routemanifest.service

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MemoryLogger
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.TRIP_PANEL_NEGATIVE_ACTION_FOREGROUND_SERVICE_CHANNEL_ID
import com.trimble.ttm.commons.utils.TRIP_PANEL_NEGATIVE_ACTION_FOREGROUND_SERVICE_ID
import com.trimble.ttm.commons.utils.createNotification
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.IS_DYA_ALERT_ACTIVE
import com.trimble.ttm.routemanifest.model.ArrivalActionStatus
import com.trimble.ttm.routemanifest.usecases.ArrivalReasonUsecase
import com.trimble.ttm.routemanifest.usecases.TripPanelActionHandleUseCase
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_MESSAGE_ID_KEY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_MESSAGE_PRIORITY_KEY
import com.trimble.ttm.routemanifest.utils.Utils.isMessageFromDidYouArrive
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val PROCESS_EXECUTION_WAIT_TIME = 5000L

class TripPanelNegativeActionShortForegroundService : LifecycleService(), KoinComponent {

    private val tag = "TripPanelNegativeActionShortService"
    private val coroutineDispatcherProvider: DispatcherProvider by inject()
    private val tripPanelActionHandleUseCase: TripPanelActionHandleUseCase by inject()
    private val arrivalReasonUsecase: ArrivalReasonUsecase by inject()
    private val dataStoreManager: DataStoreManager by inject()
    private var isProcessing = false

    override fun onCreate() {
        startNotificationForForeground()
        super.onCreate()
        Log.logLifecycle(tag, "$tag onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.logLifecycle(tag, "$tag onStartCommand")
        isProcessing = true

        intent?.extras?.let { data ->
            handleEvent(data)
        }

        if(intent == null || intent.extras == null) {
            stopForegroundService(reason = "Invalid intent or intent extras. Intent: $intent IntentExtras: ${intent?.extras}")
        }
        return START_NOT_STICKY
    }

    private fun handleEvent(data: Bundle) {
        data.getString(TRIP_PANEL_MESSAGE_ID_KEY)?.let { messageId ->
            val messagePriority = data.getString(TRIP_PANEL_MESSAGE_PRIORITY_KEY)?.toInt() ?: -1
            Log.d(tag, "Inside handleEvents messageId: $messageId messagePriority: $messagePriority")
            handleTripPanelNegativeAction(messageId.toInt(), messagePriority)
        } ?: Log.d(tag, "$TRIP_PANEL_MESSAGE_ID_KEY is null")
    }

    private fun handleTripPanelNegativeAction(messageId: Int, messagePriority: Int) {
        lifecycleScope.launch(coroutineDispatcherProvider.main() + CoroutineName("onNegativeButtonClick")) {
            if(isMessageFromDidYouArrive(messagePriority)){
                dataStoreManager.setValue(IS_DYA_ALERT_ACTIVE, false)
                val arrivalReasonHashMap = arrivalReasonUsecase.getArrivalReasonMap(
                    ArrivalActionStatus.DRIVER_CLICKED_NO.toString(), messageId, false)
                arrivalReasonUsecase.updateArrivalReasonForCurrentStop(messageId, arrivalReasonHashMap)
            }
            tripPanelActionHandleUseCase.handleTripPanelNegativeAction(messageId, tag)
            isProcessing = false
            delay(PROCESS_EXECUTION_WAIT_TIME)
            if (isProcessing.not()) stopForegroundService(reason = "Process Completed")
        }
    }

    private fun stopForegroundService(reason: String) {
        Log.logLifecycle(tag, "$tag $reason")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startNotificationForForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        startForeground(
            TRIP_PANEL_NEGATIVE_ACTION_FOREGROUND_SERVICE_ID,
            this.createNotification(
                TRIP_PANEL_NEGATIVE_ACTION_FOREGROUND_SERVICE_CHANNEL_ID,
                getString(R.string.trip_panel_negative_action_foreground_service_channel_name),
                getString(R.string.app_name),
                getString(R.string.trip_panel_negative_action_notification_content_text),
                R.mipmap.ic_app_icon,
            )
        )
    }


    override fun onDestroy() {
        Log.logLifecycle(tag, "$tag Service OnDestroy")
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryLogger.onTrimMemory(
            level,
            applicationContext,
            MemoryLogger.Scenario.TRIP_PANEL_SERVICE_TRIM
        )
    }
}