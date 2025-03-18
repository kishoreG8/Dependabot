package com.trimble.ttm.routemanifest.managers

import android.content.Context
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.ttm.commons.logger.ARRIVAL_PROMPT
import com.trimble.ttm.commons.logger.DEVICE_FCM
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.SERVICE_MANAGER
import com.trimble.ttm.commons.logger.TRIP_EDIT
import com.trimble.ttm.commons.logger.TRIP_PANEL
import com.trimble.ttm.commons.logger.TRIP_STOP_AUTO_ARRIVAL
import com.trimble.ttm.commons.model.DeviceAuthResult
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.repo.FeatureFlagCacheRepo
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.ACTION
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.APPROACH_GEOFENCE_CALLER
import com.trimble.ttm.commons.utils.DEPART_GEOFENCE_CALLER
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.formlibrary.usecases.CacheGroupsUseCase
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.UpdateInspectionInformationUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.IS_DYA_ALERT_ACTIVE
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.TRUCK_NUMBER
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.ArrivalActionStatus
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionEventData
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.getArrivedAction
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.usecases.ArrivalReasonUsecase
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.utils.APPROACH
import com.trimble.ttm.routemanifest.utils.ARRIVAL_ACTION_STATUS
import com.trimble.ttm.routemanifest.utils.ARRIVED
import com.trimble.ttm.routemanifest.utils.DEPART
import com.trimble.ttm.routemanifest.utils.DRIVERID
import com.trimble.ttm.routemanifest.utils.GEOFENCE_TYPE
import com.trimble.ttm.routemanifest.utils.SEQUENCED
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.HashMap

