package com.trimble.ttm.routemanifest.usecases

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.trimble.launchercommunicationlib.commons.EVENT_TYPE_KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_WIDGET
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.formlibrary.eventbus.EventBus
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.formlibrary.utils.toSafeDouble
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.di.UseCaseScope
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.routemanifest.model.Address
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.RouteCalculationResult
import com.trimble.ttm.routemanifest.model.STATE
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.StopInfo
import com.trimble.ttm.routemanifest.model.getSortedStops
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.utils.ADDRESS_NOT_AVAILABLE
import com.trimble.ttm.routemanifest.utils.COPILOT_ROUTE_CALC_RETRY_DELAY
import com.trimble.ttm.routemanifest.utils.CPIK_EVENT_TYPE_KEY
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_MAX_RETRY_COUNT
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_ERROR
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_STATE
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_STOPDETAIL_LIST
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_TOTAL_DISTANCE
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_TOTAL_HOUR
import com.trimble.ttm.routemanifest.utils.STOPDETAIL_LIST
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.getEventTypeKeyForRouteCalculation
import com.trimble.ttm.routemanifest.utils.Utils.getKeyForRouteCalculationResponseToClient
import com.trimble.ttm.routemanifest.utils.ext.areAllStopsComplete
import com.trimble.ttm.routemanifest.utils.ext.getCompletedStopsBasedOnCompletedTime
import com.trimble.ttm.routemanifest.utils.ext.getNonDeletedAndUncompletedStopsBasedOnActions
import com.trimble.ttm.routemanifest.utils.ext.getStopDetailList
import com.trimble.ttm.routemanifest.utils.ext.getStopInfoList
import com.trimble.ttm.routemanifest.utils.ext.getStopListBeforeGivenStop
import com.trimble.ttm.routemanifest.utils.ext.getStopListFromGivenStop
import com.trimble.ttm.routemanifest.utils.ext.getUncompletedStops
import com.trimble.ttm.routemanifest.utils.ext.hasFreeFloatingStops
import com.trimble.ttm.routemanifest.utils.ext.isEqualTo
import com.trimble.ttm.routemanifest.utils.ext.isNotEqualTo
import com.trimble.ttm.routemanifest.utils.ext.isSequentialTrip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow

