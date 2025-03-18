package com.trimble.ttm.routemanifest.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.RemoteMessage.PRIORITY_HIGH
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.DEVICE_FCM
import com.trimble.ttm.commons.logger.DISPATCH_BLOB
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.NOTIFICATION
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_MANDATORY_INSPECTION
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.FcmData
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.NotificationQueueUseCase
import com.trimble.ttm.routemanifest.usecases.TripCacheUseCase
import com.trimble.ttm.routemanifest.usecases.WorkflowAppNotificationUseCase
import com.trimble.ttm.routemanifest.utils.DISPATCH_BLOB_REF
import com.trimble.ttm.routemanifest.utils.DISPATCH_DELETION_FCM_KEY
import com.trimble.ttm.routemanifest.utils.PAYLOAD
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext


class RouteManifestNotificationService : FirebaseMessagingService(), CoroutineScope, KoinComponent {

    private val tag = "FCMService"
    private val formDataStoreManager: FormDataStoreManager by inject()
    private val authenticateUseCase: AuthenticateUseCase by inject()
    private val backboneUseCase: BackboneUseCase by inject()
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val workflowAppNotificationUseCase: WorkflowAppNotificationUseCase by inject()
    private val notificationQueueUseCase: NotificationQueueUseCase by inject()
    private val tripCacheUseCase: TripCacheUseCase by inject()
    private val dispatchStopsUseCase : DispatchStopsUseCase by inject()
    
    //cancelling this will cancel all the child jobs
    private var parentJob: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + parentJob

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(
            "$NOTIFICATION$DEVICE_FCM",
            "New FCM received",
            throwable = null,
            "Priority" to remoteMessage.priority,
            "Payload" to remoteMessage.data.toString()
        )
        //This statement will start the RouteManifestForegroundService if it is not running already.
        // Android OS may kill the foreground service in some situations like memory constraints,
        // so we are attempting to start the service on receiving FCM in order for proper functionality.
        if (remoteMessage.priority == PRIORITY_HIGH) {
            Log.d(
                "$NOTIFICATION$DEVICE_FCM",
                "Received High Priority message, attempting to start service if not running already"
            )
            startForegroundService()
        }