class ServiceManager(
    private val dataStoreManager: DataStoreManager,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val tripCompletionUseCase: TripCompletionUseCase,
    private val tripPanelUseCase: TripPanelUseCase,
    private val tripPanelEventsRepo: TripPanelEventRepo,
    private val formDataStoreManager: FormDataStoreManager,
    private val featureFlagCacheRepo: FeatureFlagCacheRepo,
    private val authenticateUseCase: AuthenticateUseCase,
    private val edvirFormUseCase: EDVIRFormUseCase,
    private val dispatchStopsUseCase: DispatchStopsUseCase,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val backboneUseCase: BackboneUseCase
): KoinComponent {

    private val tag = "ServiceManager"
    private val arrivalReasonUsecase: ArrivalReasonUsecase by inject()

    internal fun cacheFormIdsAndUserIdsFromGroupUnits(
        cacheGroupsScope: CoroutineScope,
        cacheGroupsUseCase: CacheGroupsUseCase
    ) {
        cacheGroupsScope.launch(dispatcherProvider.io() + CoroutineName(tag) + SupervisorJob()) {
            if (appModuleCommunicator.doGetCid().isEmpty() or appModuleCommunicator.doGetObcId()
                    .isEmpty() or appModuleCommunicator.isFirebaseAuthenticated().not()
            ) return@launch
            val status = cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId(),
                cacheGroupsScope,
                tag
            )
            Log.i(tag, "Groups server cache status: $status")
        }
    }

    internal suspend fun sendTripPanelMessageIfTruckStopped(
        isTruckMoving: Boolean,
        updateInspectionInformationUseCase: UpdateInspectionInformationUseCase
    ) {
        if (isTruckMoving.not() && appModuleCommunicator.getCurrentWorkFlowId("sendTripPanelMessageIfTruckStopped")
                .isNotEmpty()
        ) {
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
        } else if (isTruckMoving) {
            with(updateInspectionInformationUseCase) {
                updateInspectionRequire(isTruckMoving)
                clearPreviousAnnotations()
            }
        }
    }

    internal suspend fun handleCidChange(cId: String) {
        if (appModuleCommunicator.doGetCid() == cId) return
        appModuleCommunicator.doSetCid(cId)
    }

    internal suspend fun handleVehicleNumberChange(
        vehicleId: String
    ) {
        var oldTruckNumber = formDataStoreManager.getValue(TRUCK_NUMBER, EMPTY_STRING)
        if (oldTruckNumber.isEmpty()) {
            oldTruckNumber = appModuleCommunicator.doGetTruckNumber()
        }
        if (oldTruckNumber != vehicleId
        ) {
            appModuleCommunicator.doSetTruckNumber(vehicleId)
            dataStoreManager.setValue(TRUCK_NUMBER, vehicleId)
            Log.n("$DEVICE_FCM$SERVICE_MANAGER", "VehicleNumberChanged oldTruckNumber: $oldTruckNumber , newTruckNumber: $vehicleId")
            fetchAndStoreFCMToken(oldTruckNum = oldTruckNumber)
        }
    }

    internal suspend fun handleDsnChange(
        cacheGroupsScope: CoroutineScope,
        cacheGroupsUseCase: CacheGroupsUseCase,
        obcId: String
    ): Boolean {
        if (appModuleCommunicator.doGetObcId() != obcId
        ) {
            appModuleCommunicator.doSetObcId(obcId)
            checkAndStoreEDVIRSettingsAvailability(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId()
            )
            cacheFormIdsAndUserIdsFromGroupUnits(cacheGroupsScope, cacheGroupsUseCase)
            return true
        }
        return false
    }

    internal suspend fun checkAndStoreEDVIRSettingsAvailability(
        customerId: String,
        obcId: String
    ) {
        if (customerId.isEmpty() || obcId.isEmpty()) {
            Log.e(
                tag,
                "Error while invoking checkAndStoreEDVIRSettingsAvailability function.Customer id $customerId, obc id $obcId"
            )
            return
        }
        val edvirSettingsAvailability = edvirFormUseCase.isEDVIREnabled(customerId, obcId)
        with(formDataStoreManager) {
            setValue(
                FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU,
                edvirSettingsAvailability
            )
        }
    }

    internal suspend fun fetchAndStoreFCMToken(oldTruckNum: String = EMPTY_STRING) {
        authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.doGetTruckNumber(),
            oldTruckNum
        )
    }

    suspend fun handleAuthenticationProcess(
        caller: String,
        isInternetActive: Boolean,
        onAuthenticationComplete: () -> Unit,
        doAuthentication: () -> Unit,
        onAuthenticationFailed: () -> Unit,
        onNoInternet: () -> Unit
    ) {
        authenticateUseCase.handleAuthenticationProcess(
            caller = caller,
            isInternetActive = isInternetActive,
            onAuthenticationComplete = {
                onAuthenticationComplete()
            },
            doAuthentication = {
                doAuthentication()
            },
            onAuthenticationFailed = {
                onAuthenticationFailed()
            },
            onNoInternet = {
                onNoInternet()
            }
        )
    }

    internal suspend fun handleLibraryConnectionState(it: HostAppState) {
        Log.i(
            TRIP_PANEL,
            "Trip panel host app state - ${it.name}"
        )
        if (HostAppState.READY_TO_PROCESS == it) {
            if (dataStoreManager.hasActiveDispatch("READY_TO_PROCESS", false)) {
                with(tripPanelUseCase) {
                    checkForCompleteFormMessages()
                    sendMessageToLocationPanelBasedOnCurrentStop()
                }
            }
        } else if (HostAppState.SERVICE_DISCONNECTED == it) {
            tripPanelEventsRepo.retryConnection()
            Log.i(
                TRIP_PANEL,
                "Retrying connection to trip panel host app"
            )
        }
    }

    internal fun processGeoFenceEvents(
        activeDispatchId: String,
        geofenceSetName: String,
        context: Context,
        isTriggerFromMap : Boolean
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(dispatcherProvider.io() + SupervisorJob()) {
            Log.logGeoFenceEventFlow(tag, "GeofenceEventProcess: $geofenceSetName D$activeDispatchId")
            when {
                geofenceSetName.startsWith(APPROACH) -> {
                    val triggeredGeoFenceStopId = geofenceSetName.substringAfter(APPROACH).toInt()
                    dispatchStopsUseCase.getStopAndActions(
                        triggeredGeoFenceStopId,
                        dataStoreManager, TRIP_STOP_AUTO_ARRIVAL + SERVICE_MANAGER + APPROACH
                    ).let { stop ->
                        Log.logGeoFenceEventFlow(tag, "GeofenceEventStopFound For Approach: D$activeDispatchId S$triggeredGeoFenceStopId",
                            DISPATCHID to activeDispatchId, STOPID to triggeredGeoFenceStopId, ACTION to APPROACH, "stopLocation" to Pair(stop.latitude, stop.longitude).toString(), KEY to DISPATCH_LIFECYCLE)
                        Log.logGeoFenceEventFlow(tag, "GeofenceEventStopFound For Approach: D$activeDispatchId S$triggeredGeoFenceStopId",
                            DISPATCHID to activeDispatchId, STOPID to triggeredGeoFenceStopId, ACTION to APPROACH, KEY to DISPATCH_LIFECYCLE)
                        /** approaching occurred = StopActionReasonTypes.AUTO.name, no guf, auto Event
                         * Auto approach, Geofence cross
                         */
                        dispatchStopsUseCase.updateStopActions(stop, ActionTypes.APPROACHING.ordinal)
                        val stopActionEventData = StopActionEventData(
                            geofenceSetName.substringAfter(APPROACH).toInt(),
                            ActionTypes.APPROACHING.ordinal,
                            context,
                            hasDriverAcknowledgedArrivalOrManualArrival = false
                        )
                        dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(
                            stop,
                            activeDispatchId,
                            stopActionEventData
                        )
                        val isStopActive = (stop.deleted == 0)
                        if (isStopActive.not()) {
                            tripCompletionUseCase.checkForTripCompletionAndEndTrip(
                                "ApproachGeoFenceOfDeletedStop",
                                stopActionEventData.context
                            )
                            Log.i(tag, "Equivalent stop is inactive for the Approach event: D${stop.dispid} S${stop.stopid}")
                            return@let
                        }
                        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                            reasonType = StopActionReasonTypes.AUTO.name
                        )
                        dispatchStopsUseCase.sendStopActionEvent(
                            activeDispatchId,
                            stopActionEventData,
                            APPROACH_GEOFENCE_CALLER,
                            pfmEventsInfo
                        )

                        tripCompletionUseCase.checkForTripCompletionAndEndTrip(
                            "ApproachGeoFence",
                            stopActionEventData.context
                        )

                        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                            caller = TRIP_EDIT + APPROACH,
                            stop = stop,
                            actionType = ActionTypes.APPROACHING.ordinal,
                            stopActionReasonTypes = StopActionReasonTypes.AUTO
                        )
                    }
                }
                geofenceSetName.startsWith(ARRIVED) -> {
                    val triggeredGeoFenceStopId = geofenceSetName.substringAfter(ARRIVED).toInt()
                    if (triggeredGeoFenceStopId != 0 || dataStoreManager.getValue(
                            DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP,
                            false
                        ).not()
                    ) {
                        dispatchStopsUseCase.getStopAndActions(
                            triggeredGeoFenceStopId,
                            dataStoreManager, TRIP_STOP_AUTO_ARRIVAL + SERVICE_MANAGER + ARRIVED
                        ).let { stop ->
                            val isArriveTriggerValid =
                                tripPanelUseCase.isValidStopForTripType(triggeredGeoFenceStopId)

                            val responseSent = stop.getArrivedAction()?.responseSent ?: false
                            val arrivalReasonHashMap: HashMap<String, Any>
                            dispatchStopsUseCase.updateStopActions(stop, ActionTypes.ARRIVED.ordinal)
                            if (isArriveTriggerValid.first) {
                                arrivalReasonHashMap = arrivalReasonUsecase.getArrivalReasonMap(ArrivalActionStatus.TRIGGER_RECEIVED.toString(), stop.stopid, false)
                                Log.n(
                                    tag,
                                    "Geofence Event Found For Arrive D$activeDispatchId S$triggeredGeoFenceStopId",
                                    throwable = null,
                                    DISPATCHID to activeDispatchId,
                                    STOPID to triggeredGeoFenceStopId,
                                    ACTION to ARRIVED,
                                    "isTriggerFromMap" to isTriggerFromMap,
                                    "ResponseSent" to responseSent,
                                    "stopLocation" to Pair(stop.latitude, stop.longitude).toString(),
                                    KEY to DISPATCH_LIFECYCLE
                                )
                                tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(
                                    activeDispatchId,
                                    triggeredGeoFenceStopId
                                )
                                // Currently whenever the driver starts the trip from the first stop we're sending the trigger from workflow
                                // and whenever we move a vehicle little bit we're sending the trigger from the map
                                // so there will be duplicate triggers shown which causes un wanted issue so whenever the driver starts the trip from the first stop
                                // we're setting the value to false so that we can avoid showing the trigger from the map since the trigger is already shown from the workflow
                                if (isTriggerFromMap.not()) {
                                    dataStoreManager.setValue(
                                        DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP,
                                        true
                                    )
                                }
                                dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                                    caller = TRIP_EDIT + ARRIVED,
                                    stop = stop,
                                    actionType = ActionTypes.ARRIVED.ordinal,
                                    stopActionReasonTypes = StopActionReasonTypes.AUTO
                                )
                            } else {
                                arrivalReasonHashMap = arrivalReasonUsecase.getArrivalReasonMap(ArrivalActionStatus.TRIGGER_IGNORED_BY_WF.name+" - "+isArriveTriggerValid.second, stop.stopid, false)
                                Log.w(
                                    ARRIVAL_PROMPT,
                                    "Ignoring arrival trigger for stop as it is not matching sequence/free float criteria D$activeDispatchId S$triggeredGeoFenceStopId",
                                    throwable = null,
                                    STOPID to triggeredGeoFenceStopId,
                                    DISPATCHID to activeDispatchId,
                                    ACTION to ARRIVED,
                                    "isTriggerFromMap" to isTriggerFromMap,
                                    "IgnoreReason" to isArriveTriggerValid.second,
                                    ARRIVAL_ACTION_STATUS to arrivalReasonHashMap[ARRIVAL_ACTION_STATUS],
                                    SEQUENCED to arrivalReasonHashMap[SEQUENCED],
                                    GEOFENCE_TYPE to arrivalReasonHashMap[GEOFENCE_TYPE],
                                    DRIVERID to backboneUseCase.getCurrentUser(),
                                    "stopLocation" to Pair(stop.latitude, stop.longitude).toString(),
                                    KEY to DISPATCH_LIFECYCLE
                                )
                                dataStoreManager.setValue(IS_DYA_ALERT_ACTIVE, false)
                           }
                            if(!responseSent) arrivalReasonUsecase.setArrivalReasonForCurrentStop(stop.stopid, arrivalReasonHashMap)
                        }
                    } else {
                        dataStoreManager.setValue(
                            DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP,
                            false
                        )
                        Log.w(
                            ARRIVAL_PROMPT,
                            "Ignoring arrival trigger from maps for stop 0 as driver is starting the truck from first stop D$activeDispatchId S$triggeredGeoFenceStopId",
                            throwable = null,
                            STOPID to triggeredGeoFenceStopId,
                            DISPATCHID to activeDispatchId,
                            "IgnoreReason" to "Trigger already shown for stop 0",
                            KEY to DISPATCH_LIFECYCLE
                        )
                    }
                }
                geofenceSetName.startsWith(DEPART) -> {
                    val triggeredGeoFenceStopId = geofenceSetName.substringAfter(DEPART).toInt()
                    dispatchStopsUseCase.getStopAndActions(
                        triggeredGeoFenceStopId,
                        dataStoreManager, TRIP_STOP_AUTO_ARRIVAL + SERVICE_MANAGER + DEPART
                    ).let { stop ->
                        Log.n(tag, "GeofenceEventStopFound For Depart: D$activeDispatchId S$triggeredGeoFenceStopId", null,
                            KEY to DISPATCH_LIFECYCLE,
                            DISPATCHID to activeDispatchId,
                            STOPID to triggeredGeoFenceStopId,
                            ACTION to DEPART,
                            "stopLocation" to Pair(stop.latitude, stop.longitude).toString(),
                            "isTriggerFromMap" to isTriggerFromMap)
                        dispatchStopsUseCase.updateStopActions(stop, ActionTypes.DEPARTED.ordinal)
                        val stopIdWhichDepartActionTriggered =
                            geofenceSetName.substringAfter(DEPART).toInt()
                        /** depart occurred = StopActionReasonTypes.AUTO.name, no guf, auto Event
                         * Auto depart, Geofence cross
                         */
                        val stopActionEventData = StopActionEventData(
                            stopIdWhichDepartActionTriggered,
                            ActionTypes.DEPARTED.ordinal,
                            context,
                            hasDriverAcknowledgedArrivalOrManualArrival = false
                        )
                        dispatchStopsUseCase.updateStopActions(stop, ActionTypes.DEPARTED.ordinal)
                        dispatchStopsUseCase.postDepartureEventProcess(
                            stop,
                            stopIdWhichDepartActionTriggered,
                            activeDispatchId,
                            stopActionEventData
                        )
                        val isStopActive = (stop.deleted == 0)
                        if (isStopActive.not()) {
                            tripCompletionUseCase.checkForTripCompletionAndEndTrip(
                                "DepartGeoFenceOfDeletedStop",
                                stopActionEventData.context
                            )
                            Log.i(tag, "Equivalent stop is inactive for the Depart event: D${stop.dispid} S${stop.stopid}")
                            return@let
                        }
                        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                            reasonType = StopActionReasonTypes.AUTO.name,
                            negativeGuf = false
                        )
                        dispatchStopsUseCase.sendStopActionEvent(
                            activeDispatchId,
                            stopActionEventData,
                            DEPART_GEOFENCE_CALLER,
                            pfmEventsInfo
                        )

                        tripCompletionUseCase.checkForTripCompletionAndEndTrip(
                            "DepartGeoFence",
                            stopActionEventData.context
                        )
                    }
                }
                else -> {
                    Log.e(tag, "got geofence trigger for invalid action: $geofenceSetName")
                }
            }
        }
    }

    internal suspend fun listenForFeatureFlagDocumentUpdates() {
        featureFlagCacheRepo.listenAndUpdateFeatureFlagCacheMap { appModuleCommunicator.setFeatureFlags(it) }
    }

    internal suspend fun getAuthenticationResult(): DeviceAuthResult {
        return authenticateUseCase.doAuthentication(appModuleCommunicator.getConsumerKey())
    }

    suspend fun getEDVIRSettingsAvailabilityStatus() {
        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetObcId()
                .isEmpty()
        ) {
            Log.e(
                tag,
                "Error while getting getEDVIREnabledDocument function.Customer id ${appModuleCommunicator.doGetCid()}, obc id ${appModuleCommunicator.doGetObcId()}"
            )
            return
        }
        val isEdvirSettingsAvailable =
            edvirFormUseCase.isEDVIREnabled(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId()
            )
        with(formDataStoreManager) {
            setValue(
                FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU,
                isEdvirSettingsAvailable
            )
        }
    }
}