class RouteETACalculationUseCase(
    @UseCaseScope private val scope: CoroutineScope,
    private val tripPanelRepo: TripPanelEventRepo,
    private val sendDispatchDataUseCase: SendDispatchDataUseCase,
    private val localDataSourceRepo: LocalDataSourceRepo,
    private val dispatchFirestoreRepo: DispatchFirestoreRepo,
    private val tripInfoWidgetUseCase: TripInfoWidgetUseCase,
    private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
    private val dataStoreManager: DataStoreManager,
    private val routeResultBundleFlow: StateFlow<Bundle?>,
    private val sendWorkflowEventsToAppUseCase : SendWorkflowEventsToAppUseCase
) {
    private val tag = "RouteETACalculationUC"
    private var routeCalculationResultCollectJob: Job ?= null
    private val _routeCalculationResult = MutableSharedFlow<RouteCalculationResult?>()
    val routeCalculationResult: SharedFlow<RouteCalculationResult?> get() = _routeCalculationResult
    private var routeCalculationRetryCount = ROUTE_CALCULATION_MAX_RETRY_COUNT
    private val _retryRouteCalculation = MutableSharedFlow<Boolean>()
    val retryRouteCalculation: SharedFlow<Boolean> get() = _retryRouteCalculation

    init {
        observeRouteCalculationResultBundleFlow()
        observeTripEndEventsToCancelRouteCalculation()
    }

    // Cancel the retry route calculation when the trip is ended and reset the retry count to max value for next trip
    private fun observeTripEndEventsToCancelRouteCalculation() {
        scope.launch {
            EventBus.resetRouteCalcRetryEvents.collect {
                Log.d(tag, "cancelling route calculation retry")
                _retryRouteCalculation.emit(false)
                routeCalculationRetryCount = ROUTE_CALCULATION_MAX_RETRY_COUNT
            }
        }
    }

    private fun observeRouteCalculationResultBundleFlow() {
        routeCalculationResultCollectJob?.cancel()
        routeCalculationResultCollectJob = scope.launch() {
            routeResultBundleFlow.collect { bundle ->
                bundle?.getString(CPIK_EVENT_TYPE_KEY)?.let {
                    calculateRouteETAFromLauncherEvent(
                        eventType = it,
                        bundle
                    )
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getRouteCalculationResult(resultMap: LinkedHashMap<String, ArrayList<String>>): RouteCalculationResult {
        val routeCalculationResult = RouteCalculationResult()
        resultMap[ROUTE_CALCULATION_RESULT_STOPDETAIL_LIST]?.let { stopJsonList ->
            val stopInfoList : ArrayList<StopInfo> = ArrayList()
            stopJsonList.forEach {
                Utils.fromJsonString<StopInfo>(it)?.let { stopInfo ->
                    stopInfoList.add(stopInfo)
                }
            }
            routeCalculationResult.stopDetailList = stopInfoList.getStopDetailList()
        }
        resultMap[ROUTE_CALCULATION_RESULT_STATE]?.let {
            if (it.size > 0 && it[0].toInt() == STATE.SUCCESS.ordinal) {
                routeCalculationResult.state = STATE.SUCCESS
            } else {
                routeCalculationResult.state = STATE.ERROR
            }
        }
        resultMap[ROUTE_CALCULATION_RESULT_ERROR]?.let {
            if (it.size > 0)
                routeCalculationResult.error = it[0]
        }
        resultMap[ROUTE_CALCULATION_RESULT_TOTAL_HOUR]?.let {
            if (it.size > 0) {
                routeCalculationResult.totalHours = it[0].toSafeDouble()
            }
        }
        resultMap[ROUTE_CALCULATION_RESULT_TOTAL_DISTANCE]?.let {
            if (it.size > 0) {
                routeCalculationResult.totalDistance = it[0].toSafeDouble()
            }
        }
        Log.d(tag,"Inside getRouteCalculationResult RouteETACalculationUseCase route calculation status: ${routeCalculationResult.state} ")
        return routeCalculationResult
    }
    /**
     * Starts the route calculation process for the given list of stops.
     *
     * This method processes the provided stop list, sorts the uncompleted stops,
     * and prepares a list of stop details to be sent for route calculation. If all stops
     * are completed, it updates the trip widget and maps instead of sending a route
     * calculation request.
     *
     * @param stopList The list of stops to be processed.
     * @param caller The identifier of the caller initiating the route calculation.
     * @param dispatchName The name of the dispatch. If provided, it will replace the default empty string,
     * it is be coming as of now from trip start only. Reason- we need to send the Dispatch name when trip starts
     *
     * @throws Exception if an error occurs while processing the stops or sending the route calculation request.
     */

    // This method initiates the route calculation and called only for Active and No_Trip_Active dispatch state
    suspend fun startRouteCalculation(stopList: List<StopDetail>, caller: String) {
        try {
            val stopDetailList: ArrayList<String> = ArrayList()
            val dispatch: Dispatch = dispatchFirestoreRepo.getDispatchPayload(caller,localDataSourceRepo.getAppModuleCommunicator().doGetCid(),localDataSourceRepo.getAppModuleCommunicator().doGetTruckNumber(),localDataSourceRepo.getSelectedDispatchId(caller),false)

            val isPolygonalOptOut = sendWorkflowEventsToAppUseCase.getPolygonalOptOutDataFromManagedConfig(caller)
            Log.d(tag,"startRouteCalculation caller $caller stops ${stopList.getUncompletedStops().map { it.stopid }}")
            // send the dispatch name to getStopInfoList ony if that is active and not preview
            stopList.getUncompletedStops().getSortedStops().getStopInfoList(dispatchName = if (localDataSourceRepo.getActiveDispatchId(caller) == dispatch.dispid) dispatch.name else EMPTY_STRING, isPolygonalOptOut = isPolygonalOptOut).let {
                for (stopInfo in it) {
                    // Address is sent with empty(" ") value to make the address invisible in CPIK Hamburger(Trip -> Plan) -> MAPP-7533
                    stopInfo.Address = Address(name = stopInfo.name, address = " ")
                    Utils.toJsonString(stopInfo)
                        ?.let { stopInfoString -> stopDetailList.add(stopInfoString) }
                }
            }

            //No need to send Route calculation request if all stops are completed.
            if(stopDetailList.size > 0) {
                Bundle().apply {
                    putStringArrayList(STOPDETAIL_LIST, stopDetailList)
                    putString(EVENT_TYPE_KEY, getEventTypeKeyForRouteCalculation())
                }.also {
                    tripPanelRepo.calculateRoute(it)
                }
            } else checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps()
        } catch (e: Exception) {
            Log.e(tag, "exception in messaging to service for route calculation",throwable=null,"stack" to e.stackTraceToString())
        }
    }

    private fun clearRoute() = sendDispatchDataUseCase.sendDispatchEventForClearRoute()

    suspend fun routeCalculationResultFromLauncher(
        data: Bundle,
        stoplist: List<StopDetail> = listOf(),
        dataStoreManager: DataStoreManager,
        activeState: DispatchActiveState
    ): RouteCalculationResult {
        val result: String = data.getString(getKeyForRouteCalculationResponseToClient(), "")
        val resultMap: LinkedHashMap<String, ArrayList<String>>? = Utils.fromJsonString(result)
        resultMap?.let {
            val routeCalculationResult = getRouteCalculationResult(resultMap)
            /* If there is an active trip and the driver previewing the inactive trip means, we need to get the active stops info
            in the else part, normally we will get the stop list info when we are inside the stop list screen, if it is empty then we are fetching the selected dispatch stops */
            val stopList = if (localDataSourceRepo.hasActiveDispatch()) {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("routeCalculationResultFromLauncher")
            } else {
                val caller = "routeCalculationResultFromLauncher"
                val (cid, vehicleNumber, dispatchId) = getDispatchInfoFromLocalDataSource(caller)
                stoplist.ifEmpty {
                    fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
                        cid,
                        vehicleNumber,
                        dispatchId,
                        caller,
                        false
                    )
                }
            }

            val uncompletedStops = stopList.getUncompletedStops()
            Log.d(tag, "routeCalculationResultFromLauncher: ${routeCalculationResult.stopDetailList.map { Triple(it.stopid, it.name, it.leg) }} Error: ${routeCalculationResult.error} State: ${routeCalculationResult.state}. Stop in RM: ${stopList.map { Triple(it.stopid, it.name, it.completedTime) }}")
            if (routeCalculationResult.state == STATE.SUCCESS) {
                val isRouteCalcResultOfActiveDispatch = isRouteCalcResultOfActiveDispatch(routeCalculationResult, stopList)
                if (isRouteCalcResultOfActiveDispatch.state == STATE.IGNORE) return isRouteCalcResultOfActiveDispatch
                //Clears route if free-floating trip, because user won't have selected a stop to navigate.
                //While calculating route the whole uncompleted stoplist will be sent. so, it shows route for all the uncompleted stops
                //that is cleared and drawn route only for the current stop.
                checkAndDrawRouteForStops(stopList, activeState)
                if (uncompletedStops.isNotEmpty() && routeCalculationResult.stopDetailList.size.isNotEqualTo(uncompletedStops.size)) {
                    Log.w(
                        tag,
                        "stops may not be equal. Please compare it. -> Stops from AL ${
                            routeCalculationResult.stopDetailList.map {
                                Triple(
                                    it.stopid,
                                    it.name,
                                    it.Address
                                )
                            }
                        }." + " Stops from RM ${
                            uncompletedStops.map {
                                Pair(
                                    it.stopid,
                                    it.name
                                )
                            }
                        }." + " or Address may not be available for any stop. Retrying route calc"
                    )
                    return RouteCalculationResult().also { it.state = STATE.ERROR }
                }
                return processRouteCalculationSuccessResult(
                    dataStoreManager,
                    routeCalculationResult,
                    stopList,
                    result
                )
            } else {
                if (routeCalculationResult.stopDetailList.size.isNotEqualTo(uncompletedStops.size)) {
                    Log.d(
                        tag,
                        "stop count is not equal. Please compare it.${routeCalculationResult.stopDetailList.map { Triple(it.stopid, it.name, it.Address) }}" +
                                " Stops from RM ${uncompletedStops.map { Pair(it.stopid, it.name) }}. Retrying route calc"
                    )
                    return RouteCalculationResult().also { it.state = STATE.ERROR }
                }
                return processRouteCalculationErrorResult(
                    routeCalculationResult,
                    result
                )
            }
        }
        return RouteCalculationResult().also { it.state = STATE.ERROR }
    }

    @VisibleForTesting
    internal fun isRouteCalcResultOfActiveDispatch(routeCalculationResult: RouteCalculationResult, stopList: List<StopDetail>): RouteCalculationResult {
        val dispatchIdFromRouteCalcResult = if (routeCalculationResult.stopDetailList.isNotEmpty()) {
            try { routeCalculationResult.stopDetailList[0].dispid.toLong() } catch (nfe: NumberFormatException) { -1 }
        } else -1
        val activeDispatchId = if (stopList.isNotEmpty()) {
            try { stopList[0].dispid.toLong() } catch (nfe: NumberFormatException) { -1 }
        } else -1
        if (dispatchIdFromRouteCalcResult != activeDispatchId) {
            Log.w(tag, "Got route calc result for another dispatch. DispId from AL: $dispatchIdFromRouteCalcResult. DispId in DW: $activeDispatchId")
            return RouteCalculationResult().also { it.state = STATE.IGNORE }
        }
        return routeCalculationResult
    }

    private suspend fun processRouteCalculationErrorResult(
        routeCalculationResult: RouteCalculationResult,
        result: String
    ): RouteCalculationResult {
        val stops = fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(":Method:processRouteCalculationErrorResult")
        if (stops.areAllStopsComplete()) {
            resetTripInfoWidget(":Method:processRouteCalculationErrorResult")
        }
        Log.w(
            tag,
            routeCalculationResult.error.ifEmpty { "Error calculating Route${result}" }
        )
        return routeCalculationResult
    }

    private suspend fun processRouteCalculationSuccessResult(
        dataStoreManager: DataStoreManager,
        routeCalculationResult: RouteCalculationResult,
        stopList: List<StopDetail>,
        result: String
    ): RouteCalculationResult {
        try {
            if(stopList.all { it.completedTime.isNotEmpty() }) return routeCalculationResult
            updateDataStoreAndWidgetWithRouteResult(
                routeCalculationResult,
                stopList
            )
            updateStopActionsWithETAAndAddress(
                routeCalculationResult,
                stopList,
                dataStoreManager
            )
            return routeCalculationResult
        } catch (e: Exception) {
            Log.e(
                tag,
                "error decoding routeCalculationResult ${e.message}${result}"
            )
            return RouteCalculationResult()
        }
    }

    internal suspend fun updateStopActionsWithETAAndAddress(
        routeCalculationResult: RouteCalculationResult,
        stopList: List<StopDetail>,
        dataStoreManager: DataStoreManager
    ) : Boolean {
        routeCalculationResult.stopDetailList.firstOrNull()?.let { stopDetailFromResult ->
            if (stopDetailFromResult.dispid != stopList.firstOrNull()?.dispid) return false
        }
        routeCalculationResult.stopDetailList.forEachIndexed { index, stopDetail ->
            if (stopList.getOrNull(index).isNull()) return false
            with(stopDetail.Actions) {
                clear()
                addAll(stopList[index].Actions)
            }
            stopDetail.Address?.let {
                if (it.name != ADDRESS_NOT_AVAILABLE) dataStoreManager.putStopAddress(
                    stopDetail.stopid,
                    Gson().toJson(it)
                )
            }
        }
        return true
    }

    private suspend fun updateDataStoreAndWidgetWithRouteResult(
        routeCalculationResult: RouteCalculationResult,
        stopList: List<StopDetail>
    ) {
        val remainingStopsCount= stopList.filter { it.completedTime.isEmpty() }.size
        val dispatchId= localDataSourceRepo.getFromAppModuleDataStore(ACTIVE_DISPATCH_KEY, EMPTY_STRING)
        Log.i(TRIP_WIDGET,"RouteResult D$dispatchId Dist${routeCalculationResult.totalDistance} H${routeCalculationResult.totalHours} S$remainingStopsCount, ETA: ${routeCalculationResult.stopDetailList.map { it.etaTime }}")
        tripInfoWidgetUseCase.updateTripInfoWidget(
            distance = routeCalculationResult.totalDistance.toFloat(),
            hour = routeCalculationResult.totalHours.toFloat(),
            stopCount = remainingStopsCount,
            caller = "RouteResult",
            stopList = stopList,
            routeCalculationResult = routeCalculationResult
        )
    }

    internal suspend fun checkAndDrawRouteForStops(stopList: List<StopDetail>, activeState: DispatchActiveState){
        stopList.hasFreeFloatingStops().let { isFreeFloating ->
            if (isFreeFloating) {
                with(sendDispatchDataUseCase) {
                    sendDispatchEventForClearRouteWithDelay()
                    drawRouteForCurrentStop(shouldRedrawCopilotRoute = true)
                    setGeofenceForCachedStops(activeState)
                    return
                }
            }
        }

        stopList.isSequentialTrip().let { isSequentialTrip ->
            if (isSequentialTrip.not()) return
            if (localDataSourceRepo.getCurrentStop() == null && stopList.all { it.completedTime.isNotEmpty() }) {
                Log.d(
                    tag,
                    "-----Inside checkAndDrawRouteForStops currentstop null and all stops are complete - clearing route"
                )
                clearRoute()
            }
            //get current stop, check if all previous stops are completed → don't clear route else clear route and draw new route
            localDataSourceRepo.getCurrentStop()?.let { currentStop ->
                if (stopList.getStopListBeforeGivenStop(currentStop.stopId).areAllStopsComplete()
                    && stopList.getStopListFromGivenStop(currentStop.stopId)
                        .getCompletedStopsBasedOnCompletedTime().isEmpty()
                ) {
                    //don't clear route as it will be same as route drawn during route calculation
                    Log.d(
                        tag,
                        "-----Inside checkAndDrawRouteForStops - don't clear route as it will be same as route drawn during route calculation. currentStop: ${currentStop.stopId} stopList: ${stopList.map { it.stopid }}"
                    )
                    //set geofence for stops with uncompleted actions. We do this here because we clear the trip data in map during retry of route calculation.
                    // In that case geofence events will be missed, so registering again.
                    sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(
                        stopList.getNonDeletedAndUncompletedStopsBasedOnActions(),
                        activeState,
                        "routeETACalculationResult-Sequential"
                    )
                } else {
                    with(sendDispatchDataUseCase) {
                        sendDispatchEventForClearRouteWithDelay()
                        drawRouteForCurrentStop(shouldRedrawCopilotRoute = true)
                        setGeofenceForCachedStops(activeState)
                        return
                    }
                }
            }
        }
    }

    internal fun resetTripInfoWidget(caller: String) = tripInfoWidgetUseCase.resetTripInfoWidget(caller = caller)

    suspend fun checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps(): List<StopDetail> {
        fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps").let { stopList ->
            if (stopList.isNotEmpty()) {
                var completedStopsCount = 0
                stopList.forEach { stop ->
                    if (stop.completedTime.isNotEmpty()) completedStopsCount.plus(1)
                        .let { sum ->
                            completedStopsCount = sum
                        }
                }
                if (completedStopsCount.isEqualTo(stopList.size)) {
                    resetTripInfoWidget(":Method:checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps")
                    if (localDataSourceRepo.getCurrentStop() == null) {
                        Log.d(
                            tag,
                            "CheckForLastStopArrivalAndUpdateRouteManifestWidgetAndCopilot currentstop is null and all stops are complete - clearing route"
                        )
                        clearRoute()
                    }
                }
                return stopList
            }
            return emptyList()
        }
    }

    suspend fun calculateRouteETAFromLauncherEvent(
        eventType: String,
        bundle: Bundle
    ) {
        Log.d(tag, "OnMessageReceivedEvent: $eventType")
        if (eventType == getEventTypeKeyForRouteCalculation()) {
            val result = routeCalculationResultFromLauncher(
                data = bundle,
                dataStoreManager = dataStoreManager,
                activeState = getDispatchActiveState()
            )
          processResultAndRetryIfError(result)
        }
    }

    suspend fun processResultAndRetryIfError(result: RouteCalculationResult) {
        Log.d(tag, "processResultAndRetryIfError: ${result.state}")
        when (result.state) {
            STATE.SUCCESS -> {
                routeCalculationRetryCount = ROUTE_CALCULATION_MAX_RETRY_COUNT
                _routeCalculationResult.emit( result)
            }

            STATE.ERROR -> {
                if (result.error.isEmpty()) {
                    if (routeCalculationRetryCount > ZERO) {
                        val delayTime = COPILOT_ROUTE_CALC_RETRY_DELAY * (2.0.pow(
                            ROUTE_CALCULATION_MAX_RETRY_COUNT - routeCalculationRetryCount
                        )).toLong()
                        delay(delayTime)
                        Log.d(
                            tag,
                            "retryRouteCalc $routeCalculationRetryCount, delay: $delayTime ms"
                        )
                        if (WorkflowApplication.dispatchActivityVisible) {
                            _retryRouteCalculation.emit(true)
                        } else {
                            retryRouteCalculationFromUC()
                        }
                        routeCalculationRetryCount -= 1
                    } else {
                        _routeCalculationResult.emit( result)
                    }
                } else {
                    _routeCalculationResult.emit( result)
                }
            }

            STATE.IGNORE -> {
                _routeCalculationResult.emit( result)
            }

            else -> {
                Log.w(tag, "invalid state in calculatedRouteResult ${result.state}")
                _routeCalculationResult.emit( result)
            }
        }
    }

    /**
     * Retry route calculation from UseCase when the dispatch activity is not visible
     * cases like if dispatch started from background worker
     */
    suspend fun retryRouteCalculationFromUC() {
        val caller = "processResultAndRetryIfError"
        val (cid, vehicleNumber, dispatchId) = getDispatchInfoFromLocalDataSource(caller)

        if (cid.isEmpty() || vehicleNumber.isEmpty() || dispatchId.isEmpty()) {
            Log.e(
                tag,
                "cid or vehicleNumber or dispatchId is empty. cid: $cid, vehicleNumber: $vehicleNumber, dispatchId: $dispatchId"
            )
            return
        }

        val stopList = fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
            cid, vehicleNumber, dispatchId, caller, false
        )
        startRouteCalculation(stopList, caller)
    }

    private suspend fun getDispatchInfoFromLocalDataSource(caller: String): Triple<String, String, String> {
        val cid = localDataSourceRepo.getAppModuleCommunicator().doGetCid()
        val vehicleNumber =
            localDataSourceRepo.getAppModuleCommunicator().doGetTruckNumber()
        val dispatchId =
            localDataSourceRepo.getSelectedDispatchId(caller)
        return Triple(cid, vehicleNumber, dispatchId)
    }

    // Route calculation is called only for Active and No_Trip_Active dispatch state so there is no need to handle Preview state
    suspend fun getDispatchActiveState(): DispatchActiveState {
        return if(localDataSourceRepo.hasActiveDispatch()) DispatchActiveState.ACTIVE else DispatchActiveState.NO_TRIP_ACTIVE
    }
}