package com.trimble.ttm.routemanifest.usecases

import androidx.datastore.preferences.core.intPreferencesKey
import com.google.gson.Gson
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_ACTIVE_CHECK
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.utils.DateUtil.convertToTimeString
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.isValidDriverForm
import com.trimble.ttm.routemanifest.model.isValidReplyForm
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.utils.ADDED
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_CONFIRMED
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_TIMEOUT
import com.trimble.ttm.routemanifest.utils.REMOVED
import com.trimble.ttm.routemanifest.utils.REQUIRED_GUF_CONFIRMED
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.isEqualTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

class DispatchBaseUseCase(
    private val appModuleCommunicator: AppModuleCommunicator,
    private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
    private val dispatchRepo: DispatchFirestoreRepo,
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder,
    private val localDataSourceRepo: LocalDataSourceRepo,
    ) {
    internal suspend fun fetchAndStoreVIDFromDispatchData(
        dispatchId: String,
        dataStoreManager: DataStoreManager,
        tag: String
    ): Boolean {
        val cidAndVehicleId = getCidAndTruckNumber(tag)
        if (cidAndVehicleId.third.not()) return false
        dispatchRepo.getDispatchPayload(
            tag,
            cid = cidAndVehicleId.first,
            vehicleId = cidAndVehicleId.second,
            dispatchId
        ).also { payload ->
            if (payload.vid != 0L) {
                dataStoreManager.setValue(DataStoreManager.VID_KEY, payload.vid)
                Log.i(
                    tag,
                    "Vid in datastore",
                    throwable = null,
                    "stored vid" to payload.vid
                )
                return true
            }
        }
        return false
    }

    internal suspend fun getCidAndTruckNumber(
        tag: String,
    ): Triple<String, String, Boolean> {
        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetTruckNumber()
                .isEmpty()
        ) {
            Log.e(
                tag,
                "Vehicle Id or Customer Id is empty",
                throwable = null,
                "Cid" to appModuleCommunicator.doGetCid(),
                "Truck number" to appModuleCommunicator.doGetTruckNumber()
            )
            return Triple(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetTruckNumber(),
                false
            )
        }
        return Triple(
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.doGetTruckNumber(),
            true
        )
    }


    internal fun processStopAdditionOrUpdate(
        stopDetail: StopDetail,
        stopList: CopyOnWriteArrayList<StopDetail>
    ): CopyOnWriteArrayList<StopDetail> {
        val existingStop = stopList.find { st -> st.stopid == stopDetail.stopid }
        var existingStopIndex: Int? = null
        var existingStopActions: CopyOnWriteArrayList<Action> = CopyOnWriteArrayList()
        /**
         * Removing stop based on the its index in list, to prevent re-ordering
         * of stops position.Adding again at the position based on its(stop) original index(existing index).
         */
        existingStop?.let { stop ->
            existingStopIndex = stopList.indexOf(stop)
            existingStopActions=existingStop.Actions
            stopList.remove(stop)// This means STOP is updated
        }
        if (existingStopIndex != null) {
            stopDetail.Actions.clear()
            stopDetail.Actions.addAll(existingStopActions)
            stopList.add(existingStopIndex!!, stopDetail)
        } else {
            stopList.add(stopDetail)
        }
        return stopList
    }

    internal fun processStopRemoval(
        stopDetail: StopDetail,
        stopList: CopyOnWriteArrayList<StopDetail>
    ): CopyOnWriteArrayList<StopDetail> {
        stopList.removeIf { it.stopid == stopDetail.stopid }
        return stopList
    }

    internal suspend fun checkAndMarkStopCompletion(
        formDataStoreManager: FormDataStoreManager,
        stopDetail: StopDetail
    ): StopDetail {
        val formCountOfStops = formDataStoreManager.getValue(
            intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetail.stopid}"),
            ZERO
        )
        (stopDetail.completedTime.isNotEmpty() && formCountOfStops.isEqualTo(ZERO)).also { isStopCompleted ->
            stopDetail.StopCompleted = isStopCompleted
        }
        return stopDetail
    }

    internal fun handleActionAddAndUpdate(
        action: Action,
        stopList: CopyOnWriteArrayList<StopDetail>,
        tag: String
    ): StopDetail = try {
        Log.i(
            tag,
            "Action received for new stop - A: ${action.actionid} S: ${action.stopid} D: ${action.dispid}"
        )
        stopList.firstOrNull { stopDetail -> stopDetail.stopid == action.stopid }
            ?.also { stopDetail ->
                if (stopDetail.Actions.find {
                        it.actionid == action.actionid
                    }.isNull()) {
                    stopDetail.Actions.add(action)
                } else {
                    stopDetail.Actions.removeIf {
                        it.actionid == action.actionid
                    }
                    stopDetail.Actions.add(action)
                }
                return stopDetail
            } ?: StopDetail()
    } catch (e: Exception) {
        Log.e(
            tag,
            "Exception in handleActionAdditionOrRemoval.Returning default stop detail",
            throwable = null,
            "stack" to e.stackTraceToString()
        )
        StopDetail()
    }

    internal fun fetchAndAddFormsOfActionForFormSync(
        action: Action,
        newFormIdSet: MutableSet<FormDef>
    ): MutableSet<FormDef> {
        if (action.isValidDriverForm()) newFormIdSet.add(
            FormDef(
                formid = action.driverFormid,
                formClass = action.driverFormClass
            )
        )
        if (action.isValidReplyForm()) newFormIdSet.add(
            FormDef(
                formid = action.forcedFormId.toInt(),
                formClass = action.forcedFormClass
            )
        )
        return newFormIdSet
    }

    internal fun checkAndAddFormIfInCompleteForLocalPersistence(
        action: Action,
        isSyncToQueue: Boolean,
        listOfFormId: HashSet<Int>
    ): HashSet<Int> {
        if (action.isValidDriverForm() && !isSyncToQueue)
            listOfFormId.add(action.driverFormid)
        if (action.isValidReplyForm() && !isSyncToQueue)
            listOfFormId.add(action.forcedFormId.toInt())
        return listOfFormId
    }

    internal fun getDeepCopyOfStopDetailList(stopDetailList: MutableList<StopDetail>): List<StopDetail>? {
        return Utils.toJsonString(stopDetailList)?.let { stopDetailListString ->
            val cloneStopDetailList: List<StopDetail>? = Utils.fromJsonString(stopDetailListString)
            cloneStopDetailList
        }
    }

    internal fun anyStopAlreadyCompleted(stopList: CopyOnWriteArrayList<StopDetail>): Boolean {
        return stopList.any { st -> st.completedTime.isNotEmpty() }
    }

    internal suspend fun getCurrentStop(dataStoreManager: DataStoreManager): Stop {
        val currentStopString: String =
            dataStoreManager.getValue(DataStoreManager.CURRENT_STOP_KEY, EMPTY_STRING)
        return Gson().fromJson(currentStopString, Stop::class.java) ?: Stop()
    }

    suspend fun getActiveStopCount(selectedDispatchId:String) =
        fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(appModuleCommunicator.doGetTruckNumber(),
            appModuleCommunicator.doGetCid(),
            selectedDispatchId)
            .filter { stop -> stop.deleted == 0 }.size

    suspend fun getInActiveStopCount(selectedDispatchId:String) =
        fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(appModuleCommunicator.doGetTruckNumber(),
            appModuleCommunicator.doGetCid(),
            selectedDispatchId)
            .filter { stop -> stop.deleted == 1 }.size

    /**
     * Processes the stop manipulation.
     * @Generic Triple A - Total amount of active or inactive stops of an active trip.
     * @Generic Triple B - Added or Removed stop count.
     * @Generic Triple C - Success or Failure status.
     */
    suspend fun getManipulatedStopCount(
        selectedDispatchId: String,
        stopUpdateStatus: String,
        lastUpdatedActiveStops: Int,
        lastUpdatedInactiveStops: Int,
    ): Triple<Int, Int, Boolean> {
        return when (stopUpdateStatus) {
            REMOVED -> {
                val removedStopCount = getInActiveStopCount(selectedDispatchId)
                if (removedStopCount == lastUpdatedInactiveStops) return Triple(removedStopCount, 0, false)
                Triple(removedStopCount, abs(removedStopCount - lastUpdatedInactiveStops), true)
            }
            ADDED -> {
                val addedStopCount = getActiveStopCount(selectedDispatchId)
                if (addedStopCount == lastUpdatedActiveStops) return Triple(addedStopCount, 0, false)
                Triple(addedStopCount, abs(addedStopCount - lastUpdatedActiveStops), true)
            }
            else -> {
                Triple(0, 0, false)
            }
        }
    }

    suspend fun getTimeString(
        coroutineDispatcher: CoroutineDispatcher,
        calendarToRetrieveTime: Calendar,
        dateTimeFormat: SimpleDateFormat,
        dispatchId: String,
        tag: String,
        is24HourTimeFormat: Boolean
    ): String = convertToTimeString(
        coroutineDispatcher = coroutineDispatcher,
        calendarToRetrieveTime = calendarToRetrieveTime,
        dateTimeFormat = dateTimeFormat,
        dispatchId = dispatchId,
        tag = tag,
        is24HourTimeFormat = is24HourTimeFormat
    )

    internal suspend fun setStartTime(timeInMillis : Long, dataStoreManager: DataStoreManager) {
        dataStoreManager.setValue(
            DataStoreManager.TRIP_START_TIME_IN_MILLIS_KEY,
            timeInMillis
        )
    }

    internal suspend fun storeActiveDispatchIdToDataStore(dataStoreManager: DataStoreManager, dispatchId: String): String {
        val dispatchKey = dispatchId.ifEmpty { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING) }
        dataStoreManager.setValue(
            DataStoreManager.ACTIVE_DISPATCH_KEY,
            dispatchKey
        )
        return dispatchKey
    }

    internal fun hasActiveDispatchKey(dataStoreManager: DataStoreManager): Flow<Boolean> = flow {
        emit(dataStoreManager.hasActiveDispatch("hasActiveDispatchKey",false))
    }

    fun logNewEventWithDefaultParameters(eventName : String) = firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(eventName)

    fun logScreenViewEvent(screenName : String) = firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(screenName)

    fun getTripEventReasonTypeAndGuf(eventReason: String?): Pair<String, Boolean> {
        return when (eventReason) {
            NEGATIVE_GUF_TIMEOUT -> {
                Pair(StopActionReasonTypes.TIMEOUT.name, true)
            }
            NEGATIVE_GUF_CONFIRMED -> {
                Pair(StopActionReasonTypes.NORMAL.name, true)
            }
            REQUIRED_GUF_CONFIRMED -> {
                Pair(StopActionReasonTypes.NORMAL.name, false)
            }
            else -> {
                // Auto start trip - tripStartTimed
                Pair(StopActionReasonTypes.AUTO.name, false)
            }
        }
    }

    /**
     * Gets the state of the current trip, based on whether the active trip is currently selected.
     * @param activeDispatchId The dispatch id of the current in-progress trip, or null if none started
     * @param selectedDispatchId The dispatch id of the trip being displayed
     */
    fun getDispatchActiveState(activeDispatchId: String?, selectedDispatchId: String): DispatchActiveState {
        if (activeDispatchId.isNullOrEmpty()) {
            Log.d(
                TRIP_ACTIVE_CHECK,
                "No Active trip. SelectedTrip=$selectedDispatchId"
            )
            return DispatchActiveState.NO_TRIP_ACTIVE
        }

        if (selectedDispatchId.isEmpty()) {
            Log.w(
                TRIP_ACTIVE_CHECK,
                "ActiveTrip=$activeDispatchId SelectedTrip is empty!"
            )
        } else {
            Log.d(
                TRIP_ACTIVE_CHECK,
                "ActiveTrip=$activeDispatchId SelectedTrip=$selectedDispatchId"
            )
        }

        if (activeDispatchId == selectedDispatchId) {
            return DispatchActiveState.ACTIVE
        }
        return DispatchActiveState.PREVIEWING
    }

    suspend fun getDispatchAndCheckIsCompleted(dispatchId: String) : Boolean {
        val dispatch = fetchDispatchStopsAndActionsUseCase.getDispatch(appModuleCommunicator.doGetCid(),appModuleCommunicator.doGetTruckNumber(),dispatchId, false)
        if (dispatch.isCompleted){
            if (dispatchId == localDataSourceRepo.getActiveDispatchId("getDispatchAndCheckIsCompleted")){
                    //when the stop list got opened but the trip already completed means we need to check that the current active dispatch is same means we need to clear the datastore values for the already completed dispatch
                    localDataSourceRepo.removeAllKeysOfAppModuleDataStore()
                }
        }
        return dispatch.isCompleted
    }
}