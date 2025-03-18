package com.trimble.ttm.routemanifest.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.launchercommunicationlib.commons.EVENT_TYPE_KEY
import com.trimble.ttm.commons.logger.*
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ARE_STOPS_SEQUENCED_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.CURRENT_DISPATCH_NAME_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.DISPATCH_NAME_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.IS_DYA_ALERT_ACTIVE
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.NAVIGATION_ELIGIBLE_STOP_LIST_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.SELECTED_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.STOPS_SERVICE_REFERENCE_KEY
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DO_ON_ARRIVAL_CALLER
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.SPACE
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.commons.utils.ext.isNotNull
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.NO_REPLY_ACTION
import com.trimble.ttm.formlibrary.utils.REPLY_WITH_NEW
import com.trimble.ttm.formlibrary.utils.REPLY_WITH_SAME
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.toSafeInt
import com.trimble.ttm.routemanifest.BuildConfig
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.routemanifest.model.*
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
import com.trimble.ttm.routemanifest.utils.ADDED
import com.trimble.ttm.routemanifest.utils.DEFAULT_ARRIVED_RADIUS_IN_FEET
import com.trimble.ttm.routemanifest.utils.DRIVING_SCREEN_TYPE
import com.trimble.ttm.routemanifest.utils.DRIVING_SUB_SCREEN_NAVIGATION_OPTION_TYPE
import com.trimble.ttm.routemanifest.utils.DRIVING_SUB_SCREEN_TYPE_KEY
import com.trimble.ttm.routemanifest.utils.EVENTS_PROCESSING_FOREGROUND_SERVICE_INTENT_ACTION
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import com.trimble.ttm.routemanifest.utils.LAUNCH_SCREEN_INTENT
import com.trimble.ttm.routemanifest.utils.LISTEN_GEOFENCE_EVENT
import com.trimble.ttm.routemanifest.utils.REMOVED
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_MAX_RETRY_COUNT
import com.trimble.ttm.routemanifest.utils.SCREEN_TYPE_KEY
import com.trimble.ttm.routemanifest.utils.SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP
import com.trimble.ttm.routemanifest.utils.TRUE
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.WORKFLOW_SERVICE_INTENT_ACTION_KEY
import com.trimble.ttm.routemanifest.utils.ext.getAppLauncherFullPackageName
import com.trimble.ttm.routemanifest.utils.ext.isGreaterThan
import com.trimble.ttm.routemanifest.utils.ext.isLessThan
import com.trimble.ttm.routemanifest.utils.ext.makeLongLivingToast
import com.trimble.ttm.routemanifest.utils.ext.startDispatchFormActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

const val PAYLOAD = "Payload"
const val STOPS = "Stops"
const val ACTIONS = "Actions"
const val OFFLINE_SYNC_TIMEOUT = 3000L // 3 seconds

