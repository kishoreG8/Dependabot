package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.WorkManager
import com.trimble.ttm.commons.logger.AUTO_TRIP_START_CALLER_TRIP_END
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_COMPLETE
import com.trimble.ttm.commons.logger.TRIP_COMPLETE_FORM_PENDING
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.WorkFlowEvents
import com.trimble.ttm.commons.model.WorkflowEventDataParameters
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureFlagGateKeeper
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.RUN_ON_TRIP_END_CALLER
import com.trimble.ttm.formlibrary.eventbus.EventBus
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.managers.workmanager.StopActionLateNotificationWorker
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.FcmData
import com.trimble.ttm.routemanifest.model.JsonData
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.hasNoPendingAction
import com.trimble.ttm.routemanifest.model.isStopSoftDeleted
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.routemanifest.repo.TripMobileOriginatedEventsRepo
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_DISPATCH_EVENT
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_TRIP_END
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_VEHICLES
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import com.trimble.ttm.routemanifest.utils.JsonDataConstructionUtils
import com.trimble.ttm.routemanifest.utils.Utils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TripCompletionUseCase(
    private val dispatchFirestoreRepo: DispatchFirestoreRepo,
    private val tripMobileOriginatedEventsRepo: TripMobileOriginatedEventsRepo,
    private val formUseCase: FormUseCase,
    private val draftUseCase: DraftUseCase,
    private val dispatchStopsUseCase: DispatchStopsUseCase,
    private val sendDispatchDataUseCase: SendDispatchDataUseCase,
    private val stopDetentionWarningUseCase: StopDetentionWarningUseCase,
    private val tripPanelUseCase: TripPanelUseCase,
    private val backboneUseCase: BackboneUseCase,
    private val localDataSourceRepo: LocalDataSourceRepo,
    private val managedConfigurationRepo: ManagedConfigurationRepo,
    private val tripInfoWidgetUseCase: TripInfoWidgetUseCase,
    private val sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase,
    private val dispatchListUseCase: DispatchListUseCase,
    private val coroutineDispatcherProvider: DispatcherProvider
) {
    private val appModuleCommunicator = localDataSourceRepo.getAppModuleCommunicator()

    suspend fun isManualTripCompletionDisabled(
        caller:String,
        cid: String,
        vehicleId: String,
        dispatchId: String
    ): Boolean {
        dispatchFirestoreRepo.getDispatchPayload(caller,cid, vehicleId, dispatchId).tripStartDisableManual.let {
            return it == 1
        }
    }

    // In addition to IsCompleted flag, this function also updates IsActiveDispatch flag in firestore
    internal suspend fun updateTripCompletedFlagToFirestore(caller: String, dispatchId: String) {
        val cidAndTruckNumber = getCidAndTruckNumber(caller)
        if (cidAndTruckNumber.third.not()) return
        tripMobileOriginatedEventsRepo.setIsCompleteFlagForTrip(
            caller, "$DISPATCH_COLLECTION/${cidAndTruckNumber.first}/${cidAndTruckNumber.second}/${dispatchId}"
        )
    }

    private suspend fun getCidAndTruckNumber(
        caller: String,
    ): Triple<String, String, Boolean> {
        val cid = appModuleCommunicator.doGetCid()
        val truckNumber = appModuleCommunicator.doGetTruckNumber()
        if (cid.isEmpty() || truckNumber.isEmpty()) {
            Log.e(
                caller,
                "Vehicle Id or Customer Id is empty",
                throwable = null,
                "Cid" to cid,
                "Truck number" to truckNumber
            )
            return Triple(
                cid, truckNumber, false
            )
        }
        return Triple(
            cid, truckNumber, true
        )
    }


    fun saveTripEndActionResponse(collectionName: String, path: String, response: JsonData) {
        tripMobileOriginatedEventsRepo.saveTripActionResponse(collectionName, path, response)
    }

    fun getFormsTemplateListFlow(): Flow<ArrayList<FormTemplate>> {
        return formUseCase.getFormsTemplateListFlow()
    }

    suspend fun formsSync(customerId: String, formDefList: ArrayList<FormDef>){
        formUseCase.formsSync(customerId, formDefList)
    }

    suspend fun isFormSaved(path: String, actionId: String): Boolean {
        return formUseCase.isFormSaved(path, actionId)
    }

    suspend fun isFormDrafted(path: String, actionId: String): Boolean {
        return draftUseCase.isDraftSaved(path, actionId)
    }

    fun getStopsForDispatch(
        vehicleId: String,
        cid: String,
        dispatchId: String
    ): Flow<Set<StopDetail>>{
        return dispatchFirestoreRepo.getStopsForDispatch(vehicleId, cid, dispatchId)
    }

    fun listenToStopActions(
        vehicleId: String,
        cid: String,
        dispatchId: String, stopId: String
    ): Flow<Set<Action>> {
        return dispatchFirestoreRepo.listenToStopActions(vehicleId, cid, dispatchId, stopId)
    }

    private fun unRegisterFirestoreActiveDispatchLiveListeners() = dispatchFirestoreRepo.unRegisterFirestoreLiveListeners()

    internal suspend  fun runOnTripEnd(
        dispatchId: String,
        caller: String, workManager: WorkManager,
        pfmEventsInfo: PFMEventsInfo.TripEvents
    ) = coroutineScope {
        val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("runOnTripEnd")
        val cidAndTruckNumber = getCidAndTruckNumber("${caller}runOnTripEnd")
        if (cidAndTruckNumber.third.not()) return@coroutineScope
        val cid = cidAndTruckNumber.first
        val truckNumber = cidAndTruckNumber.second

        //return if dispatch is already completed
        if (this@TripCompletionUseCase.isDispatchAlreadyCompleted(cid, truckNumber, dispatchId)) {
            Log.d(
                TRIP_COMPLETE + caller,
                "DispatchAlreadyCompleted.D:$dispatchId)"
            )
            return@coroutineScope
        }

        if (activeDispatchId.isEmpty() || dispatchId != activeDispatchId) {
            Log.n(
                TRIP_COMPLETE + caller,
                "InActiveDispatchComplete.D:$dispatchId)"
            )
            updateTripCompletedFlagToFirestore(TRIP_COMPLETE + caller, dispatchId)
            return@coroutineScope
        }

        // Background trip completion in background removes datastoreManager keys - dataStoreManager.removeAllKeys()
        // if we don't add contains check then the default value of bool key will be false and that will clear the route in copilot.
        // So, a contains check is added here and it's marked as false in trip start to satisfy this condition
        if (localDataSourceRepo.isKeyAvailableInAppModuleDataStore(DataStoreManager.IS_ACTIVE_DISPATCH_STOP_MANIPULATED) &&
            dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip().not()) {
            sendDispatchCompleteEventToCPIK(caller, dispatchId)
        } else {
            // The below call will remove geofences if sendDispatchCompleteEventToCPIK() is not invoked.
            sendDispatchDataUseCase.removeAllGeofences()
        }
        unRegisterFirestoreActiveDispatchLiveListeners()
        disposeStopListEventsCacheOnTripEnd(dispatchId)
        stopDetentionWarningUseCase.cancelDetentionWarningTimer()
        cancelAllLateNotificationCheckWorksByTag(dispatchId, workManager)
        try {
            awaitAll(
                async { sendDispatchEndDataToBackbone(dispatchId) },
                async { updateTripCompletedFlagToFirestore(TRIP_COMPLETE + caller,dispatchId) },
            )
            updateTripInfoWidgetOnTripCompletion()
            sendTripEndEventToPFM(cid, truckNumber, dispatchId, caller, pfmEventsInfo)
            sendWorkflowEventsToAppUseCase.sendWorkflowEvent(
                WorkflowEventDataParameters(
                    dispatchId = dispatchId,
                    dispatchName = EMPTY_STRING,
                    stopId = EMPTY_STRING,
                    stopName = EMPTY_STRING,
                    eventName = WorkFlowEvents.TRIP_END_EVENT,
                    reasonCode = EMPTY_STRING,
                    timeStamp = System.currentTimeMillis()
                ),
                caller = RUN_ON_TRIP_END_CALLER
            )
            tripPanelUseCase.dismissTripPanelMessage(tripPanelUseCase.lastSentTripPanelMessage.messageId)
        } catch (e: Exception) {
            Log.e(TRIP_COMPLETE + caller, "Exception in runOnTripEnd. D:$dispatchId ${e.stackTraceToString()}")
        }
        logTripEndMetrics(cid, truckNumber, dispatchId)
        localDataSourceRepo.removeAllKeysOfAppModuleDataStore()
        // Check and schedule auto start trip for the next dispatch on the trip end
        dispatchListUseCase.getDispatchesForTheTruckAndScheduleAutoStartTrip(appModuleCommunicator.doGetCid(),
            appModuleCommunicator.doGetTruckNumber(), AUTO_TRIP_START_CALLER_TRIP_END
        )
        EventBus.resetRouteCalculationRetry()
        Log.d(TRIP_COMPLETE + caller, "Trip Ended D:$dispatchId - TripCompletionUseCase", null, KEY to DISPATCH_LIFECYCLE, DISPATCHID to dispatchId)
    }

    private suspend fun logTripEndMetrics(cid: String, truckNumber: String, dispatchId: String) {
        val stops = dispatchFirestoreRepo.getStopsFromFirestore(
            TRIP_COMPLETE, vehicleId = truckNumber, cid = cid, dispatchId = dispatchId, isForceFetchedFromServer = false
        )
        val tripEndMetrics = Utils.getTripMetrics(stops, managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration(TRIP_COMPLETE))
        Log.n(
            TRIP_COMPLETE,
            "Trip end metrics",
            null,
            DISPATCHID to dispatchId,
            "Stops with circular geofence" to tripEndMetrics.noOfStopsBasedOnGeofenceType.circular,
            "Circular Stops with arrival trigger received" to tripEndMetrics.noOfStopsTriggerReceived.circular,
            "Circular Stops with arrival status sent to PFM" to tripEndMetrics.noOfArrivalResponseSent.circular,
            "Circular Stops with arrival trigger not received" to tripEndMetrics.noOfStopsBasedOnGeofenceType.circular - tripEndMetrics.noOfStopsTriggerReceived.circular,
            "Stops with polygonal geofence" to tripEndMetrics.noOfStopsBasedOnGeofenceType.polygonal,
            "Polygonal Stops with arrival trigger received" to tripEndMetrics.noOfStopsTriggerReceived.polygonal,
            "Polygonal Stops with arrival status sent to PFM" to tripEndMetrics.noOfArrivalResponseSent.polygonal,
            "Polygonal Stops with arrival trigger not received" to tripEndMetrics.noOfStopsBasedOnGeofenceType.polygonal - tripEndMetrics.noOfStopsTriggerReceived.polygonal,
            "Stops sorted based on arrival trigger received" to  tripEndMetrics.stopsSortedOnArrivalTriggerReceivedTime.map{it.stopid},
            "Completed Stops with completed time not set" to tripEndMetrics.completedStopsWithCompletedTimeMissing.map{it.stopid},
            "No of deleted stops" to tripEndMetrics.noOfStopsDeleted,
            "Stops sequence info" to tripEndMetrics.stopsOverview,
            "Is sequential trip" to tripEndMetrics.isSeqTrip,
            "Is vehicle trail out of sequence in sequential trip" to tripEndMetrics.isSeqTripOutOfTrailSeq,
            KEY to DISPATCH_LIFECYCLE
        )
    }

    internal fun cancelAllLateNotificationCheckWorksByTag(dispatchId: String, workManager: WorkManager) {
        StopActionLateNotificationWorker.cancelAllLateNotificationCheckWorkByTag(
            workManager = workManager,
            workTag = dispatchId
        )
    }

    internal fun disposeStopListEventsCacheOnTripEnd(dispatchId: String) {
        WorkflowEventBus.stopListEvents.replayCache.firstOrNull()?.let { stopList ->
            if(stopList.any { it.dispid==dispatchId } || stopList.isEmpty()){
                Log.d(TRIP_COMPLETE,"RemovingTripCache D:$dispatchId")
                WorkflowEventBus.disposeCacheOnTripEnd()
            }
        }
    }

    internal fun sendDispatchCompleteEventToCPIK(caller: String, dispatchId: String = EMPTY_STRING) {
        sendDispatchDataUseCase.sendDispatchCompleteEvent() // clears the stop cache, map route and geofences in AL
        Log.d(TRIP_COMPLETE + caller,"Trip completion event sent to maps D:$dispatchId")
    }

    internal suspend fun sendTripEndEventToPFM(
        cid: String,
        truckNumber: String,
        dispatchId: String,
        caller: String,
        pfmEventsInfo: PFMEventsInfo.TripEvents
    ) {
        if (dispatchId.isEmpty()) {
            Log.w(
                TRIP_COMPLETE + caller,
                "DispatchIdEmptyInSendTripEndEventToPFM - TripCompletionUC. C:$cid T:$truckNumber"
            )
            return
        }
        val customerIdObcIdVehicleId = Triple(
            cid.toInt(),
            appModuleCommunicator.doGetObcId(), truckNumber
        )
        val isConfigurableOdometerEnabled = FeatureFlagGateKeeper().isFeatureTurnedOn(
            FeatureGatekeeper.KnownFeatureFlags.SHOULD_USE_CONFIGURABLE_ODOMETER,
            appModuleCommunicator.getFeatureFlags(),
            cid
        )
        saveTripEndActionResponse(
            COLLECTION_NAME_TRIP_END,
            "$cid/$COLLECTION_NAME_VEHICLES/$truckNumber/$COLLECTION_NAME_DISPATCH_EVENT/$dispatchId",
            JsonDataConstructionUtils.getTripEventJson(
                dispatchId = dispatchId,
                pfmEventsInfo = pfmEventsInfo,
                fuelLevel = backboneUseCase.getFuelLevel(),
                currentLocationLatLong = backboneUseCase.getCurrentLocation(),
                odometerReading = backboneUseCase.getOdometerReading(isConfigurableOdometerEnabled),
                customerIdObcIdVehicleId = customerIdObcIdVehicleId
            )
        )
        tripPanelUseCase.dismissTripPanelOnLaunch()
    }

    internal suspend fun sendDispatchEndDataToBackbone(
        dispatchId: String
    ) = backboneUseCase.setWorkflowEndAction(dispatchId.toInt())

    internal fun updateTripInfoWidgetOnTripCompletion() =
        tripInfoWidgetUseCase.resetTripInfoWidget("updateTripInfoWidgetOnTripCompletion")

    internal suspend fun checkForTripCompletionAndEndTrip(
        caller:String,
        context: Context,
    ) {
        withContext(coroutineDispatcherProvider.io()) {
            val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("${caller}checkForTripCompletionAndEndTrip")

            if (isTripComplete(caller, activeDispatchId).second.not()) {
                return@withContext
            }
            val pfmEventsInfo = PFMEventsInfo.TripEvents(
                reasonType = StopActionReasonTypes.AUTO.name,
                negativeGuf = false
            )
            runOnTripEnd(
                dispatchId = activeDispatchId,
                caller = caller,
                workManager = getWorkManager(context),
                pfmEventsInfo = pfmEventsInfo
            )
        }
    }

    internal fun getWorkManager(context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    suspend fun isTripComplete(
        caller: String,
        dispatchId: String
    ): Pair<String, Boolean> {
        val cidAndTruckNumber = getCidAndTruckNumber("${caller}isTripCompleted")
        if (cidAndTruckNumber.third.not()) return Pair(dispatchId, false)
        val cid = cidAndTruckNumber.first
        val truckNumber = cidAndTruckNumber.second
        val stopList = dispatchFirestoreRepo.getStopsFromFirestore(
            "$TRIP_COMPLETE$caller",
            vehicleId = truckNumber, cid = cid, dispatchId = dispatchId, isForceFetchedFromServer = false
        )
        return if (stopList.isNotEmpty()) {
            if (stopList.none { it.deleted == 0 }) {
                Log.n(
                    TRIP_COMPLETE,
                    "Cid:$cid T#:$truckNumber D:$dispatchId. reason: none undeleted stops"
                )
                return Pair(dispatchId, true)
            }

            val uncompletedActionsStopList =
                stopList.filterNot { stopDetail ->
                    if (stopDetail.deleted == 1) true else {
                        stopDetail.completedTime.isNotEmpty() && stopDetail.Actions.isNotEmpty() && stopDetail.hasNoPendingAction()
                    }
                }

            val uncompletedFormStopList = stopList.filterNot { stopDetail ->
                val unCompletedFormCount = localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetail.stopid}"), ZERO
                )
                if (stopDetail.isStopSoftDeleted()) true
                else unCompletedFormCount == 0
            }

            val logUncompletedStopIDs = uncompletedActionsStopList.map { it.stopid }
            val logUncompletedStopFormCount = uncompletedFormStopList.map { it.stopid }
            Log.d(
                TRIP_COMPLETE,
                "$caller tripId: $dispatchId " +
                        "uncompletedStops: $logUncompletedStopIDs " + "uncompletedStopFormCount: $logUncompletedStopFormCount"
            )
            if (uncompletedActionsStopList.isEmpty() && uncompletedFormStopList.isNotEmpty() && WorkflowApplication.isDispatchActivityVisible()) {
                Log.w(
                    TRIP_COMPLETE_FORM_PENDING,
                    "All stop actions are completed and Form response is pending $caller tripId: $dispatchId " +
                            "uncompletedStops: $logUncompletedStopIDs " + "uncompletedStopFormCount: $logUncompletedStopFormCount"
                )
            }
            Pair(dispatchId, uncompletedActionsStopList.isEmpty() && uncompletedFormStopList.isEmpty())
        } else Pair(dispatchId, false)
    }

    internal fun getLocalDataSourceRepo(): LocalDataSourceRepo = localDataSourceRepo

    suspend fun areAllStopsDeleted(
        cid: String,
        truckNumber: String,
        dispatchId: String
    ): Boolean {
        dispatchFirestoreRepo.getStopCountOfDispatch(cid, truckNumber, dispatchId)?.let { stopCount ->
            return stopCount == ZERO
        }
        return false
    }

    suspend fun isDispatchAlreadyCompleted(cid: String, truckNumber: String, dispatchId: String):Boolean {
        return dispatchFirestoreRepo.isDispatchCompleted(dispatchId, cid, truckNumber)
    }

    suspend fun processDeletedDispatchAndSendTripCompletionEventsToPFM(fcmData: FcmData) {
        if (appModuleCommunicator.getCurrentWorkFlowId("DispatchDelete") != EMPTY_STRING && appModuleCommunicator.getCurrentWorkFlowId(
                "DispatchDelete"
            ) == fcmData.dispatchId
        ) {
            // PFM TripEvents kept same as dispatch completion via removing all stops
            val pfmEventsInfo = PFMEventsInfo.TripEvents(
                reasonType = StopActionReasonTypes.AUTO.name,
                negativeGuf = false
            )
            runOnTripEnd(
                dispatchId = fcmData.dispatchId,
                caller = "DispatchDeleteFCM",
                workManager = WorkManager.getInstance(ApplicationContextProvider.getApplicationContext()),
                pfmEventsInfo = pfmEventsInfo
            )
        }
        setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(
            customerId = fcmData.cid,
            vehicleId = fcmData.vid,
            dispatchId = fcmData.dispatchId,
            dispatchDeletedTime = fcmData.dispatchDeletedTime
        )
    }

    suspend fun setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(customerId: String, vehicleId : String, dispatchId: String, dispatchDeletedTime : String){
        tripMobileOriginatedEventsRepo.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(customerId, vehicleId, dispatchId, dispatchDeletedTime)
    }
}