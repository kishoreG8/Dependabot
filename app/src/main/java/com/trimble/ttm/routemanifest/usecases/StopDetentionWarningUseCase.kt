package com.trimble.ttm.routemanifest.usecases

import android.app.Application
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.DetentionWarningMethod
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.ui.activities.DetentionWarningActivity
import com.trimble.ttm.routemanifest.utils.DEFAULT_DETENTION_WARNING_RADIUS_IN_FEET
import com.trimble.ttm.routemanifest.utils.DETENTION_WARNING_STOP_ID
import com.trimble.ttm.routemanifest.utils.DETENTION_WARNING_TEXT
import com.trimble.ttm.routemanifest.utils.Utils.getRouteData
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Timer
import kotlin.concurrent.timer

class StopDetentionWarningUseCase(
    private val applicationInstance: Application,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val dataStoreManager: DataStoreManager,
    private val dispatcherProvider: DispatcherProvider,
    private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
    private val backboneUseCase: BackboneUseCase
) {

    private val tag = "StopDetentionWarningUseCase"
    private var detentionTimer: Timer? = null
    private var detentionWarningTimerJob: Job? = null

    fun checkForDisplayingDetentionWarningAndStartDetentionWarningTimer(messageId : Int) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(CoroutineName("observeDetentionWarningTrigger") + dispatcherProvider.io()) {
                val currentStop = fetchDispatchStopsAndActionsUseCase.getStopData(messageId)
                if (messageId == -1 || canDisplayDetentionWarning(currentStop).not()) return@launch
                startDetentionWarningTimer(currentStop, "observeDetentionWarningTrigger")
            }
    }

    fun canDisplayDetentionWarning(currentStop: StopDetail?): Boolean {
        Log.i(tag, "currentStopDtwMethod: ${currentStop?.dtwMethod}")
        return currentStop != null && currentStop.dtwMethod != DetentionWarningMethod.NONE.value
    }

    fun startDetentionWarningTimer(currentStop: StopDetail?, caller: String) {
        detentionWarningTimerJob?.cancel()
        detentionWarningTimerJob = appModuleCommunicator.getAppModuleApplicationScope().launch(dispatcherProvider.default() + CoroutineName(tag)) {
            currentStop?.let {
                if (it.StopCompleted) {
                    return@let
                }
                detentionTimer?.cancel()
                detentionTimer?.purge()
                val initialDelay = calculateDetentionTime(
                    it.dtwMins,
                    it.dtwMethod,
                    it.stopid,
                    60 * 1000 // minutes * seconds * milliseconds
                )
                Log.i(tag, "Detention timer started for stop: ${it.name}. Caller: $caller InitialDelay: $initialDelay")
                detentionTimer = timer("detentionTimer", false, initialDelay, 1000 * 60 * 5) {
                    processDetentionWarningTimer(it, appModuleCommunicator.getAppModuleApplicationScope(), caller)
                }
            }
        }
    }

    private fun processDetentionWarningTimer(stopDetail: StopDetail, scope: CoroutineScope, caller: String) {
        detentionTimer?.cancel()
        detentionTimer?.purge()
        scope.launch(dispatcherProvider.default() + CoroutineName(tag)) {
            val distance = getDistanceInFeet(
                Stop(
                    latitude = stopDetail.latitude,
                    longitude = stopDetail.longitude,
                )
            ).toInt()
            Log.i(tag, "Detention timer ended for stop: ${stopDetail.name}. Caller: $caller. Distance between current location and stop's location: $distance feet.")
            if (distance <= DEFAULT_DETENTION_WARNING_RADIUS_IN_FEET) {
                startDetentionWarningActivity(stopDetail.name, stopDetail.stopid)
            }
        }
    }


    suspend fun calculateDetentionTime(
        dtwMinutes: Int,
        dtwMethod: Int,
        stopId: Int,
        minutesMultiplier: Int
    ): Long {
        val dtwTime = dtwMinutes * minutesMultiplier
        var initialDelay = 0L
        if (dtwMethod == DetentionWarningMethod.START_AT_ARRIVAL.value) {
            initialDelay = dtwTime.toLong()
        } else if (dtwMethod == DetentionWarningMethod.NO_PENALTY.value) {
            val etaAndActualTimeDifference = getRouteData(
                stopId,
                dataStoreManager
            )?.etaTime?.time?.minus(getCurrentDate().time) ?: 0

            initialDelay = if (etaAndActualTimeDifference > 0) {
                etaAndActualTimeDifference + dtwTime
            } else {
                dtwTime.toLong()
            }
        }
        return initialDelay
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getCurrentDate() = Date()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun startDetentionWarningActivity(detentionWarningName: String, stopId: Int) {
        val intent = Intent(applicationInstance, DetentionWarningActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val detentionWarningText = String.format(applicationInstance.getString(R.string.dtw_text), detentionWarningName)
        intent.putExtra(DETENTION_WARNING_TEXT, detentionWarningText)
        intent.putExtra(DETENTION_WARNING_STOP_ID, stopId)
        ContextCompat.startActivity(applicationInstance, intent, null)
    }

    fun cancelDetentionWarningTimer() {
        detentionTimer?.cancel()
        detentionTimer?.purge()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun getDistanceInFeet(stop: Stop): Double =
        FormUtils.getMilesToFeet(
            FormUtils.getDistanceBetweenLatLongs(
                backboneUseCase.getCurrentLocation(),
                Pair(stop.latitude, stop.longitude)
            )
        )

}