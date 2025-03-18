package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import androidx.work.WorkManager
import com.google.gson.GsonBuilder
import com.trimble.ttm.commons.logger.*
import com.trimble.ttm.commons.model.WorkFlowEvents
import com.trimble.ttm.commons.model.WorkflowEventDataParameters
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.STOPS_SERVICE_REFERENCE_KEY
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.*
import com.trimble.ttm.formlibrary.eventbus.EventBus
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.toSafeInt
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.model.*
import com.trimble.ttm.routemanifest.utils.ARRIVED
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.COPILOT_ROUTE_CLEARING_TIME_SECONDS
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.getDistanceBetweenLatLongs
import com.trimble.ttm.routemanifest.utils.Utils.toFeet
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

enum class TripStartCaller {
    AUTO_TRIP_START_BACKGROUND, // Trip Started from AutoTripStartWorker
    DISPATCH_DETAIL_SCREEN, // Trip Started by Tapping "Yes" on "Your Trip has to be started. Wold you like to Start Trip" Dialog in Trip List
    START_TRIP_BUTTON_PRESS_FROM_DISPATCH_DETAIL_SCREEN // Trip Started by tapping on "Start Trip" button inside DispatchDetailActivity
}
class TripStartUseCase(private val coroutineDispatcherProvider: DispatcherProvider,
                       private val dataStoreManager: DataStoreManager,
                       private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
                       private val dispatchBaseUseCase: DispatchBaseUseCase,
                       private val dispatchStopsUseCase: DispatchStopsUseCase,
                       private val lateNotificationUseCase: LateNotificationUseCase,
                       private val backboneUseCase: BackboneUseCase,
                       private val tripPanelUseCase: TripPanelUseCase,
                       private val sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase,
                       private val sendDispatchDataUseCase: SendDispatchDataUseCase,
                       private val routeETACalculationUseCase: RouteETACalculationUseCase,
                       private val serviceManager : ServiceManager,
                       private val appModuleCommunicator: AppModuleCommunicator,
                       private val context  : Context) {
    suspend fun updateDispatchInfoInDataStore(dispatchId: String, dispatchName: String, caller: String){
        Log.i(
            caller, "updateDispatchInfoInDataStore: " +
                    "dispatchId $dispatchId dispatchName : $dispatchName caller : $caller"
        )
        if(dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING).isEmpty()) {
            dataStoreManager.setValue(DataStoreManager.SELECTED_DISPATCH_KEY, dispatchId)
        }
        dataStoreManager.setValue(DataStoreManager.DISPATCH_NAME_KEY, dispatchName)
        dataStoreManager.setValue(DataStoreManager.CURRENT_DISPATCH_NAME_KEY, dispatchName)
    }

    suspend fun getDispatchStopsAndActionsAndStartTrip(cid: String, vehicleNumber: String, dispatchId: String,tripStartEventStartReason:String, caller: String, tripStartCaller:TripStartCaller) {

        val tripEventReasonTypeAndGuf = dispatchBaseUseCase.getTripEventReasonTypeAndGuf(tripStartEventStartReason)
        val pfmEventsInfo = PFMEventsInfo.TripEvents(
            reasonType = tripEventReasonTypeAndGuf.first,
            negativeGuf = tripEventReasonTypeAndGuf.second
        )

        val stopList = fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
            cid = cid,
            vehicleNumber = vehicleNumber,
            dispatchId = dispatchId,
            caller = caller,
            isForceFetchFromServer = false
        )
        if (stopList.isEmpty()) {
            Log.w(
                caller,
                "The stop detail list is empty during trip start. The late notification worker is not scheduled"
            )
            return
        }
        dispatchStopsUseCase.updateSequencedKeyInDataStore(CopyOnWriteArrayList(stopList),null)
        scheduleLateNotificationWorker(dispatchId, caller)
        startTrip(tripStartInfo = TripStartInfo(stopDetailList = stopList, timeInMillis = DateUtil.getCalendar().timeInMillis, pfmEventsInfo = pfmEventsInfo, dispatchId = dispatchId, cid = cid, vehicleNumber = vehicleNumber, tripStartCaller = tripStartCaller, caller = caller))
    }

    fun scheduleLateNotificationWorker(dispatchId: String, caller: String){
        lateNotificationUseCase.scheduleLateNotificationCheckWorker(
            scope = appModuleCommunicator.getAppModuleApplicationScope(),
            workManager = WorkManager.getInstance(ApplicationContextProvider.getApplicationContext()),
            dispatchId = dispatchId,
            caller = caller,
            isFromTripStart = true
        )
    }

    suspend fun startTrip(tripStartInfo: TripStartInfo) {
        appModuleCommunicator.getAppModuleApplicationScope()
            .launch(CoroutineName(TRIP_START_CALL) + coroutineDispatcherProvider.io()) {
                Log.i(tripStartInfo.caller, "Starting trip initiated")
                val activeDispatchId = dispatchBaseUseCase.storeActiveDispatchIdToDataStore(dataStoreManager, tripStartInfo.dispatchId)
                if (activeDispatchId.isEmpty()) {
                    Log.e(
                        tripStartInfo.caller,
                        "startTrip activeDispatchKey is empty from datastore D$activeDispatchId"
                    )
                    return@launch
                }
                val stopDetailList = tripStartInfo.stopDetailList ?: WorkflowEventBus.stopListEvents.firstOrNull() ?: emptyList()
                checkAndTriggerArrivalForStop( activeDispatchId, getFirstSequentialStop(stopDetailList))
                // Background trip completion in background removes datastoreManager keys - dataStoreManager.removeAllKeys()
                // if we don't add contains check then the default value of bool key will be false and that will clear the route in copilot.
                // So, a contains check is added here and it's marked as false in trip start to satisfy this condition
                dispatchStopsUseCase.unMarkActiveDispatchStopManipulation()
                dispatchBaseUseCase.setStartTime(
                    timeInMillis = tripStartInfo.timeInMillis,
                    dataStoreManager = dataStoreManager
                )
                EventBus.resetRouteCalculationRetry()
                //Cancel the worker if it is exist in worker after the manual start
                WorkManager.getInstance(ApplicationContextProvider.getApplicationContext()).cancelAllWorkByTag("${tripStartInfo.cid}${tripStartInfo.vehicleNumber}${activeDispatchId}")

                Log.n(tripStartInfo.caller, "Trip started: dispatch $activeDispatchId", null, KEY to DISPATCH_LIFECYCLE, DISPATCHID to activeDispatchId)

                sendDispatchStartDataToBackbone(
                    dispatchId = activeDispatchId,
                    backboneUseCase = backboneUseCase,
                    coroutineScope = this
                )
                sendTripStartEventToThirdPartyApps(activeDispatchId, System.currentTimeMillis())

                dispatchStopsUseCase.sendTripStartEventToPFM(
                    customerId = tripStartInfo.cid,
                    truckNumber = tripStartInfo.vehicleNumber,
                    dispatchId = activeDispatchId,
                    pfmEventsInfo = tripStartInfo.pfmEventsInfo
                )
                dispatchStopsUseCase.setActiveDispatchFlagInFirestore(
                    tripStartInfo.cid,
                    tripStartInfo.vehicleNumber,
                    activeDispatchId
                )
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()

                dispatchStopsUseCase.getAllDispatchBlobData(
                    tripStartInfo.cid,
                    tripStartInfo.vehicleNumber
                ).let { dispatchBlobList ->
                    val dispatchBlobIdList = ArrayList<String>()
                    dispatchBlobList.forEach { dispatchBlob ->
                        dispatchBlobIdList.add(dispatchBlob.id)
                        sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(
                            dispatchBlob = dispatchBlob,
                            eventName = WorkFlowEvents.DISPATCH_BLOB_EVENT,
                            timeStamp = System.currentTimeMillis(),
                            caller = TRIP_START_CALL
                        )
                    }
                    dispatchStopsUseCase.deleteAllDispatchBlobDataForVehicle(
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetTruckNumber(),
                        dispatchBlobIdList
                    )
                }
                when (tripStartInfo.tripStartCaller) {
                    TripStartCaller.AUTO_TRIP_START_BACKGROUND -> {
                        CopyOnWriteArrayList(stopDetailList).let {
                            putStopsIntoPreference(tripStartInfo, it)
                            dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
                                it
                            )
                            startRouteCalculation(tripStartInfo.caller, it) // Just Setting Route, Geofence will be set once RouteCalculationResultFromLauncher is received
                        }
                    }
                    TripStartCaller.DISPATCH_DETAIL_SCREEN -> {
                        // Ignore (Route Calculation and Geofence Data are set already in DispatchDetailViewModel, So Ignore Setting from Here)
                    }
                    TripStartCaller.START_TRIP_BUTTON_PRESS_FROM_DISPATCH_DETAIL_SCREEN -> {
                            startRouteCalculation(tripStartInfo.caller, stopDetailList) // Just Setting Route, Geofence will be set once RouteCalculationResultFromLauncher is received
                    }
                }
            }
    }

    private fun getFirstSequentialStop(stops: List<StopDetail>): StopDetail {
        return stops.firstOrNull { it.sequenced == 1 } ?: stops.first()
    }

    suspend fun putStopsIntoPreference(
        tripStartInfo: TripStartInfo,
        it: CopyOnWriteArrayList<StopDetail>
    ) {
        if (Utils.isIncomingDispatchSameAsActiveDispatch(
                incomingDispatchId = tripStartInfo.dispatchId,
                activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("tripAutoStartPref")
            )
        ) {
            val uncompletedStopList = it.filter { st -> st.completedTime == EMPTY_STRING }.getSortedStops()
            dataStoreManager.setValue(
                STOPS_SERVICE_REFERENCE_KEY,
                GsonBuilder().setPrettyPrinting().create()
                    .toJson(uncompletedStopList)
            )
        }
    }

    fun sendDispatchStartDataToBackbone(
        dispatchId: String,
        backboneUseCase: BackboneUseCase,
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch(
            CoroutineName(TRIP_START_CALL) + coroutineDispatcherProvider.io()
        ) {
            backboneUseCase.setWorkflowStartAction(dispatchId.toSafeInt())
        }
    }

    fun sendTripStartEventToThirdPartyApps(activeDispatchId: String, timeStamp: Long) {
        sendWorkflowEventsToAppUseCase.sendWorkflowEvent(
            WorkflowEventDataParameters(
                dispatchId = activeDispatchId,
                dispatchName = EMPTY_STRING,
                stopId = EMPTY_STRING,
                stopName = EMPTY_STRING,
                eventName = WorkFlowEvents.TRIP_START_EVENT,
                reasonCode = EMPTY_STRING,
                message = EMPTY_STRING,
                timeStamp = timeStamp
            ),
            SEND_TRIP_START_EVENT_CALLER
        )
    }

    suspend fun startRouteCalculation(caller: String, stopDetailList: List<StopDetail>) {
        Log.i(caller, "Starting route calculation - stopDetailList size:${stopDetailList.size}")
        if (stopDetailList.isEmpty() || stopDetailList.none { it.completedTime.isEmpty() }) {
            //init datastore values
            routeETACalculationUseCase.resetTripInfoWidget(":Method:startRouteCalculation")
            return
        }
        sendDispatchDataUseCase.sendDispatchCompleteEvent() // Additional safe call, incase sendDispatchCompleteEvent from runOnTripEnd() is not called, this clears the stop cache, map route and geofences in AL
        delay(COPILOT_ROUTE_CLEARING_TIME_SECONDS)
        routeETACalculationUseCase.startRouteCalculation(stopDetailList, caller) // Free Float and Mixed trips gets the geofence set from the RouteCalculationResult. So we are sending a generic route calculation request, for any type of trip
        Log.d(caller,"stopDetailList CompletedTime: ${stopDetailList.map { Pair(it.stopid, it.completedTime) }}")
        return
    }

    suspend fun checkAndTriggerArrivalForStop(
        activeDispatchId: String,
        stopData : StopDetail
    ) {
        if (stopData.stopid == -1) {
            Log.e(TRIP_START_CALL, "stopDetail is empty while check for arrive trigger")
            return
        }

        val currentLatLong = backboneUseCase.getCurrentLocation()
        val stopLatLong = Pair(stopData.latitude, stopData.longitude)
        val distance = getDistanceBetweenLatLongs(currentLatLong, stopLatLong).toFeet()
        val radius = stopData.getArrivedAction()?.radius ?: 0
        val siteCoordinate = stopData.siteCoordinates ?: emptyList()
        if (!siteCoordinate.all { it.latitude > 0.0 && it.longitude > 0.0 } && radius != 0 && distance < radius) {
            Log.i(
                TRIP_START_CALL,
                "Vehicle is inside the stop Id ${stopData.stopid} arrival geofence D:$activeDispatchId"
            )
            serviceManager.processGeoFenceEvents(
                activeDispatchId = activeDispatchId,
                geofenceSetName = ARRIVED + stopData.stopid,
                context = context,
                isTriggerFromMap = false
            )
        } else {
            Log.i(
                TRIP_START_CALL,
                "Vehicle is not inside the geofence of the first stop, Distance between current location and first stop is $distance feet, radius is $radius feet"
            )
        }
    }
}