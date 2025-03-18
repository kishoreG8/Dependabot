package com.trimble.ttm.routemanifest.usecases

import android.os.Bundle
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.launchercommunicationlib.commons.EVENT_TYPE_KEY
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.usecases.CacheGroupsUseCase
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.utils.EVENTS_PROCESSING_FOREGROUND_SERVICE_INTENT_ACTION
import com.trimble.ttm.routemanifest.utils.LISTEN_GEOFENCE_EVENT
import com.trimble.ttm.routemanifest.utils.WORKFLOW_SERVICE_INTENT_ACTION_KEY
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkFlowApplicationUseCase(
    private val applicationScope: CoroutineScope,
    private val serviceManager: ServiceManager,
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val backboneUseCase: BackboneUseCase,
    private val cacheGroupsUseCase: CacheGroupsUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val edvirSettingsCacheUseCase: EDVIRSettingsCacheUseCase,
    private val notificationQueueUseCase: NotificationQueueUseCase,
    private val tripCacheUseCase: TripCacheUseCase,
    private val workflowAppNotificationUseCase: WorkflowAppNotificationUseCase
) {
    private val tag = "WorkFlowApplicationUseCase"
    private var eDVIRSettingsCacheJob: Job? = null
    internal var monitorCidJob : Job? = null
    internal var monitorVehicleIdJob : Job? = null
    internal var monitorObcIdJob : Job? = null
    private var featureFlagListenerJob : Job? = null

    fun listenForFeatureFlagDocumentUpdates() {
        featureFlagListenerJob?.cancel()
        featureFlagListenerJob =
            applicationScope.launch(CoroutineName(tag) + coroutineDispatcherProvider.io()) {
                serviceManager.listenForFeatureFlagDocumentUpdates()
            }
    }

    fun monitorDeviceChanges() {
        if(monitorCidJob?.isActive == true) {
            cancelCidJob()
        }
        monitorCidJob = applicationScope.launch(CoroutineName(tag) + coroutineDispatcherProvider.main()) {
            backboneUseCase.monitorCustomerId().collect { cid ->
                serviceManager.handleCidChange(cid)
            }
        }


        if(monitorVehicleIdJob?.isActive == true){
            cancelVehicleIdJob()
        }
        monitorVehicleIdJob =
            applicationScope.launch(CoroutineName(tag) + coroutineDispatcherProvider.main()) {
                backboneUseCase.monitorVehicleId().collect { vehicleId ->
                    serviceManager.handleVehicleNumberChange(vehicleId)
                }
            }

        if(monitorObcIdJob?.isActive == true) {
            cancelObcIdJob()
        }
        monitorObcIdJob =
            applicationScope.launch(CoroutineName(tag) + coroutineDispatcherProvider.main()) {
                backboneUseCase.monitorOBCId().collect { obcId ->
                    if (serviceManager.handleDsnChange(
                            applicationScope,
                            cacheGroupsUseCase,
                            obcId
                        )
                    ) {
                        listenEdvirSettings()
                    }
                }
            }
    }

    suspend fun listenEdvirSettings() {
        if (appModuleCommunicator.isFirebaseAuthenticated().not()) return
        eDVIRSettingsCacheJob?.cancel()
        eDVIRSettingsCacheJob =
            applicationScope.launch(coroutineDispatcherProvider.io() + CoroutineName(tag)) {
                edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates()
            }
    }

    fun sendGeofenceServiceIntentToLauncher() {
        //Request to listen to CoPilot events
        Log.d(tag, "registering new geofence events")
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

    suspend fun showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(){
        // We show the notifications after the user leaves the inspection screen
        withContext(CoroutineName(tag) + coroutineDispatcherProvider.io()){
            notificationQueueUseCase.getEnqueuedNotificationsList(DataStoreManager.NOTIFICATION_LIST).let {
                // And then we process it
                it.forEach { fcmData ->
                    workflowAppNotificationUseCase.processReceivedFCMMessage(
                        fcmData,
                        cacheStopAndActionData = { cid, vehicleNumber, dispatchId ->
                            // Cache stops, actions and sync forms on stop addition/removal
                            tripCacheUseCase.getStopsAndActions(
                                cid,
                                vehicleNumber,
                                dispatchId
                            )
                        })
                }
            }
        }
    }

    fun handleTripPanelConnectionStatus(
        newState: HostAppState,
        tripPanelServiceConnectionStatusScope: CoroutineScope
    ) {
        tripPanelServiceConnectionStatusScope.launch(coroutineDispatcherProvider.io() + CoroutineName(tag)) {
            serviceManager.handleLibraryConnectionState(newState)
        }
    }

    fun cancelCidJob() = monitorCidJob?.cancel()
    fun cancelVehicleIdJob() = monitorVehicleIdJob?.cancel()
    fun cancelObcIdJob() = monitorObcIdJob?.cancel()
}