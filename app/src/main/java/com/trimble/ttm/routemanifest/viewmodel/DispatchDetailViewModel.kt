package com.trimble.ttm.routemanifest.viewmodel

import android.app.Application
import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.trimble.ttm.commons.logger.FeatureLogTags
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TIMELINE
import com.trimble.ttm.commons.logger.TRIP_PREVIEWING
import com.trimble.ttm.commons.logger.TRIP_START_CALL
import com.trimble.ttm.commons.logger.TRIP_START_INSIDE_APP
import com.trimble.ttm.commons.logger.TRIP_STOP_LIST
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.HOTKEYS_COLLECTION_NAME
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.DISPATCH_NAME_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ROUTE_DATA_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_DISTANCE_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_HOURS_KEY
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.RouteCalculationResult
import com.trimble.ttm.routemanifest.model.RouteData
import com.trimble.ttm.routemanifest.model.STATE
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.StopType
import com.trimble.ttm.routemanifest.model.TripStartInfo
import com.trimble.ttm.routemanifest.model.getSortedStops
import com.trimble.ttm.routemanifest.usecases.DispatchBaseUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchValidationUseCase
import com.trimble.ttm.routemanifest.usecases.FetchDispatchStopsAndActionsUseCase
import com.trimble.ttm.routemanifest.usecases.LateNotificationUseCase
import com.trimble.ttm.routemanifest.usecases.RouteETACalculationUseCase
import com.trimble.ttm.routemanifest.usecases.SendDispatchDataUseCase
import com.trimble.ttm.routemanifest.usecases.StopDetentionWarningUseCase
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartCaller
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.utils.ADDED
import com.trimble.ttm.routemanifest.utils.REMOVED
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_MAX_RETRY_COUNT
import com.trimble.ttm.routemanifest.utils.STOP_COUNT_CHANGE_LISTEN_DELAY
import com.trimble.ttm.routemanifest.utils.Utils.getCalendarFromDate
import com.trimble.ttm.routemanifest.utils.Utils.getGsonInstanceWithIsoDateFormatter
import com.trimble.ttm.routemanifest.utils.Utils.getSystemLocalDateFromUTCDateTime
import com.trimble.ttm.routemanifest.utils.Utils.toJsonString
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

