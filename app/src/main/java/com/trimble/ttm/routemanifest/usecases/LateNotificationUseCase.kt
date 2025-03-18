package com.trimble.ttm.routemanifest.usecases

import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_STOP_ACTION_LATE_NOTIFICATION
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.EXPIRE_AT
import com.trimble.ttm.commons.utils.FeatureFlagGateKeeper
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.UNDERSCORE
import com.trimble.ttm.commons.utils.VALUE
import com.trimble.ttm.routemanifest.managers.workmanager.StopActionLateNotificationWorker
import com.trimble.ttm.routemanifest.model.*
import com.trimble.ttm.routemanifest.repo.ArrivalReasonEventRepo
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.repo.TripMobileOriginatedEventsRepo
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_STOP_EVENTS
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_VEHICLES
import com.trimble.ttm.routemanifest.utils.JsonDataConstructionUtils
import com.trimble.ttm.routemanifest.utils.VEHICLES_COLLECTION
import com.trimble.ttm.routemanifest.utils.actionId
import com.trimble.ttm.routemanifest.utils.reason
import com.trimble.ttm.routemanifest.viewmodel.STOPS
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class LateNotificationUseCase(
    private val scope: CoroutineScope,
    private val backboneUseCase: BackboneUseCase,
    private val dispatchFirestoreRepo: DispatchFirestoreRepo,
    private val tripMobileOriginatedEventsRepo: TripMobileOriginatedEventsRepo,
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val arrivalReasonEventRepo: ArrivalReasonEventRepo
) {
    private val appModuleCommunicator = dispatchFirestoreRepo.getAppModuleCommunicator()

    internal fun processForLateActionNotification(
        coroutineDispatcherProvider: DispatcherProvider,
        action: Action
    ) {
        scope.launch(
            coroutineDispatcherProvider.io() + CoroutineName(
                TRIP_STOP_ACTION_LATE_NOTIFICATION
            )
        ) {
            val response = getActionResponseJson(action)
            val actionIdWithReason = getActionIdWithReason(response, Gson())
            val documentPath = getLateNotificationActionPath(action, actionIdWithReason.second.removeSurrounding("\""))
            if (actionIdWithReason.first.isEmpty() || tripMobileOriginatedEventsRepo.isLateNotificationExists(
                    ActionTypes.entries[action.actionType].action,
                    documentPath
                )
            ) return@launch
            val arrivalReason = when(action.actionType) {
                ActionTypes.ARRIVED.ordinal -> arrivalReasonEventRepo.getCurrentStopArrivalReason(
                    "${appModuleCommunicator.doGetCid()}/$VEHICLES_COLLECTION/${appModuleCommunicator.doGetTruckNumber()}/$DISPATCH_COLLECTION/${action.dispid}/$STOPS/${action.stopid}")
                else -> ArrivalReason()
            }
            tripMobileOriginatedEventsRepo.saveStopActionResponse(
                ActionTypes.entries[action.actionType].action,
                documentPath,
                hashMapOf(
                    VALUE to response.value,
                    EXPIRE_AT to DateUtil.getExpireAtDateTimeForTTLInUTC(),
                    DISPATCHID to action.dispid
                ),
                arrivalReason,
                action.triggerReceivedTime
            )
            Log.i(
                TRIP_STOP_ACTION_LATE_NOTIFICATION,
                "ETA missed for action under $documentPath"
            )
        }
    }

    internal suspend fun fetchInCompleteStopActions(
        caller: String,
        dispatchId: String
    ): List<Action> {
        val inCompleteActionsOfActiveStops = mutableListOf<Action>()
        val stopList = dispatchFirestoreRepo.getStopsFromFirestore(
            caller = caller,
            vehicleId = appModuleCommunicator.doGetTruckNumber(),
            cid = appModuleCommunicator.doGetCid(),
            dispatchId = dispatchId,
            isForceFetchedFromServer = false
        )
        // filter out active stops (not soft deleted stops) and its incomplete actions
        stopList.filter { it.deleted == 0 }.let { activeStops ->
            activeStops.forEach { stop ->
                stop.Actions.filter { !it.responseSent }.let { inCompleteActions ->
                    inCompleteActionsOfActiveStops.addAll(inCompleteActions)
                }
            }
        }
        return inCompleteActionsOfActiveStops
    }

    internal fun scheduleLateNotificationCheckWorker(
        scope: CoroutineScope,
        caller: String,
        dispatchId: String,
        workManager: WorkManager,
        isFromTripStart: Boolean
    ) {
        scope.launch(CoroutineName(caller) + coroutineDispatcherProvider.io()) {
            val inCompleteActionsOfActiveStops =
                fetchInCompleteStopActions(caller = "doOnTripStart", dispatchId = dispatchId)
            inCompleteActionsOfActiveStops.forEach { inCompleteAction ->
                if (inCompleteAction.eta.isEmpty()) return@forEach
                val workUniqueName: String =
                    dispatchId + inCompleteAction.stopid + inCompleteAction.actionid
                val delayToStartFromNowInUnixMillis = inCompleteAction.getEtaDifferenceFromNow(
                    TRIP_STOP_ACTION_LATE_NOTIFICATION + caller
                )
                if (isFromTripStart && delayToStartFromNowInUnixMillis < 0) {
                    StopActionLateNotificationWorker.enqueueStopActionLateNotificationCheckWork(
                        workManager = workManager,
                        workUniqueName = workUniqueName,
                        dispatchId = dispatchId,
                        delay = 0L
                    )
                    return@forEach
                } else if (delayToStartFromNowInUnixMillis < 0) return@forEach
                StopActionLateNotificationWorker.enqueueStopActionLateNotificationCheckWork(
                    workManager = workManager,
                    workUniqueName = workUniqueName,
                    dispatchId = dispatchId,
                    delay = delayToStartFromNowInUnixMillis
                )
            }
        }
    }

    private suspend fun getActionResponseJson(action: Action): JsonData {
        /** approaching late, arrival late, depart late = StopActionReasonTypes.TIMEOUT.name
         * late notification / occur by is passed on trip xml
         */
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
            reasonType = StopActionReasonTypes.TIMEOUT.name,
            negativeGuf = false
        )
        val customerId = appModuleCommunicator.doGetCid()
        val isConfigurableOdometerEnabled =  FeatureFlagGateKeeper().isFeatureTurnedOn(
            FeatureGatekeeper.KnownFeatureFlags.SHOULD_USE_CONFIGURABLE_ODOMETER, appModuleCommunicator.getFeatureFlags(), customerId)
        val customerIdObcIdVehicleId = Triple(customerId.toInt(), appModuleCommunicator.doGetObcId(), appModuleCommunicator.doGetTruckNumber())

        return JsonDataConstructionUtils.getStopActionJson(
            action = action,
            createDate = getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time),
            pfmEventsInfo = pfmEventsInfo,
            fuelLevel = backboneUseCase.getFuelLevel(),
            odometerReading = backboneUseCase.getOdometerReading(isConfigurableOdometerEnabled),
            customerIdObcIdVehicleId = customerIdObcIdVehicleId,
            currentLocationLatLong = backboneUseCase.getCurrentLocation()
        )
    }

    suspend fun getLateNotificationActionPath(action: Action, reason: String): String =
            "${appModuleCommunicator.doGetCid()}/$COLLECTION_NAME_VEHICLES/${appModuleCommunicator.doGetTruckNumber()}" +
            "/$COLLECTION_NAME_STOP_EVENTS/${action.dispid}$UNDERSCORE${action.stopid}$UNDERSCORE${action.actionid}$UNDERSCORE$reason"

    internal fun getActionIdWithReason(response: JsonData, gson: Gson): Pair<String, String> {
        try {
            val responseObject: JsonObject = gson.fromJson(response.value, JsonObject::class.java)
            val actionId = responseObject.get(actionId).toString()
            val reasonType = responseObject.get(reason).toString()
            return Pair(actionId, reasonType)
        } catch (e: Exception) {
            Log.e(
                TRIP_STOP_ACTION_LATE_NOTIFICATION,
                "Error in converting string to JsonObject ${response.value}"
            )
        }
        return Pair("", "")
    }
}