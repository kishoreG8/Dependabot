package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.logger.*
import com.trimble.ttm.commons.model.*
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ARRIVAL_TIME
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.COMPLETED_STOP_ID_SET_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.CURRENT_DISPATCH_NAME_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.CURRENT_STOP_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.DEPARTED_TIME
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.IS_DYA_ALERT_ACTIVE
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.NAVIGATION_ELIGIBLE_STOP_LIST_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.STOPS_SERVICE_REFERENCE_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY
import com.trimble.ttm.commons.usecase.*
import com.trimble.ttm.commons.utils.CID
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.DateUtil.calculateTimeDifference
import com.trimble.ttm.commons.utils.DateUtil.getCalendar
import com.trimble.ttm.commons.utils.DateUtil.getDate
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.EXPIRE_AT
import com.trimble.ttm.commons.utils.FeatureFlagGateKeeper
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.ON_BACKGROUND_NEGATIVE_GUF_CALLER
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.commons.utils.UNDERSCORE
import com.trimble.ttm.commons.utils.Utils
import com.trimble.ttm.commons.utils.VALUE
import com.trimble.ttm.commons.utils.VEHICLE_ID
import com.trimble.ttm.commons.utils.toSafeString
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.model.*
import com.trimble.ttm.routemanifest.repo.*
import com.trimble.ttm.routemanifest.ui.activities.DispatchDetailActivity
import com.trimble.ttm.routemanifest.utils.ARRIVAL_ACTION_STATUS
import com.trimble.ttm.routemanifest.utils.ARRIVAL_REASON_DETAILS
import com.trimble.ttm.routemanifest.utils.AUTO_ARRIVED
import com.trimble.ttm.routemanifest.utils.AUTO_DEPARTED
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_DISPATCH_EVENT
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_STOP_EVENTS
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_TRIP_START
import com.trimble.ttm.routemanifest.utils.COLLECTION_NAME_VEHICLES
import com.trimble.ttm.routemanifest.utils.DEFAULT_DEPART_RADIUS_IN_FEET
import com.trimble.ttm.routemanifest.utils.DEPART
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_TO_RENDER
import com.trimble.ttm.routemanifest.utils.DRIVERID
import com.trimble.ttm.routemanifest.utils.FALSE
import com.trimble.ttm.routemanifest.utils.GEOFENCE_TYPE
import com.trimble.ttm.routemanifest.utils.GMT_DATE_TIME_FORMAT
import com.trimble.ttm.routemanifest.utils.JsonDataConstructionUtils
import com.trimble.ttm.routemanifest.utils.MANUAL_ARRIVED
import com.trimble.ttm.routemanifest.utils.MANUAL_DEPARTED
import com.trimble.ttm.routemanifest.utils.SEQUENCED
import com.trimble.ttm.routemanifest.utils.TIME_TAKEN_FROM_ARRIVAL_TO_DEPARTURE
import com.trimble.ttm.routemanifest.utils.TRUE
import com.trimble.ttm.routemanifest.utils.VEHICLES_COLLECTION
import com.trimble.ttm.routemanifest.utils.ext.*
import com.trimble.ttm.routemanifest.utils.ext.isEqualTo
import com.trimble.ttm.routemanifest.viewmodel.ACTIONS
import com.trimble.ttm.routemanifest.viewmodel.STOPS
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class DispatchStopsUseCase(
    private val formsRepository: FormsRepository,
    private val dispatchFirestoreRepo: DispatchFirestoreRepo,
    private val dispatchProvider: DispatcherProvider,
    private val stopDetentionWarningUseCase: StopDetentionWarningUseCase,
    private val routeETACalculationUseCase: RouteETACalculationUseCase,
    private val featureFlagGateKeeper: FeatureGatekeeper,
    private val formUseCase: FormUseCase,
    private val sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase,
    private val deepLinkUseCase: DeepLinkUseCase,
    private val arriveTriggerDataStoreKeyManipulationUseCase: ArriveTriggerDataStoreKeyManipulationUseCase,
    private val dataStoreManager: DataStoreManager,
    private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase
) : KoinComponent {
    private val tag = "DispatchStopsUseCase"
    private val tripMobileOriginatedEventsRepo: TripMobileOriginatedEventsRepo by inject()
    private val tripPanelUseCase: TripPanelUseCase by inject()
    private val sendDispatchDataUseCase: SendDispatchDataUseCase by inject()
    internal val appModuleCommunicator = dispatchFirestoreRepo.getAppModuleCommunicator()
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder by inject()
    private val backboneUseCase: BackboneUseCase by inject()
    private val arrivalReasonUsecase: ArrivalReasonUsecase by inject()
    private val arrivalReasonEventRepo : ArrivalReasonEventRepo by inject()
    private val dispatchStopsCoroutineScope = CoroutineScope(SupervisorJob() + dispatchProvider.io())

    suspend fun putStopIntoPreferenceAsCurrentStop(
        stopDetail: StopDetail, dataStoreManager: DataStoreManager
    ): Boolean = withContext(dispatchProvider.io()) {
        //Do not set Current stop in datastore for inactive/Preview trip.
        if(com.trimble.ttm.routemanifest.utils.Utils.isIncomingDispatchSameAsActiveDispatch(incomingDispatchId = stopDetail.dispid, activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("putStopIntoPreferenceAsCurrentStop")).not()){
            Log.d(tag, "putStopIntoPreferenceAsCurrentStop: returning false ${stopDetail.stopid} dispId: ${stopDetail.dispid}")
            return@withContext false
        }
        if (dataStoreManager.containsKey(CURRENT_STOP_KEY).not()) {
            Gson().toJson(Stop())?.also { stopStr ->
                dataStoreManager.setValue(CURRENT_STOP_KEY, stopStr)
            }
        }
        val stop: Stop = Gson().fromJson(
            dataStoreManager.getValue(CURRENT_STOP_KEY, EMPTY_STRING), Stop::class.java
        ) ?: Stop()
        if (stop.stopId == stopDetail.stopid) return@withContext false
        Gson().toJson(initStopDataAndUpdate(stop, stopDetail))?.also { curStop ->
            dataStoreManager.setValue(CURRENT_STOP_KEY, curStop)
        }
        return@withContext true
    }

    fun isStopActionsAreTriggered(stop: Stop): Boolean {
        var hasNoPendingAction = true
        if (stop.approachRadius > 0 && !stop.approachResponseSent) hasNoPendingAction = false
        if (stop.arrivedRadius > 0 && !stop.arrivedResponseSent) hasNoPendingAction = false
        if (stop.departRadius > 0 && !stop.departResponseSent) hasNoPendingAction = false

        return hasNoPendingAction
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun setNextStopForTracking(
        currentStop: Stop, dataStoreManager: DataStoreManager
    ) {
        val stops = removeCompletedStopFromStopsServicePreference(dataStoreManager, currentStop)
        if (stops.isNotEmpty()) {
            stops.getStopListFromGivenStop(currentStop.stopId).getUncompletedStopsBasedOnCompletedTime().let { stopList ->
                if (stopList.isNotEmpty()) {
                    val nextStop = stopList.first()
                    putStopIntoPreferenceAsCurrentStop(nextStop, dataStoreManager)
                }
            }
        }
    }

    suspend fun getStopAndActions(
        stopId: Int, dataStoreManager: DataStoreManager, caller: String
    ): StopDetail = coroutineScope {
        val cid = appModuleCommunicator.doGetCid()
        val truckNum = appModuleCommunicator.doGetTruckNumber()
        val dispatchId = dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING)
        if (cid.isEmpty() || truckNum.isEmpty() || dispatchId.isEmpty()) {
            Log.i(
                caller,
                "invalid data request to access stop and its actions. cid:$cid trucknum:$truckNum dispId:$dispatchId"
            )
            return@coroutineScope StopDetail()
        }
        val stopDetailJob = async(dispatchProvider.io()) {
            dispatchFirestoreRepo.getStop(
                cid, truckNum, dispatchId, stopId.toString()
            )
        }

        val stopActionsJob = async(dispatchProvider.io()) {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                dispatchId, stopId.toString(), "getStopAndActions"
            )
        }
        val stopDetailJobResults = listOf(stopDetailJob, stopActionsJob).awaitAll()
        return@coroutineScope handleStopDetailResultAndReturn(stopDetailJobResults)
    }

    @SuppressWarnings("unchecked")
    private fun handleStopDetailResultAndReturn(
        stopDetailJobResults: List<Any>
    ): StopDetail {
        if (stopDetailJobResults.size == 2) {
            val stopDetail = stopDetailJobResults[0] as StopDetail
            val actions = stopDetailJobResults[1] as List<Action>
            if (stopDetail.stopid != -1 && actions.isEmpty().not()) {
                stopDetail.Actions.clear()
                stopDetail.Actions.addAll(actions)
                return stopDetail
            }
        }
        return StopDetail()
    }

    suspend fun getActionDataFromStop(
        activeDispatchId: String, stopId: Int, actionType: Int
    ): Action? {
        fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
            activeDispatchId, stopId.toString(), "getActionDataFromStop"
        ).forEach { action ->
            if (action.actionType == actionType) return action
        }
        return null
    }

    suspend fun getActionsOfStop(activeDispatchId: String, stopId: Int, caller : String) : List<Action> {
        return fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
            activeDispatchId, stopId.toString(), caller
        )
    }

    suspend fun handleStopEvents(
        action: Action?,
        stopActionEventData: StopActionEventData,
        stopDetail: StopDetail = StopDetail(),
        caller: String,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ): String =
        withContext(dispatchProvider.io()) {
            var stopArrivedTime = ""
            val currentStopFromPreference = Gson().fromJson(
                dataStoreManager.getValue(CURRENT_STOP_KEY, EMPTY_STRING),
                Stop::class.java
            )
            Log.logGeoFenceEventFlow(
                ACTION_COMPLETION,
                "GeofenceEventCurrentStop: ${currentStopFromPreference != null} D${action?.dispid} S${action?.stopid} A${action?.actionid} Sent${action?.responseSent}",
                KEY to DISPATCH_LIFECYCLE,
                DISPATCHID to action?.dispid,
                STOPID to action?.stopid,
            )
            checkAndLaunchDeepLinkForArrivalAction(
                action = action,
                context = stopActionEventData.context,
                caller = "handleStopEvents:${pfmEventsInfo.reasonType}"
            )
            if(action?.actionType == ActionTypes.ARRIVED.ordinal) {
                dispatchStopsCoroutineScope.async {
                    checkAndAutoDepartThePreviousStopOnArrival(
                        action.dispid, //since stop detail can be default value passing action's dispatch id
                        stopActionEventData
                    )
                }.await()
            }
            if (action != null && action.responseSent.not()) {
                dispatchStopsCoroutineScope.async {
                    recordStopEventsInFirebaseMetrics(action, pfmEventsInfo)
                    stopArrivedTime = checkStopDataOfEventTriggerAndProceed(
                        currentStopFromPreference,
                        action,
                        pfmEventsInfo
                    )
                    sendStopActionWorkflowEventsToThirdPartyApps(
                        action,
                        pfmEventsInfo.reasonType,
                        timeStamp = System.currentTimeMillis(),
                        caller
                    )
                }.await()
        } else {
            // Check if this event is triggered for the current stop
            if (currentStopFromPreference != null && stopActionEventData.stopId ==
                currentStopFromPreference.stopId
                && stopActionEventData.hasDriverAcknowledgedArrivalOrManualArrival
            ) {
                dispatchStopsCoroutineScope.async {
                    // Update the CompletedTime in firebase
                    stopArrivedTime = updateStopCompletionTimeForCurrentStopInFirestore(
                        null,
                        currentStopFromPreference,
                        true
                    )
                }.await()
            } else if (currentStopFromPreference == null || stopActionEventData.stopId != currentStopFromPreference.stopId
                && stopActionEventData.hasDriverAcknowledgedArrivalOrManualArrival
            ) {
                dispatchStopsCoroutineScope.async {
                    val stop = initStopDataAndUpdate(Stop(), stopDetail)
                    // Update the CompletedTime in firebase
                    stopArrivedTime = updateStopCompletionTimeInFirestoreAndRemoveStopFromDataStore(
                        null,
                        stop,
                        true
                    )
                }.await()
            }
        }
        return@withContext stopArrivedTime
    }
    /*
    During any stop arrival this method will get the previous stop detail for which departure action is pending and mark it is completed
     */
    suspend fun checkAndAutoDepartThePreviousStopOnArrival(
        dispatchId: String,
        stopActionEventData: StopActionEventData
    ) {
        val stopInfoWhereDepartureActionNeedsToBeMarked =
            getStopDetailWhereDepartureActionNeedsToBeCompleted(dispatchId = dispatchId)
        stopInfoWhereDepartureActionNeedsToBeMarked?.let {
            Log.d(
                ACTION_COMPLETION,
                "departure action needs to be completed ${stopInfoWhereDepartureActionNeedsToBeMarked.stopid}",
                null,
                "triggered stop event" to stopActionEventData.stopId
            )
            val stopAction = StopActionEventData(
                stopId = stopInfoWhereDepartureActionNeedsToBeMarked.stopid,
                actionType = ActionTypes.DEPARTED.ordinal,
                context = stopActionEventData.context,
                hasDriverAcknowledgedArrivalOrManualArrival = false
            )
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                reasonType = StopActionReasonTypes.AUTO.name,
                negativeGuf = false
            )
            handleStopEvents(
                action = stopInfoWhereDepartureActionNeedsToBeMarked.Actions.firstOrNull { it.actionType == ActionTypes.DEPARTED.ordinal },
                stopActionEventData = stopAction,
                caller = "autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived",
                pfmEventsInfo = pfmEventsInfo
            )
            //Since we're auto marking the departure event removing the geofence, removing the trigger from cache and un marking the stop manipulation key like we do for auto departure
            postDepartureEventProcess(
                stopInfoWhereDepartureActionNeedsToBeMarked,
                stopAction.stopId,
                dispatchId,
                stopAction
            )
        }
    }

    suspend fun getStopDetailWhereDepartureActionNeedsToBeCompleted(dispatchId: String): StopDetail? {
        return fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
            cid = appModuleCommunicator.doGetCid(),
            vehicleNumber = appModuleCommunicator.doGetTruckNumber(),
            dispatchId = dispatchId,
            caller = "getStopDetailWhereDepartureActionNeedsToBeCompleted",
            isForceFetchFromServer = false
        ).let { stopDetailList ->
            stopDetailList.firstOrNull { stopDetail ->
                isStopHasArriveActionCompletedAndDepartureActionPending(stopDetail)
            }
        }
    }

    suspend fun getAllActiveStopsAndActions(caller: String) =
        fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(caller)

    private fun isStopHasArriveActionCompletedAndDepartureActionPending(stopDetail: StopDetail): Boolean {
        return stopDetail.isStopSoftDeleted().not() && stopDetail.hasNoArrivedAction()
            .not() && stopDetail.hasNoDepartedAction()
            .not() && stopDetail.isArrived() && stopDetail.isDeparted().not()
    }

    /*
    This method is used to remove the arriveTrigger from preference , remove the geofence from maps and un mark the stop manipulation key
    It will be called from two places
    ServiceManager - When a departure event is triggered automatically from launcher
    DispatchStopsUseCase - When any stop is arrived we auto complete the departure event for the previously arrived stop as per MAPP-10196
     */
    suspend fun postDepartureEventProcess(
        stop: StopDetail,
        stopIdWhichDepartActionTriggered: Int,
        activeDispatchId: String,
        stopActionEventData: StopActionEventData
    ) {
        arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(
            stopIdWhichDepartActionTriggered
        ) { stopId ->
            tripPanelUseCase.removeMessageFromPriorityQueueAndUpdateTripPanelFlags(stopId)
        }
        processGeofenceTriggerForGeofenceRemoval(
            stop,
            activeDispatchId,
            stopActionEventData
        )
        if (stop.deleted == 0) {
            unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                caller = TRIP_EDIT + DEPART,
                stop = stop,
                actionType = ActionTypes.DEPARTED.ordinal,
                stopActionReasonTypes = StopActionReasonTypes.AUTO
            )
        }
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun checkStopDataOfEventTriggerAndProceed(
        currentStopFromPreference: Stop?,
        action: Action,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ): String {
        var stopArrivedTime: String
        var departedStop = currentStopFromPreference ?: Stop()
        Log.logGeoFenceEventFlow(
            ACTION_COMPLETION,
            "GeofenceEventTriggerAndProceed: ${currentStopFromPreference?.stopId} D${action.dispid} S${action.stopid} A${action.actionid}"
        )
        // Check if this event is triggered for the current stop
        if (currentStopFromPreference != null && action.stopid == currentStopFromPreference.stopId) {
            sendActionResponse(
                currentStopFromPreference, action, pfmEventsInfo
            )

            // Update the CompletedTime in firebase
            stopArrivedTime = updateStopCompletionTimeForCurrentStopInFirestore(
                action, currentStopFromPreference, false
            )
            if (action.actionType == ActionTypes.APPROACHING.ordinal) tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        } else {
            // Driver manually arrived into the stop.Not the current stop.
            getStopAndActions(
                action.stopid, dataStoreManager, TRIP_STOP_MANUAL_ARRIVAL
            ).let { stopDetail ->  //todo we could get stop detail from invoker.
                // Don't put this into preference
                val stop = initStopDataAndUpdate(Stop(), stopDetail)
                // Send an action response
                sendActionResponse(
                    stop,
                    action,
                    pfmEventsInfo
                )
                // Update the CompletedTime in firebase
                stopArrivedTime = updateStopCompletionTimeInFirestoreAndRemoveStopFromDataStore(
                    action, stop
                )
                departedStop = stop
            }
        }

        if (action.actionType == ActionTypes.DEPARTED.ordinal) {
            updateStopDepartureTimeInFirebase(departedStop, dataStoreManager)
            formUseCase.removeFormFromPreference(action.stopid, dataStoreManager)
        }
        return stopArrivedTime
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun recordStopEventsInFirebaseMetrics(
        action: Action?,
        pfmEventsStopActionInfo: PFMEventsInfo.StopActionEvents
    ) {
        when (action?.actionType) {
            ActionTypes.ARRIVED.ordinal -> recordArriveEventInFirebaseMetrics(
                pfmEventsStopActionInfo
            )

            ActionTypes.DEPARTED.ordinal -> recordDepartEventInFirebaseMetrics(
                pfmEventsStopActionInfo
            )
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun recordDepartEventInFirebaseMetrics(pfmEventsStopActionInfo: PFMEventsInfo.StopActionEvents) {
        when (pfmEventsStopActionInfo.reasonType) {
            StopActionReasonTypes.MANUAL.name -> firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                MANUAL_DEPARTED
            )

            else -> firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                AUTO_DEPARTED
            )
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun recordArriveEventInFirebaseMetrics(pfmEventsStopActionInfo: PFMEventsInfo.StopActionEvents) {
        when (pfmEventsStopActionInfo.reasonType) {
            StopActionReasonTypes.MANUAL.name -> firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                MANUAL_ARRIVED
            )

            else -> firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                AUTO_ARRIVED
            )
        }
    }

    private fun checkAndLaunchDeepLinkForArrivalAction(
        action: Action?,
        context: Context,
        caller: String
    ) {
        if (action != null && action.actionType == ActionTypes.ARRIVED.ordinal && action.responseSent.not()) {
            deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                context = context,
                caller = caller
            )
        }
    }

    private suspend fun updateStopCompletionTimeForCurrentStopInFirestore(
        action: Action?,
        currentStopFromPreference: Stop,
        driverAckedArrival: Boolean
    ): String {
        var stopArrivedTime = ""
        val canUpdateStopCompleteTime = canUpdateStopCompletionTimeBasedOnActionCompletion(
            dataStoreManager, action
        )
        Log.d(
            ACTION_COMPLETION,
            "GeofenceUpdateCurrentStopCompletion:D${action?.dispid} S${action?.stopid} A${action?.actionid} $canUpdateStopCompleteTime ack$driverAckedArrival"
        )
        if (canUpdateStopCompleteTime || driverAckedArrival) {
            stopArrivedTime = updateCompletionTimeInStopDocument(
                currentStopFromPreference, dataStoreManager
            )
            removeCompletedStopAndSetTheCurrentStop(
                currentStopFromPreference, driverAckedArrival
            )
            return stopArrivedTime
        }
        return stopArrivedTime
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun updateStopCompletionTimeInFirestoreAndRemoveStopFromDataStore(
        action: Action?, stop: Stop, driverAckedArrival: Boolean = false
    ): String {
        val arrivedTime: String
        val canUpdateStopCompleteTime = canUpdateStopCompletionTimeBasedOnActionCompletion(
            dataStoreManager, action
        )
        Log.logGeoFenceEventFlow(
            ACTION_COMPLETION,
            "GeofenceEventUpdateStopCompletion: D${action?.dispid} S${action?.stopid} A${action?.actionid} $canUpdateStopCompleteTime ack$driverAckedArrival"
        )
        arrivedTime = if (canUpdateStopCompleteTime || driverAckedArrival) {
            arrivedTime = updateCompletionTimeInStopDocument(
                stop, dataStoreManager
            )
            removeCompletedStopFromStopsServicePreference(dataStoreManager, stop)
            return arrivedTime
            // Don't set next stop here as this was arrived manually
        } else ""
        return arrivedTime
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun removeCompletedStopAndSetTheCurrentStop(
        currentStopFromPreference: Stop,
        driverAckedArrival: Boolean
    ) {
        // This stop is completed so remove this from preference
        dataStoreManager.removeItem(CURRENT_STOP_KEY)
        if (driverAckedArrival) {
            removeCompletedStopFromStopsServicePreference(
                dataStoreManager, currentStopFromPreference
            )
        }
        // Set the next stop for tracking
        if (dataStoreManager.getValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE))
            setNextStopForTracking(
                currentStopFromPreference,
                dataStoreManager
            )
        // Either a new stop is also set Or just removed. So send the message to location panel
        tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
    }


    suspend fun removeCompletedStopFromStopsServicePreference(
        dataStoreManager: DataStoreManager, stop: Stop
    ): List<StopDetail> = dataStoreManager.getValue(STOPS_SERVICE_REFERENCE_KEY, EMPTY_STRING).let {
        Log.d(tag, "removeCompletedStopFromStopsServicePreference $it D${stop.dispId} disp string: $it")
        if (it.isNotEmpty()) {
            try {
                Gson().fromJson<List<StopDetail>>(
                    it, object : TypeToken<List<StopDetail>>() {}.type
                )?.filter { stopDetail -> stopDetail.stopid != stop.stopId }?.getSortedStops()?.let { stopList ->
                    if (stopList.isNotEmpty()) {
                        dataStoreManager.setValue(
                            STOPS_SERVICE_REFERENCE_KEY,
                            Utils.toJsonString(stopList) ?: EMPTY_STRING
                        )
                        return stopList
                    } else {
                        dataStoreManager.setValue(
                            STOPS_SERVICE_REFERENCE_KEY,
                            Utils.toJsonString(EMPTY_STRING) ?: EMPTY_STRING
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception in removeCompletedStopFromStopsServicePreference $it D${stop.dispId}",
                    e
                )
            }
        }
        return emptyList()
    }


    fun initStopDataAndUpdate(
        stop: Stop, stopDetail: StopDetail
    ): Stop {
        stop.apply {
            stopId = stopDetail.stopid
            stopName = stopDetail.name
            latitude = stopDetail.latitude
            longitude = stopDetail.longitude
        }
        stop.dispId = stopDetail.dispid

        /**
         * As we take previous stop object and replace the values with the newer one
         * clear the approachRadius, arrivedRadius, departRadius values if no actions
         * available for the newer stop
         * */
        stop.apply {
            approachRadius = 0
            appraochFormId = 0
            arrivedRadius = 0
            arrivedFormId = 0
            arrivedFormClass = -1
            departRadius = 0
            departFormId = 0
        }

        stopDetail.Actions.forEach {
            stop.apply {
                when (it.actionType) {
                    0 -> {
                        approachRadius = it.radius
                        appraochFormId = it.driverFormid
                    }

                    1 -> {
                        arrivedFormId = it.driverFormid
                        arrivedFormClass = it.driverFormClass
                        arrivedRadius = it.radius
                        hasArriveAction = true
                    }

                    2 -> {
                        departRadius = it.radius
                        departFormId = it.driverFormid
                    }
                }
            }
        }

        stop.apply {
            if (stopDetail.siteCoordinates.isNotNull()) siteCoordinates =
                stopDetail.siteCoordinates!!
            // Add default geofence radius, based on depart radius if arrived action does not exists
            if (stopDetail.hasNoArrivedAction()) stopDetail.getDefaultArrivedRadius()?.let { arrivedRadius = it }
            // Add default geofence radius if depart action does not exists
            if (stopDetail.hasNoDepartedAction()) departRadius = DEFAULT_DEPART_RADIUS_IN_FEET
        }

        return stop
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun sendActionResponse(
        stop: Stop,
        action: Action,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ) {
        val cid = appModuleCommunicator.doGetCid()
        val truckNumber = appModuleCommunicator.doGetTruckNumber()
        val currentWorkflowId = appModuleCommunicator.getCurrentWorkFlowId("sendActionResponse")
        val isConfigurableOdometerEnabled =  FeatureFlagGateKeeper().isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SHOULD_USE_CONFIGURABLE_ODOMETER, appModuleCommunicator.getFeatureFlags(), cid)
        val customerIdObcIdVehicleId = Triple(cid.toInt(), appModuleCommunicator.doGetObcId(), truckNumber)
        /**
         * //todo why do we get action again if we have incoming action.if it is required refactor to get already obtained actions from the invoker
         */
        fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
            currentWorkflowId,
            stop.stopId.toString(),
            "sendActionResponse"
        )
            .find { actionData -> actionData.actionType == action.actionType }
            ?.let { matchingAction ->
                if (matchingAction.responseSent) return
                Log.logGeoFenceEventFlow(
                    ACTION_COMPLETION,
                    "GeofenceEventSendAction: D$currentWorkflowId S${action.stopid} A${action.actionid} R${pfmEventsInfo.reasonType}"
                )
                val path = buildActionDocumentPath(stop, action)
                val jsonData = JsonDataConstructionUtils.getStopActionJson(
                    action = action,
                    createDate = getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time),
                    pfmEventsInfo = pfmEventsInfo,
                    fuelLevel = backboneUseCase.getFuelLevel(),
                    odometerReading = backboneUseCase.getOdometerReading(isConfigurableOdometerEnabled),
                    customerIdObcIdVehicleId = customerIdObcIdVehicleId,
                    currentLocationLatLong = backboneUseCase.getCurrentLocation()
                )
                val arrivalReason = when(action.actionType) {
                    ActionTypes.ARRIVED.ordinal -> arrivalReasonEventRepo.getCurrentStopArrivalReason(
                        "$cid/$VEHICLES_COLLECTION/$truckNumber/$DISPATCH_COLLECTION/${action.dispid}/$STOPS/${stop.stopId}"
                    )
                    else -> ArrivalReason()
                }
                tripMobileOriginatedEventsRepo.saveStopActionResponse(
                    ActionTypes.entries[action.actionType].action,
                    "$cid/$COLLECTION_NAME_VEHICLES/$truckNumber" +
                            "/$COLLECTION_NAME_STOP_EVENTS/${action.dispid}$UNDERSCORE${stop.stopId}$UNDERSCORE${action.actionid}",
                    hashMapOf(
                        VALUE to jsonData.value,
                        EXPIRE_AT to DateUtil.getExpireAtDateTimeForTTLInUTC(),
                        DISPATCHID to currentWorkflowId
                    ),
                    arrivalReason,
                    action.triggerReceivedTime
                )
                action.responseSent = true
                tripMobileOriginatedEventsRepo.updateActionPayload(
                    path,
                    action
                )
                when (action.actionType) {
                    0 -> stop.approachResponseSent = true
                    1 -> stop.arrivedResponseSent = true
                    2 -> stop.departResponseSent = true
                }
            } ?: Log.e(
            ACTION_COMPLETION,
            "GeofenceEventMatchingActionNA: $cid T:$truckNumber D$currentWorkflowId S${stop.stopId} A${action.actionid} R${pfmEventsInfo.reasonType}"
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun buildActionDocumentPath(
        stop: Stop, action: Action
    ): String {
        return "$DISPATCH_COLLECTION/${
            appModuleCommunicator.doGetCid()
        }/${
            appModuleCommunicator.doGetTruckNumber()
        }/${
            appModuleCommunicator.getCurrentWorkFlowId("buildActionDocumentPath")
        }/$STOPS/${stop.stopId}/$ACTIONS/${action.actionid}"
    }

    fun getUncompletedFormsMessage(
        formsList: ArrayList<DispatchFormPath>,
        singleStopMessage: String, multipleStopsMessage: String
    ): String {

        val stopsIds = hashSetOf<Int>()
        // Get all stop ids
        formsList.forEach {
            stopsIds.add(it.stopId)
        }

        return when {
            stopsIds.size == 1 -> String.format(
                singleStopMessage, formsList.first().stopName
            )

            stopsIds.size > 1 -> String.format(
                multipleStopsMessage, stopsIds.size
            )

            else -> ""
        }
    }


    suspend fun canUpdateStopCompletionTimeBasedOnActionCompletion(
        dataStoreManager: DataStoreManager, action: Action?
    ): Boolean {
        if (action == null) return false
        if (action.actionType == ActionTypes.ARRIVED.ordinal) return true
        val stopDetail = getStopAndActions(
            action.stopid, dataStoreManager,
            "$TRIP:Method:canUpdateStopCompletionTimeBasedOnActionCompletion"
        )
        return if (stopDetail.stopid != -1 && stopDetail.Actions.isEmpty().not()) {
            when {
                (action.actionType == ActionTypes.DEPARTED.ordinal && stopDetail.hasDepartActionOnly()) || (action.actionType == ActionTypes.DEPARTED.ordinal && stopDetail.hasNoArrivedAction()) -> true
                action.actionType == ActionTypes.APPROACHING.ordinal && stopDetail.hasArrivingActionOnly() -> true
                else -> false
            }
        } else false
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun updateCompletionTimeInStopDocument(
        stop: Stop,
        dataStoreManager: DataStoreManager
    ): String {
        Log.logGeoFenceEventFlow(
            tag,
            "GeofenceEventUpdateStop: D${stop.dispId} S${stop.stopId}"
        )
        val stopCompletionTime = getUTCFormattedDate(Calendar.getInstance().time)
        val customerId = appModuleCommunicator.doGetCid()
        val vehicleId = appModuleCommunicator.doGetTruckNumber()
        val dispatchId = appModuleCommunicator.getCurrentWorkFlowId("updateCompletionTimeInStopDocument")
        if(customerId.isEmpty() || vehicleId.isEmpty()  || dispatchId.isEmpty() || stop.stopId < 0) {
            Log.w(tag, "Stop complete time cannot be set due to CID, vehicleId, DispatchId or stopId is empty", null, CID to customerId, VEHICLE_ID to vehicleId, DISPATCHID to dispatchId, STOPID to stop.stopId)
            return EMPTY_STRING
        }
        tripMobileOriginatedEventsRepo.setCompletedTimeForStop(
            "$DISPATCH_COLLECTION/$customerId/$vehicleId/$dispatchId/$STOPS/${stop.stopId}",
            stopCompletionTime, COMPLETED_TIME_KEY
        )
        val cachedCompletedStops =
            dataStoreManager.getValue(COMPLETED_STOP_ID_SET_KEY, emptySet()).toMutableSet()
        cachedCompletedStops.add(stop.stopId.toString())
        dataStoreManager.setValue(COMPLETED_STOP_ID_SET_KEY, cachedCompletedStops)
        dataStoreManager.setValue(ARRIVAL_TIME, getCalendar().time.toString())
        if (dataStoreManager.getValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE).not()
        ) {
            setStopsNavigateEligibility(stop.stopId, dataStoreManager)
        }
        return stopCompletionTime
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun updateStopDepartureTimeInFirebase(
        stop: Stop,
        dataStoreManager: DataStoreManager
    ) {
        val stopDepartureTime = getUTCFormattedDate(Calendar.getInstance().time)
        dataStoreManager.setValue(DEPARTED_TIME, getCalendar().time.toString())

        // Logging event to record the time taken from arrival to departure
        recordTimeDifferenceBetweenArriveAndDepartEvent()
        tripMobileOriginatedEventsRepo.setDepartedTimeForStop(
            "$DISPATCH_COLLECTION/${
                appModuleCommunicator.doGetCid()
            }/${
                appModuleCommunicator.doGetTruckNumber()
            }/${
                appModuleCommunicator.getCurrentWorkFlowId("updateStopCompletionTimeInFirebase")
            }/$STOPS/${stop.stopId}",
            stopDepartureTime, DEPARTED_TIME_KEY
        )
    }

    suspend fun recordTimeDifferenceBetweenArriveAndDepartEvent() =
        withContext(dispatchProvider.io()) {
            val timeDuration = calculateTimeDifference(
                getDate(
                    GMT_DATE_TIME_FORMAT,
                    dataStoreManager.getValue(ARRIVAL_TIME, EMPTY_STRING)
                ),
                getDate(
                    GMT_DATE_TIME_FORMAT,
                    dataStoreManager.getValue(DEPARTED_TIME, EMPTY_STRING)
                )
            )
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(
                eventName = TIME_TAKEN_FROM_ARRIVAL_TO_DEPARTURE,
                duration = timeDuration
            )
        }

    suspend fun setStopsEligibilityForFirstTime(
        list: List<StopDetail>, dataStoreManager: DataStoreManager
    ): Set<String> {
        val eligibleStops = setSequencedStopsEligibility(-1, list, mutableSetOf())
        dataStoreManager.setValue(
            NAVIGATION_ELIGIBLE_STOP_LIST_KEY, eligibleStops
        )
        return eligibleStops
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun setStopsNavigateEligibility(
        finishedStopId: Int, dataStoreManager: DataStoreManager
    ) {
        getAllActiveStopsAndActions("setStopsNavigateEligibility").let { stopList ->
            if(stopList.isNotEmpty()) {
                val alreadyStoredEligibleSequentialStops =
                    dataStoreManager.getValue(NAVIGATION_ELIGIBLE_STOP_LIST_KEY, mutableSetOf())
                        .toMutableSet()
                dataStoreManager.setValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY, setSequencedStopsEligibility(
                        finishedStopId, stopList, alreadyStoredEligibleSequentialStops
                    )
                )
            }
        }
    }


    fun setSequencedStopsEligibility(
        finishedStopId: Int, stopList: List<StopDetail>, eligibleStopIds: MutableSet<String>
    ): MutableSet<String> = try {
        if (checkAllStopsAreCompletedOrNoSequentialStopsToSet(stopList)) mutableSetOf<String>()
        val unCompletedSequentialStops =
            stopList.filter { stop -> stop.sequenced == 1 && stop.completedTime.isEmpty() }
        run loop@{
            getEligibleStopIdsForNextStop(
                finishedStopId, stopList, unCompletedSequentialStops, eligibleStopIds
            )
        }
        removeStopFromEligibleIdsIfItIsCompleted(eligibleStopIds, stopList)
        eligibleStopIds
    } catch (e: Exception) {
        Log.e(tag, "Exception in setSequencedStopsEligibility ${e.message}", e)
        eligibleStopIds
    }

    private fun removeStopFromEligibleIdsIfItIsCompleted(
        eligibleStopIds: MutableSet<String>, stopList: List<StopDetail>
    ) {
        if (eligibleStopIds.isNotEmpty()) {
            stopList.filter { it.completedTime.isNotEmpty() }.forEach {
                if (eligibleStopIds.contains(it.stopid.toString())) eligibleStopIds.remove(
                    it.stopid.toString()
                )
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getEligibleStopIdsForNextStop(
        finishedStopId: Int,
        stopList: List<StopDetail>,
        unCompletedSequentialStops: List<StopDetail>,
        eligibleStopIds: MutableSet<String>
    ): MutableSet<String> {
        for (stopId in finishedStopId until stopList.size) {
            stopList.getOrNull(stopId + 1)?.run {
                if (completedTime.isNotEmpty()) return@run
                when (sequenced) {
                    0 -> {
                        if (unCompletedSequentialStops.isNotEmpty() && unCompletedSequentialStops.size.isEqualTo(
                                stopList.filter { stop -> stop.sequenced == 1 }.size
                            )
                        ) {
                            eligibleStopIds.add(unCompletedSequentialStops.first().stopid.toString())
                        }
                        return eligibleStopIds
                    }

                    1 -> {
                        eligibleStopIds.add(stopid.toString())
                        return eligibleStopIds
                    }
                }
            }
        }
        return mutableSetOf()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun checkAllStopsAreCompletedOrNoSequentialStopsToSet(stopList: List<StopDetail>) =
        stopList.none { stop -> stop.sequenced == 1 && stop.completedTime.isEmpty() }


    suspend fun getArrivedTriggerDataFromPreferenceString(): ArrayList<ArrivedGeoFenceTriggerData> =
        arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()


    suspend fun performActionsAsDriverAcknowledgedArrivalOfStop(
        activeDispatchId: String,
        messageId: Int,
        context: Context,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents,
        caller: String
    ) {
        stopDetentionWarningUseCase.checkForDisplayingDetentionWarningAndStartDetentionWarningTimer(messageId)
        //Remove Arrived Geofence on responding to "Did you arrive" trip panel message
        getActionDataFromStop(
            activeDispatchId,
            messageId,
            ActionTypes.ARRIVED.ordinal
        )?.let { action ->
            sendDispatchDataUseCase.sendRemoveGeoFenceEvent(action)
        }
        sendStopActionEvent(
            activeDispatchId,
            StopActionEventData(
                messageId,
                ActionTypes.ARRIVED.ordinal, context,
                hasDriverAcknowledgedArrivalOrManualArrival = true
            ),
            caller,
            pfmEventsInfo
        )

        tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
    }

    suspend fun processGeofenceTriggerForGeofenceRemoval(
        stop: StopDetail,
        activeDispatchId: String,
        sendStopActionEventData: StopActionEventData
    ) {
        withContext(dispatchProvider.default()) {
            stop.Actions.firstOrNull { it.actionType == sendStopActionEventData.actionType }
                ?.let { action ->
                    Log.logGeoFenceEventFlow(
                        tag,
                        "GeofenceEventRemove: D$activeDispatchId S${action.stopid} A${action.actionid}"
                    )
                    sendDispatchDataUseCase.sendRemoveGeoFenceEvent(action)
                }
        }
    }


    suspend fun sendStopActionEvent(
        activeDispatchId: String,
        sendStopActionEventData: StopActionEventData,
        caller: String,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ) {
        withContext(dispatchProvider.io()) {
            getStopAndActions(
                sendStopActionEventData.stopId, dataStoreManager,
                "$TRIP:Method:sendStopActionEvent"
            ).let { stop ->
                if (stop.stopid < 0 || stop.isStopSoftDeleted()) {
                    Log.i(
                        "$tag$caller",
                        "Stop is invalid or soft deleted. StopId: ${stop.stopid} IsDeleted: ${stop.isStopSoftDeleted()}"
                    )
                    return@let
                }
                Log.logGeoFenceEventFlow(
                    tag,
                    "GeofenceEventGetStopAndActions: D$activeDispatchId S${stop.stopid} A${sendStopActionEventData.actionType}",
                    KEY to DISPATCH_LIFECYCLE,
                    DISPATCHID to activeDispatchId,
                    STOPID to stop.stopid,
                )
                if (stop.Actions.isEmpty()) {
                    Log.e(
                        tag,
                        "GeofenceEventActionsEmpty: D$activeDispatchId S${stop.stopid} A${sendStopActionEventData.actionType}"
                    )
                }

                //if driver presses No for "Did you arrive?" and crosses the stop, it shouldn't mark the depart action as DEPARTED-COMPLETE for that stop.
                // This snippet will prevent that.
                if (doNotConsiderDepartEventTriggerIfArrivedActionNotCompleted(
                        stop,
                        sendStopActionEventData
                    )
                ) {
                    //If incase trigger is ignored by WF and driver didn't receive trigger this would prevent from overwriting the reason with DYA_IGNORED
                    if (dataStoreManager.getValue(IS_DYA_ALERT_ACTIVE, false)) {
                        val arrivalReasonHashMap = arrivalReasonUsecase.getArrivalReasonMap(
                            ArrivalActionStatus.DYA_IGNORED_BY_DRIVER.toString(),
                            stop.stopid,
                            false
                        )
                        arrivalReasonUsecase.updateArrivalReasonForCurrentStop(
                            stop.stopid,
                            arrivalReasonHashMap
                        )
                    }
                    val arrivalReason = arrivalReasonEventRepo.getCurrentStopArrivalReason(
                        arrivalReasonEventRepo.getArrivalReasonCollectionPath(stop.stopid)
                    )
                    val isArrivedTriggerInPreference = getArrivedTriggerDataFromPreferenceString().filter { it.messageId == stop.stopid }
                    Log.n(
                        tag,
                        "Ignoring depart due to arrive action is not completed: D$activeDispatchId S${stop.stopid} A${sendStopActionEventData.actionType}",
                        throwable = null,
                        STOPID to stop.stopid,
                        DISPATCHID to activeDispatchId,
                        ARRIVAL_REASON_DETAILS to arrivalReason,
                        DRIVERID to arrivalReason.driverID,
                        ARRIVAL_ACTION_STATUS to arrivalReason.arrivalActionStatus,
                        GEOFENCE_TYPE to arrivalReason.geofenceType,
                        SEQUENCED to arrivalReason.sequenced,
                        "Is arrived trigger in preference" to isArrivedTriggerInPreference,
                        KEY to DISPATCH_LIFECYCLE
                    )
                    dataStoreManager.setValue(IS_DYA_ALERT_ACTIVE, false)
                    return@let
                }
                handleStopActionCompletionAndFormNavigation(
                    activeDispatchId,
                    stop,
                    sendStopActionEventData,
                    caller,
                    pfmEventsInfo
                )
            }
        }
    }

    private suspend fun handleStopActionCompletionAndFormNavigation(
        activeDispatchId: String,
        stop: StopDetail,
        sendStopActionEventData: StopActionEventData,
        caller: String,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ) {
        stop.Actions.firstOrNull { it.actionType == sendStopActionEventData.actionType }
            ?.let { action ->
                Log.logGeoFenceEventFlow(
                    tag,
                    "GeofenceEventActions: D$activeDispatchId S${action.stopid} A${action.actionid}",
                    KEY to DISPATCH_LIFECYCLE,
                    DISPATCHID to activeDispatchId,
                    STOPID to stop.stopid,
                )
                val isArriveActionValid = isPreviousStopActionsComplete(action)
                if (isArriveActionValid.first && !action.responseSent) {

                    // To handle stop with both approach and arrive action
                    // In any scenario if the approach event not sent means we need to send the approach action along arrival or depart action
                    if (action.actionType != ActionTypes.APPROACHING.ordinal) {
                        /** approaching occurred = StopActionReasonTypes.NORMAL.name, no guf, cross in Event
                         * There is no way to force a manual approach - if the arrival occurs before the approaching event,
                         * the approaching event is assumed to occur with the arrival event
                         */
                        sendApproachAction(
                            curStopDetail = stop,
                            pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                                reasonType = StopActionReasonTypes.NORMAL.name
                            )
                        )
                    }

                    /*Action will be non null if stop has only approach or only depart or only
                           * approach and depart or all the three actions. In this case action response
                           * event will be sent to backend server and stop is marked as completed
                           * based on the hasDriverAcknowledgedArrivalOrManualArrival flag.*/
                    openFormActivityBasedOnFormAvailability(
                        action,
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetTruckNumber(),
                        stop,
                        sendStopActionEventData,
                        caller
                    )
                    /* Notice - As Part of MAPP-12745, we have launched coroutines inside of the Custom Coroutine Scope dispatchStopsCoroutineScope.
                    If we need to add firestore calls inside handleStopEvents, launch a coroutine from dispatchStopsCoroutineScope. */
                    handleStopEvents(
                        action,
                        sendStopActionEventData,
                        caller = caller,
                        pfmEventsInfo = pfmEventsInfo
                    )
                } else {
                    if (isArriveActionValid.first.not()) {
                        Log.w(
                            ARRIVAL_PROMPT,
                            "Ignoring the arrive trigger for the stop",
                            null,
                            "StopId" to stop.stopid,
                            "DispatchId" to activeDispatchId,
                            "IgnoreReason" to isArriveActionValid.second
                        )
                    } else {
                        Log.d(
                            ARRIVAL_PROMPT,
                            "Action response already sent for the stop S:${stop.stopid}, A:${action.actionid}, caller:$caller"
                        )
                    }
                }
            } ?: run {
            // TODO Check for possiblity of removing this else flow
            /*actionType is sent as ActionTypes.ARRIVED.ordinal when Yes button pressed
                    * in Did you arrive at? message in trip panel in AppLauncher or in dialog
                    * inside RouteManifest*/
            handleArriveActionCompletion(
                sendStopActionEventData,
                stop,
                caller = caller,
                pfmEventsInfo
            )
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun handleArriveActionCompletion(
        sendStopActionEventData: StopActionEventData,
        stop: StopDetail,
        caller: String,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ) {
        if (sendStopActionEventData.actionType == ActionTypes.ARRIVED.ordinal) {
            handleStopEvents(
                null, sendStopActionEventData, stop, caller = caller, pfmEventsInfo
            )
        }
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun doNotConsiderDepartEventTriggerIfArrivedActionNotCompleted(
        stop: StopDetail, sendStopActionEventData: StopActionEventData
    ): Boolean {
        stop.Actions.forEach { action ->
            if (sendStopActionEventData.actionType == ActionTypes.DEPARTED.ordinal && action.actionType == ActionTypes.ARRIVED.ordinal && action.responseSent.not()) {
                return true
            }
        }
        return false
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun openFormActivityBasedOnFormAvailability(
        action: Action,
        customerId: String,
        vehicleId: String,
        stop: StopDetail,
        sendStopActionEventData: StopActionEventData,
        caller: String = EMPTY_STRING
    ) {
        Log.logGeoFenceEventFlow(
            tag, "GeofenceEventOpenForm: D${stop.dispid} S${action.stopid} F${action.driverFormid}"
        )
        if (action.driverFormid > 0) {

            getArrivedActionFormDataToProceedToSave(
                action, customerId, vehicleId, stop, sendStopActionEventData
            )
        } else if (caller != ON_BACKGROUND_NEGATIVE_GUF_CALLER) {
            /**Opening the Stop list screen if the not arrival marked from the ON_BACKGROUND_NEGATIVE_GUF_CALLER
            reason - the ON_BACKGROUND_NEGATIVE_GUF_CALLER called if there is a trip panel break, so on that time, we don't want to open the screen**/
            //No form associated with the arrived action.mark the stop as completed.

            navigateToStopListActivityNoFormsFound(
                action, customerId, vehicleId, sendStopActionEventData
            )
        }
    }

    suspend fun restoreSelectedDispatch(){
        appModuleCommunicator.restoreSelectedDispatch()
    }

    /**
     * Arrived action only have form id.Form associated with the arrived action.redirect to form section
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun getArrivedActionFormDataToProceedToSave(
        action: Action,
        customerId: String,
        vehicleId: String,
        stop: StopDetail,
        sendStopActionEventData: StopActionEventData
    ) {
        if (action.actionType == ActionTypes.ARRIVED.ordinal && customerId.isNotEmpty() && vehicleId.isNotEmpty()) {
            val dispatchFormPath =
                DispatchFormPath(
                    stop.name,
                    stop.stopid,
                    action.actionid,
                    action.driverFormid,
                    action.driverFormClass,
                    dataStoreManager.getValue(CURRENT_DISPATCH_NAME_KEY, EMPTY_STRING)
                )
            val path =
                "$INBOX_FORM_RESPONSE_COLLECTION/${customerId}/${vehicleId}/${
                    appModuleCommunicator.getCurrentWorkFlowId("getArrivedActionFormDataToProceedToSave")
                }/${stop.stopid}/${action.actionid}"
            val isSyncDataToQueue =
                getFormSaveStatusFromFirestore(path, action, false)

            saveFormIdToDataStoreToAccountUncompletedForm(
                isSyncDataToQueue,
                dispatchFormPath
            )

            withContext(dispatchProvider.main()) {
                sendStopActionEventData.context.startDispatchFormActivity(
                    isComposeEnabled = isComposeFormFeatureFlagEnabled(appModuleCommunicator.doGetCid()),
                    path = path,
                    dispatchFormPath = dispatchFormPath,
                    isManualArrival = false,
                    isFormResponseSentToServer = true
                )
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun getFormSaveStatusFromFirestore(
        path: String,
        action: Action,
        isSyncDataToQueue: Boolean
    ): Boolean {
        var isFormAlreadySaved = isSyncDataToQueue
        try {
            val formResponse =
                formUseCase.getSavedFormResponse(path, action.actionid.toString(), true)
            isFormAlreadySaved = if (formResponse.formData.fieldData.isEmpty()) {
                Log.d(
                    tag,
                    "saved form field data not found.response:$formResponse",
                    throwable = null,
                    "path" to "$path${action.actionid}"
                )
                false
            } else {
                formResponse.isSyncDataToQueue
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "exception getting saved form field data",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
        }
        return isFormAlreadySaved
    }

    /**
     * Save unsaved form ids of stops to account uncompleted stop forms. So that we can later show uncompleted form data to the driver, if skipped.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun saveFormIdToDataStoreToAccountUncompletedForm(
        isFormCompletedByDriver: Boolean,
        dispatchFormPath: DispatchFormPath
    ) {
        if (isFormCompletedByDriver) return
        addDispatchFormPathToFormStack(dispatchFormPath, dataStoreManager)
    }

    internal suspend fun addDispatchFormPathToFormStack(
        dispatchFormPath: DispatchFormPath,
        dataStoreManager: DataStoreManager
    ) {
        dataStoreManager.getValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING)
            .let { formStack ->
                UncompletedFormsUseCase.addFormToPreference(
                    formStack,
                    dispatchFormPath
                ).let { formListJson ->
                    if (formListJson.isNotEmpty()) {
                        dataStoreManager.setValue(
                            UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                            formListJson
                        )
                        Log.i(tag, "Uncompleted forms list: $formListJson")
                    }
                }
            }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun navigateToStopListActivityNoFormsFound(
        action: Action,
        customerId: String,
        vehicleId: String,
        sendStopActionEventData: StopActionEventData,
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main
    ) {
        if (action.actionType == ActionTypes.ARRIVED.ordinal && customerId.isNotEmpty() && vehicleId.isNotEmpty()) {
            //There is a chance that geofence triggers of an active trip can occur while previewing a trip, so on responding to geofence trigger,
            // we need to display active dispatch details, so restore active dispatch data.
            restoreSelectedDispatch()
            withContext(mainDispatcher) {
                Intent(
                    sendStopActionEventData.context,
                    DispatchDetailActivity::class.java
                ).apply {
                    putExtra(DISPATCH_ID_TO_RENDER, action.dispid)
                    this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also {
                        sendStopActionEventData.context.startActivity(it)
                    }
                }
            }
        }
    }

    suspend fun getDriverFormsToFill(
        dataStoreManager: DataStoreManager
    ): ArrayList<DispatchFormPath> {
        var formList: ArrayList<DispatchFormPath> = ArrayList()
        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetTruckNumber()
                .isEmpty()
        ) return formList
        dataStoreManager.getValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING).let {
            if (it.isNotEmpty()) {
                formList = Gson().fromJson(
                    it,
                    object : TypeToken<ArrayList<DispatchFormPath>>() {}.type
                )
            }
        }
        return formList
    }

    suspend fun sendApproachAction(
        curStopDetail: StopDetail,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ) {
        curStopDetail.getArrivingAction()?.let { arrivingAction ->
            if (arrivingAction.responseSent.not()) {
                Log.d(STOP_ACTION_EVENTS, "Sending Approach response during Arrive")
                sendApproachActionResponse(
                    curStopDetail,
                    arrivingAction,
                    pfmEventsInfo
                )
                sendStopActionWorkflowEventsToThirdPartyApps(
                    arrivingAction,
                    pfmEventsInfo.reasonType,
                    timeStamp = System.currentTimeMillis(),
                    "sendApproachAction"
                )
            }
        }
    }

    suspend fun sendApproachActionResponse(
        stopDetail: StopDetail,
        action: Action,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ) {
        val stop = initStopDataAndUpdate(Stop(), stopDetail)
        sendActionResponse(
            stop,
            action,
            pfmEventsInfo
        )
    }

    internal fun getFirstUncompletedStopForSequentialTrip(stopList: CopyOnWriteArrayList<StopDetail>): StopDetail? {
        return stopList.firstOrNull { stop -> stop.completedTime.isEmpty() }
    }

    internal suspend fun shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(
        dataStoreManager: DataStoreManager,
        stopList: CopyOnWriteArrayList<StopDetail>
    ): Boolean {
        stopList.filter { stop ->
            stop.sequenced == 1
        }.also { sequentialStops ->
            if (stopList.size != sequentialStops.size) return false
            if (doesStoreHasCurrentStop(dataStoreManager).not()) return true
        }
        return false
    }

    internal suspend fun doesStoreHasCurrentStop(dataStoreManager: DataStoreManager): Boolean =
        dataStoreManager.containsKey(CURRENT_STOP_KEY)

    suspend fun setCurrentStopAndUpdateTripPanelForSequentialTrip(
        stopList: CopyOnWriteArrayList<StopDetail>
    ): Boolean {
        if (shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(
                dataStoreManager,
                stopList
            )
        ) {
            //Set current stop
            getFirstUncompletedStopForSequentialTrip(stopList)
                ?.let {
                    //sets current stop
                    putStopIntoPreferenceAsCurrentStop(it, dataStoreManager)
                    //updates trip panel upon setting the current stop
                    tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
                    return true
                } ?: Log.w(
                tag,
                "Unable to fetch the 1st uncompleted stop for sequential trip $stopList"
            )
        }
        return false
    }

    suspend fun getStopsFromFirestoreCacheFirst(caller: String, vehicleId: String, cid: String, dispatchId: String) =
        dispatchFirestoreRepo.getStopsFromFirestore(caller, vehicleId = vehicleId, cid = cid, dispatchId = dispatchId, isForceFetchedFromServer = false)

    suspend fun isPreviousStopActionsComplete(action: Action): Pair<Boolean, String> {
        return if (action.actionType == ActionTypes.ARRIVED.ordinal) {
            isValidStopForTripType(action.stopid)
        } else {
            Pair(true, EMPTY_STRING)
        }
    }

    suspend fun isValidStopForTripType(stopId: Int): Pair<Boolean, String> {
        val cid = appModuleCommunicator.doGetCid()
        val truckNumber = appModuleCommunicator.doGetTruckNumber()
        val currentWorkflowId = appModuleCommunicator.getCurrentWorkFlowId("isValidStopForTripType")
        val stopsFromFirestore = dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
            vehicleId = truckNumber,
            cid = cid,
            dispatchId = currentWorkflowId
        ).filter { stop -> stop.deleted == 0 }.getSortedStops()
        val triggeredStopDetail = stopsFromFirestore.filter { it.stopid == stopId }
        if (triggeredStopDetail.isNull() || triggeredStopDetail.isEmpty()) {
            Log.e(
                tag,
                "GeofenceEventDidNotMatchStopList: D$currentWorkflowId S$stopId StopsInDW ${stopsFromFirestore.map { it.stopid }} "
            )
            return Pair(false, "Stop is deleted and not found in the stop list")
        }
        val stopDetailOfTrigger = triggeredStopDetail.first()
        val isStopHasArrivedAction = dispatchFirestoreRepo.getActionsOfStop(
            currentWorkflowId,
            stopDetailOfTrigger.stopid.toString(),
            "isValidStopForTripType"
        ).firstOrNull { it.actionType == ActionTypes.ARRIVED.ordinal }.isNotNull()
        if(isStopHasArrivedAction.not()){
            Log.e(
                tag,
                "GeofenceArriveEvent Triggered for A Stop With No Arrive Action: D$currentWorkflowId S$stopId"
            )
            return Pair(false, "Arrive Triggered for A Stop With No Arrive Action")
        }
        val tripType = getTripType(stopsFromFirestore)
        val triggeredStopIndex = stopsFromFirestore.indexOf(stopDetailOfTrigger)
        Log.d(
            "GeofenceEventIncoming: $stopId",
            "${stopDetailOfTrigger.dispid} S${stopDetailOfTrigger.stopid} type $tripType index $triggeredStopIndex"
        )
        return when (tripType) {
            TripTypes.SEQUENTIAL.name -> {
                val previousStopIndex = triggeredStopIndex - 1
                if (previousStopIndex < 0) {
                    Pair(true, EMPTY_STRING)
                } else {
                    val previousSeqStop = stopsFromFirestore[previousStopIndex]
                    val previousSeqStopActions =
                        fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                            activeDispatchId = previousSeqStop.dispid,
                            stopId = previousSeqStop.stopid.toString(),
                            "isValidStopForTripType"
                        )
                    if (previousSeqStopActions.isNotEmpty()) {
                        val isPreviousSeqStopCompleted =
                            previousSeqStopActions.all { action -> action.responseSent }
                        Pair(
                            isPreviousSeqStopCompleted,
                            if (isPreviousSeqStopCompleted) EMPTY_STRING else "Sequential Trip, Previous Seq Stop:${previousSeqStop.stopid} Actions are not completed"
                        )
                    } else {
                        Log.w(
                            GEOFENCE_EVENT_PROCESS,
                            "Previous Sequence Stop I:$previousStopIndex Actions are empty for the dispatch D:${stopDetailOfTrigger.dispid} S${stopDetailOfTrigger.stopid}"
                        )
                        Pair(false, "Previous Seq Stop Actions are empty")
                    }
                }
            }

            TripTypes.MIXED.name -> {
                isValidStopForMixedTrip(stopDetailOfTrigger, stopsFromFirestore, triggeredStopIndex)
            }

            else -> Pair(true, EMPTY_STRING)
        }
    }

    private suspend fun isValidStopForMixedTrip(
        stopDetailOfTrigger: StopDetail,
        stopsFromFireStore: List<StopDetail>,
        triggeredStopIndex: Int
    ): Pair<Boolean, String> {
        return if (stopDetailOfTrigger.isSequencedStop().not()) {
            Pair(true, EMPTY_STRING)
        } else {
            stopsFromFireStore.subList(0, triggeredStopIndex)
                .filter { stop -> stop.isSequencedStop() }
                .let { previousSequentialStopsFromStopList ->
                    return if (previousSequentialStopsFromStopList.isEmpty()) {
                        Pair(true, EMPTY_STRING)
                    } else {
                        val previousSeqStop = previousSequentialStopsFromStopList.last()
                        val previousSeqStopActions =
                            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                                activeDispatchId = previousSeqStop.dispid,
                                stopId = previousSeqStop.stopid.toSafeString(),
                                "isValidStopForMixedTrip"
                            )
                        val isPreviousSeqStopCompleted = previousSeqStopActions.all { action -> action.responseSent }
                        Pair(
                            isPreviousSeqStopCompleted,
                            if (isPreviousSeqStopCompleted) EMPTY_STRING else "Mixed Trip Previous Seq Stop:${previousSeqStop.stopid} Actions are not completed"
                        )
                    }
                }
        }
    }

    fun getTripType(stops: List<StopDetail>): String {
        return when {
            stops.isSequentialTrip() -> TripTypes.SEQUENTIAL.name
            stops.isFreeFloatingTrip() -> TripTypes.FREE_FLOATING.name
            else -> TripTypes.MIXED.name
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun getDistanceInFeet(stop: Stop): Double = FormUtils.getMilesToFeet(
        FormUtils.getDistanceBetweenLatLongs(
            backboneUseCase.getCurrentLocation(),
            Pair(stop.latitude, stop.longitude)
        )
    )

    fun isComposeFormFeatureFlagEnabled(cid: String): Boolean {
        val flags = appModuleCommunicator.getFeatureFlags()
        return featureFlagGateKeeper.isFeatureTurnedOn(
            FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG,
            flags,
            cid
        )
    }

    internal suspend fun areStopsManipulatedForTheActiveTrip(): Boolean {
        return dataStoreManager.getValue(
            DataStoreManager.IS_ACTIVE_DISPATCH_STOP_MANIPULATED,
            false
        )
    }

    suspend fun dismissTripPanelMessage() = coroutineScope {
        tripPanelUseCase.dismissTripPanelMessage(tripPanelUseCase.lastSentTripPanelMessage.messageId)
    }

    internal suspend fun markActiveDispatchStopAsManipulated() {
        dataStoreManager.setValue(DataStoreManager.IS_ACTIVE_DISPATCH_STOP_MANIPULATED, true)
    }

    internal suspend fun unMarkActiveDispatchStopManipulation() {
        dataStoreManager.setValue(DataStoreManager.IS_ACTIVE_DISPATCH_STOP_MANIPULATED, false)
    }

    internal suspend fun unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
        caller: String,
        stop: StopDetail,
        actionType: Int,
        stopActionReasonTypes: StopActionReasonTypes
    ) {
        val actionCount = stop.Actions.size
        if (actionCount == 1) {
            val actionTypeFromStop = stop.Actions[0].actionType
            if (actionTypeFromStop == actionType) {
                unMarkActiveDispatchStopManipulationAndStartRouteCalculation(caller)
                Log.i(
                    caller,
                    "unmarked and sent route calc request. stop: ${
                        Triple(
                            "S" + stop.stopid,
                            "D" + stop.dispid,
                            stop.Actions.map { Pair("AT" + it.actionType, "RS" + it.responseSent) })
                    }"
                )
            } else {
                Log.i(
                    caller,
                    "action type mismatch. ActionType from caller: $actionType ActionType from stop: $actionTypeFromStop stop: ${
                        Triple(
                            "S" + stop.stopid,
                            "D" + stop.dispid,
                            stop.Actions.map { Pair("AT" + it.actionType, "RS" + it.responseSent) })
                    }"
                )
                return
            }
        } else if (actionCount > 1) {
            when (stopActionReasonTypes.name) {
                StopActionReasonTypes.MANUAL.name -> {
                    handleUnMarkActiveDispatchStopManipulationForManualStopCompletion(
                        caller = caller,
                        stop = stop,
                        actionType = actionType
                    )
                    return
                }

                StopActionReasonTypes.AUTO.name -> {
                    handleUnMarkActiveDispatchStopManipulationForAutoStopCompletion(
                        caller = caller,
                        stop = stop,
                        actionType = actionType
                    )
                    return
                }

                else -> {
                    Log.w(caller, "invalid stopActionReasonType: ${stopActionReasonTypes.name}")
                }
            }
        } else {
            Log.w(
                caller,
                "invalid amount of actions in stop: ${
                    stop.Actions.map {
                        Triple(
                            "AT" + it.actionType,
                            "RS" + it.responseSent,
                            "D" + it.dispid
                        )
                    }
                }" +
                        "ActionType from the caller: $actionType"
            )
        }
    }

    private suspend fun handleUnMarkActiveDispatchStopManipulationForManualStopCompletion(
        caller: String,
        stop: StopDetail,
        actionType: Int
    ) {
        if (stop.Actions.last().responseSent) {
            unMarkActiveDispatchStopManipulationAndStartRouteCalculation(caller)
            Log.i(
                caller,
                "unmarked and sent route calc request. stop: ${
                    Triple(
                        "S" + stop.stopid,
                        "D" + stop.dispid,
                        stop.Actions.map { Pair("AT" + it.actionType, "RS" + it.responseSent) })
                }"
            )
        } else {
            Log.i(
                caller,
                "not the last action. ActionType: $actionType. stop: ${
                    Triple(
                        "S" + stop.stopid,
                        "D" + stop.dispid,
                        stop.Actions.map { Pair("AT" + it.actionType, "RS" + it.responseSent) })
                }"
            )
        }
    }

    private suspend fun handleUnMarkActiveDispatchStopManipulationForAutoStopCompletion(
        caller: String,
        stop: StopDetail,
        actionType: Int
    ) {
        //Arrive action will be written to firestore only in onPositiveButtonClicked and onMessageDismissed of RouteManifestForegroundService.
        // That's the reason for the last action drop
        if (stop.Actions.dropLast(1)
                .any { it.responseSent.not() }
        ) {
            Log.i(
                caller, "Actions before this action should be completed first." +
                        " StopId: ${stop.stopid}" +
                        " Actions from stop: ${
                            stop.Actions.map {
                                Triple(
                                    "AT" + it.actionType,
                                    "RS" + it.responseSent,
                                    "D" + it.dispid
                                )
                            }
                        }" +
                        "ActionType from the caller: $actionType"
            )
            return
        }
        if (stop.Actions.find { it.actionType == actionType } == null) {
            Log.i(
                caller,
                "action type mismatch. ActionType from caller: $actionType stop: ${
                    Triple(
                        "S" + stop.stopid,
                        "D" + stop.dispid,
                        stop.Actions.map { Pair("AT" + it.actionType, "RS" + it.responseSent) })
                }"
            )
            return
        }
        unMarkActiveDispatchStopManipulationAndStartRouteCalculation(caller)
        Log.i(
            caller,
            "unmarked and sent route calc request. stop: ${
                Triple(
                    "S" + stop.stopid,
                    "D" + stop.dispid,
                    stop.Actions.map { Pair("AT" + it.actionType, "RS" + it.responseSent) })
            }"
        )
    }

    internal suspend fun unMarkActiveDispatchStopManipulationAndStartRouteCalculation(caller: String) {
        unMarkActiveDispatchStopManipulation()
        routeETACalculationUseCase.startRouteCalculation(
            getAllActiveStopsAndActions(caller),
            caller
        )
    }

    internal suspend fun sendTripStartEventToPFM(
        customerId: String,
        truckNumber: String,
        dispatchId: String,
        pfmEventsInfo: PFMEventsInfo.TripEvents
    ) {
        val customerIdObcIdVehicleId = Triple(customerId.toInt(), appModuleCommunicator.doGetObcId(), truckNumber)
        val isConfigurableOdometerEnabled =  FeatureFlagGateKeeper().isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SHOULD_USE_CONFIGURABLE_ODOMETER, appModuleCommunicator.getFeatureFlags(), customerId)
        tripMobileOriginatedEventsRepo.saveTripActionResponse(
            COLLECTION_NAME_TRIP_START, "$customerId/$COLLECTION_NAME_VEHICLES/$truckNumber/$COLLECTION_NAME_DISPATCH_EVENT/$dispatchId",
            JsonDataConstructionUtils.getTripEventJson(
                dispatchId = dispatchId,
                pfmEventsInfo = pfmEventsInfo,
                fuelLevel = backboneUseCase.getFuelLevel(),
                odometerReading = backboneUseCase.getOdometerReading(isConfigurableOdometerEnabled),
                customerIdObcIdVehicleId = customerIdObcIdVehicleId,
                currentLocationLatLong = backboneUseCase.getCurrentLocation()
            )
        )
    }

    internal suspend fun isTripStarted(dispatchId: String, customerId: String, truckNumber: String) =
        tripMobileOriginatedEventsRepo.isTripActionResponseSaved(
            COLLECTION_NAME_TRIP_START, "$customerId/$COLLECTION_NAME_VEHICLES/$truckNumber/$COLLECTION_NAME_DISPATCH_EVENT/$dispatchId")

    suspend fun sendStopActionWorkflowEventsToThirdPartyApps(
        action: Action,
        reasonCode: String,
        timeStamp: Long,
        caller: String
    ) {
        var stopName = ""
        fetchDispatchStopsAndActionsUseCase.getStopData(action.stopid)?.let { stop ->
            stopName = stop.name
        }
        Log.d(
            DRIVER_WORKFLOW_EVENTS_COMMUNICATION,
            "send stopAction workflowEvent, dispatchId: ${action.dispid}, stopId: ${action.stopid}, action: ${action.actionType}, caller: $caller"
        )
        sendWorkflowEventsToAppUseCase.sendWorkflowEvent(
            WorkflowEventDataParameters(
                dispatchId = action.dispid,
                dispatchName = EMPTY_STRING,
                stopId = action.stopid.toString(),
                stopName = stopName,
                eventName = (action.actionType).getWorkflowEventName(),
                reasonCode = reasonCode, // StopActionReasonTypes for Approach, Arrive and Depart events
                timeStamp = timeStamp
            ),
            caller = caller
        )
    }

    suspend fun getSpecificStopAndItsActionsFromFirestoreCacheFirst(
        caller: String,
        stopId: Int
    ): StopDetail? {
        val truckNumber = dispatchFirestoreRepo.getAppModuleCommunicator().doGetTruckNumber()
        val customerId = dispatchFirestoreRepo.getAppModuleCommunicator().doGetCid()
        val dispatchId =
            dispatchFirestoreRepo.getAppModuleCommunicator().getCurrentWorkFlowId(caller)
        dispatchFirestoreRepo.getStopsFromFirestore(
            caller,
            vehicleId = truckNumber,
            cid = customerId,
            dispatchId = dispatchId,
            isForceFetchedFromServer = false
        ).let { stops ->
            return stops.find { it.stopid == stopId } ?: run {
                Log.e(
                    caller,
                    "stop not found in firestore cache",
                    throwable = null,
                    "activeDispatchId" to dispatchId,
                    "stopId" to stopId,
                    "stops" to stops.size
                )
                null
            }
        }
    }

    suspend fun getAllDispatchBlobData(cid: String, vehicleId: String): ArrayList<DispatchBlob> =
        dispatchFirestoreRepo.getAllDispatchBlobDataForVehicle(cid, vehicleId)

    suspend fun deleteDispatchBlobDocument(
        cid: String,
        vehicleNumber: String,
        dispatchBlobId: String
    ) {
        dispatchFirestoreRepo.deleteDispatchBlobByBlobId(cid, vehicleNumber, dispatchBlobId)
    }

    suspend fun deleteAllDispatchBlobDataForVehicle(
        cid: String,
        vehicleNumber: String,
        dispatchBlobIdList: List<String>
    ) {
        dispatchFirestoreRepo.deleteAllDispatchBlobDataForVehicle(cid, vehicleNumber, dispatchBlobIdList)
    }

    internal fun getFeatureFlagGateKeeper() = featureFlagGateKeeper

    suspend fun updateStopDetail(stop: StopDetail, valueMap : HashMap<String, Any>){
        if(appModuleCommunicator.doGetCid() == EMPTY_STRING || appModuleCommunicator.doGetTruckNumber() == EMPTY_STRING || appModuleCommunicator.getCurrentWorkFlowId("updateStopDetail") == EMPTY_STRING) return
        tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(
            path = "$DISPATCH_COLLECTION/${
                appModuleCommunicator.doGetCid()
            }/${
                appModuleCommunicator.doGetTruckNumber()
            }/${
                appModuleCommunicator.getCurrentWorkFlowId("updateStopDetail")
            }/$STOPS/${stop.stopid}",
            valueMap = valueMap
        )
    }


    suspend fun setActiveDispatchFlagInFirestore(
        cid: String,
        truckNumber: String,
        activeDispatchId: String
    ) {
        dispatchFirestoreRepo.setActiveDispatchFlagInFirestore(cid, truckNumber, activeDispatchId)
    }

    internal suspend fun updateSequencedKeyInDataStore(stops: CopyOnWriteArrayList<StopDetail>, stopDetail: StopDetail?){
        //Update ARE_STOPS_SEQUENCED_KEY value based on the sequenced value of stops. Previously we have handled only if condition and it caused issues while previewing Free float trip.
        if (stops.any { it.sequenced.isEqualTo(ZERO) } || stopDetail?.sequenced.isEqualTo(ZERO)){
            dataStoreManager.setValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, FALSE
            )
        }else{
            dataStoreManager.setValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
            )
        }
    }

    fun getLastSequentialCompletedStop(stops: List<StopDetail>): StopDetail? {
        var lastCompletedStop: StopDetail? = null
        stops.takeWhile { stop ->
            when (stop.sequenced) {
                1 -> {
                    if (stop.Actions.all { it.responseSent }) {
                        lastCompletedStop = stop
                        true
                    } else {
                        false
                    }
                }
                0 -> true
                else -> false
            }
        }
        return lastCompletedStop
    }

    suspend fun updateStopActions(stop: StopDetail, actionType:Int){
        if(appModuleCommunicator.doGetCid() == EMPTY_STRING || appModuleCommunicator.doGetTruckNumber() == EMPTY_STRING || appModuleCommunicator.getCurrentWorkFlowId("updateStopActions") == EMPTY_STRING) {
            Log.w(
                tag,
                "Update Trigger Received for stop's action ${actionType} : cid, truckNumber or dispatchId is empty",
                null
            )
            return
        }
        dispatchFirestoreRepo.getActionsOfStop(stop.dispid, stop.stopid.toString(), "updateStopActions").firstOrNull { it.actionType == actionType }?.let { action ->
            action.triggerReceived = true
            action.triggerReceivedTime = getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time)
            tripMobileOriginatedEventsRepo.updateActionPayload(
                path = "$DISPATCH_COLLECTION/${
                    appModuleCommunicator.doGetCid()
                }/${
                    appModuleCommunicator.doGetTruckNumber()
                }/${
                    appModuleCommunicator.getCurrentWorkFlowId("updateStopActions")
                }/$STOPS/${stop.stopid}/$ACTIONS/${action.actionid}",
                actionData = action
            )
        }
    }
}