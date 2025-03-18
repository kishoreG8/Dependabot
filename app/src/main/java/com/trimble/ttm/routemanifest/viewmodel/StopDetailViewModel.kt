package com.trimble.ttm.routemanifest.viewmodel

import android.app.Application
import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MANUAL_ARRIVAL_STOP
import com.trimble.ttm.commons.logger.TRIP_CURRENT_STOP_ID
import com.trimble.ttm.commons.logger.TRIP_EDIT
import com.trimble.ttm.commons.logger.TRIP_ID
import com.trimble.ttm.commons.logger.TRIP_PREVIEWING
import com.trimble.ttm.commons.logger.VIEW_MODEL
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.commons.utils.ext.isNotNull
import com.trimble.ttm.commons.utils.isFeatureTurnedOn
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.DEBOUNCE_INTERVAL
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.CURRENT_DISPATCH_NAME_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.SHIPMENTS_IDS_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TRAILER_IDS_KEY
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.ArrivalType
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionEventData
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.getSortedStops
import com.trimble.ttm.routemanifest.model.hasArrivedActionOnly
import com.trimble.ttm.routemanifest.model.hasArrivingActionOnly
import com.trimble.ttm.routemanifest.model.hasArrivingAndArrivedActionOnly
import com.trimble.ttm.routemanifest.model.hasArrivingAndDepartActionOnly
import com.trimble.ttm.routemanifest.model.hasDepartActionOnly
import com.trimble.ttm.routemanifest.model.hasNoDepartedAction
import com.trimble.ttm.routemanifest.model.logAction
import com.trimble.ttm.routemanifest.usecases.ArrivalReasonUsecase
import com.trimble.ttm.routemanifest.usecases.DispatchBaseUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchValidationUseCase
import com.trimble.ttm.routemanifest.usecases.FetchDispatchStopsAndActionsUseCase
import com.trimble.ttm.routemanifest.usecases.RouteETACalculationUseCase
import com.trimble.ttm.routemanifest.usecases.SendDispatchDataUseCase
import com.trimble.ttm.routemanifest.usecases.StopDetentionWarningUseCase
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.usecases.UncompletedFormsUseCase
import com.trimble.ttm.routemanifest.utils.ADDED
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.INVALID_STOP_INDEX
import com.trimble.ttm.routemanifest.utils.REMOVED
import com.trimble.ttm.routemanifest.utils.STOP_COUNT_CHANGE_LISTEN_DELAY
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.getNonDeletedAndUncompletedStopsBasedOnActions
import com.trimble.ttm.routemanifest.utils.ext.getWorkflowEventName
import com.trimble.ttm.routemanifest.utils.stopId
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject
import java.util.TreeMap