abstract class DispatchBaseViewModel(
    private val application: Application,
    private val routeETACalculationUseCase: RouteETACalculationUseCase,
    private val tripCompletionUseCase: TripCompletionUseCase,
    private val dataStoreManager: DataStoreManager,
    private val formDataStoreManager: FormDataStoreManager,
    val dispatchBaseUseCase: DispatchBaseUseCase,
    internal val dispatchStopsUseCase: DispatchStopsUseCase,
    internal val sendDispatchDataUseCase: SendDispatchDataUseCase,
    private val tripPanelUseCase: TripPanelUseCase,
    val dispatchValidationUseCase: DispatchValidationUseCase,
    val coroutineDispatcher: DispatcherProvider,
    val stopDetentionWarningUseCase: StopDetentionWarningUseCase,
    val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
) : AndroidViewModel(application), KoinComponent {

    private val logTripId = "tripId"
    private val tag = "DispatchBaseVM"

    private val backboneUseCase: BackboneUseCase by inject()
    private val arrivalReasonUsecase: ArrivalReasonUsecase by inject()
    protected val _currentStop = MutableLiveData<StopDetail>()
    val currentStop: LiveData<StopDetail> = _currentStop
    private val _eTAForStop = MutableLiveData<String>()
    val eTAForStop: LiveData<String> = _eTAForStop
    private var _onStopCompleted = MutableLiveData<Boolean>()
    val onStopCompleted: LiveData<Boolean> = _onStopCompleted
    private var _onTripEnd = MutableLiveData<Boolean>()
    val onTripEnd: LiveData<Boolean> = _onTripEnd
    protected val _rearrangedStops = MutableLiveData<List<StopDetail>>()
    internal val stopDetailList = CopyOnWriteArrayList<StopDetail>()
    private val stopActionsListenerJobIndex = mutableMapOf<Int, Job>()

    private lateinit var selectedDispatchId: String
    private lateinit var selectedDispatchName: String
    private var firstRead = true

    //map to keep track of reading all actions for all stops.
    private var stopActionsReadStatusMap = HashMap<Int, Boolean>()
    var isStopsReadFirstTime = false
    var isStopsGeofenceDataSentToMaps = false

    internal var routeCalculationRetryCount = ROUTE_CALCULATION_MAX_RETRY_COUNT
    internal val appModuleCommunicator = dispatchStopsUseCase.appModuleCommunicator
    private val _stopActionReadCompleteFlow = MutableSharedFlow<Int>()
    val stopActionReadCompleteFlow : SharedFlow<Int> = _stopActionReadCompleteFlow.asSharedFlow()
    private var driverFormId = 0
    /**
     * Lock to prevent sending did you arrive messages to trip panel and showing did you arrive messages inside the route manifest
     * while navigating to form activity.
     */
    internal var isNavigatingToFormActivity = false
    private var didYouArriveAlertTimerCollectJob : Job? = null

    // Uses a data store listener to stay up-to-date with whether the current trip is active.
    private val _dispatchActiveStateFlow = MutableStateFlow(DispatchActiveState.NO_TRIP_ACTIVE)
    val dispatchActiveStateFlow: StateFlow<DispatchActiveState> = _dispatchActiveStateFlow

    // Tracks whether the action buttons (ARRIVE, NAVIGATE, etc.) should be enabled based on if
    // the current trip is active.
    private val _stopActionsAllowed = MutableLiveData(false)
    val stopActionsAllowed: LiveData<Boolean> = _stopActionsAllowed

    private val _addressDisplayed = MutableLiveData(false)
    val addressDisplayed: LiveData<Boolean> = _addressDisplayed

    private val _tripStartAction = MutableLiveData(false)
    val tripStartAction: LiveData<Boolean> = _tripStartAction

    companion object {
        private var lastUpdatedStopListCount = 0
    }

    init {
        initializeDispatchBaseViewModel()
    }

    fun initializeDispatchBaseViewModel(){
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.default()) {
            resetValues()
            dataStoreManager.fieldObserver(ACTIVE_DISPATCH_KEY).collectLatest { activeDispatchId ->
                val selectedDispatchId = getSelectedDispatchId(TRIP_ACTIVE_CHECK)
                val newState = dispatchBaseUseCase.getDispatchActiveState(activeDispatchId, selectedDispatchId)
                setActiveDispatchFlowValue(newState)
                _stopActionsAllowed.postValue(newState == DispatchActiveState.ACTIVE)
                Log.d(tag+ TRIP_PREVIEWING, "DBVM Active Dispatch Id: $activeDispatchId, Selected Dispatch Id:$selectedDispatchId Dispatch Active State: $newState")
            }
        }
    }

    private fun resetValues(){
        stopDetailList.clear()
        resetSelectedDispatchId()
        resetSelectedDispatchName()
        firstRead = true
        stopActionsReadStatusMap.clear()
        isStopsReadFirstTime = false
        isStopsGeofenceDataSentToMaps = false
        routeCalculationRetryCount = ROUTE_CALCULATION_MAX_RETRY_COUNT
        driverFormId = 0
        isNavigatingToFormActivity = false
    }

    internal fun reloadUIIfRequired(dispatchIdToRender : String, currentRenderedDispatchId : String, reloadFunction : () -> Unit) {
        if (dispatchIdToRender.isNotNull() && dispatchIdToRender.isNotEmpty() && currentRenderedDispatchId.isNotNull() && currentRenderedDispatchId.isNotEmpty() && currentRenderedDispatchId != dispatchIdToRender){
            reloadFunction()
        }
    }

    internal fun setActiveDispatchFlowValue(newState: DispatchActiveState) {
        _dispatchActiveStateFlow.value = newState
    }

    internal fun setAddressDisplayedLiveDataValue(newState: Boolean){
        _addressDisplayed.value = newState
    }

    internal fun setTripStartActionLiveDataValue(newState: Boolean){
        _tripStartAction.value = newState
    }

    suspend fun isComposeFormFeatureFlagEnabled(): Boolean = withContext(coroutineDispatcher.io()) {
        dispatchStopsUseCase.isComposeFormFeatureFlagEnabled(appModuleCommunicator.doGetCid())
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal fun getVIDFromDispatchData(dispatchId: String) {
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            if(dispatchId.isNotEmpty()){
                Log.i(tag, "Step 1: Getting VID of dispatchId: $dispatchId")
                dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(
                    dispatchId, dataStoreManager, tag
                )
            } else {
                Log.d(tag,"DispatchId is empty, fetching and storing VID could not be done")
            }
        }
    }

    internal suspend fun getSelectedDispatchId(tag: String): String {
        if (::selectedDispatchId.isInitialized && selectedDispatchId.isEmpty().not()) {
            selectedDispatchId
        } else {
            selectedDispatchId = dataStoreManager.getValue(SELECTED_DISPATCH_KEY, EMPTY_STRING)
            selectedDispatchId
        }
        return selectedDispatchId.ifEmpty {
            Log.e(
                tag, "Returning empty dispatch id.caller should handle"
            )
            EMPTY_STRING
        }
    }

    internal fun getSelectedDispatchName(dispatchName: (String) -> Unit) {
        viewModelScope.launch(coroutineDispatcher.io()) {
            if (::selectedDispatchName.isInitialized && selectedDispatchName.isEmpty().not()) {
                selectedDispatchName
            } else {
                selectedDispatchName = dataStoreManager.getValue(DISPATCH_NAME_KEY, EMPTY_STRING)
                selectedDispatchName
            }
            dispatchName(selectedDispatchName.uppercase())
        }
    }

    internal fun resetSelectedDispatchName(){
        selectedDispatchName = EMPTY_STRING
    }

    internal fun resetSelectedDispatchId(){
        selectedDispatchId = EMPTY_STRING
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal fun getStopsForDispatch(
        dispatchId: String
    ) {
        viewModelScope.launch(coroutineDispatcher.io() + CoroutineName("$tag Get Stops for Dispatch")) {
            val cidAndVehicleId = dispatchBaseUseCase.getCidAndTruckNumber(tag)
            if (cidAndVehicleId.third && dispatchId.isNotEmpty()) {
                Log.i(
                    tag,
                    "Step 2: Getting stops of dispatchId: $dispatchId"
                )
                observeForStopsOfActiveDispatch(
                    customerId = cidAndVehicleId.first,
                    vehicleId = cidAndVehicleId.second,
                    dispatchId
                )
            }

        }
    }

    private suspend fun observeForStopsOfActiveDispatch(
        customerId: String,
        vehicleId: String,
        dispatchId: String
    ) = coroutineScope {
        tripCompletionUseCase.getStopsForDispatch(
            vehicleId,
            customerId,
            dispatchId
        ).catch { throwable ->
            Log.e(
                tag,
                "Exception at observeForStopsOfActiveDispatch $dispatchId",
                throwable = null,
                "stack" to throwable.stackTraceToString()
            )
        }.onEach { stops ->
            Log.i(tag, "observeForStopsOfActiveDispatch's getStopsForDispatch onEach invoked ${stops.map { it.stopid }}")
            if (stops.isEmpty()) {
                onStopsEmpty()
                return@onEach
            }
            stops.forEach {
                try {
                    if (it.deleted == 0) {
                        handleStopAdditionOrUpdate(
                            it,
                            stopDetailList,
                            firstRead,
                            lastUpdatedStopListCount
                        )
                    } else handleStopRemoval(
                        it,
                        stops,
                        stopDetailList,
                        firstRead,
                        lastUpdatedStopListCount
                    )
                    dispatchBaseUseCase.checkAndMarkStopCompletion(formDataStoreManager, it)
                } catch (e: Exception) {
                    Log.e(tag, "Exception in getStopsForDispatch ${e.message}", e)
                    Log.e(
                        tag,
                        "Exception in getStopsForDispatch",
                        throwable = null,
                        "stack" to e.stackTraceToString()
                    )
                }
            }
            isStopsReadFirstTime = true //read all the stops from firestore. The stop list object will be updated in the above loop
            setStopsEligibilityForFirstTime(stopDetailList, dispatchStopsUseCase)
            postStopsProcessing("ObservingStops")
            postStopActions()
        }.launchIn(this)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
     suspend fun handleStopAdditionOrUpdate(
        stopDetail: StopDetail, stops: CopyOnWriteArrayList<StopDetail>, firstRead: Boolean,
        lastUpdatedStopListCount: Int
    ) = coroutineScope {
        dispatchBaseUseCase.processStopAdditionOrUpdate(stopDetail, stops)
        //Update ARE_STOPS_SEQUENCED_KEY value based on the sequenced value of stops. Previously we have handled only if condition and it caused issues while previewing Free float trip.
        //Do not update ARE_STOPS_SEQUENCED_KEY for inactive/Preview trips as it may cause issues in trip Panel messages.
        if(Utils.isIncomingDispatchSameAsActiveDispatch(incomingDispatchId = stopDetail.dispid, activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("handleStopAdditionOrUpdate"))){
            Log.d(tag, "Updating ARE_STOPS_SEQUENCED_KEY")
            dispatchStopsUseCase.updateSequencedKeyInDataStore(stops, stopDetail)
        }

        if (lastUpdatedStopListCount.isLessThan(stops.size)) {
            DispatchBaseViewModel.lastUpdatedStopListCount = stops.size
            if (firstRead.not()) {
                with(WorkflowEventBus) {
                    postStopCountListener(ADDED)
                    markActiveDispatchStopAsManipulated("handleStopAdditionOrUpdate", ADDED)
                }
                Log.i(TRIP_EDIT, "stop added - StopId: ${stopDetail.stopid} DispId: ${stopDetail.dispid}")
            }
        }
        if (firstRead.not() && stopDetail.completedTime.isNotEmpty()) {
            Log.i(
                tag,
                "Form Count Of Stop ${stopDetail.stopid} = " + formDataStoreManager.getValue(
                    intPreferencesKey(name = FORM_COUNT_FOR_STOP + stopDetail.stopid),
                    ZERO
                ),
                throwable = null,
                "dispatch id" to stopDetail.dispid
            )
            completeStop()
        }

    }

    private suspend fun markActiveDispatchStopAsManipulated(caller: String, event: String) {
        val isStopUpdatedForTheActiveTrip = isActiveDispatch(caller)
        if (isStopUpdatedForTheActiveTrip) {
            Log.d(TRIP_EDIT, "trip stop $event")
            dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
        }
    }

    private suspend fun isActiveDispatch(caller: String): Boolean {
        val selectedDispatchId = getSelectedDispatchId(caller)
        val currentDispatchId = appModuleCommunicator.getCurrentWorkFlowId(caller)
        return selectedDispatchId == currentDispatchId
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun handleStopRemoval(
        stopDetail: StopDetail,
        stops: Set<StopDetail>,
        stopsList: CopyOnWriteArrayList<StopDetail>,
        firstRead: Boolean,
        lastUpdatedStopListCount: Int
    ) {
        dispatchBaseUseCase.processStopRemoval(stopDetail, stopsList)
        if (lastUpdatedStopListCount.isGreaterThan(stopsList.size)) {
            DispatchBaseViewModel.lastUpdatedStopListCount = stopsList.size
            if (firstRead.not()) {
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetail.stopid}").let { key ->
                    if (formDataStoreManager.containsKey(key)) {
                        formDataStoreManager.removeItem(
                            key
                        )
                        Log.d(
                            tag,
                            "Removing stop form count",
                            throwable = null,
                            logTripId to stopDetail.dispid,
                            "key" to key
                        )
                    }
                }
                if(stopActionsReadStatusMap.containsKey(stopDetail.stopid)){
                    stopActionsReadStatusMap.remove(stopDetail.stopid)
                }
                with(WorkflowEventBus) {
                    postStopCountListener(REMOVED)
                    markActiveDispatchStopAsManipulated("handleStopRemoval", REMOVED)
                }
                Log.i(TRIP_EDIT, "stop removed - StopId: ${stopDetail.stopid} DispId: ${stopDetail.dispid}")
            }
        }
        if (firstRead.not()) {
            postStopsProcessing("handleStopRemoval")
            postStopActions()
        }
        if (firstRead && stops.size == stops.indexOf(stopDetail) + 1 && stopsList.isEmpty()) {
            postStopsProcessing("handleStopRemovalFirstRead")
        }
        completeStop()
    }

    fun isManualTripCompletionDisabled(): Flow<Boolean> = flow {
        val cidAndVehicleId = dispatchBaseUseCase.getCidAndTruckNumber(tag)
        if (cidAndVehicleId.third.not()) {
            Log.d(tag,"isManualTripCompletionDisabled - emitting false as either CID or truck number is empty.")
            emit(false)
        } else if (getSelectedDispatchId(tag).isEmpty()) {
            Log.d(tag,"isManualTripCompletionDisabled - emitting false because Dispatch ID is empty.")
            emit(false)
        }else{
            emit(
                tripCompletionUseCase.isManualTripCompletionDisabled(
                    tag,
                    cid = cidAndVehicleId.first,
                    vehicleId = cidAndVehicleId.second,
                    getSelectedDispatchId(tag)
                )
            )
        }
    }

    private fun completeStop() {
        _onStopCompleted.postValue(true)
    }

    fun sendDispatchCompleteEventToCPIK() {
        Log.d(tag,"-----DispatchBaseViewModel sendDispatchDataUsecase.sendDispatchCompleteEvent()")
        tripCompletionUseCase.sendDispatchCompleteEventToCPIK(tag) // clears the stop cache, map route and geofences in AL
    }

    private suspend fun sendCurrentDispatchDataToMaps() = withContext(coroutineDispatcher.io()) {
        //On responding "No" in Did you arrive trip panel, we need to send the trip data again to maps as copilot map is clearing the crossed stop without actual arrival.
        with(sendDispatchDataUseCase) {
            sendDispatchEventForClearRouteWithDelay()
            sendCurrentDispatchDataToMaps(shouldRegisterGeofence = false, shouldRedrawCopilotRoute = true, caller = "No Clicked in DYA - Popup - Geofence Willnot be set")
        }
    }

    protected open suspend fun onStopsEmpty() {
        //Ignore
    }

    internal fun checkETAForStop(stopId: Int) {
        viewModelScope.launch(
            coroutineDispatcher.io() + CoroutineName("$tag Checking ETA for Stop")
        ) {
            _eTAForStop.postValue(
                getETAForStop(
                    stopId,
                    getApplication()
                )
            )
        }
    }

    private suspend fun getETAForStop(stopId: Int, context: Context): String =
        Utils.getRouteData(stopId, dataStoreManager)?.let {
            getFormattedDateTime(it.etaTime, context)
        } ?: EMPTY_STRING

    protected fun getFormattedDateTime(etaTime: Date, context: Context): String {
        return DateFormat.getDateFormat(
            context
        ).format(etaTime) + SPACE + DateFormat.getTimeFormat(context).format(
            etaTime
        )
    }

    private fun postStopActions() {
        firstRead = false
    }

    private fun getActionListData(stopId : Int) {
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            dispatchStopsUseCase.getActionsOfStop(activeDispatchId = getSelectedDispatchId(tag), stopId = stopId, caller = TRIP_PREVIEWING).forEach {action ->
                dispatchBaseUseCase.handleActionAddAndUpdate(
                    action,
                    stopDetailList,
                    tag
                )
                _stopActionReadCompleteFlow.emit(action.stopid)

            }
        }
    }

    internal suspend fun getActionsForStop(
        stopId: Int
    ) = coroutineScope {
        stopActionsListenerJobIndex[stopId]?.cancel()
        stopActionsListenerJobIndex[stopId] =
            viewModelScope.launch(CoroutineName(tag + coroutineDispatcher.io() + SupervisorJob())) {
                if(dispatchActiveStateFlow.value == DispatchActiveState.ACTIVE || dispatchActiveStateFlow.value == DispatchActiveState.NO_TRIP_ACTIVE){
                    listenToActionsOfNewlyAddedStop(stopId)
                }else{
                    //For inactive dispatches, if we register listeners, then face this issue - sometimes auto start trip gets completed immediately.
                    //We want actions to display data like pre planned arrival in stop detail screen, so we fetch actions using get call.
                    Log.d(tag+ TRIP_PREVIEWING,"Dispatch is inactive. Fetching actions using firestore get call for stopId: $stopId")
                    getActionListData(stopId)
                }
            }
    }

    internal suspend fun listenToActionsOfNewlyAddedStop(stopId: Int) =
        coroutineScope {
            val cidAndVehicleId = dispatchBaseUseCase.getCidAndTruckNumber(tag)
            val customerId = cidAndVehicleId.first
            val vehicleId = cidAndVehicleId.second
            val dispatchId = getSelectedDispatchId(tag)
            Log.i(
                tag,
                "Step 4: Listen to stop actions stopId: $stopId",
                throwable = null,
                logTripId to dispatchId
            )

            tripCompletionUseCase.listenToStopActions(
                vehicleId, customerId, dispatchId, stopId.toString()
            ).catch { throwable ->
                Log.e(
                    tag,
                    "Exception at listenToActionsOfNewlyAddedStop",
                    throwable = null,
                    "stack" to throwable.stackTraceToString()
                )
                stopActionsReadStatusMap[stopId] = false
            }.onEach { newStopActions ->
                try {
                    if (newStopActions.isEmpty()) return@onEach
                    val newFormIdSet = mutableSetOf<FormDef>()
                    newStopActions.forEach { action ->
                        dispatchBaseUseCase.handleActionAddAndUpdate(
                            action,
                            stopDetailList,
                            tag
                        )
                        dispatchBaseUseCase.fetchAndAddFormsOfActionForFormSync(
                            action,
                            newFormIdSet
                        )
                    }
                    updateStopDetailList("actionListener",selectedDispatchId)

                    newStopActions.firstOrNull()?.let { firstAvailableAction -> //Check why firstOrNull introduced
                        _stopActionReadCompleteFlow.emit(firstAvailableAction.stopid)
                    }

                    syncFormsIfFormIdIsAvailableInAction(
                        newFormIdSet,
                        this,
                        customerId,
                        dispatchId
                    )
                    viewModelScope.launch(CoroutineName("setFormCountOfStopList")+ coroutineDispatcher.io() + SupervisorJob()) {
                        setFormCountOfStopList(dispatchId,newFormIdSet)
                    }
                    if (stopDetailList.isEmpty()) return@onEach
                    processStopsDataOnActionReceivedForMaps(stopId)
                    if (newStopActions.all { it.responseSent }) completeStop()
                } catch (e: CancellationException) {
                    //Ignore
                } catch (e: Exception) {
                    stopActionsReadStatusMap[stopId] = false
                    Log.e(
                        tag,
                        "Exception in onEach of listenToActionsOfNewlyAddedStop ${e.message}",
                        e,
                        "Class" to "DispatchBaseViewModel",
                        "Action" to "listenToActionsOfNewlyAddedStop"
                    )
                }
            }.launchIn(this)
        }

    private suspend fun syncFormsIfFormIdIsAvailableInAction(
        newFormIdSet: MutableSet<FormDef>,
        coroutineScope: CoroutineScope,
        customerId: String,
        dispatchId: String
    ) {
        if (newFormIdSet.isEmpty()) {
            setStopsEligibilityForFirstTime(stopDetailList, dispatchStopsUseCase)
            putStopsIntoPreferenceForServiceReference(dispatchId)
        } else {
            coroutineScope.launch(CoroutineName("$tag Sync Dispatch Form")) {
                syncForms(customerId, newFormIdSet, dispatchId)
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun syncForms(
        customerId: String,
        newFormIdSet: MutableSet<FormDef>,
        tripId: String
    ) =
        supervisorScope {
            Log.i(
                tag,
                "Step 5: Sync form data",
                throwable = null,
                "form id" to newFormIdSet,
                logTripId to tripId
            )
            with(tripCompletionUseCase) {
                if (customerId.isNotEmpty()) {
                    getFormsTemplateListFlow().onEach { formTemplateList ->
                        Log.d(
                            tag,
                            "Forms download flow collector called FormTemplateList Size: ${formTemplateList.size}"
                        )
                        if (formTemplateList.size == newFormIdSet.size) {
                            setStopsEligibilityForFirstTime(stopDetailList, dispatchStopsUseCase)
                            putStopsIntoPreferenceForServiceReference(tripId)
                        }
                    }.launchIn(this@supervisorScope)
                    formsSync(
                        customerId,
                        ArrayList(newFormIdSet)
                    )
                } else Log.e(tag, "Customer Id is empty when invoking syncFormsFromServer")
            }
        }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun getFormDataToGetFormSaveStatus(path: String, actionId: String): Boolean  {
        var formSaved = tripCompletionUseCase.isFormSaved(path, actionId)
        if(!formSaved) {
            formSaved = tripCompletionUseCase.isFormDrafted(path, actionId)
        }
        return formSaved
    }


    /**
     * Function used to persist the form counts of each actions in every stops of a selected trip.
     * This persisted form count used to determine the stop completion.
     */
    private suspend fun setFormCountOfStopList(dispatchId: String, newFormIdSet: MutableSet<FormDef>) {
        try {
            var isSyncToQueue: Boolean
            dispatchBaseUseCase.getDeepCopyOfStopDetailList(stopDetailList.toMutableList())
                ?.let { deepCopiedStopDetailList ->
                    val stopListIterator = deepCopiedStopDetailList.iterator()
                    while (stopListIterator.hasNext()) {
                        val uncompletedFormIdSet = HashSet<Int>()
                        val stop = stopListIterator.next()
                        val stopActionsIterator = stop.Actions.iterator()
                        while (stopActionsIterator.hasNext()) {
                            val action = stopActionsIterator.next()
                            newFormIdSet.find { it.formid == action.driverFormid }?.let { formDef ->
                                if (action.actionType == ActionTypes.ARRIVED.ordinal && (action.replyActionType in setOf(
                                        REPLY_WITH_SAME,
                                        REPLY_WITH_NEW
                                    ) || (action.replyActionType == NO_REPLY_ACTION && formDef.driverEditable == 1))
                                ) { // For No reply action forms, its not mandatory to complete the form to end the trip
                                    getFormDataToGetFormSaveStatus(
                                        "$INBOX_FORM_RESPONSE_COLLECTION/${
                                            appModuleCommunicator.doGetCid()
                                        }/${
                                            appModuleCommunicator.doGetTruckNumber()
                                        }/${
                                            dispatchId
                                        }/${action.stopid}/${action.actionid}",
                                        action.actionid.toString()
                                    ).let {
                                        isSyncToQueue = it
                                    }
                                    dispatchBaseUseCase.checkAndAddFormIfInCompleteForLocalPersistence(
                                        action,
                                        isSyncToQueue,
                                        uncompletedFormIdSet
                                    )
                                }
                            } ?: run {
                                Log.i(tag, "Driver form not in Forms set")
                            }
                        formDataStoreManager.setValue(
                            intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stop.stopid}"),
                            uncompletedFormIdSet.size
                        )
                        Log.i(
                            tag,
                            "Step 8:Uncompleted forms of stops",
                            throwable = null,
                            logTripId to dispatchId,
                            "Stop id " to "${stop.stopid} size ${uncompletedFormIdSet.size}",
                            "formIds" to uncompletedFormIdSet.toString()
                        )
                    }
                }
                }
        } catch (cancellationException: CancellationException) {
            //Ignored
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception in setFormCountOfStopList",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun putStopsIntoPreferenceForServiceReference(tripId: String) {
        try {
            val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("putStopsIntoPreferenceForServiceReference")
            if(tripId != EMPTY_STRING && Utils.isIncomingDispatchSameAsActiveDispatch(incomingDispatchId = tripId, activeDispatchId = activeDispatchId).not()){
                Log.d(tag,"putStopsIntoPreferenceForServiceReference - tripId is not same as current tripId. So, ignoring the operation.")
                return
            }
            val uncompletedStopList = stopDetailList.filter { st -> st.completedTime == "" }.getSortedStops()
            dataStoreManager.setValue(
                STOPS_SERVICE_REFERENCE_KEY,
                GsonBuilder().setPrettyPrinting().create()
                    .toJson(uncompletedStopList)
            )
            val logUncompletedStopIDs = uncompletedStopList.map { it.stopid }
            Log.i(
                tag,
                "Step 7: Put stop list for serviceRef",
                throwable = null,
                "size" to uncompletedStopList.size,
                "stop ids" to logUncompletedStopIDs,
                logTripId to tripId
            )
        } catch (cancellationException: CancellationException) {
            //Ignored
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception in putStopsIntoPreferenceForServiceReference",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
        }
    }

    fun hasFirstStopAsCurrentStopHandled(
        stopList: CopyOnWriteArrayList<StopDetail>
    ): Flow<Boolean> = flow {
        if (dispatchBaseUseCase.anyStopAlreadyCompleted(stopList) && appModuleCommunicator.hasActiveDispatch(
                "hasFirstStopAsCurrentStopHandled", false
            ).not()
        ) {
            Log.d(TRIP_FIRST_STOP_CURRENT_STOP, "anyStopCompleted")
            emit(true)
        } else {
            dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
                stopList
            )
            canAllowNavigationClick()
        }
    }

    private suspend fun FlowCollector<Boolean>.canAllowNavigationClick() {
        if (dispatchStopsUseCase.doesStoreHasCurrentStop(dataStoreManager).not()) {
            Log.d(TRIP_FIRST_STOP_CURRENT_STOP, "NoCurrentStop")
            emit(true)
        } else {
            val stop: Stop = dispatchBaseUseCase.getCurrentStop(dataStoreManager)
            Log.d(TRIP_FIRST_STOP_CURRENT_STOP, "currentStop ${stop.stopId}")
            /**
             * If stop has no arrived action then assign default arrived action radius
             * @see DEFAULT_ARRIVED_RADIUS_IN_FEET
             */
            val arrivedGeoFenceRadius =
                if (stop.arrivedRadius > 0) stop.arrivedRadius else DEFAULT_ARRIVED_RADIUS_IN_FEET
            if (dispatchStopsUseCase.getDistanceInFeet(stop) <= arrivedGeoFenceRadius) {
                Log.d(TRIP_FIRST_STOP_CURRENT_STOP, "insideArriveRadius $arrivedGeoFenceRadius")
                emit(false)
            } else {
                Log.d(TRIP_FIRST_STOP_CURRENT_STOP, "notInsideArriveRadius $arrivedGeoFenceRadius")
                emit(true)
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun openDispatchFormActivity(
        stop: Stop,
        actionData: Action,
        isFormResponseSentToServer: Boolean
    ) {
        if (appModuleCommunicator.doGetCid()
                .isNotEmpty() && appModuleCommunicator.doGetTruckNumber()
                .isNotEmpty() && stop.arrivedFormId > 0
        ) {
            val dispatchFormPath = DispatchFormPath(
                stop.stopName,
                stop.stopId,
                actionData.actionid,
                actionData.driverFormid,
                actionData.driverFormClass,
                dataStoreManager.getValue(CURRENT_DISPATCH_NAME_KEY, EMPTY_STRING)
            )

            dispatchStopsUseCase.addDispatchFormPathToFormStack(
                dispatchFormPath,
                dataStoreManager
            )
            getApplication<Application>().applicationContext.startDispatchFormActivity(
                isComposeEnabled = dispatchStopsUseCase.isComposeFormFeatureFlagEnabled(appModuleCommunicator.doGetCid()),
                path = "$INBOX_FORM_RESPONSE_COLLECTION/${
                    appModuleCommunicator.doGetCid()
                }/${
                    appModuleCommunicator.doGetTruckNumber()
                }/${
                    appModuleCommunicator.getCurrentWorkFlowId("ToStartFormActivity")
                }/${stop.stopId}/${actionData.actionid}",
                dispatchFormPath = dispatchFormPath,
                isManualArrival = false,
                isFormResponseSentToServer = isFormResponseSentToServer
            )
        }
    }

    abstract fun postStopsProcessing(caller: String)

    abstract suspend fun startRouteCalculation(caller: String)

    fun handleNavigateClicked(
        stopDetail: StopDetail,
        context: Context
    ) {
        handleNavigateClicked(
            context,
            stopDetail
        )
    }

    /*
       When the navigate button is clicked either from list or stop detail then send the distance message to launcher and launch main activity of it
     */
    fun handleNavigateClicked(
        context: Context,
        stopDetail: StopDetail
    ) {
        Log.logUiInteractionInNoticeLevel(tag, "Stop list item navigation clicked")
        viewModelScope.launch(
            CoroutineName(tag) + coroutineDispatcher.io()
        ) {
            if (isActiveDispatch("handleNavigateClicked").not()) {
                return@launch
            }

            dispatchStopsUseCase.unMarkActiveDispatchStopManipulation()

            //update current stop in datastore
            Log.d(tag,"handleNavigateClicked clicked - putStopIntoPreferenceAsCurrentStop")
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                stopDetail,
                dataStoreManager
            )
            //update trip panel message
            Log.d(tag,"handleNavigateClicked clicked - updateTripPanelWithMilesAwayMessage")
            updateTripPanelWithMilesAwayMessage(tripPanelUseCase)
            //send stopInfo data to maps
            ArrayList<StopDetail>().let {
                it.add(stopDetail)
                it
            }.also { stopDetailList ->
                Log.d(tag,"handleNavigateClicked clicked - sendDispatchDataToMapsForSelectedFreeFloatStop")
                sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(
                    stopDetailList
                )
            }
            Log.d(tag,"handleNavigateClicked clicked - hasFirstStopAsCurrentStopHandled")
            hasFirstStopAsCurrentStopHandled(stopDetailList)
                .safeCollect(tag) {
                    val isLauncherInstalled = Utils.isPackageInstalled(BuildConfig.FLAVOR.getAppLauncherFullPackageName(), context.packageManager)
                    Log.d(tag,"handleNavigateClicked value: $it, isLauncherInstalled - $isLauncherInstalled")
                    if (
                        isLauncherInstalled.not() ||
                        it.not()
                    ) {
                        return@safeCollect
                    }
                    startLauncherActivity(context)
                }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun updateTripPanelWithMilesAwayMessage(tripPanelUseCase: TripPanelUseCase) {
        tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(
            SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
        )
        tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
        tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
    }

    private fun startLauncherActivity(context: Context) {
        val intentToBeLaunched = Intent(LAUNCH_SCREEN_INTENT).apply {
            putExtra(SCREEN_TYPE_KEY, DRIVING_SCREEN_TYPE)
            putExtra(DRIVING_SUB_SCREEN_TYPE_KEY, DRIVING_SUB_SCREEN_NAVIGATION_OPTION_TYPE)
        }
        context.startActivity(intentToBeLaunched)
    }

    fun getCompletedTime(completedTime: String) =
        if (completedTime.isNotEmpty()) Utils.getSystemLocalDateTimeFromUTCDateTime(completedTime) else ""

    fun getNotAvailableString(): String =
        getApplication<Application>().getString(R.string.not_available)

    private fun getNAString(): String =
        getApplication<Application>().getString(R.string.not_available_short)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun setStopsEligibilityForFirstTime(
        stopsList: CopyOnWriteArrayList<StopDetail>,
        dispatchStopsUseCase: DispatchStopsUseCase
    ) {
        if(stopsList.isNotEmpty() && Utils.isIncomingDispatchSameAsActiveDispatch(incomingDispatchId = stopsList.first().dispid, activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("setStopsEligibilityForFirstTime")).not()){
            Log.d(tag,"incoming dispId is not same as active dispatchId, hence returning.")
            return
        }
        val areStopsSequenced = dataStoreManager.getValue(ARE_STOPS_SEQUENCED_KEY, TRUE)
        val eligibleSequentialStops = dataStoreManager.getValue(
            NAVIGATION_ELIGIBLE_STOP_LIST_KEY, emptySet()
        )
        if (areStopsSequenced.not() && stopsList.none { it.completedTime.isNotEmpty() } && eligibleSequentialStops.isEmpty())
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                stopsList, dataStoreManager
            )
    }

    fun obtainFormattedAddress(
        stopId: Int,
        isShortFormNAStr: Boolean,
        isLocaleAvailable: Boolean
    ): Flow<String> = flow {
        val stopAddress = dataStoreManager.getStopAddress(stopId)
        val addressToShow = if (
            isLocaleAvailable &&
            stopAddress.isNotEmpty()
        ) {
            Utils.getFormattedAddress(
                Gson().fromJson(
                    stopAddress,
                    Address::class.java
                )
            )
        } else {
            if (isShortFormNAStr)
                getNAString()
            else
                getNotAvailableString()
        }
        emit(
            addressToShow
        )
    }

    fun sendDispatchStartDataToBackbone(
        dispatchId: String,
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch(
            CoroutineName(TRIP_START_CALL) + coroutineDispatcher.io()
        ) {
            backboneUseCase.setWorkflowStartAction(dispatchId.toSafeInt())
        }
    }

    // Todo Revisit this logic for optimisation. Firestore read is added as a fallback for invalid data in stopDetailList.
    // The action writes in stopDetailList for each stop is happening in separate coroutine.
    // handleActionAddAndUpdate() of DispatchBaseUseCase modifies stopDetailList Object.
    // Due to high concurrency, the copyOnWriteArrayList is giving old snapshot to this function and
    // this leads to invalid action data in stopDetailList.
    // Note: This firestore read will happen unnecessarily while reading stops. We cannot neglect that for now.
    fun updateStopDetailList(src: String, selectedDispatchId: String) {
        viewModelScope.launch(coroutineDispatcher.io()) {
            if (stopDetailList.isEmpty() || stopDetailList.any { stop -> stop.Actions.isEmpty() }) {
                val stopMap = mutableMapOf<Int, List<Action>>()
                stopDetailList.forEach { stop ->
                    if (stop.Actions.isNotEmpty()) return@forEach
                    stopMap[stop.stopid] = fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                        selectedDispatchId,
                        stop.stopid.toString(),
                        "StopListUpdateStopDetailList"
                    )
                }
                stopMap.forEach { (_, actionList) ->
                    actionList.forEach { action ->
                        dispatchBaseUseCase.handleActionAddAndUpdate(action, stopDetailList, tag)
                    }
                }
            }
            Log.i(tag, "UpdateStopDetailList Src:$src stopdetailList Count:${stopDetailList.size}")
            WorkflowEventBus.postStopList(stopDetailList.getSortedStops())
        }
    }

    fun canShowNavigate(
        stopDetail: StopDetail
    ): Boolean = stopDetail.completedTime.isEmpty()

    fun sendRemoveGeofenceEvent(activeDispatchId:String,messageId: Int) {
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            dispatchStopsUseCase.getActionDataFromStop(
                activeDispatchId,
                messageId,
                ActionTypes.ARRIVED.ordinal
            )?.let { action ->
                sendDispatchDataUseCase.sendRemoveGeoFenceEvent(action)
            }
        }
    }

    //This method will send the stop list to map after reading all the stops and actions to set geofence.
    internal fun sendStopsDataToMapsUponReadingActions(
        isStopsReadFirstTime: Boolean,
        stopsToActionsReadMap: HashMap<Int,Boolean>
    ) {
        Log.d(tag, "-----Log Inside sendStopsDataToMapsUponReadingActions")
        if ((dispatchActiveStateFlow.value == DispatchActiveState.ACTIVE || dispatchActiveStateFlow.value == DispatchActiveState.NO_TRIP_ACTIVE) && isStopsReadFirstTime && isAllStopsActionsRead(stopsToActionsReadMap) && isStopsGeofenceDataSentToMaps.not()) {
            Log.d(tag,
                "-----Log Inside sendStopsDataToMapsUponReadingActions inside if condition stopDetailList count: ${stopDetailList.size}." +
                        " sending geofence request to AL")
            sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(stopDetailList, dispatchActiveStateFlow.value, "DispatchBaseVM-AllStopsRead-Finished")
            isStopsGeofenceDataSentToMaps = true
        }
    }

    internal suspend fun sendStopsDataToMapsForRouteCalculation(
        isStopsReadFirstTime: Boolean
    ) {
        Log.d(tag, "*****Log Inside sendStopsDataToMapsForRouteCalculation")
        if (isStopsReadFirstTime) {
            Log.d(tag,
                "*****Log Inside sendStopsDataToMapsForRouteCalculation inside if condition stopDetailList count: ${stopDetailList.size}")
            routeCalculationRetryCount = ROUTE_CALCULATION_MAX_RETRY_COUNT
            startRouteCalculation(caller = "sendStopsDataToMapsForRouteCalculation")
        }
    }

    private fun isAllStopsActionsRead(stopsToActionsReadMap : HashMap<Int,Boolean>) : Boolean {
        return stopDetailList.size == stopsToActionsReadMap.keys.size
    }

    private fun processStopsDataOnActionReceivedForMaps(stopId: Int) {
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            val isActiveDispatchStopsManipulated =
                dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip()
            stopDetailList.find { stop -> stop.stopid == stopId }?.let { stopDetail ->
                if (stopDetail.deleted == 0) stopActionsReadStatusMap[stopId] = true
            }
            if (isActiveDispatchStopsManipulated) return@launch
            sendStopsDataToMapsUponReadingActions(isStopsReadFirstTime, stopActionsReadStatusMap)
        }
    }

    private fun checkForLastStopArrivalAndUpdateRouteManifestWidget() =
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps()
        }

    fun notifyStopCountChange(
        context: Context,
        stopUpdateStatus: String,
        removedOrAddedStopsCount: Int
    ) {
        context.makeLongLivingToast(
            if (stopUpdateStatus.equals(ADDED, true)) {
                if (removedOrAddedStopsCount == 1) {
                    context.resources.getString(R.string.stop_added_msg)
                } else {
                    String.format(
                        context.getString(R.string.n_stop_added_msg),
                        removedOrAddedStopsCount.toString()
                    )
                }
            } else {
                if (removedOrAddedStopsCount == 1) {
                    context.resources.getString(
                        R.string.stop_removed_msg
                    )
                } else {
                    String.format(
                        context.getString(R.string.n_stop_removed_msg),
                        removedOrAddedStopsCount.toString()
                    )
                }
            }
        ).also {
            it.show()
        }
    }

    internal fun listenToCopilotEvents() {
        //Request to listen to CoPilot events
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            Log.d(
                tag,
                "registering new geofence events"
            )
            val bundle = Bundle().also {
                it.putString(EVENT_TYPE_KEY, LISTEN_GEOFENCE_EVENT)
                it.putString(
                    WORKFLOW_SERVICE_INTENT_ACTION_KEY,
                    EVENTS_PROCESSING_FOREGROUND_SERVICE_INTENT_ACTION
                )
            }
            AppLauncherCommunicator.sendMessage(101, bundle, null)
            Log.i(tag, "Copilot event listener registered")
        }
    }

    internal fun runOnTripEnd(
        dispatchId: String,
        caller: String,
        pfmEventsInfo: PFMEventsInfo.TripEvents,
        workManager: WorkManager = getWorkManagerInstance()
    ) {
        val tripCompletionScope = CoroutineScope(coroutineDispatcher.io() + CoroutineName(TRIP_COMPLETE))
        tripCompletionScope.launch {
            tripCompletionUseCase.runOnTripEnd(
                dispatchId = dispatchId,
                caller = caller,
                workManager = workManager,
                pfmEventsInfo = pfmEventsInfo
            )
            lastUpdatedStopListCount = 0
            withContext(coroutineDispatcher.main()) {
                _onTripEnd.value = true
            }
        }.invokeOnCompletion {
            tripCompletionScope.cancel()
        }
    }

    internal fun getWorkManagerInstance() = WorkManager.getInstance(application.applicationContext)

    fun logNewEventWithDefaultParameters(eventName : String) {
        if(eventName.isNotEmpty()) {
            dispatchBaseUseCase.logNewEventWithDefaultParameters(eventName)
        }
    }

    fun logScreenViewEvent(screenName : String) {
        if(screenName.isNotEmpty()) {
            dispatchBaseUseCase.logScreenViewEvent(screenName)
        }
    }

    fun isTripCompleted() = flow {
        emit(tripCompletionUseCase.isTripComplete(tag, getSelectedDispatchId("${tag}isTripCompleted")))
    }

    internal fun resetIsDraftView() = viewModelScope.launch(coroutineDispatcher.io()) {
        tripCompletionUseCase.getLocalDataSourceRepo().setToFormLibModuleDataStore(
            FormDataStoreManager.IS_DRAFT_VIEW, false)
    }

    private suspend fun sendMessage() {
        if (dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY)) {
            with(tripPanelUseCase) {
                putArrivedMessagesIntoPriorityQueue()
                sendMessageToLocationPanelBasedOnCurrentStop()
                checkForCompleteFormMessages()
            }
        }
    }

    internal fun updateTripPanelMessagePriority() {
        appModuleCommunicator.getAppModuleApplicationScope().launch(
            coroutineDispatcher.io() + CoroutineName(tag)
        ) {
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
            sendMessage()
        }
    }

    private suspend fun areMoreArrivedTriggersAvailableToDisplayInForeGround(): Boolean {
        dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString().let { geofenceTriggerList ->
            Log.d(ARRIVAL_PROMPT, "Arrive triggers available for message/stop ids: " + geofenceTriggerList.map { it.messageId })
            return geofenceTriggerList.isNotEmpty()
        }
    }

    internal fun updateTripPanelMessagePriorityIfThereIsNoMoreArrivalTrigger() {
        // Send Distance Or Select stop message
        appModuleCommunicator.getAppModuleApplicationScope().launch(
            coroutineDispatcher.io() + CoroutineName(tag)
        ) {
            if (areMoreArrivedTriggersAvailableToDisplayInForeGround().not()) {
                updateTripPanelMessagePriority()
            }
        }
    }

    internal fun checkAndDisplayDidYouArriveIfTriggerEventAvailable(isDidYouArriveDialogNotNull: Boolean, showDidYouArriveDialog: (StopDetail?, String, LauncherMessageWithPriority) -> Unit) {
        viewModelScope.launch(coroutineDispatcher.io()) {
            if(Utils.isActiveDispatchIdSameAsSelectedDispatchId(activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("checkAndDisplayDidYouArriveIfTriggerEventAvailable"), selectedDispatchId = appModuleCommunicator.getSelectedDispatchId("checkAndDisplayDidYouArriveIfTriggerEventAvailable")).not()){
                return@launch
            }
            dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString().let { geofenceArriveTriggerData ->
                Log.d(
                    ARRIVAL_PROMPT,
                    "Arrival trigger from preference, stop ids: ${geofenceArriveTriggerData.map { it.messageId }}, is alertDialog already displaying: $isDidYouArriveDialogNotNull"
                )
                if (geofenceArriveTriggerData.isNotEmpty() && isDidYouArriveDialogNotNull.not()) {
                    tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(true)
                    tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
                    Log.d(
                        ARRIVAL_PROMPT,
                        "listLauncherMessageWithPriority [messageId, message, messagePriority]: " + tripPanelUseCase.listLauncherMessageWithPriority.map {
                            Triple(
                                it.messageID,
                                it.message,
                                it.messagePriority
                            )
                        })
                    val arrivedTriggerData =
                        tripPanelUseCase.listLauncherMessageWithPriority.peek()
                            ?: LauncherMessageWithPriority()
                    Log.d(
                        ARRIVAL_PROMPT,
                        "Retrieved listLauncherMessageWithPriority peek,  messageId: ${arrivedTriggerData.messageID}, message: ${arrivedTriggerData.message}, messagePriority: ${arrivedTriggerData.messagePriority}"
                    )

                    if (arrivedTriggerData.messagePriority == TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP ||
                        arrivedTriggerData.messagePriority == TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY
                    ) {
                        showDidYouArriveDialog(arrivedTriggerData, showDidYouArriveDialog)
                    } else {
                        Log.d(
                            ARRIVAL_PROMPT,
                            "listLauncherMessageWithPriority peek does not have arrival message"
                        )
                        tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
                    }
                }
            }
        }
    }

    private suspend fun showDidYouArriveDialog(arriveTriggerData: LauncherMessageWithPriority, showDidYouArriveDialog: (StopDetail?, String, LauncherMessageWithPriority) -> Unit) {
        tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(arriveTriggerData.messageID)
        val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId(SHOW_ARRIVAL_DIALOG)
        val stopData =
            dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(
                SHOW_ARRIVAL_DIALOG,
                stopId = arriveTriggerData.messageID
            )
        showDidYouArriveDialog(stopData, activeDispatchId, arriveTriggerData)
    }

    fun cancelDidYouArriveAlertTimerCollectJob() {
        if(didYouArriveAlertTimerCollectJob?.isActive == true) {
            didYouArriveAlertTimerCollectJob?.cancel()
        }
    }

    private suspend fun isSaveToDraftsFeatureOn(): Boolean = dispatchStopsUseCase.getFeatureFlagGateKeeper().isFeatureTurnedOn(
        FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG,
        appModuleCommunicator.getFeatureFlags(),
        appModuleCommunicator.doGetCid()
    )

    internal suspend fun restoreFormsWhenDraftFunctionalityIsTurnedOff(isDidYouArriveDialogNull: Boolean, startDispatchFormActivity: (Boolean, String, DispatchFormPath, Boolean, Boolean) -> Unit) {
        if (!isSaveToDraftsFeatureOn() && dataStoreManager.hasActiveDispatch(
                "BaseOnResume",
                false
            ) && dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString().isEmpty() && isDidYouArriveDialogNull
        ) {
            dispatchStopsUseCase.getDriverFormsToFill(
                dataStoreManager
            ).let { formList ->
                if (formList.isNotEmpty()) {
                    startDispatchFormActivity(
                        isComposeFormFeatureFlagEnabled(),
                        "$INBOX_FORM_RESPONSE_COLLECTION/${appModuleCommunicator.doGetCid()}/${appModuleCommunicator.doGetTruckNumber()}/${
                            appModuleCommunicator.getCurrentWorkFlowId("restoreFormsWhenDraftFunctionalityIsTurnedOff")
                        }/${formList.first().stopId}/${formList.first().actionId}",
                        formList.first(),
                        false,
                        true
                    )
                }
            }
        }
    }

    private var tripCompletionJob: Job? = null
    internal fun checkForTripCompletion(showTripCompleteToast: () -> Unit) {
        tripCompletionJob?.takeIf { it.isActive }?.cancel()
        tripCompletionJob = viewModelScope.launch(coroutineDispatcher.io()) {
            isTripCompleted().filter { it.second }.collect {
                    /** trip end = StopActionReasonTypes.AUTO.name, no more actions Event
                     * This is when all stops actions are complete for every stop on the trip
                     */
                    val pfmEventsInfo = PFMEventsInfo.TripEvents(
                        reasonType = StopActionReasonTypes.AUTO.name,
                        negativeGuf = false
                    )
                    runOnTripEnd(
                        dispatchId = it.first,
                        caller = "checkForTripCompletion",
                        pfmEventsInfo = pfmEventsInfo
                    )
                    showTripCompleteToast()
            }
        }
    }

    private suspend fun performActionsAsDriverAcknowledgedArrival(
        activeDispatchId: String,
        arriveTriggerData: LauncherMessageWithPriority,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents
    ) {
        tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(arriveTriggerData.messageID)
        tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(arriveTriggerData.messageID)
        // Remove Arrived Geofence on responding to "Did you arrive" dialog
        sendRemoveGeofenceEvent(activeDispatchId, arriveTriggerData.messageID)
        dispatchStopsUseCase.sendStopActionEvent(
            activeDispatchId,
            StopActionEventData(
                stopId = arriveTriggerData.messageID,
                actionType = ActionTypes.ARRIVED.ordinal,
                context = application,
                hasDriverAcknowledgedArrivalOrManualArrival = true
            ),
            DO_ON_ARRIVAL_CALLER,
            pfmEventsInfo
        )
        if (areMoreArrivedTriggersAvailableToDisplayInForeGround().not()) {
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        }
    }

    private fun getFormIdOfAction(
        activeDispatchId: String,
        arriveTriggerData: LauncherMessageWithPriority
    ) {
        // Check any form associated with the arrived action.If so, mark the isNavigatingToFormActivity to TRUE.
        viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.io()) {
            dispatchStopsUseCase.getActionDataFromStop(
                activeDispatchId,
                arriveTriggerData.messageID,
                ActionTypes.ARRIVED.ordinal
            )?.let { action ->
                if (action.driverFormid > 0) {
                    driverFormId = action.driverFormid
                    isNavigatingToFormActivity = true
                }
            }
        }
    }

    internal fun doOnArrival(
        arriveTriggerData: LauncherMessageWithPriority,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents,
        stopDetail: StopDetail? = null,
        checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch: () -> Unit,
        dismissDidYouArriveAlert: () -> Unit,
        checkForTripCompletion: () -> Unit
    ) {
        viewModelScope.launch(coroutineDispatcher.io()) {
            if (stopDetentionWarningUseCase.canDisplayDetentionWarning(stopDetail)) {
                stopDetentionWarningUseCase.startDetentionWarningTimer(
                    stopDetail,
                    DO_ON_ARRIVAL_CALLER
                )
            }
            val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("driverAck")
            getFormIdOfAction(activeDispatchId, arriveTriggerData)
            performActionsAsDriverAcknowledgedArrival(
                activeDispatchId,
                arriveTriggerData,
                pfmEventsInfo
            )
            dismissDidYouArriveAlert()
            checkForLastStopArrivalAndUpdateRouteManifestWidget()
            if (driverFormId <= 0 && areMoreArrivedTriggersAvailableToDisplayInForeGround()) {
                checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch()
            }
            checkForTripCompletion()
            if(isActive && didYouArriveAlertTimerCollectJob?.isActive == true) {
                didYouArriveAlertTimerCollectJob?.cancel()
            }
        }
    }

    private suspend fun startCountDownTimerBasedOnNegativeGUF(
        stopData: StopDetail?,
        activeDispatchId: String,
        arriveTriggerData: LauncherMessageWithPriority,
        dismissDialog: () -> Unit,
        updateDialogPositiveButtonText: (String) -> Unit,
        doOnArrival: (LauncherMessageWithPriority, PFMEventsInfo.StopActionEvents, StopDetail?) -> Unit
    ) {
        stopData?.getArrivedAction()?.gufType?.let { gufType ->
            if (gufType == DRIVER_NEGATIVE_GUF) {
                Log.d(
                    ARRIVAL_PROMPT, "App Did you arrive shown with timer",
                    throwable = null,
                    DISPATCHID to activeDispatchId, STOPID to arriveTriggerData.messageID, KEY to DISPATCH_LIFECYCLE
                )
                didYouArriveAlertTimerCollectJob?.cancel()
                didYouArriveAlertTimerCollectJob =
                    viewModelScope.launch(CoroutineName(tag) + coroutineDispatcher.main()) {
                        WorkflowEventBus.negativeGufTimerEvents.collect { timeRemainingInDidYouArriveTimer ->
                            if (timeRemainingInDidYouArriveTimer > 0) {
                                updateDialogPositiveButtonText(
                                    application.getString(R.string.yes).plus("(")
                                        .plus(timeRemainingInDidYouArriveTimer).plus(")")
                                )
                            } else {
                                val arrivalReasonHashMap = arrivalReasonUsecase.getArrivalReasonMap(ArrivalType.TIMER_EXPIRED.toString(), stopData.stopid, true)
                                arrivalReasonUsecase.updateArrivalReasonForCurrentStop(stopData.stopid, arrivalReasonHashMap)
                                Log.logUiInteractionInInfoLevel(
                                    ARRIVAL_PROMPT,
                                    "App DYA Yes button timer expired",
                                    throwable = null,
                                    DISPATCHID to activeDispatchId,
                                    STOPID to arriveTriggerData.messageID,
                                    KEY to DISPATCH_LIFECYCLE
                                )
                                /** arrival occurred = StopActionReasonTypes.TIMEOUT.name, neg guf timeout Event
                                 * Arrived the stop with timer expired for did you arrive prompt
                                 */
                                val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                                    reasonType = StopActionReasonTypes.TIMEOUT.name,
                                    negativeGuf = true
                                )
                                doOnArrival(
                                    arriveTriggerData,
                                    pfmEventsInfo,
                                    stopData
                                )
                                dismissDialog()
                            }
                        }
                    }
                tripPanelUseCase.scheduleBackgroundTimer(stopData.stopid, ARRIVAL_PROMPT)
            } else {
                Log.d(
                    ARRIVAL_PROMPT,
                    "App Guf type is not negative guf and App DYA shown without timer",
                    throwable = null,
                    DISPATCHID to activeDispatchId,
                    STOPID to arriveTriggerData.messageID,
                    "negativeGuf" to gufType
                )
            }
        } ?: run {
            Log.w(
                ARRIVAL_PROMPT,
                "App stop/arrived action/guf type is null",
                throwable = null,
                DISPATCHID to activeDispatchId,
                STOPID to arriveTriggerData.messageID,
                "stopData" to stopData
            )
        }
    }

    internal fun setDidYouArriveDialogListener(
        arriveTriggerData: LauncherMessageWithPriority,
        stopData: StopDetail?, activeDispatchId: String,
        updateDialogPositiveButtonText: (String) -> Unit, dismissDialog: () -> Unit,
        doOnArrival: (LauncherMessageWithPriority, PFMEventsInfo.StopActionEvents, StopDetail?) -> Unit
    ) {
        viewModelScope.launch(coroutineDispatcher.io()) {
            startCountDownTimerBasedOnNegativeGUF(
                stopData,
                activeDispatchId,
                arriveTriggerData,
                dismissDialog,
                updateDialogPositiveButtonText,
                doOnArrival
            )
            tripPanelUseCase.dismissTripPanelMessage(tripPanelUseCase.lastSentTripPanelMessage.messageId)
        }
    }

    internal fun doOnDidYouArrivePositiveButtonPress(
        doOnArrival: (PFMEventsInfo.StopActionEvents) -> Unit
    ) {
        viewModelScope.launch(CoroutineName(ARRIVAL_DIALOG_YES_CLICK)) {
            tripPanelUseCase.cancelNegativeGufTimer(ARRIVAL_DIALOG_YES_CLICK)
            /** arrival occurred = StopActionReasonTypes.NORMAL.name, required guf confirm event
             * Driver arrives on tapping yes on did you arrive trigger
             */
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                reasonType = StopActionReasonTypes.NORMAL.name,
                negativeGuf = false
            )
            doOnArrival(pfmEventsInfo)
        }
    }

    suspend fun updateArrivalReasonForCurrentStop(stop: StopDetail?, arrivalReason: String, isArrival :Boolean = false) = stop?.let {
        val arrivalReasonHashMap = arrivalReasonUsecase.getArrivalReasonMap( arrivalReason, stop.stopid, isArrival)
        arrivalReasonUsecase.updateArrivalReasonForCurrentStop(stop.stopid, arrivalReasonHashMap)
    }

    internal fun doOnDidYouArriveNegativeButtonPress(
        arriveTriggerData: LauncherMessageWithPriority,
        dismissDidYouArriveAlert: () -> Unit,
        showDidYouArrive: () -> Unit
    ) {
        viewModelScope.launch(CoroutineName(ARRIVAL_DIALOG_NO_CLICK)) {
            isNavigatingToFormActivity = false
            tripPanelUseCase.cancelNegativeGufTimer(ARRIVAL_DIALOG_NO_CLICK)
            dismissDidYouArriveAlert()
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(arriveTriggerData.messageID)
            tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(arriveTriggerData.messageID)
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
            if (areMoreArrivedTriggersAvailableToDisplayInForeGround()) {
                showDidYouArrive()
            }
            // On responding "No" in Did you arrive trip panel, we need to send the trip data again to maps as copilot map is clearing the crossed stop without actual arrival.
            sendCurrentDispatchDataToMaps()
        }
    }
    suspend fun checkDispatchIsCompleted() : Boolean {
        val selectedDispatchId = getSelectedDispatchId("ISTRIPCOMPLETED")
        if ( selectedDispatchId != EMPTY_STRING) {
            return dispatchBaseUseCase.getDispatchAndCheckIsCompleted(selectedDispatchId)
        }
        return false
    }

    suspend fun setIsDyaShownKey(value: Boolean){
        dataStoreManager.setValue(IS_DYA_ALERT_ACTIVE, value)
    }
}