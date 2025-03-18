package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.logger.LISTEN_ALL_DISPATCHES
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_LIST
import com.trimble.ttm.commons.logger.TRIP_STOP_COUNT
import com.trimble.ttm.commons.repo.FeatureFlagCacheRepo
import com.trimble.ttm.commons.utils.DateUtil.getTimeDifferenceFromNow
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.FeatureGatekeeper.KnownFeatureFlags.LAUNCHER_MAPS_PERFORMANCE_FIX_VERSION
import com.trimble.ttm.commons.utils.FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE
import com.trimble.ttm.commons.utils.ZERO
import com.trimble.ttm.commons.utils.getFeatureFlagDataAsLong
import com.trimble.ttm.commons.utils.isFeatureTurnedOnIfFeatureFlagAvailable
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.managers.workmanager.AutoTripStartWorker
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.ISO_DATE_TIME_FORMAT
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_CONFIRMED
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_TIMEOUT
import com.trimble.ttm.routemanifest.utils.REQUIRED_GUF_CONFIRMED
import com.trimble.ttm.routemanifest.utils.TRIP_START_TIME_VALIDATION_FORMAT
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.toCalendarInUTC
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Locale

class DispatchListUseCase(
    private val dispatchFirestoreRepo: DispatchFirestoreRepo,
    private val featureFlagCacheRepo: FeatureFlagCacheRepo,
    private val localDataSourceRepo: LocalDataSourceRepo,
    private val tripPanelUseCase: TripPanelUseCase,
    private val formDataStoreManager: FormDataStoreManager
) {
    private val dispatches: MutableList<Dispatch> = mutableListOf()
    private val removedDispatchIdFlowPair = getCallbackFlow<String>()
    private val lastDispatchReachedFlowPair = getCallbackFlow<Boolean>()
    private val tag = "DispatchListUC"
    private val latestDispatches: MutableList<Dispatch> = mutableListOf()

    fun getDispatches(): MutableList<Dispatch> = dispatches

    suspend fun listenDispatchesForTruck(
        vehicleId: String, cid: String
    ) {
        dispatchFirestoreRepo.listenDispatchesForTruck(vehicleId, cid)
    }

    fun listenDispatchesList(): Flow<Any> {
        return callbackFlow {
            listenDispatchListFlow().collect { data ->
                if (data is Set<*>) {
                    filterDispatches(data, dispatches, "$TRIP_LIST $LISTEN_ALL_DISPATCHES")
                    //Added toList() to avoid concurrent modif exception.
                    // dispatches object is manipulated in handleDispatchAdditionAndRemoval
                    Log.d("$TRIP_LIST $LISTEN_ALL_DISPATCHES", "SendTrips${dispatches.size}")
                    if (this.isClosedForSend.not()) this.trySend(dispatches.toMutableList()).isSuccess
                } else {
                    if (this.isClosedForSend.not()) this.trySend(data as Exception).isSuccess
                    handleDispatchesError(data, "$TRIP_LIST $LISTEN_ALL_DISPATCHES")
                }
            }
            awaitClose {
                cancel()
            }
        }
    }

    suspend fun removeSelectedDispatchIdFromLocalCache() = localDataSourceRepo.removeSelectedDispatchIdFromLocalCache()

    internal fun handleDispatchAdditionAndRemoval(
        newDispatchListFromServer: List<Dispatch>, dispatches: MutableList<Dispatch>
    ): MutableList<Dispatch> {
        newDispatchListFromServer.forEach { incomingDispatch ->
            val dispatchAlreadyAvailable: Dispatch? =
                dispatches.find { it.dispid == incomingDispatch.dispid }
            if (dispatchAlreadyAvailable == null) {
                dispatches.add(incomingDispatch)
            } else {
                //Existing dispatch .  Remove and add to update dispatch if any field is updated in dispatch
                dispatches.remove(dispatchAlreadyAvailable)
                dispatches.add(incomingDispatch)
            }
        }
        return dispatches
    }

    internal fun removeDispatchIfNotAvailableInNewDispatchList(
        newDispatchListFromServer: List<Dispatch>,
        oldDispatchList: MutableList<Dispatch>
    ) {
        val dispatchesToRemove = ArrayList<Dispatch>()
        oldDispatchList.forEach { oldDispatch ->
            val isDispatchFound = newDispatchListFromServer.find { it.dispid == oldDispatch.dispid }
            if (isDispatchFound == null) {
                dispatchesToRemove.add(oldDispatch)
            }
            dispatchesToRemove.forEach {
                oldDispatchList.remove(it)
            }
        }
    }

    suspend fun checkAndUpdateStopCount(dispatchList: List<Dispatch>): List<Dispatch> {
        val filteredDispatchList = mutableListOf<Dispatch>()
        dispatchList.forEach { dispatch ->
            val cid = dispatch.cid.toString()
            val truckNumber = dispatch.vehicleNumber.trim()
            val dispatchId = dispatch.dispid
            getStopCountOfDispatch(
                cid, truckNumber, dispatchId
            ).let { stopCount ->
                stopCount?.let {
                    dispatch.stopsCountOfDispatch = it
                }
                filteredDispatchList.add(dispatch)
            }
        }
        return filteredDispatchList
    }

    private fun listenDispatchListFlow() = dispatchFirestoreRepo.listenDispatchListFlow()
    fun getRemovedDispatchIDFlow() = removedDispatchIdFlowPair.second

    fun getLastDispatchReachedFlow() = lastDispatchReachedFlowPair.second

    fun sortDispatchListByTripStartTime(dispatchList: List<Dispatch>): List<Dispatch> {
        return dispatchList.sortedWith(
            compareBy({ it.tripStarttime.toCalendarInUTC().timeInMillis },
                { it.created.toCalendarInUTC().timeInMillis })
        )
    }

    fun getDispatchToBeStarted(sortedDispatchList: List<Dispatch>, tripStartCaller: TripStartCaller): Dispatch {
        if (sortedDispatchList.isEmpty()) return Dispatch(
            dispid = ""
        )
        val oldestDispatch = sortedDispatchList.first()
        return when(tripStartCaller){
            TripStartCaller.DISPATCH_DETAIL_SCREEN -> {
                if (oldestDispatch.tripStarttime.toCalendarInUTC().timeInMillis < Utils.calendarInUTC().timeInMillis) oldestDispatch
                else Dispatch(dispid = "")
            }
            TripStartCaller.AUTO_TRIP_START_BACKGROUND ->{
                oldestDispatch
            }
            TripStartCaller.START_TRIP_BUTTON_PRESS_FROM_DISPATCH_DETAIL_SCREEN -> {
                // Ignore the START_TRIP_BUTTON_PRESS_FROM_DISPATCH_DETAIL_SCREEN Block, we are no longer in DispatchList Screen and the trip is already started
                Dispatch()
            }
        }
    }

    fun isTripCreatedTimeNotDuplicated(
        tripCreatedTimeString: String, tripStartedTimeString: String
    ): Boolean {
        SimpleDateFormat(TRIP_START_TIME_VALIDATION_FORMAT, Locale.getDefault()).format(
            SimpleDateFormat(
                ISO_DATE_TIME_FORMAT, Locale.getDefault()
            ).parse(tripCreatedTimeString)!!
        ).let { formattedCreatedTimeString ->
            return formattedCreatedTimeString != tripStartedTimeString
        }
    }

    suspend fun getStopCountOfDispatch(
        cid: String, truckNumber: String, dispatchId: String
    ): Int? {
        if (cid.isEmpty() || truckNumber.isEmpty() || dispatchId.isEmpty()) {
            Log.e(
                TRIP_STOP_COUNT,
                "Error getting stop count.Empty trip values",
                throwable = null,
                "trip id" to dispatchId
            )
            return null
        }
        return dispatchFirestoreRepo.getStopCountOfDispatch(cid, truckNumber, dispatchId)
    }

    suspend fun scheduleDispatch(
        dispatchId: String,
        cid: String,
        vehicleId: String,
        created: String
    ) {
        dispatchFirestoreRepo.scheduleDispatchToDisplay(
            dispatchId,
            cid,
            vehicleId,
            created
        )
    }

    fun canShowDispatchStartPopup(
        existingDispatch: Dispatch?, newDispatch: Dispatch
    ): Boolean {
        return existingDispatch?.dispid != newDispatch.dispid || existingDispatch.name != newDispatch.name
    }

    suspend fun addNotifiedDispatchToTheDispatchList(
        dispatches:MutableList<Dispatch>,
        cid: String, truckNumber: String, dispatchId: String
    ): MutableList<Dispatch> {
        dispatches.find { it.dispid == dispatchId }?.apply {
            this.stopsCountOfDispatch =
                getStopCountOfDispatch(cid, truckNumber, dispatchId) ?: ZERO
            dispatches.add(this)
        }
        return dispatches
    }

    fun getAppLauncherMapsPerformanceFixVersionFromFireStore(featureFlagDocumentMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>): Long =
        featureFlagDocumentMap.getFeatureFlagDataAsLong(LAUNCHER_MAPS_PERFORMANCE_FIX_VERSION)

    internal suspend fun dismissTripPanelMessageIfThereIsNoActiveTrip(): Boolean {
        if (localDataSourceRepo.isKeyAvailableInAppModuleDataStore(DataStoreManager.ACTIVE_DISPATCH_KEY).not()) {
            tripPanelUseCase.dismissTripPanelOnLaunch()
            return true
        }
        return false
    }

    suspend fun getRemoveOneMinuteDelayFeatureFlag() {
        dispatchFirestoreRepo.getAppModuleCommunicator().getFeatureFlags()
            .let { featureFlagDocumentMap ->
                if (featureFlagDocumentMap.isFeatureTurnedOnIfFeatureFlagAvailable(
                        ONE_MINUTE_DELAY_REMOVE
                    ).second.not()
                ) {
                    featureFlagCacheRepo.listenAndUpdateFeatureFlagCacheMap {
                        dispatchFirestoreRepo.getAppModuleCommunicator().setFeatureFlags(
                            it
                        )
                    }
                }
            }
    }

    // This function is used to update the active dispatch id in datastore when app is uninstalled or cleared data in middle of active trip
    suspend fun updateActiveDispatchDatastoreKeys(sortedDispatchList: List<Dispatch>) {
        val dispatchWithActiveFlag = sortedDispatchList.find { it.isActive }
        if(dispatchFirestoreRepo.getCurrentWorkFlowId("DispatchListUC: updateActiveDispatchId").isEmpty()
            && formDataStoreManager.getValue(FormDataStoreManager.IS_FIRST_TIME_OPEN,false)
            && dispatchWithActiveFlag != null) {
                setDispatchAsActive(dispatchWithActiveFlag.dispid, dispatchWithActiveFlag.name)
        }
        // Important: Remove this else block once v3.1.0 is fully rolled out to all customers
        // This else block is to support the backward compatibility(To display Active Dispatch Chip) for the customers who start the dispatch from the old version
        else {
            if (dispatchWithActiveFlag == null) {
                sortedDispatchList.map {
                    it.apply {
                        isActive =
                            dispatchFirestoreRepo.getCurrentWorkFlowId("isActiveUi") == dispid
                    }
                }
            }
        }
    }
    private suspend fun setDispatchAsActive(dispatchId: String, dispatchName: String) {
        //set the datastore key to false once we set the active dispatch info
        formDataStoreManager.setValue(FormDataStoreManager.IS_FIRST_TIME_OPEN, false)
        dispatchFirestoreRepo.setCurrentWorkFlowId(dispatchId)
        dispatchFirestoreRepo.setCurrentWorkFlowDispatchName(dispatchName)
    }

    fun getTripStartEventReasons(oldestDispatch: Dispatch): String {
        return if(oldestDispatch.tripStartNegGuf == 1) {
            /** trip start = StopActionReasonTypes.NORMAL.name, negative guf confirmed Event (sent from HomeFragment)
             * The trip has auto_start_driver_negative_guf tag and it is started on tapping yes with timer
             */
            NEGATIVE_GUF_CONFIRMED
        } else {
            /** trip start = StopActionReasonTypes.NORMAL.name, required guf confirmed Event (sent from HomeFragment)
             * when there is no negative_guf and the trip is not auto start, confirmed yes in prompt
             */
            REQUIRED_GUF_CONFIRMED
        }
    }

    internal fun getTripStartEventReasonsFromWorker(oldestDispatch: Dispatch): String {
        return if(oldestDispatch.tripStartNegGuf == 1) {
            NEGATIVE_GUF_TIMEOUT
        } else {
            EMPTY_STRING
        }
    }

    internal fun getLocalDataSourceRepo() = localDataSourceRepo

    /**
     * Retrieves the latest dispatches for the given company ID and vehicle ID by executing the constructed Firestore query.
     *
     * @param cid The company ID to filter the dispatches.
     * @param vehicleId The vehicle ID to filter the dispatches.
     */
    suspend fun getDispatchesForTheTruckAndScheduleAutoStartTrip(
        cid: String,
        vehicleId: String,
        caller: String
    ) {
        val dispatchSet = dispatchFirestoreRepo.getDispatchesList(vehicleId, cid, caller)
        dispatchSet.find { it.isActive }?.let {
            if(dispatchFirestoreRepo.getCurrentWorkFlowId("getDispatchesForTheTruckAndScheduleAutoStartTrip").isEmpty()
                && formDataStoreManager.getValue(FormDataStoreManager.IS_FIRST_TIME_OPEN,false)){
                setDispatchAsActive(it.dispid, it.name)
            }
        }
        val nonActiveDispatches = dispatchSet.filter { it.isActive.not() }.toMutableSet()

        Log.i(caller, "getDispatchesForTheTruck size ${dispatchSet.size}")
        if (dispatchSet.isNotNull()) {
            filterAndSortDispatchesToGetAutoStart(nonActiveDispatches, caller)
        }
    }

    /**
     * Handles the dispatches by filtering, sorting, and determining the eligible dispatch to start.
     *
     * @param dispatchesSet The set of dispatch data.
     */
    private fun filterAndSortDispatchesToGetAutoStart(dispatchesSet: Set<Dispatch>,caller: String) {
        filterDispatches(dispatchesSet, latestDispatches, caller)

        // Sort the dispatches by trip start time or created time
        val sortedDispatchList = sortDispatchListByTripStartTime(latestDispatches)

        // Get the dispatch eligible to start
        val dispatchToStart = getDispatchToBeStarted(sortedDispatchList, TripStartCaller.AUTO_TRIP_START_BACKGROUND)

        // Schedule the auto trip start worker if there is a dispatch to start
        if (dispatchToStart.dispid.isNotEmpty() && (dispatchToStart.tripStartTimed == 1 || dispatchToStart.tripStartNegGuf == 1)) {
            Log.i(caller, "dispatchToStart dispatch ID ${dispatchToStart.dispid} ")
            scheduleAutoTripStartWorker(dispatchToStart,caller)
        }
    }

    /**
     * Handles errors that occur during the dispatch data collection.
     *
     * @param data The data object which is an exception in this case.
     */
    private fun handleDispatchesError(data: Any, caller: String) {
        (data as Exception).apply {
            Log.e(caller, message, this)
        }
    }

    /**
     * Schedules the auto trip start worker with the given dispatch details.
     *
     * @param dispatch The dispatch that is eligible to start.
     */
    private fun scheduleAutoTripStartWorker(dispatch: Dispatch,caller: String) {
        // Calculate the time difference from now to the trip start time or creation time
        val tripStartTimeDifference = dispatch.tripStarttime
            .takeIf { it.isNotEmpty() }
            ?.getTimeDifferenceFromNow(caller + "trip_start_time")
            ?: dispatch.created.getTimeDifferenceFromNow(caller + "trip_created_time")

        Log.i(caller, "caller $caller Trip scheduled in DispatchListUseCase")

        // Enqueue the work to start the auto trip
        AutoTripStartWorker.enqueueAutoTripStartWork(
            dispatchId = dispatch.dispid,
            dispatchName = dispatch.name,
            cid = dispatch.cid.toString(),
            vehicleId = dispatch.vehicleNumber,
            tripStartEventReason = getTripStartEventReasonsFromWorker(dispatch),
            delay = tripStartTimeDifference,
            caller = caller
        )
    }

    /**
     * Filters the incoming dispatch data, updates the latest dispatches list,
     * and logs the information based on the caller.
     *
     * @param data The incoming data which is expected to be a set of dispatches.
     * @param dispatches The mutable list of the latest dispatches to be updated.
     * @param caller The string identifier for the caller, used for logging purposes.
     */
    private fun filterDispatches(data:Any, dispatches:MutableList<Dispatch>, caller:String){
        val incomingDispatchList = (data as? Set<*>)?.filterIsInstance<Dispatch>()
        Log.i(caller,"filterDispatches incomingDispatchList size ${incomingDispatchList?.size}")

        incomingDispatchList?.let { incomingDispatches ->
            dispatches.clear()
            dispatches.addAll(incomingDispatches)
        }
    }

    fun clear() {
        dispatches.clear()
    }
}