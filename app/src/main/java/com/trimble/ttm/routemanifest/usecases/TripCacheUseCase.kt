package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.NOTIFICATION
import com.trimble.ttm.commons.logger.TRIP_CACHING
import com.trimble.ttm.commons.logger.TRIP_ONE_MINUTE_DELAY
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.isFeatureTurnedOn
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.RECEIVED_DISPATCH_SET_KEY
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.isValidDriverForm
import com.trimble.ttm.routemanifest.model.isValidReplyForm
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.repo.FireStoreCacheRepository
import com.trimble.ttm.routemanifest.utils.RECEIVED_DISPATCH_SET_LIMIT
import com.trimble.ttm.routemanifest.utils.SCHEDULE_TIME_FOR_DISPATCH_VISIBILITY
import com.trimble.ttm.routemanifest.utils.Utils.setCrashReportIdentifierAfterBackboneDataCache
import com.trimble.ttm.routemanifest.utils.ext.isGreaterThanAndEqualTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.koin.core.component.KoinComponent
import java.util.concurrent.CopyOnWriteArrayList

class TripCacheUseCase(
    private val fireStoreCacheRepository: FireStoreCacheRepository,
    private val dataStoreManager: DataStoreManager,
    private val workflowAppNotificationUseCase: WorkflowAppNotificationUseCase,
    private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
    private val dispatchListUseCase: DispatchListUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val dispatchFirestoreRepo: DispatchFirestoreRepo,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : KoinComponent {
    private val classTag: String = "TripCacheUseCase"

    private val formDefUpdateListenJob = Job()
    private val formDefUpdateListenScope =
        CoroutineScope(Dispatchers.Default + formDefUpdateListenJob)

    suspend fun cacheDispatchData(
        cid: String,
        vehicleNumber: String,
        dispatchId: String
    ): HashMap<String, String> {
        try {
            setCrashReportIdentifierAfterBackboneDataCache(appModuleCommunicator)
        } catch (e: Exception) {
            Log.i(classTag, "Exception while fetching backbone data ${e.message}.", e)
        }

        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetTruckNumber()
                .isEmpty() || cid.isEmpty() || vehicleNumber.isEmpty() || dispatchId.isEmpty()
        ) {
            Log.e(
                classTag,
                "Error while caching dispatch data.One of the required fields may be empty.",
                null,
                "customer id in preferences" to appModuleCommunicator.doGetCid(),
                "vehicle id in preferences" to appModuleCommunicator.doGetTruckNumber(),
                "customer id from silent fcm push msg" to cid,
                "vehicle id from silent fcm push msg" to vehicleNumber,
                "dispatch id from silent fcm push msg" to dispatchId
            )
            if (cid != appModuleCommunicator.doGetCid() || vehicleNumber != appModuleCommunicator.doGetTruckNumber()) {
                Log.e(
                    classTag,
                    "Error while caching dispatch data.Backbone data in preferences and data from silent fcm push msg are different.",
                    null,
                    "customer id in preferences" to appModuleCommunicator.doGetCid(),
                    "vehicle id in preferences" to appModuleCommunicator.doGetTruckNumber(),
                    "customer id from silent fcm push msg" to cid,
                    "vehicle id from silent fcm push msg" to vehicleNumber
                )
                return hashMapOf()
            }
        }
        if (cid == appModuleCommunicator.doGetCid() && vehicleNumber == appModuleCommunicator.doGetTruckNumber()) {
            getDispatch(cid, vehicleNumber, dispatchId)
            return getStopsAndActions(cid, vehicleNumber, dispatchId)
        }
        return hashMapOf()
    }

    /**
     * This fun gets the dispatch document to cache the dispatch for offline cases when it receives new dispatch notification.
     */
    private suspend fun getDispatch(cid: String, vehicleNumber: String, dispatchId: String) {
        fetchDispatchStopsAndActionsUseCase.getDispatch(cid, vehicleNumber, dispatchId)
            .also { dispatch ->
                if (dispatch.dispid.isNotEmpty()) {
                    Log.i(
                        NOTIFICATION,
                        "Dispatch document cached D: $dispatchId"
                    )
                    // schedule the dispatch based on ONE_MINUTE_DELAY_REMOVE feature flag and display notification
                    scheduleOneMinuteDelayBasedOnFeatureFlag(
                        dispatchId = dispatchId,
                        isTripCompleted = dispatch.isCompleted,
                        isTripReady = dispatch.isReady,
                        dispatch = dispatch,
                        coroutineDispatcher = ioDispatcher
                    )
                } else {
                    Log.e(NOTIFICATION, "Cached Dispatch document is empty D: $dispatchId")
                }
            }
    }

    suspend fun getStopsAndActions(
        cid: String,
        vehicleNumber: String,
        dispatchId: String
    ): HashMap<String, String> {
        val stopData = HashMap<String, String>()
        fetchDispatchStopsAndActionsUseCase.getStopsAndActions(cid, vehicleNumber, dispatchId, TRIP_CACHING)
            .forEach { stop ->
                Log.i(
                    classTag,
                    "StopId: ${stop.stopid} -> SiteCoordinates: ${stop.siteCoordinates}"
                )
                stopData[stop.stopid.toString()] = stop.name
                getForms(cid, stop.Actions)
            }
        return stopData
    }

    suspend fun getForms(cid: String, actionList: CopyOnWriteArrayList<Action>) {
        actionList.forEach { actionData ->
            if (actionData.isValidDriverForm()) {
                syncFormDataBasedOnTheFormClass(
                    cid,
                    actionData.driverFormid.toString(),
                    actionData.driverFormClass
                )
            }

            if (actionData.isValidReplyForm()) {
                syncFormDataBasedOnTheFormClass(
                    cid,
                    actionData.forcedFormId,
                    actionData.forcedFormClass
                )
            }
        }
    }

    private suspend fun syncFormDataBasedOnTheFormClass(
        cid: String,
        formId: String,
        formClass: Int
    ) {
        fireStoreCacheRepository.syncFormData(
            cid = cid,
            formId = formId,
            formClass = formClass
        )
    }

    suspend fun scheduleOneMinuteDelayBasedOnFeatureFlag(
        dispatchId: String,
        isTripCompleted: Any?,
        isTripReady: Any?,
        dispatch: Dispatch?,
        coroutineDispatcher: CoroutineDispatcher
    ): Boolean {
        if (appModuleCommunicator.getFeatureFlags()
                .isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE)
                .not()
        ) {
            Log.i("$NOTIFICATION$TRIP_ONE_MINUTE_DELAY", "One minute delay removal FF is off")
            if (addDispatchIdIfDispatchIsNewElseReturn(dispatchId.trim())) return true
            scheduleDispatchAndSendNotification(
                isTripCompleted,
                isTripReady,
                dispatch,
                coroutineDispatcher
            )
        } else {
            Log.d("$NOTIFICATION$TRIP_ONE_MINUTE_DELAY", "One minute delay removal FF is on,sending HPN")
            if (sendNewDispatchNotificationIfThereIsNoActiveDispatch(dispatch)) return true
        }
        return false
    }

    /**
     * The Received dispatches are maintained in a datastore key to avoid  scheduling again.
     * If the incoming dispatch id available inside datastore list, will not schedule again.
     *
     */
    suspend fun addDispatchIdIfDispatchIsNewElseReturn(dispatchId: String): Boolean {
        val receivedDispatches: MutableSet<String> =
            dataStoreManager.getValue(
                RECEIVED_DISPATCH_SET_KEY,
                mutableSetOf()
            ).toMutableSet()
        if (receivedDispatches.contains(dispatchId)) {
            Log.d(TRIP_ONE_MINUTE_DELAY, "Dispatch is already received and scheduled")
            return true
        }
        if (receivedDispatches.size.isGreaterThanAndEqualTo(
                RECEIVED_DISPATCH_SET_LIMIT
            )
        ) receivedDispatches.clear()
        receivedDispatches.add(dispatchId)
        dataStoreManager.setValue(
            RECEIVED_DISPATCH_SET_KEY,
            receivedDispatches
        )
        return false
    }


    private suspend fun scheduleDispatchAndSendNotification(
        tripIsCompleted: Any?,
        tripIsReady: Any?,
        dispatch: Dispatch?,
        coroutineDispatcher: CoroutineDispatcher
    ) = supervisorScope {
        launch(CoroutineName("tripCacheScheduleDispatch")  + coroutineDispatcher) {
            Log.d(TRIP_ONE_MINUTE_DELAY, "SchedulingDispatchFromUC ${dispatch?.dispid}")
            delay(SCHEDULE_TIME_FOR_DISPATCH_VISIBILITY)
            if (((tripIsCompleted as Boolean?)?.not() == true)
                and (tripIsReady == null || (tripIsReady as Boolean?)?.not() == true)
            ) {
                dispatch?.let {
                    dispatchListUseCase.scheduleDispatch(dispatch.dispid, dispatch.cid.toString(), dispatch.vehicleNumber,dispatch.created)
                    sendNewDispatchNotificationIfThereIsNoActiveDispatch(dispatch)
                } ?: Log.e(
                    "$NOTIFICATION$TRIP_ONE_MINUTE_DELAY",
                    "Dispatch data is null, unable to schedule and show dispatch notification"
                )
            }
        }
    }

    suspend fun sendNewDispatchNotificationIfThereIsNoActiveDispatch(dispatch: Dispatch?): Boolean {
        if (dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY)) {
            Log.d(NOTIFICATION, "Active dispatch is already available, not sending HPN")
            return true
        }
        workflowAppNotificationUseCase.sendNewDispatchAppNotification(dispatch)
        return false
    }

    fun clearDisposable() {
        formDefUpdateListenScope.cancel()
    }

    suspend fun getDispatchBlob(cid: String, vehicleNUmber: String, blobId: String) = dispatchFirestoreRepo.getDispatchBlobDataByBlobId(cid, vehicleNUmber, blobId)

}