class StopDetailViewModel(
    private val applicationInstance: Application,
    routeETACalculationUseCase: RouteETACalculationUseCase,
    tripCompletionUseCase: TripCompletionUseCase,
    private val dataStoreManager: DataStoreManager,
    formDataStoreManager: FormDataStoreManager,
    dispatchBaseUseCase: DispatchBaseUseCase,
    dispatchStopsUseCase: DispatchStopsUseCase,
    sendDispatchDataUseCase: SendDispatchDataUseCase,
    tripPanelUseCase: TripPanelUseCase,
    val dispatcherProvider: DispatcherProvider,
    stopDetentionWarningUseCase: StopDetentionWarningUseCase,
    dispatchValidationUseCase: DispatchValidationUseCase,
    fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
    private val formViewModel: FormViewModel,
    private val backboneUseCase: BackboneUseCase,
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
    dispatcherProvider,
    stopDetentionWarningUseCase,
    fetchDispatchStopsAndActionsUseCase
) {
    private val arrivalReasonUsecase: ArrivalReasonUsecase by inject()
    private val tag = "StopDetailVM"
    private var currentIndex = 0
    private var selectedStopId = 0
    private val _stopOfStop = MutableLiveData<String>()
    private val _displayArrived = MutableLiveData<Boolean>()
    private val _arriveButtonText = MutableLiveData<String>()
    private val _departButtonText = MutableLiveData<String>()
    private val _displayDeparted = MutableLiveData<Boolean>()
    private val _displayNavigate = MutableLiveData<Boolean>()
    private val _enableDepart = MutableLiveData<Boolean>()
    private val _nextStopAvailable = MutableLiveData<Boolean>()
    private val _previousStopAvailable = MutableLiveData<Boolean>()
    private val _trailerIds = MutableLiveData<String>()
    private val _shipmentIds = MutableLiveData<String>()
    private val _etaOrArrivedTime = MutableLiveData<String>()
    private var _isCurrentStopActionsEmptyOffline = MutableLiveData<Boolean>()

    val stopOfStop: LiveData<String> = _stopOfStop
    val displayArrived: LiveData<Boolean> = _displayArrived
    val arriveButtonText: LiveData<String> = _arriveButtonText
    val departButtonText: LiveData<String> = _departButtonText
    val displayDeparted: LiveData<Boolean> = _displayDeparted
    val displayNavigate: LiveData<Boolean> = _displayNavigate
    val nextStopAvailable: LiveData<Boolean> = _nextStopAvailable
    val previousStopAvailable: LiveData<Boolean> = _previousStopAvailable
    val trailerIds: LiveData<String> = _trailerIds
    val shipmentIds: LiveData<String> = _shipmentIds
    val etaOrArrivedTime: LiveData<String> = _etaOrArrivedTime
    val enableDepart: LiveData<Boolean> = _enableDepart
    val isCurrentStopActionsEmptyOffline: LiveData<Boolean> = _isCurrentStopActionsEmptyOffline
    private var dataStoreListenerJob: Job? = null
    private var stopsAndActionsUpdateCallerJob: Job? = null
    private var stopsAndActionsUpdateCollectionJob: Job? = null
    private var stopListenerJob: Job? = null
    private var nextAndPreviousStopButtonDebounceJob: Job? = null
    private var actionDisplayDebounceJob: Job? = null
    private var actionMissOfflineCheckAndReporterJob: Job? = null
    private var stopDataExceptionRetryCount = 5
    private val mutableScreenState =
        MutableStateFlow<ScreenContentState>(ScreenContentState.Success())
    internal val screenContentState = mutableScreenState.asStateFlow()

    private val _stopUpdateStatus = MutableSharedFlow<String>()
    internal val stopUpdateStatus: SharedFlow<String> = _stopUpdateStatus.asSharedFlow()
    private var stopManipulationListenerJob: Job? = null
    private val _shouldFinishActivityLiveData = MutableLiveData<Unit>()
    val shouldFinishActivityLiveData: LiveData<Unit> = _shouldFinishActivityLiveData

    /**
     * Cache completed actions to avoid flicker in button ui during jetpack datastore changes.
     * @Generic Key - StopId.
     * @Generic Pair A - Arrive action completion status.
     * @Generic Pair B - Depart action completion status.
     */
    internal var cacheCompletedActions = TreeMap<Int, Pair<Boolean, Boolean>>()

    init {
        initializeStopDetailViewModel()
    }

    fun initializeStopDetailViewModel(){
        getStopListData()
        listenForStopActionReadCompletion()
    }


    fun getStopListData() {
        stopListenerJob?.cancel()
        stopListenerJob =
            viewModelScope.launch(CoroutineName(tag) + dispatcherProvider.io()) {
                resetButtonVisibilityFlags()
                getTrailersAndShipmentsData()
                mutableScreenState.value = ScreenContentState.Loading()
                getStopsForDispatch(getSelectedDispatchId(tag))
            }
    }

    internal fun listenForStopActionReadCompletion() {
        viewModelScope.launch(dispatcherProvider.io()) {
            stopActionReadCompleteFlow.collect { stopId ->
                if (stopId != selectedStopId) return@collect
                updateStopDetailScreenWithActions()
            }
        }
    }

    override fun onCleared() {
        stopListenerJob?.cancel()
        dataStoreListenerJob?.cancel()
        stopsAndActionsUpdateCallerJob?.cancel()
        stopsAndActionsUpdateCollectionJob?.cancel()
        cacheCompletedActions.clear()
        super.onCleared()
    }

    override fun postStopsProcessing(caller: String) {
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.default() + SupervisorJob()) {
            stopDetailList.forEach {
                getActionsForStop(it.stopid)
            }
            if (currentIndex == 0 && stopDetailList.isNotEmpty()) {
                currentIndex = stopDetailList.indexOfFirst { it.stopid == selectedStopId }
            }
        }
    }

    fun getCurrentStopIndex() = currentIndex

    //just override the method here since it is declared as abstract method
    override suspend fun startRouteCalculation(caller: String) {
        //not required in this class.
    }

    private fun resetButtonVisibilityFlags() {
        _displayArrived.postValue(false)
        _displayDeparted.postValue(false)
        _enableDepart.postValue(false)
    }

    private fun getTrailersAndShipmentsData() {
        viewModelScope.launch(CoroutineName(tag)) {
            backboneUseCase.monitorTrailersData()
                .collect { value: List<String> ->
                    if (value.isEmpty()) {
                        dataStoreManager.setValue(TRAILER_IDS_KEY, EMPTY_STRING)
                    } else {
                        val trailers = value.joinToString(",")
                        dataStoreManager.setValue(TRAILER_IDS_KEY, trailers)
                    }
                }
        }

        viewModelScope.launch(CoroutineName(tag)) {
            backboneUseCase.monitorShipmentsData()
                .collect { value: List<String> ->
                    if (value.isEmpty()) {
                        dataStoreManager.setValue(SHIPMENTS_IDS_KEY, EMPTY_STRING)
                    } else {
                        val shipments = value.joinToString(",")
                        dataStoreManager.setValue(
                            SHIPMENTS_IDS_KEY, shipments
                        )
                    }
                }
        }
    }

    fun setCurrentStop(currentStopIndex: Int?, caller: String) {
        if (currentStopIndex == null || currentStopIndex < 0 || (stopDetailList.isNotEmpty() && currentStopIndex == stopDetailList.size)) {
            Log.e(
                tag,
                "invalid current stop index",
                throwable = null,
                TRIP_CURRENT_STOP_ID to currentStopIndex,
                "caller" to caller,
            )
            /**
            When the user is in formActivity and the stop is deleted
            The count of the stop list changes and if the old index is greater than the present
            the stopDetailActivity is finished
             */
            viewModelScope.launch(dispatcherProvider.io()) {
                _stopUpdateStatus.emit(INVALID_STOP_INDEX)
            }
            mutableScreenState.value = ScreenContentState.Error(getErrorString())
            return
        }

        viewModelScope.launch(CoroutineName(tag) + dispatcherProvider.default()) {
            when {
                stopDetailList.isNotEmpty() && currentStopIndex < stopDetailList.size -> {
                    setCurrentStopRelatedData(currentStopIndex)
                }
                else -> {
                    Log.w(
                        tag,
                        "Current stop not yet available. Stop Detail List ${stopDetailList.size}. StopIndex $currentStopIndex. Retrying"
                    )
                    delay(300)
                    setCurrentStopRelatedData(currentStopIndex)
                }
            }
        }
    }

    private suspend fun setCurrentStopRelatedData(
        currentStopIndex: Int
    ) {
        try {
            val sortedStopList = stopDetailList.getSortedStops()
            if (sortedStopList.isEmpty() || currentStopIndex > sortedStopList.size) {
                if (stopDataExceptionRetryCount > 0) {
                    stopDataExceptionRetryCount = stopDataExceptionRetryCount.minus(1)
                    throw IndexOutOfBoundsException(
                        "current stop not yet available in available stop list ${sortedStopList.size} currentStop $currentStopIndex"
                    )
                } else {
                    mutableScreenState.value = ScreenContentState.Error(getErrorString())
                    _isCurrentStopActionsEmptyOffline.postValue(
                        WorkflowApplication.getLastValueOfInternetCheck().not()
                    )
                    Log.e(
                        tag,
                        "Could not get current stop data even after 15 seconds",
                        throwable = null,
                        TRIP_CURRENT_STOP_ID to currentStopIndex,
                        TRIP_ID to appModuleCommunicator.getCurrentWorkFlowId("retry set current stop"),
                        "stops size" to stopDetailList.size.toString()
                    )
                    return
                }
            }

            withContext(dispatcherProvider.main()) {
                _currentStop.value = sortedStopList[currentStopIndex]
            }
            setStopsAvailableFlags(currentStopIndex, sortedStopList.size)
            setActionButtons(currentStop.value!!)
            actionMissOfflineCheckAndReporterJob?.cancel()
            actionMissOfflineCheckAndReporterJob = viewModelScope.launch {
                delay(OFFLINE_SYNC_TIMEOUT)
                isCurrentStopActionsEmptyOffline(currentStop.value)
            }
            updateStopUIData(currentStopIndex)
        } catch (e: Exception) {
            Log.d(
                tag,
                "Retrying setCurrentStopRelatedData",
                throwable = null,
                TRIP_ID to appModuleCommunicator.getCurrentWorkFlowId("retry set current stop"),
                "current stop index" to currentStopIndex,
                "Stop list size" to stopDetailList.size,
                "stack" to e.stackTraceToString()
            )
            actionMissOfflineCheckAndReporterJob?.cancel()
            delay(300)
            setCurrentStopRelatedData(currentStopIndex)
        }
    }

    private suspend fun updateStopUIData(
        currentStopIndex: Int
    ) {
        withContext(dispatcherProvider.main()) {
            _etaOrArrivedTime.value = getETAOrArrivedTimeForStop()
            _stopOfStop.value = ("STOP ${currentStopIndex.inc()} of ${stopDetailList.size}")
            setArriveButtonText(getArriveButtonText(currentStop = currentStop.value))
            setDepartButtonText(getDepartButtonText(currentStop = currentStop.value))
        }
    }

    private suspend fun getETAOrArrivedTimeForStop() = currentStop.value?.let { stopDetail ->
        if (stopDetail.completedTime.isNotEmpty()) {
            getFormattedCompletedTime(stopDetail.completedTime)
        } else {
            Utils.getRouteData(selectedStopId, dataStoreManager)?.let { routeData ->
                getFormattedDateTime(
                    routeData.etaTime,
                    ApplicationContextProvider.getApplicationContext()
                )
            } ?: getApplication<Application>().getString(R.string.not_available_short)
        }
    } ?: getApplication<Application>().getString(R.string.not_available_short)

    private fun sendManualActionResponse(
        action: Action, caller: String
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(CoroutineName(tag) + coroutineDispatcher.io())  {
            val stopActionEventData = StopActionEventData(
                action.stopid,
                action.actionType,
                getApplication(),
                hasDriverAcknowledgedArrivalOrManualArrival = true
            )
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                reasonType = StopActionReasonTypes.MANUAL.name,
                negativeGuf = false
            )
            val stopCompletionTime = dispatchStopsUseCase.handleStopEvents(
                action, stopActionEventData, caller = caller, pfmEventsInfo = pfmEventsInfo
            )

            updateCurrentStopWithDBData(action)

            currentStop.value?.run {
                if (completedTime.isEmpty() && action.stopid == stopid) {
                    completedTime = stopCompletionTime
                }
                setActionButtons(this)
                if (completedTime.isEmpty()) return@launch
            }

            // To handle stop with both approach and arrive action
            if (action.actionType != ActionTypes.APPROACHING.ordinal) {
                // Send approach event if stop has an approaching action
                sendApproachResponseIfNoArriveAction()
            }
            currentStop.value?.let { stopDetail ->
                dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                    caller = TRIP_EDIT + "HandleStopActionClick",
                    stop = stopDetail,
                    actionType = action.actionType,
                    stopActionReasonTypes = StopActionReasonTypes.MANUAL
                )
            }
            dispatchStopsUseCase.getLastSequentialCompletedStop(stopDetailList)?.let { lastCompletedStop ->
                Log.i(tag, "Last Sequential All Assigned Actions Completed StopId lastCompletedStopId ${lastCompletedStop.stopid}")
                val activeDispatchId = dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
                //Check for the next stop and trigger arrival if required
                val stopIdToCheckArrivalTrigger = lastCompletedStop.stopid+1
                if (stopDetailList.size > stopIdToCheckArrivalTrigger) {
                    val stopData = stopDetailList[stopIdToCheckArrivalTrigger]
                    tripStartUseCase.checkAndTriggerArrivalForStop(
                        activeDispatchId,
                        stopData
                    )
                }

            }
            navigateToNextStop(action)

        }
    }

    /**
     * This fun introduced to resolve the problem of _stopActionReadCompleteFlow.emit(firstAvailableAction.stopid) not getting updated properly during response sent true update
     * Until we resolve that issue, we are using this fun to update the stop detail screen with actions
     *
     */
    private suspend fun updateCurrentStopWithDBData(action: Action) {
        dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(
            "postActionUpdate", action.stopid
        )?.let {
            if (it.stopid == currentIndex && currentStop.value != it) {
                withContext(coroutineDispatcher.mainImmediate()) {
                    _currentStop.value = it
                }
            }
        }
    }

    private fun navigateToNextStop(action: Action) {
        currentStop.value?.let { currentStopDetail ->
            if (action.actionType == ActionTypes.DEPARTED.ordinal) getNextStop()
            else if ((action.actionType == ActionTypes.APPROACHING.ordinal || action.actionType == ActionTypes.ARRIVED.ordinal)
                && currentStopDetail.hasNoDepartedAction()) getNextStop()
        }
    }

    private fun getFormattedCompletedTime(completedTime: String): String {
        return Utils.getSystemLocalDateTimeFromUTCDateTime(completedTime).let {
            it.ifEmpty { getApplication<Application>().getString(R.string.not_available_short) }
        }
    }

    private fun handleApproach(stopDetail: StopDetail) {
        if (stopDetail.hasArrivingActionOnly()) {
            _displayArrived.postValue(true)
            _displayDeparted.postValue(false)
        }
    }

    private suspend fun setDepartureTimeOfStop(stopId: Int) {
        val stopList = fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("setDepartureTimeOfStop")
        stopList.firstOrNull { it.stopid == stopId }?.let {
            _currentStop.value?.departedTime = it.departedTime
        }
    }

    private fun handleArrive(
        stopDetail: StopDetail, action: Action, cid: String, vehicleNumber: String
    ) {
        _displayArrived.postValue(true)
        if (stopDetail.hasArrivedActionOnly() || stopDetail.hasArrivingAndArrivedActionOnly()) _displayDeparted.postValue(
            false
        )
        _enableDepart.postValue(action.responseSent)
        Log.i(
            tag,
            " CustomerId = $cid VehicleId = $vehicleNumber DispId = ${stopDetail.dispid}, StopId = ${stopDetail.stopid} --> ${_displayArrived.value} , ${_displayDeparted.value} , ${_enableDepart.value}"
        )
    }

    private fun handleDepart(stopDetail: StopDetail) {
        _displayDeparted.postValue(true)
        if (stopDetail.hasDepartActionOnly()) {
            _enableDepart.postValue(true)
            _displayArrived.postValue(false)
        }
    }

    private fun doIfActionsNotEmpty(stopDetail: StopDetail) {
        if (stopDetail.Actions.isNotEmpty()) {
            mutableScreenState.value = ScreenContentState.Success()
        } else {
            mutableScreenState.value = ScreenContentState.Error(getErrorString())
            Log.w(
                tag,
                "stop actions empty",
                throwable = null,
                "trip id" to stopDetail.dispid,
                "stop id" to stopDetail.stopid,
                "Stop list size" to stopDetailList.size
            )
        }
    }

    private fun doIfOnlyArriveAndDepartExist(stopDetail: StopDetail) {
        if (stopDetail.hasArrivingAndDepartActionOnly()) {
            _displayDeparted.postValue(true)
            _enableDepart.postValue(true)
        }
    }

    suspend fun setActionButtons(stopDetail: StopDetail) {
        selectedStopId = stopDetail.stopid
        val showNavigate = canShowNavigate(stopDetail)
        _displayNavigate.postValue(showNavigate)
        Log.i(
            tag,
            "Actions Size: ${stopDetail.Actions.size}",
            throwable = null,
            "trip" to stopDetail.dispid,
            "stop" to stopDetail.stopid
        )
        actionDisplayDebounceJob?.cancel()
        actionDisplayDebounceJob = viewModelScope.launch(dispatcherProvider.default()) {
            delay(DEBOUNCE_INTERVAL)
            stopDetail.Actions.forEach {
                Log.i(tag, stopDetail.logAction(it))
                when (it.actionType) {
                    ActionTypes.APPROACHING.ordinal -> handleApproach(stopDetail)
                    ActionTypes.ARRIVED.ordinal -> handleArrive(
                        stopDetail,
                        it,
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetTruckNumber()
                    )
                    ActionTypes.DEPARTED.ordinal -> handleDepart(stopDetail)
                }
            }
            doIfOnlyArriveAndDepartExist(stopDetail)
            //we are not relying on actions in StopDetail for inactive dispatch.
            if (dispatchActiveStateFlow.value == DispatchActiveState.ACTIVE || dispatchActiveStateFlow.value == DispatchActiveState.NO_TRIP_ACTIVE) {
                doIfActionsNotEmpty(stopDetail)
            } else {
                mutableScreenState.value = ScreenContentState.Success()
            }
        }
    }

    fun setCurrentStopIndexAndSelectedStopId(
        currentStopIndex: Int,
        selectedStopId: Int
    ) {
        setCurrentStopIndex(currentStopIndex)
        setSelectedStopId(selectedStopId)
    }

    private fun setCurrentStopIndex(currentStopIndex: Int) {
        this.currentIndex = currentStopIndex
    }

    private fun setSelectedStopId(selectedStopId: Int) {
        this.selectedStopId = selectedStopId
    }

    private fun isCurrentStopActionsEmptyOffline(stopDetail: StopDetail?) {
        stopDetail?.let {
            _isCurrentStopActionsEmptyOffline.postValue(
                it.Actions.isEmpty() && WorkflowApplication.getLastValueOfInternetCheck().not()
            )
        }
    }

    fun setStopsAvailableFlags(index: Int, totalStops: Int) {
        _previousStopAvailable.postValue(index > 0)
        _nextStopAvailable.postValue(index < totalStops - 1)
    }

    fun getNextStop() {
        nextAndPreviousStopButtonDebounceJob?.cancel()
        nextAndPreviousStopButtonDebounceJob =
            viewModelScope.launch(CoroutineName(tag) + dispatcherProvider.default()) {
                if (stopDetailList.size > currentIndex.plus(1)) {
                    Log.logUiInteractionInInfoLevel(tag, "$tag Clicked on next stop button")
                    mutableScreenState.value = ScreenContentState.Loading()
                    delay(DEBOUNCE_INTERVAL)
                    resetButtonVisibilityFlags()
                    currentIndex = currentIndex.inc()
                    setCurrentStop(currentIndex, "nextStop")
                }
            }
    }

    fun getPreviousStop() {
        nextAndPreviousStopButtonDebounceJob?.cancel()
        nextAndPreviousStopButtonDebounceJob =
            viewModelScope.launch(CoroutineName(tag) + dispatcherProvider.default()) {
                Log.logUiInteractionInInfoLevel(tag, "$tag Clicked on previous stop button")
                mutableScreenState.value = ScreenContentState.Loading()
                delay(DEBOUNCE_INTERVAL)
                resetButtonVisibilityFlags()
                currentIndex = currentIndex.dec()
                setCurrentStop(currentIndex, "previousStop")
            }
    }

    fun getPreviousStopForXMLBinding() {
        getPreviousStop()
    }

    private suspend fun notifyLiveDataChanged(
        data: String,
        liveData: MutableLiveData<String>
    ) {
        withContext(dispatcherProvider.main()) {
            liveData.value = data.ifEmpty {
                getApplication<Application>().getString(
                    R.string.not_available_short
                )
            }
        }
    }

    fun registerDataStoreListener() {
        dataStoreListenerJob?.cancel()
        dataStoreListenerJob = CoroutineScope(
            dispatcherProvider.default() + SupervisorJob()
        ).launch(CoroutineName(tag)) {
            WorkflowEventBus.stopCountListenerEvents.onEach { stopEditType ->
                when (stopEditType) {
                    ADDED, REMOVED -> {
                        _stopUpdateStatus.emit(stopEditType.ifEmpty {
                            applicationInstance.getString(
                                R.string.not_available_short
                            )
                        })
                        Log.d(
                            tag,
                            "----Inside StopDetailViewModel registerDataStoreListener invoking ActiveDispatchStopManipulationListener: stop edit type: $stopEditType "
                        )
                    }
                }
            }.launchIn(this)

            dataStoreManager.fieldObserver(TRAILER_IDS_KEY).onEach {
                it?.let {
                    notifyLiveDataChanged(it, _trailerIds)
                }
            }.launchIn(this)

            dataStoreManager.fieldObserver(SHIPMENTS_IDS_KEY).onEach {
                it?.let {
                    notifyLiveDataChanged(it, _shipmentIds)
                }
            }.launchIn(this)
        }
    }

    internal fun updateStopDetailScreenWithActions() {
        setCurrentStop(
            currentIndex,
            "dataStoreTrigger"
        )
    }

    private fun getErrorString() =
        getApplication<Application>().applicationContext.getString(R.string.error_stop_detail)

    fun unRegisterDataStoreListener() = dataStoreListenerJob?.cancel()

    fun setArriveButtonText(value: String) {
        _arriveButtonText.value = value
    }

    private fun setDepartButtonText(value: String){
        _departButtonText.value = value
    }

    fun getArriveButtonText(currentStop: StopDetail?): String = currentStop?.let { stopDetail ->
        val cachedActionsStatus: Pair<Boolean, Boolean> =
            cacheCompletedActions[currentStop.stopid] ?: Pair(first = false, second = false)
        return@let if (stopDetail.hasArrivingActionOnly()) getApproachActionStatus(
            stopDetail, cachedActionsStatus
        ) else getArrivedActionStatus(stopDetail, cachedActionsStatus)
    } ?: EMPTY_STRING

    internal fun getApproachActionStatus(
        currentStop: StopDetail, cachedActionsStatus: Pair<Boolean, Boolean>
    ): String = if (currentStop.Actions.find {
            it.actionType == ActionTypes.APPROACHING.ordinal
        }?.responseSent == true) {
        cacheCompletedActions[currentStop.stopid] =
            Pair(first = true, second = cachedActionsStatus.second)
        getApplication<Application>().getString(R.string.arrive_complete)
    } else {
        if (cachedActionsStatus.first) getApplication<Application>().getString(R.string.arrive_complete)
        else getApplication<Application>().getString(R.string.arrive)
    }

    internal fun getArrivedActionStatus(
        currentStop: StopDetail, cachedActionsStatus: Pair<Boolean, Boolean>
    ): String {
        if (currentStop.Actions.find {
                it.actionType == ActionTypes.ARRIVED.ordinal
            }?.responseSent == true) {
            cacheCompletedActions[currentStop.stopid] =
                Pair(first = true, second = cachedActionsStatus.second)
            return getApplication<Application>().getString(R.string.arrive_complete)
        } else {
            if (currentStop.Actions.find {
                    it.actionType == ActionTypes.APPROACHING.ordinal
                }?.responseSent == true) {
                cacheCompletedActions[currentStop.stopid] =
                    Pair(first = false, second = cachedActionsStatus.second)
                return getApplication<Application>().getString(R.string.arrive)
            }
            return if (cachedActionsStatus.first) getApplication<Application>().getString(R.string.arrive_complete)
            else getApplication<Application>().getString(R.string.arrive)
        }
    }

    fun getDepartButtonText(currentStop: StopDetail?): String {
        return currentStop?.Actions?.find { it.actionType == ActionTypes.DEPARTED.ordinal }?.let {
            val cachedActionsStatus: Pair<Boolean, Boolean> =
                cacheCompletedActions[currentStop.stopid] ?: Pair(first = false, second = false)
            if (it.responseSent) {
                cacheCompletedActions[currentStop.stopid] =
                    Pair(first = cachedActionsStatus.first, second = true)
                getApplication<Application>().getString(R.string.depart_complete)
            } else {
                if (cachedActionsStatus.second) {
                    getApplication<Application>().getString(R.string.depart_complete)
                } else {
                    getApplication<Application>().getString(R.string.depart)
                }
            }
        } ?: EMPTY_STRING
    }

    private suspend fun sendApproachResponseIfNoArriveAction() {
        currentStop.value?.let { curStopDetail ->
            /** approaching occurred = StopActionReasonTypes.NORMAL.name, no guf, cross in Event
             * There is no way to force a manual approach - if the arrival occurs before the approaching event,
             * the approaching event is assumed to occur with the arrival event
             */
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                reasonType = StopActionReasonTypes.NORMAL.name,
                negativeGuf = false
            )
            dispatchStopsUseCase.sendApproachAction(
                curStopDetail,
                pfmEventsInfo
            )
        }
    }

    fun updateArrivedButtonText() {
        if (currentStop.value != null) {
            _currentStop.value = currentStop.value
        }
    }

    private fun sendRemoveGeofenceEvent(action: Action){
        sendDispatchDataUseCase.sendRemoveGeoFenceEvent(action)
    }

    //This method will be called when the user manually arrives at a stop.
    //This method will update the stop detail with the manual arrival details(location at which user manually arrived) and send the updated stop detail to the maps.
    //This arrival location will be used to create the departure geofence for the stop if that stop is manually arrived outside of arrival geofence.
    internal fun manipulateDepartureGeofenceOnManualArrival(){
        viewModelScope.launch {
            currentStop.value?.let { stopDetail ->
                // set geofence and update stopDetail only for first time Arrive button is Clicked, and not everytime Arrive-Complete is Clicked, as part of MAPP-11926
                if(stopDetail.completedTime.isEmpty()) {
                    Log.d("$MANUAL_ARRIVAL_STOP$VIEW_MODEL", "manipulateDepartureGeofenceOnArrival() called for stop: ${stopDetail.stopid} ")
                    backboneUseCase.getCurrentLocation().let { location ->
                        stopDetail.isManualArrival = true
                        stopDetail.arrivalLatitude = location.first
                        stopDetail.arrivalLongitude = location.second
                    }
                    val stopDetailToUpdate = HashMap<String, Any>().also {
                        it["isManualArrival"] = stopDetail.isManualArrival
                        it["arrivalLatitude"] = stopDetail.arrivalLatitude
                        it["arrivalLongitude"] = stopDetail.arrivalLongitude
                    }
                    withContext(dispatcherProvider.io()){
                        dispatchStopsUseCase.updateStopDetail(stopDetail, stopDetailToUpdate)
                    }
                    val uncompletedAndUnDeletedStops = stopDetailList.getNonDeletedAndUncompletedStopsBasedOnActions()
                    Log.d("$MANUAL_ARRIVAL_STOP$VIEW_MODEL", "sending uncompleted stops based on actions - stoplist after manual arrival to maps: ${uncompletedAndUnDeletedStops.map { it.stopid }}")
                    sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(uncompletedAndUnDeletedStops, dispatchActiveStateFlow.value, "StopDetail - Manual Arrived")
                } else {
                    Log.d("$MANUAL_ARRIVAL_STOP$VIEW_MODEL","Arrive Complete Clicked - Skipping Setting Geofence for ${stopDetail.stopid} since stop is already arrived at ${stopDetail.completedTime}")
                }
           }
        }
    }

    fun listenForStopAdditionAndRemoval() {
        stopManipulationListenerJob?.cancel()
        stopManipulationListenerJob = viewModelScope.launch(dispatcherProvider.io()) {
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
                        stopUpdateStatus,
                        lastUpdatedActiveStops,
                        lastUpdatedInactiveStops
                    )
                    if (stopManipulationResult.third.not()) return@stopManipulationListenerLaunch
                    finishActivityIfThereIsNoActiveStops()
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
                            _shouldFinishActivityLiveData.postValue(Unit)
                        }
                    }

                    Log.i(
                        tag,
                        "lastUpdatedActiveStops: $lastUpdatedActiveStops lastUpdatedInactiveStops: $lastUpdatedInactiveStops " +
                                "removedStopCount $removedStopCount addedStopCount: $addedStopCount removedOrAddedStopsCount: $removedOrAddedStopsCount"
                    )

                    withContext(dispatcherProvider.main()) {
                        stopCountChangeTimer?.cancel()
                        stopCountChangeTimer = object : CountDownTimer(
                            STOP_COUNT_CHANGE_LISTEN_DELAY,
                            STOP_COUNT_CHANGE_LISTEN_DELAY
                        ) {
                            override fun onTick(millisUntilFinished: Long) {
                                //Ignore
                            }
                            override fun onFinish() {
                                viewModelScope.launch(dispatcherProvider.io()) {
                                    withContext(dispatcherProvider.main()) {
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
                                    // collect should execute and finish. That's why its a blocking call here
                                    isTripCompleted().filter { it.second }.collect {
                                        _shouldFinishActivityLiveData.postValue(Unit)
                                    }
                                }
                            }
                        }
                        stopCountChangeTimer?.start()
                    }
                }
            }
        }
    }

    private fun finishActivityIfThereIsNoActiveStops() {
        stopDetailList.distinct().size.let { stopCount ->
            if (stopCount == 0) _shouldFinishActivityLiveData.postValue(Unit)
        }
    }

    fun shouldDisplayPrePlannedArrival() =  appModuleCommunicator.getFeatureFlags()
        .containsKey(FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_PREPLANNED_ARRIVAL) && appModuleCommunicator.getFeatureFlags()
        .isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_PREPLANNED_ARRIVAL)

    internal fun shouldDisplayAddress() =
        appModuleCommunicator.getFeatureFlags().isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_ADDRESS)

    internal fun determineAndUpdateAddressVisibility(){
        if (shouldDisplayAddress() && stopActionsAllowed.value.isNotNull() && stopActionsAllowed.value!!) {
            setAddressDisplayedLiveDataValue(true)
        }else{
            setAddressDisplayedLiveDataValue(false)
        }
    }

    //To avoid multiple clicks to prevent opening 2 or more forms
    private fun canEnableNextAndPreviousButton(action: Action) = action.driverFormid <= 0

    private fun saveFormPathToUncompletedDispatchFormStack(
        path: String,
        dispatchFormPath: DispatchFormPath,
        actionId: String,
        isActionResponseSentToServer: Boolean
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(CoroutineName(tag) + dispatcherProvider.io() + SupervisorJob()) {
            val uiFormResponse: UIFormResponse = formViewModel.getFormData(path, actionId, isActionResponseSentToServer)
            uiFormResponse.isSyncDataToQueue.let { isFormSaved ->
                if (isFormSaved.not() && (currentStop.value?.departedTime?.isEmpty() == true)) {
                    dataStoreManager.getValue(DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING).let { formStack ->
                        UncompletedFormsUseCase.addFormToPreference(
                            formStack,
                            dispatchFormPath
                        ).let { formListJson ->
                            if (formListJson.isNotEmpty()) dataStoreManager.setValue(
                                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, formListJson)
                            Log.i(tag, "FORM_STACK_KEY -> StopDetailActivity $formListJson")
                        }
                    }
                }
            }
        }
    }

    private fun sendActionResponseIfNotAlreadySent(action: Action) {
        if (action.responseSent) {
            navigateToNextStop(action)
            return
        }
        /** arrival occurred OR depart occurred = StopActionReasonTypes.MANUAL.name, driver manual Events
         * Arrived the stop by tapping "Arrive" button on stop detail screen
         * OR Manual depart button click in stop detail screen
         */
        sendManualActionResponse(action, (action.actionType).getWorkflowEventName().name + "ManualComplete")
    }

    private fun handleStopActionManualClick(
        action: Action,
        disableButtonsToPreventDoubleClick: () -> Unit,
        startFormActivity: (String, DispatchFormPath, Boolean) -> Unit,
        checkForTripCompletion: () -> Unit
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            val isActionResponseSent = action.responseSent
            if (canEnableNextAndPreviousButton(action).not()) {
                withContext(dispatcherProvider.main()) { disableButtonsToPreventDoubleClick() }
            }
            if(action.actionType == ActionTypes.ARRIVED.ordinal && !isActionResponseSent) {
                    val arrivalReasonHashMap = arrivalReasonUsecase.getArrivalReasonMap(
                        ArrivalType.MANUAL_ARRIVAL.toString(),
                        action.stopid,
                        true
                    )
                    arrivalReasonUsecase.setArrivalReasonForCurrentStop(
                        action.stopid,
                        arrivalReasonHashMap
                    )
            }

            sendRemoveGeofenceEvent(action)
            if (action.driverFormid >= 0 && action.actionType == ActionTypes.ARRIVED.ordinal) {
                val cid = appModuleCommunicator.doGetCid()
                val truckNumber = appModuleCommunicator.doGetTruckNumber()
                if (cid.isEmpty() || truckNumber.isEmpty()) {
                    Log.e(tag, "Invalid cid or truck number. cid: $cid truck number: $truckNumber")
                    return@launch
                }
                //saves the uncompleted form to SharedPref on Arrived button press
                val dispatchFormPath = DispatchFormPath(
                    stopName = currentStop.value?.name ?: EMPTY_STRING,
                    stopId = action.stopid,
                    actionId = action.actionid,
                    formId = action.driverFormid,
                    formClass = action.driverFormClass,
                    dispatchName = dataStoreManager.getValue(CURRENT_DISPATCH_NAME_KEY, EMPTY_STRING)
                )
                val path =
                    INBOX_FORM_RESPONSE_COLLECTION + "/" + cid + "/" + truckNumber + "/" + action.dispid + "/" + action.stopid + "/" + action.actionid
                saveFormPathToUncompletedDispatchFormStack(
                    path,
                    dispatchFormPath,
                    action.actionid.toString(),
                    isActionResponseSent
                )
                startFormActivity(path, dispatchFormPath, isActionResponseSent)
            }
            sendActionResponseIfNotAlreadySent(action)
            checkForTripCompletion()

        }
    }


    internal fun processManualApproachOrArrival(
        disableButtonsToPreventDoubleClick: () -> Unit,
        startFormActivity: (String, DispatchFormPath, Boolean) -> Unit,
        checkForTripCompletion: () -> Unit
    ) {
        currentStop.value?.let { currentStopDetail ->
            if (stopDetentionWarningUseCase.canDisplayDetentionWarning(currentStopDetail)) {
                stopDetentionWarningUseCase.startDetentionWarningTimer(
                    currentStopDetail,
                    "setupArrivedClickListener"
                )
            }

            handleStopActionManualClick(
                action = if (currentStopDetail.hasArrivingActionOnly()) {
                    currentStopDetail.Actions[0]
                } else {
                    currentStopDetail.Actions.find { action -> action.actionType == ActionTypes.ARRIVED.ordinal }
                        ?: run {
                            Log.e(
                                tag,
                                "Arrive action not found. StopId: ${currentStopDetail.stopid} ActionIds: ${currentStopDetail.Actions.map { it.actionid }}"
                            )
                            Action()
                        }
                },
                disableButtonsToPreventDoubleClick = {
                    disableButtonsToPreventDoubleClick()
                },
                startFormActivity = { path, dispatchFormPath, isActionResponseSent ->
                    startFormActivity(path, dispatchFormPath, isActionResponseSent)
                },
                checkForTripCompletion = {
                    checkForTripCompletion()
                }
            )
        }
    }

    internal fun processManualDeparture(
        disableButtonsToPreventDoubleClick: () -> Unit,
        startFormActivity: (String, DispatchFormPath, Boolean) -> Unit,
        checkForTripCompletion: () -> Unit,
        showDepartureTime: () -> Unit
    ) {
        stopDetentionWarningUseCase.cancelDetentionWarningTimer()
        currentStop.value?.Actions?.find { action -> action.actionType == ActionTypes.DEPARTED.ordinal }
            ?.let { departedAction ->
                viewModelScope.launch(coroutineDispatcher.main()){
                    setDepartureTimeOfStop(currentStop.value?.stopid!!)
                }
                handleStopActionManualClick(
                    action = departedAction,
                    disableButtonsToPreventDoubleClick = {
                        disableButtonsToPreventDoubleClick()
                    },
                    startFormActivity = { path, dispatchFormPath, isActionResponseSent ->
                        startFormActivity(path, dispatchFormPath, isActionResponseSent)
                    },
                    checkForTripCompletion = {
                        checkForTripCompletion()
                    }
                )
                showDepartureTime()
            }
    }

    fun updateUIElements() {
        viewModelScope.launch(coroutineDispatcher.mainImmediate()) {
            updateStopUIData(currentIndex)
        }
    }

    private fun resetCurrentStopIndex() {
        currentIndex = 0
    }

    fun restoreSelectedDispatchIdOnReInitialize(functionToExecute : () -> Unit) {
        viewModelScope.launch(dispatcherProvider.io()) {
            Log.d(tag+ TRIP_PREVIEWING,"restoreSelectedDispatchIdOnReInitialize() invoked in StopDetailViewModel")
            dispatchStopsUseCase.restoreSelectedDispatch()
            resetCurrentStopIndex()
            initializeDispatchBaseViewModel()
            initializeStopDetailViewModel()
            withContext(dispatcherProvider.main()){
                functionToExecute()
            }
        }
    }
}