        remoteMessage.data.let { pushMessage ->
            val fcmData = if(pushMessage.containsKey(PAYLOAD)){
                Gson().fromJson(pushMessage[PAYLOAD], FcmData::class.java)
            }else{
                getFCMDataFromRemoteMessageMap(pushMessage)
            }
            if (pushMessage.containsKey(DISPATCH_DELETION_FCM_KEY)) {
                // Implemented as Part of MAPP-12392 - Handling Dispatch Delete, view ticket for more details
                appModuleCommunicator.getAppModuleApplicationScope()
                    .launch(CoroutineName(tag) + Dispatchers.IO) {
                        Log.d(
                            "$NOTIFICATION$DEVICE_FCM",
                            "Dispatch delete fcm received for active dispatch id"
                        )
                        workflowAppNotificationUseCase.processDispatchDeletion(fcmData)
                        return@launch
                    }
            }
            if (pushMessage.containsKey(DISPATCH_BLOB_REF)) {
                //read dispatch blob and send to third party app
                //Changing log type to notice for DataDog widget - Custom Workflow Events Processing Insights
                Log.n(
                    "$NOTIFICATION$DEVICE_FCM$DISPATCH_BLOB",
                    "New dispatch blob received, processing it. BlobId: ${pushMessage["DispatchBlobDocRef"]}"
                )
                appModuleCommunicator.getAppModuleApplicationScope()
                    .launch(CoroutineName(tag) + Dispatchers.IO) {
                        workflowAppNotificationUseCase.processDispatchBlobData(
                            pushMessage,
                            getDispatchBlob = { cid, vehicleNumber, blobId ->
                                tripCacheUseCase.getDispatchBlob(cid, vehicleNumber, blobId)
                            },
                            deleteDispatchBlobDocument = { cid, vehicleNumber, blobId ->
                                dispatchStopsUseCase.deleteDispatchBlobDocument(
                                    cid,
                                    vehicleNumber,
                                    blobId
                                )
                            })
                    }
            } else {
                appModuleCommunicator.getAppModuleApplicationScope()
                    .launch(CoroutineName(tag) + Dispatchers.IO) {
                        workflowAppNotificationUseCase.logTimeDifferenceIfFCMIsOfNewTrip(fcmData)
                        workflowAppNotificationUseCase.sendWorkflowEventToThirdPartyApps(fcmData, cacheNewDispatchData = { cid, vehicleNumber, dispatchId ->
                            Log.d(
                                "$NOTIFICATION$DEVICE_FCM",
                                "New trip notification received, caching dispatch data.D:${dispatchId}"
                            )
                            tripCacheUseCase.cacheDispatchData(
                                cid,
                                vehicleNumber,
                                dispatchId
                            )
                        })
                        // Check if we are in a mandatory inspection
                        if (!formDataStoreManager.getValue(IS_MANDATORY_INSPECTION, false)) {
                            // If not, we continue the notification flow
                            Log.i(
                                "$NOTIFICATION$DEVICE_FCM",
                                "New FCM,not inside mandatory inspection.${fcmData}"
                            )
                            workflowAppNotificationUseCase.processReceivedFCMMessage(fcmData,
                                cacheStopAndActionData = { cid, vehicleNumber, dispatchId ->
                                    // Cache stops, actions and sync forms on stop addition/removal
                                    tripCacheUseCase.getStopsAndActions(
                                        cid,
                                        vehicleNumber,
                                        dispatchId
                                    )
                                })
                        } else {
                            Log.i(
                                "$NOTIFICATION$DEVICE_FCM",
                                "Queuing fcm message,driver is in mandatory inspection.${fcmData}"
                            )
                            // If we are, we save the notification data to display it later
                            notificationQueueUseCase.enqueueNotifications(
                                fcmData,
                                DataStoreManager.NOTIFICATION_LIST
                            )
                            launch {
                                workflowAppNotificationUseCase.sendMessageDeliveryConfirmation(
                                    fcmData
                                )
                            }
                        }
                    }

            }
        }
    }

    override fun onNewToken(newFcmToken: String) {
        super.onNewToken(newFcmToken)
        Log.d(
            "$NOTIFICATION$DEVICE_FCM",
            "newTokenReceived",
            null,
            "newToken" to newFcmToken
        )
        launch(CoroutineName(tag)) {
            cacheBackboneData()
            val cid = appModuleCommunicator.doGetCid()
            val truckNumber = appModuleCommunicator.doGetTruckNumber()
            if(appModuleCommunicator.isFirebaseAuthenticated()){
                if (cid.isEmpty() || truckNumber.isEmpty()
                ) {
                    Log.e(
                        "$NOTIFICATION$DEVICE_FCM",
                        "Error registerDeviceSpecificTokenToFireStore.Customer id ${cid}, vehicle id $truckNumber"
                    )
                    return@launch
                }
                authenticateUseCase.registerDeviceSpecificTokenToFireStore(
                    cid,
                    truckNumber,
                    newFcmToken,
                    caller ="$NOTIFICATION$DEVICE_FCM-OnNewToken"
                )
            }else{
                Log.w("$NOTIFICATION$DEVICE_FCM","Ignoring saving FCM token since device is not authenticated")
            }
        }
    }

    private suspend fun cacheBackboneData() {
        try {
            with(backboneUseCase) {
                getCustomerId()?.let {
                    appModuleCommunicator.doSetCid(it)
                }
                getVehicleId()?.let {
                    appModuleCommunicator.doSetTruckNumber(it)
                }
                getOBCId()?.let {
                    appModuleCommunicator.doSetObcId(it)
                }
            }

            Utils.setCrashReportIdentifierAfterBackboneDataCache(
                appModuleCommunicator
            )
        } catch (e: Exception) {
            Log.e("$NOTIFICATION$DEVICE_FCM", e.message, e)
        }
    }

    private fun startForegroundService() {
        startForegroundServiceIfNotStartedPreviously(RouteManifestForegroundService::class.java)
    }

    private fun getFCMDataFromRemoteMessageMap(data: MutableMap<String, String>) : FcmData{
        val gson = Gson()
        return gson.fromJson(gson.toJson(data), FcmData::class.java)
    }
}