class DispatchDetailViewModel(
    private val applicationInstance: Application,
    private val routeETACalculationUseCase: RouteETACalculationUseCase,
    tripCompletionUseCase: TripCompletionUseCase,
    private val dataStoreManager: DataStoreManager,
    formDataStoreManager: FormDataStoreManager,
    dispatchBaseUseCase: DispatchBaseUseCase,
    dispatchStopsUseCase: DispatchStopsUseCase,
    sendDispatchDataUseCase: SendDispatchDataUseCase,
    private val tripPanelUseCase: TripPanelUseCase,
    dispatchValidationUseCase: DispatchValidationUseCase,
    coroutineDispatcher: DispatcherProvider,
    stopDetentionWarningUseCase: StopDetentionWarningUseCase,
    private val backboneUseCase: BackboneUseCase,
    private val lateNotificationUseCase: LateNotificationUseCase,
    private val sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase,
    fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
    private val formLibraryUseCase: FormLibraryUseCase,
    private val tripStartUseCase: TripStartUseCase
    ) : DispatchBaseViewModel(
    applicationInstance,
    routeETACalculationUseCase,
    tripCompletionUseCase,
    dataStoreManager,
    formDataStoreManager,
    dispatchBaseUseCase,
    dispatchStopsUseCase,
    sendDispatchDataUseCase,
    tripPanelUseCase,
    dispatchValidationUseCase,
    coroutineDispatcher,
    stopDetentionWarningUseCase,
    fetchDispatchStopsAndActionsUseCase,
), KoinComponent {

    private val tag = "DispatchDetailVM"
    private val _totalDistance = MutableLiveData<Double>()
    private val _totalHours = MutableLiveData<Double>()
    private val _nextStopAvailable = MutableLiveData<Boolean>()
    private val _previousStopAvailable = MutableLiveData<Boolean>()
    private val _dispatchDetailError = MutableLiveData<String>()
    private var _isEndTripEnabled = MutableLiveData<Boolean>()
    private var _verticalLineTimeString = MutableLiveData<String>()
    private var currentIndex = 0

    val rearrangedStops: LiveData<List<StopDetail>> = _rearrangedStops
    val totalDistance: LiveData<Double> = _totalDistance
    val totalHours: LiveData<Double> = _totalHours
    val nextStopAvailable: LiveData<Boolean> = _nextStopAvailable
    val previousStopAvailable: LiveData<Boolean> = _previousStopAvailable
    var dispatchName: String = ""
    val dispatchDetailError: LiveData<String> = _dispatchDetailError
    val isEndTripEnabled: LiveData<Boolean> = _isEndTripEnabled
    private var _canShowStopsNotAvailablePopup = MutableLiveData<Boolean>()
    val canShowStopsNotAvailablePopup: LiveData<Boolean> = _canShowStopsNotAvailablePopup
    internal val verticalLineTimeString :LiveData<String> = _verticalLineTimeString
    private var dataStoreListenerJob: Job? = null
    private var stopListenerJob: Job? = null

    private val _stopUpdateStatus = MutableSharedFlow<String>()
    internal val stopUpdateStatus: SharedFlow<String> = _stopUpdateStatus
    private var stopManipulationListenerJob: Job? = null
    private val _updateMenu: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var _isHotKeysAvailable = MutableLiveData<Boolean>()
    val isHotKeysAvailable : LiveData<Boolean> = _isHotKeysAvailable


    init {
        initializeDispatchDetailViewModel()
        observeRouteCalculationResult()
        observeRetryRouteCalculation()
    }

    fun initializeDispatchDetailViewModel(){
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.default()) {
            dispatchName = dataStoreManager.getValue(DISPATCH_NAME_KEY, EMPTY_STRING)
            getVIDFromDispatchData(getSelectedDispatchId(tag))
            stopListenerJob = launch {
                getStopsForDispatch(getSelectedDispatchId(tag))
            }
            /*Post totalDistance as Calculating distance... and totalHours as Calculating ETA...
            since copilot has to be ready and route should be calculated for calculating eta and miles*/
            dataStoreManager.getValue(TOTAL_DISTANCE_KEY, -1f).let {
                _totalDistance.postValue(it.toDouble())
            }
            dataStoreManager.getValue(TOTAL_HOURS_KEY, -1f).let {
                _totalHours.postValue(it.toDouble())
            }
        }
    }


    private fun observeRouteCalculationResult() {
        viewModelScope.launch {
            routeETACalculationUseCase.routeCalculationResult.collectLatest { result ->
                result?.let {
                    calculatedRouteResult(it)
                }
            }
        }
    }

    private fun observeRetryRouteCalculation() {
        viewModelScope.launch {
            routeETACalculationUseCase.retryRouteCalculation.collectLatest { retryRouteCalculation ->
                if (retryRouteCalculation) {
                    startRouteCalculation("retryRouteCalculation")
                }
            }
        }
    }

    override fun onCleared() {
        stopListenerJob?.cancel()
        super.onCleared()
    }

    override fun postStopsProcessing(caller: String) {
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.default() + SupervisorJob()) {
            try {
                stopDetailList.forEach {
                    getActionsForStop(it.stopid)
                }

                val dispatchId = getSelectedDispatchId(caller)
                //For stop list screen initial data update
                updateStopDetailList("post stop processing",dispatchId)
                if (dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip().not()) {
                    sendStopsDataToMapsForRouteCalculation(isStopsReadFirstTime)
                }
                if(stopDetailList.filter { it.completedTime.isNotEmpty() }.size == stopDetailList.size){
                    //all stops are completed, so update data for timeline - in other cases, timeline data will be updated after calculating route.
                    updateRouteDataMapOnAllStopsCompleted()
                }
                // Todo add this to stop addition hpn upon its implementation and remove from here - scheduleLateNotificationCheckWorker()
                dispatchStopsUseCase.isTripStarted(dispatchId, appModuleCommunicator.doGetCid(), appModuleCommunicator.doGetTruckNumber()).let { isTripStarted ->
                    if (isTripStarted) {
                        scheduleLateNotificationCheckWorker("postStopsProcessing", isFromTripStart = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    caller,
                    "post stops processing",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
            }
        }
    }

    private fun setStopsAvailableFlags() {
        _previousStopAvailable.postValue(currentIndex > 0)
        if (!rearrangedStops.value.isNullOrEmpty()) _nextStopAvailable.postValue(currentIndex < _rearrangedStops.value!!.size - 1)
    }

    fun registerDataStoreListener() {
        dataStoreListenerJob?.cancel()
        dataStoreListenerJob =
            CoroutineScope(coroutineDispatcher.io() + SupervisorJob()).launch(CoroutineName(tag)) {
                WorkflowEventBus.stopCountListenerEvents.onEach { stopEditType ->
                    when (stopEditType) {
                        ADDED, REMOVED -> {
                            _stopUpdateStatus.emit(stopEditType)
                            Log.d(
                                tag,
                                "----Inside DispatchDetailViewModel registerDataStoreListener invoking ActiveDispatchStopManipulationListener: stop edit type: $stopEditType "
                            )
                        }
                    }
                }.launchIn(this)
            }
    }

    fun unRegisterDataStoreListener() = dataStoreListenerJob?.cancel()

    override suspend fun startRouteCalculation(caller: String) {
        //We need to calculate ETA and draw trip only for active dispatches, not for inactive dispatches.
        if(dispatchActiveStateFlow.value == DispatchActiveState.ACTIVE || dispatchActiveStateFlow.value == DispatchActiveState.NO_TRIP_ACTIVE){
            Log.i(caller, "Starting route calculation - stopDetailList size:${stopDetailList.size}")
            if (stopDetailList.isEmpty() || stopDetailList.none { it.completedTime.isEmpty() }) {
                _totalDistance.postValue(0.0)
                _totalHours.postValue(0.0)
                _rearrangedStops.postValue(ArrayList())
                routeETACalculationUseCase.resetTripInfoWidget(":Method:startRouteCalculation")
                return
            }
            _totalDistance.postValue(-1.0)
            _totalHours.postValue(-1.0)
            handleMapClearForInActiveDispatchTripPreview()
            routeETACalculationUseCase.startRouteCalculation(stopDetailList, "DispatchDetailVM")
            Log.d(tag,"stopDetailList CompletedTime: ${stopDetailList.map { Pair(it.stopid, it.completedTime) }}")
            return
        }
    }

    suspend fun calculatedRouteResult(routeCalculationResult: RouteCalculationResult): RouteCalculationResult {
        Log.d(tag, "calculatedRouteResult: $routeCalculationResult")
        return when (routeCalculationResult.state) {
            STATE.SUCCESS -> {
                routeCalculationRetryCount = ROUTE_CALCULATION_MAX_RETRY_COUNT
                handleRouteCalculationSuccess(routeCalculationResult)
            }
            STATE.ERROR -> {
                postRouteCalculationError(routeCalculationResult)
                routeCalculationResult
            }
            STATE.IGNORE -> {
                //Ignored
                routeCalculationResult
            }
            else -> {
                Log.w(tag, "invalid state in calculatedRouteResult ${routeCalculationResult.state}")
                routeCalculationResult
            }
        }
    }

    internal fun postRouteCalculationError(routeCalculationResult: RouteCalculationResult) {
        if (routeCalculationResult.error.isNotEmpty()) _dispatchDetailError.postValue(
            routeCalculationResult.error
        )
        Log.e(
            tag,
            routeCalculationResult.error.ifEmpty { "Error calculating Route" },
            throwable = null,
            "stack" to routeCalculationResult.toString()
        )
        _totalDistance.postValue(0.0)
        _totalHours.postValue(0.0)
    }

    private suspend fun handleRouteCalculationSuccess(routeCalculationResult: RouteCalculationResult): RouteCalculationResult {
        try {
            val dispatchId = getSelectedDispatchId("route calc success")
            if(stopDetailList.all { it.completedTime.isNotEmpty() }) return RouteCalculationResult()
            _totalDistance.postValue(routeCalculationResult.totalDistance)
            _totalHours.postValue(routeCalculationResult.totalHours)
            // To update stop list ui. Upon action completion route calculation will be triggered and it's result (this one) will update the stop list again for updated data.
            // Action is suffice for this. For some reason if that not working. This may update the ui.
            // Route calculations normally takes sometime and this stop list should have every action at that point of time
            updateStopDetailList("route calc success result",dispatchId)
            Log.d(
                tag, "post value stops", throwable = null, "stopsSize" to stopDetailList.size
            )
            val routeDataMap = mutableMapOf<Int, RouteData>()

                val tempRearrangeList = ArrayList<StopDetail>()
                WorkflowEventBus.stopListEvents.firstOrNull()
                    ?.filter { it.completedTime.isNotEmpty() }?.onEach {
                        val stopCompletedTimeCalendar = getCalendarFromDate(
                            getSystemLocalDateFromUTCDateTime(it.completedTime)!!
                        )
                        it.EstimatedArrivalTime = stopCompletedTimeCalendar.clone() as Calendar
                        it.etaTime = stopCompletedTimeCalendar.time
                        tempRearrangeList.add(it)

                        routeDataMap[it.stopid] = RouteData(
                            etaTime = getSystemLocalDateFromUTCDateTime(it.completedTime)
                                ?: Date(), address = it.Address, leg = it.leg
                        )
                    }
                tempRearrangeList.addAll(routeCalculationResult.stopDetailList)
                _rearrangedStops.postValue(tempRearrangeList.getSortedStops())
                if (routeCalculationResult.stopDetailList.isNotEmpty()) {
                    routeCalculationResult.stopDetailList.forEach { stopDetail ->
                        routeDataMap[stopDetail.stopid] = RouteData(
                            etaTime = stopDetail.etaTime ?: Date(),
                            address = stopDetail.Address,
                            leg = stopDetail.leg
                        )
                    }
                }
            toJsonString(routeDataMap, getGsonInstanceWithIsoDateFormatter())?.also {
                Log.i(
                    FeatureLogTags.ROUTE_CALCULATION_RESULT.name,
                    "Got RouteCalculation result: $it"
                )
                dataStoreManager.setValue(ROUTE_DATA_KEY, it)
            }
            return routeCalculationResult
        } catch (e: Exception) {
            Log.e(
                tag,
                "error decoding routeCalculationResult ${e.message}",
                throwable = null,
                "CalcSuccess" to e.stackTraceToString(),
                "result" to routeCalculationResult.toString()
            )
            return routeCalculationResult
        }
    }

    fun selectStop(stopIndex: Int) {
        currentIndex = stopIndex
        setStopsAvailableFlags()
        if (!rearrangedStops.value.isNullOrEmpty()) {
            _currentStop.value = _rearrangedStops.value!![stopIndex]
        }
    }

    fun getNextStop() {
        if (!rearrangedStops.value.isNullOrEmpty() && currentIndex >= 0 && currentIndex < _rearrangedStops.value!!.size - 1) {
            Log.logUiInteractionInInfoLevel(tag, "Timeline screen next stop button clicked. Current stop index: $currentIndex")
            currentIndex++
            setStopsAvailableFlags()
            if ((currentIndex >= 0 && currentIndex < _rearrangedStops.value!!.size)) {
                _currentStop.value = _rearrangedStops.value!![currentIndex]
            }
        }
    }

    fun getPreviousStop() {
        if (!rearrangedStops.value.isNullOrEmpty() && currentIndex >= 1 && currentIndex < _rearrangedStops.value!!.size) {
            Log.logUiInteractionInInfoLevel(tag, "Timeline screen previous stop button clicked. Current stop index: $currentIndex")
            currentIndex--
            setStopsAvailableFlags()
            if ((currentIndex >= 0 && currentIndex < _rearrangedStops.value!!.size)) {
                _currentStop.value = _rearrangedStops.value!![currentIndex]
            }
        }
    }

    fun getResourceId(iconType: StopType): Int {
        return when (iconType) {
            StopType.PRE_TRIP -> R.drawable.ic_baseline_assignment_24
            StopType.ENTER -> R.drawable.ic_baseline_assignment_24
            StopType.EXIT -> R.drawable.ic_baseline_assignment_24
            StopType.GAS_STATION -> R.drawable.ic_local_gas_station_black_24dp
            StopType.POST_TRIP -> R.drawable.ic_baseline_assignment_24
            StopType.STOP_COMPLETED -> R.drawable.ic_check_circle_black_24dp
        }
    }

    override suspend fun onStopsEmpty() {
        val isConnectedToNetwork = WorkflowApplication.getLastValueOfInternetCheck()
        withContext(coroutineDispatcher.main()) {
            _canShowStopsNotAvailablePopup.value = isConnectedToNetwork.not()
        }
        Log.i(
            TRIP_STOP_LIST,
            "EmptyStopList.IsNetworkConnected$isConnectedToNetwork.",
            throwable = null,
            "dispatch id" to dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING)
        )
    }

    fun showEndTripNavigationViewItem(isEnabled: Boolean) {
        _isEndTripEnabled.postValue(isEnabled)
    }

    private suspend fun updateRouteDataMapOnAllStopsCompleted() {
        val routeDataMap = mutableMapOf<Int, RouteData>()
        val tempRearrangeList = ArrayList<StopDetail>()
        stopDetailList.filter { it.completedTime.isNotEmpty() }.onEach {
            val stopCompletedTimeCalendar = getCalendarFromDate(
                getSystemLocalDateFromUTCDateTime(it.completedTime)!!
            )
            it.EstimatedArrivalTime = stopCompletedTimeCalendar.clone() as Calendar
            it.etaTime = stopCompletedTimeCalendar.time
            tempRearrangeList.add(it)

            routeDataMap[it.stopid] = RouteData(
                etaTime = getSystemLocalDateFromUTCDateTime(it.completedTime) ?: Date(),
                address = it.Address,
                leg = it.leg
            )
        }
        _rearrangedStops.postValue(tempRearrangeList.getSortedStops())

        toJsonString(routeDataMap, getGsonInstanceWithIsoDateFormatter())?.also {
            Log.i(
                FeatureLogTags.ROUTE_CALCULATION_RESULT.name, "Got RouteCalculation result: $it"
            )
            dataStoreManager.setValue(ROUTE_DATA_KEY, it)
        }
    }

    fun listenForStopAdditionAndRemoval() {
        stopManipulationListenerJob?.cancel()
        stopManipulationListenerJob = viewModelScope.launch(coroutineDispatcher.io()) {
            val selectedDispatchId=getSelectedDispatchId("listenForStopAdditionAndRemoval")
            var removedOrAddedStopsCount = 0
            var stopCountChangeTimer: CountDownTimer? = null
            var stopManipulationListenerJob: Job? = null
            var removedStopCount = 0
            var addedStopCount = 0
            var lastUpdatedInactiveStops = dispatchBaseUseCase.getInActiveStopCount(selectedDispatchId)
            var lastUpdatedActiveStops = dispatchBaseUseCase.getActiveStopCount(selectedDispatchId)
            stopUpdateStatus.collectLatest { stopUpdateStatus ->
                stopManipulationListenerJob?.cancel()
                stopManipulationListenerJob = launch stopManipulationListenerLaunch@{
                    val stopManipulationResult = dispatchBaseUseCase.getManipulatedStopCount(
                        selectedDispatchId,
                        stopUpdateStatus, lastUpdatedActiveStops, lastUpdatedInactiveStops
                    )
                    if (stopManipulationResult.third.not()) return@stopManipulationListenerLaunch
                    when (stopUpdateStatus) {
                        REMOVED -> {
                            removedStopCount = stopManipulationResult.first
                            removedOrAddedStopsCount = stopManipulationResult.second
                        }

                        ADDED -> {
                            addedStopCount = stopManipulationResult.first
                            removedOrAddedStopsCount = stopManipulationResult.second
                        }

                        else -> {
                            //Ignored
                        }
                    }

                    Log.i(
                        tag,
                        "lastUpdatedActiveStops: $lastUpdatedActiveStops lastUpdatedInactiveStops: $lastUpdatedInactiveStops removedStopCount $removedStopCount addedStopCount: $addedStopCount removedOrAddedStopsCount: $removedOrAddedStopsCount"
                    )

                    withContext(coroutineDispatcher.main()) {
                        stopCountChangeTimer?.cancel()
                        stopCountChangeTimer = object : CountDownTimer(
                            STOP_COUNT_CHANGE_LISTEN_DELAY, STOP_COUNT_CHANGE_LISTEN_DELAY
                        ) {
                            override fun onTick(millisUntilFinished: Long) {
                                //Ignore
                            }

                            override fun onFinish() {
                                viewModelScope.launch(coroutineDispatcher.io()) {
                                    withContext(coroutineDispatcher.main()) {
                                        notifyStopCountChange(
                                            applicationInstance,
                                            stopUpdateStatus,
                                            removedOrAddedStopsCount
                                        )
                                    }
                                    removedOrAddedStopsCount = 0
                                    lastUpdatedInactiveStops =
                                        dispatchBaseUseCase.getInActiveStopCount(selectedDispatchId)
                                    lastUpdatedActiveStops =
                                        dispatchBaseUseCase.getActiveStopCount(selectedDispatchId)
                                }
                            }
                        }
                        stopCountChangeTimer?.start()
                    }
                }
            }
        }
    }

     internal fun saveSelectedTripName() {
         appModuleCommunicator.getAppModuleApplicationScope().launch(coroutineDispatcher.io()) {
             dispatchValidationUseCase.updateNameOnSelected(dispatchName)
         }
     }

    private fun updateMenuState() {
        viewModelScope.launch(
            coroutineDispatcher.io() + CoroutineName(tag)
        ) {
            _updateMenu.emit(true)
        }
    }

    fun getTimeString(
        calendarToRetrieveTime: Calendar,
        dateTimeFormat: SimpleDateFormat,
        tag: String,
        numberOfHours: Int,
        is24HourTimeFormat : Boolean
    ) {
        viewModelScope.launch(coroutineDispatcher.main() + CoroutineName("${TIMELINE}GetTimeString")) {
            for (index in 1..numberOfHours) {
                _verticalLineTimeString.value = dispatchBaseUseCase.getTimeString(
                    coroutineDispatcher = coroutineDispatcher.default(),
                    calendarToRetrieveTime = calendarToRetrieveTime,
                    dateTimeFormat = dateTimeFormat,
                    dispatchId = getSelectedDispatchId(tag),
                    tag = tag,
                    is24HourTimeFormat = is24HourTimeFormat
                )
            }
        }
    }

    fun startTrip(
        timeInMillis: Long,
        stopDetailList: CopyOnWriteArrayList<StopDetail>? = null,
        pfmEventsInfo: PFMEventsInfo.TripEvents,
        tripStartCaller : TripStartCaller
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(CoroutineName(TRIP_START_CALL) + coroutineDispatcher.io()) {

            tripStartUseCase.startTrip(
                tripStartInfo = TripStartInfo(
                    stopDetailList = stopDetailList as? List<StopDetail>,
                    timeInMillis = timeInMillis,
                    pfmEventsInfo = pfmEventsInfo,
                    cid = appModuleCommunicator.doGetCid(),
                    vehicleNumber = appModuleCommunicator.doGetTruckNumber(),
                    tripStartCaller = tripStartCaller,
                    caller = TRIP_START_INSIDE_APP
                )
            )
            updateMenuState()
            enableEndTripNavigationViewItem(this)
            hasFirstStopAsCurrentStopHandled(
                stopDetailList = stopDetailList
            )
            saveSelectedTripName()
        }
    }

    /**
     * Enables end trip functionality if enabled in trip xml
     */
    internal fun enableEndTripNavigationViewItem(coroutineScope: CoroutineScope = viewModelScope) {
        coroutineScope.launch(coroutineDispatcher.io()) {
            Log.d(TRIP_START_CALL,"enableEndTripNavigationViewItem() invoked")
            isManualTripCompletionDisabled().collect {
                showEndTripNavigationViewItem(it.not())
            }
        }
    }

    private suspend fun hasFirstStopAsCurrentStopHandled(
        stopDetailList: CopyOnWriteArrayList<StopDetail>? = null
    ) {
        (if (stopDetailList.isNull()) this@DispatchDetailViewModel.stopDetailList else stopDetailList)?.let { stopList ->
            hasFirstStopAsCurrentStopHandled(stopList).collect()
        }
    }

    internal fun resetTripInfoWidgetIfThereIsNoActiveTrip() {
        viewModelScope.launch(coroutineDispatcher.io()) {
            hasActiveDispatchKey().filter { hasActiveDispatch -> hasActiveDispatch.not() }.collect {
                routeETACalculationUseCase.resetTripInfoWidget("resetTripInfoWidgetIfThereIsNoActiveTrip")
            }
        }
    }

    internal fun hasActiveDispatchKey(): Flow<Boolean> = dispatchBaseUseCase.hasActiveDispatchKey(dataStoreManager)

    internal fun scheduleLateNotificationCheckWorker(
        caller: String,
        isFromTripStart: Boolean,
        workManager: WorkManager = WorkManager.getInstance(
            applicationInstance.applicationContext
        )
    ) {
        if (stopDetailList.isEmpty()) {
            Log.w(
                caller,
                "The stop detail list is empty during trip start. The late notification worker is not scheduled"
            )
            return
        }
        lateNotificationUseCase.scheduleLateNotificationCheckWorker(
            scope = appModuleCommunicator.getAppModuleApplicationScope(),
            workManager = workManager,
            dispatchId = stopDetailList[0].dispid,
            caller = caller,
            isFromTripStart = isFromTripStart
        )
    }

    fun getTripEventReasonTypeAndGuf(eventReason: String?): Pair<String, Boolean> =
        dispatchBaseUseCase.getTripEventReasonTypeAndGuf(eventReason)

    internal fun processEndTrip() {
        viewModelScope.launch(coroutineDispatcher.io()) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulation()
            /** trip end = StopActionReasonTypes.MANUAL.name, driver manual Event
             * Driver manually ends the trip via hamburger menu
             */
            val pfmEventsInfo = PFMEventsInfo.TripEvents(
                reasonType = StopActionReasonTypes.MANUAL.name,
                negativeGuf = false
            )
            runOnTripEnd(
                getSelectedDispatchId("showEndTripDialog"),
                "showEndTripDialog", pfmEventsInfo
            )
        }
    }

    internal fun setMenuDataOptionsListener(updateDropDownMenu: () -> Unit) {
        viewModelScope.launch(coroutineDispatcher.io()) {
            _updateMenu.collect {
                if(it) updateDropDownMenu()
            }
        }
    }

    /**
     * This method checks if we have only one dispatch and has an active dispatch
     */
    internal fun checkIfHasOnlyOneDispatchAndIsActive(
        hasOnlyOneActiveDispatch : (Boolean) -> Unit
    ) {
        viewModelScope.launch(coroutineDispatcher.main()) {
            hasOnlyOneActiveDispatch(dispatchValidationUseCase.hasOnlyOne() && dispatchValidationUseCase.hasAnActiveDispatch())
        }
    }

    /**
     * dispatch complete is sent to map for the below scenario.
     * dispatcher sends a dispatch. driver starts the dispatch. then dispatcher removes all the stops of that trip.
     * the trip will end in driver workflow. The route will persist in copilot according to MAPP-8085
     * now the driver selects a trip from trip list and the route for this trip is rendered in copilot,
     * when the driver press back button from stop list the route in copilot should be cleared
     */
    internal fun handleMapClearForInActiveDispatchTripPreview() {
        appModuleCommunicator.getAppModuleApplicationScope().launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            val job = this.coroutineContext.job
            if (dataStoreManager.hasActiveDispatch("handleMapClearForInActiveDispatchTripPreview",false).not()
                && dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING).isNotEmpty()) sendDispatchCompleteEventToCPIK()
            if (job.isActive) job.cancel()
        }
    }

    fun restoreSelectedDispatchIdOnReInitialize(functionToExecute : () -> Unit) {
        viewModelScope.launch(coroutineDispatcher.io()) {
            Log.d(tag+ TRIP_PREVIEWING,"restoreSelectedDispatchIdOnReInitialize() invoked in DispatchDetailViewModel")
            dispatchStopsUseCase.restoreSelectedDispatch()
            initializeDispatchBaseViewModel()
            initializeDispatchDetailViewModel()
            withContext(coroutineDispatcher.main()){
                functionToExecute()
            }
        }
    }

    fun canShowHotKeysMenu() {
        viewModelScope.launch(coroutineDispatcher.main()) {
            val obcId = appModuleCommunicator.doGetObcId()
            if(obcId.isNotEmpty()) {
                formLibraryUseCase.getHotKeysWithoutDescription(HOTKEYS_COLLECTION_NAME, obcId).collectLatest {
                    _isHotKeysAvailable.postValue(it.isNotEmpty())
                }
            } else {
                _isHotKeysAvailable.postValue(false)
            }
        }
    }
}