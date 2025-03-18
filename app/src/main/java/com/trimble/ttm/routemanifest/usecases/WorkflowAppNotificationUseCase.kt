package com.trimble.ttm.routemanifest.usecases

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.trimble.ttm.commons.logger.AUTO_TRIP_START_CALLER_PUSH_NOTIFICATION
import com.trimble.ttm.commons.logger.DEVICE_FCM
import com.trimble.ttm.commons.logger.DISPATCH_BLOB
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.INBOX
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.NOTIFICATION
import com.trimble.ttm.commons.logger.STOP
import com.trimble.ttm.commons.logger.TRIP
import com.trimble.ttm.commons.logger.TRIP_IS_READY
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.model.WorkFlowEvents
import com.trimble.ttm.commons.model.WorkflowEventDataParameters
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.NEW_DISPATCH_NOTIFICATION_ID
import com.trimble.ttm.commons.utils.NEW_MESSAGE_NOTIFICATION_ID
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.commons.utils.WORKFLOWS_CHANNEL_ID
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.manager.getNotificationManager
import com.trimble.ttm.formlibrary.repo.isNewMessageNotificationReceived
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.usecases.MessageConfirmationUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.INTENT_CLASS_NAME
import com.trimble.ttm.formlibrary.utils.IS_FOR_NEW_MESSAGE
import com.trimble.ttm.formlibrary.utils.IS_FOR_NEW_TRIP
import com.trimble.ttm.formlibrary.utils.LAUNCH_MODE
import com.trimble.ttm.formlibrary.utils.NOTIFICATION_DISPATCH_DATA
import com.trimble.ttm.formlibrary.utils.SCREEN
import com.trimble.ttm.formlibrary.utils.Screen
import com.trimble.ttm.formlibrary.widget.DriverTtsWidget
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.managers.workmanager.StopRemovalNotificationWorker
import com.trimble.ttm.routemanifest.model.AppNotificationDisplayPriority
import com.trimble.ttm.routemanifest.model.BuildNotificationData
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.FcmData
import com.trimble.ttm.routemanifest.model.FcmDataState
import com.trimble.ttm.routemanifest.ui.activities.DispatchDetailActivity
import com.trimble.ttm.routemanifest.ui.activities.DispatchListActivity
import com.trimble.ttm.routemanifest.ui.activities.NotificationRedirectionActivity
import com.trimble.ttm.routemanifest.utils.DISPATCH_BLOB_REF
import com.trimble.ttm.routemanifest.utils.cid
import com.trimble.ttm.routemanifest.utils.vid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

data class NotificationStopRemovalData(
    val stopList: MutableSet<Int> = mutableSetOf(),
    val timeRemoved: Long = 0,
    val dispatchName: String = ""
)

private const val HIGH_PRIORITY_ALERT = "notification-priority"
private const val SHOULD_AUTO_DISMISS = "auto-dismiss"
private const val HIGH_PRIORITY_ALERT_COLOR = "alert-color"
private const val NOTIFICATION_DISPLAY_PRIORITY = "display-priority"
private const val HIGH_PRIORITY = 1000

class WorkflowAppNotificationUseCase(
    private val application: Application,
    private val dataStoreManager: DataStoreManager,
    private val messageConfirmationUseCase: MessageConfirmationUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase,
    private val dispatchStopsUseCase: DispatchStopsUseCase,
    private val dispatchListUseCase: DispatchListUseCase,
    private val backboneUseCase: BackboneUseCase,
    private val tripCompletionUseCase : TripCompletionUseCase
) : ICancelNotificationHelper, KoinComponent {
    private val tag = "WorkflowAppNotificationUseCase"
    private val removeExpiredTripPanelMessageUseCase: RemoveExpiredTripPanelMessageUseCase by inject {
        parametersOf((application as WorkflowApplication).applicationScope)
    }

    var stopRemovalNotifications: ConcurrentHashMap<String, NotificationStopRemovalData> =
        ConcurrentHashMap()
        private set

    fun updateMapWithNewElements(updatedMap: ConcurrentHashMap<String, NotificationStopRemovalData>) {
        stopRemovalNotifications = updatedMap
    }


    suspend fun sendInboxMessageDeliveryConfirmation(fcmData: FcmData) {
        if (fcmData.cid == appModuleCommunicator.doGetCid() && fcmData.vid == appModuleCommunicator.doGetTruckNumber()) {
            Log.i(
                tag,
                "Sending Inbox message delivery confirmation. Cid: ${fcmData.cid} T#:${fcmData.vid} Asn: ${fcmData.asn}"
            )
            messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(
                tag,
                fcmData.asn.toString(),
                null
            )
        }
    }

    suspend fun sendStopRemovalNotification(fcmData: FcmData) {
        coroutineScope {
            updateMapWithNewElements(
                putStopRemovalNotificationsInAMap(
                    stopRemovalNotifications,
                    fcmData.dispatchId,
                    fcmData.stopId,
                    fcmData.dispatchName,
                    convertDateStringToTimeStamp(fcmData.startDateTime)
                )
            )
            addWorkToTheQueue(fcmData.dispatchId)
        }
    }

    private fun addWorkToTheQueue(dispatchId: String) {
        StopRemovalNotificationWorker.enqueueStopRemovalNotificationWork(
            WorkManager.getInstance(application),
            dispatchId =  dispatchId,
            10,
            TimeUnit.SECONDS
        )
    }

    @SuppressLint("SimpleDateFormat")
    fun convertDateStringToTimeStamp(timeRemoved: String): Long {
        return try {
            val formatter: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            val date = formatter.parse(timeRemoved) as Date
            date.time
        } catch (e: Exception) {
            0
        }
    }

    suspend fun sendNewDispatchAppNotification(dispatch: Dispatch?) {
         dispatch?.let {
            createNotificationChannel()
            buildNotification(
                BuildNotificationData(
                    DispatchListActivity::class.java,
                    application.getString(R.string.new_trip_notification_title),
                    it.name,
                    NEW_DISPATCH_NOTIFICATION_ID,
                    ContextCompat.getColor(application, android.R.color.holo_red_dark),
                    HIGH_PRIORITY,
                    false,
                    AppNotificationDisplayPriority.EVERYWHERE.value,
                    it
                )
            )
             Log.i(
                 "$NOTIFICATION$TRIP",
                 "new trip notification sent to AL ${it.dispid} ${it.name}")
        }
    }

    private fun notifyToMsgWidget() {
        appModuleCommunicator.getAppModuleApplicationScope().safeLaunch(
            Dispatchers.Default +
                    CoroutineName(
                        "$tag Notify message widget"
                    )
        ) {
            application.let {
                delay(1000)
                it.sendBroadcast(
                    Intent(
                        it,
                        DriverTtsWidget::class.java
                    ).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    }
                )
            }
        }
    }

    suspend fun sendNewMessageAppNotification(asn:Int) {
        createNotificationChannel()
        notifyToMsgWidget()
        buildNotification(
            BuildNotificationData(
                MessagingActivity::class.java,
                application.getString(R.string.new_msg_notification_title),
                getNewMessageNotification(),
                NEW_MESSAGE_NOTIFICATION_ID,
                ContextCompat.getColor(application, android.R.color.holo_red_dark),
                HIGH_PRIORITY,
                false,
                AppNotificationDisplayPriority.EVERYWHERE.value,
                null,
                true
            )
        )
        Log.i(
            "$INBOX$NOTIFICATION",
            "new message notification to AL $asn")
    }

    private fun getNewMessageNotification() = application.getString(R.string.new_message_hpn_content_text)

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = application.getString(R.string.workflow_notification_channel_name)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(WORKFLOWS_CHANNEL_ID, name, importance)
            // Register the channel with the system
            getNotificationManager(application).createNotificationChannel(channel)
        }
    }

    private suspend fun buildNotification(
        buildNotificationData: BuildNotificationData
    ) {
        // Create an explicit intent for an Activity in your app
        val extras =
            createIntentBundleForHighPriorityAlert(
                buildNotificationData.priority,
                buildNotificationData.color,
                buildNotificationData.autoDismiss,
                buildNotificationData.displayPriority
            )

        val builder = NotificationCompat.Builder(application, WORKFLOWS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_app_icon)
            .setContentTitle(buildNotificationData.contentTitle)
            .setContentText(buildNotificationData.contentText)
            .setExtras(extras)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // Set the intent that will fire when the user taps the notification
        builder.setContentIntent(
            createPendingIntent(
                buildNotificationData,
                FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP,  // Since now we have a middle activity for redirection,
                0                                                    // the intent for the notification must always be a new task or a single top
            )
        )

        with(NotificationManagerCompat.from(application)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(application.applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(tag, "Permission to post notifications is not granted in buildNotification()")
                return
            }
            notify(buildNotificationData.notificationId, builder.build())
        }
    }

    private suspend fun createPendingIntent(
        buildNotificationData: BuildNotificationData, launchMode: Int, requestCode: Int
    ): PendingIntent {
        // We create the pendingIntent and led it to the redirection service
        Intent(application, NotificationRedirectionActivity::class.java).apply {
            flags = FLAG_ACTIVITY_SINGLE_TOP
            buildNotificationData.dispatch?.let {
                putExtra(NOTIFICATION_DISPATCH_DATA, buildNotificationData.dispatch)
                putExtra(IS_FOR_NEW_TRIP, true)
            }
            if (dataStoreManager.hasActiveDispatch("createPendingIntent",false)){
                putExtra(SCREEN, Screen.DISPATCH_DETAIL.ordinal)
            } else {
                putExtra(SCREEN, Screen.DISPATCH_LIST.ordinal)
            }
            putExtra(INTENT_CLASS_NAME, buildNotificationData.cls?.name)
            putExtra(IS_FOR_NEW_MESSAGE, buildNotificationData.isForNewMessage)
            putExtra(LAUNCH_MODE, launchMode)
        }.also {
            return PendingIntent.getActivity(
                application,
                requestCode,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    private fun createIntentBundleForHighPriorityAlert(
        priority: Int,
        color: Int?,
        autoDismiss: Boolean,
        displayPriority: Int
    ): Bundle {
        return Bundle().apply {
            putInt(HIGH_PRIORITY_ALERT, priority)
            color?.let {
                putInt(HIGH_PRIORITY_ALERT_COLOR, color)
            }
            putBoolean(SHOULD_AUTO_DISMISS, autoDismiss)
            putInt(NOTIFICATION_DISPLAY_PRIORITY, displayPriority)
        }
    }

    suspend fun putStopRemovalNotificationsInAMap(
        notificationList: ConcurrentHashMap<String, NotificationStopRemovalData>,
        dispatchID: String,
        stopId: Int,
        dispatchName: String, timeRemoved: Long
    ): ConcurrentHashMap<String, NotificationStopRemovalData> {
        coroutineScope {
            var existingStopData = NotificationStopRemovalData(
                mutableSetOf(),
                timeRemoved,
                dispatchName
            )
            notificationList[dispatchID]?.let {
                existingStopData = it
            }
            existingStopData.stopList.add(stopId)
            notificationList.put(dispatchID, existingStopData)
        }
        return notificationList
    }


    suspend fun isCurrentDispatchStopRemovalNotificationAvailableInTheNotificationMap(
        notificationList: ConcurrentMap<String, NotificationStopRemovalData>,
        currentDispatchId: String
    ): Boolean {
        Log.d(
            tag,
            "Checking the current dispatch  is in NotificationStopRemovalData map",
            null,
            "currentDispatchId" to currentDispatchId,
            "notificationList" to notificationList,
            "containsKey" to notificationList.containsKey(currentDispatchId),
            "Action" to "isCurrentDispatchStopRemovalNotificationAvailableInTheNotificationMap"
        )
        return coroutineScope {
            notificationList.containsKey(currentDispatchId)
        }
    }

    suspend fun getRemovedStopListOfDispatchKey(
        notificationList: ConcurrentMap<String, NotificationStopRemovalData>,
        dispatchId: String
    ): NotificationStopRemovalData {
        return coroutineScope {
            if (notificationList.containsKey(dispatchId)) {
                notificationList[dispatchId]!!
            } else {
                NotificationStopRemovalData()
            }
        }
    }

    suspend fun sortStopRemovalNotificationsBasedOnTheReceivedTime(notificationList: ConcurrentMap<String, NotificationStopRemovalData>): List<String> {
        return coroutineScope {
            notificationList.entries.sortedWith(compareBy { it.value.timeRemoved })
                .map { it.key }
        }
    }

    fun isInInManualInspectionScreen(): Boolean {
        return WorkflowApplication.isInManualInspectionScreen()
    }

    suspend fun prepareNotificationDataBasedOnPriority(
        notificationList: ConcurrentHashMap<String, NotificationStopRemovalData>,
        dispatchId: String
    ) {
        if (isInInManualInspectionScreen()) {
            if (notificationList.isNotEmpty()) {
                addWorkToTheQueue(notificationList.keys.stream().findFirst().get())
                Log.i(
                    tag,
                    "When in manual inspection screen and with a non empty notification list, adds the list to queue",
                    null,
                    "notificationList" to notificationList,
                    "Action" to "prepareNotificationDataBasedOnPriority"
                )
            }
            return
        }
        val currentDispatchId = dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, "")
        if (currentDispatchId.isEmpty() || (currentDispatchId.isNotEmpty() && checkAndGetIfCurrentDispatchRemovalNotificationAvailable(
                notificationList,
                currentDispatchId
            ).stopList.isEmpty())
        ) {
            getNonActiveDispatchDataAfterSort(notificationList, dispatchId)
            Log.i(
                tag,
                "Getting non active dispatches data based in the dispatch ID and the notification list",
                null,
                "dispatchId" to dispatchId,
                "notificationList" to notificationList,
                "Action" to "prepareNotificationDataBasedOnPriority"
            )
        } else {
            sendStopRemovalNotificationToAppLauncher(
                dispatchId,
                checkAndGetIfCurrentDispatchRemovalNotificationAvailable(
                    notificationList,
                    currentDispatchId
                ).dispatchName, DispatchDetailActivity::class.java
            )
            notificationList.remove(currentDispatchId)

            Log.i(
                tag,
                "Sending stop removal notification to AppLauncher and removing the current dispatch from notification list",
                null,
                "currentDispatchId" to currentDispatchId,
                "notificationList" to notificationList,
                "Action" to "prepareNotificationDataBasedOnPriority"
            )
        }

        updateMapWithNewElements(notificationList)

        if (notificationList.isNotEmpty()) {
            addWorkToTheQueue(notificationList.keys.stream().findFirst().get())
        }
    }

    suspend fun getNonActiveDispatchDataAfterSort(
        notificationList: ConcurrentHashMap<String, NotificationStopRemovalData>,
        dispatchId: String
    ) {
        if (notificationList.isNotEmpty()) {
            val otherDispatchStopRemovalData =
                sortStopRemovalNotificationsBasedOnTheReceivedTime(notificationList)
            val dispatchName: String =
                notificationList[otherDispatchStopRemovalData.getOrElse(0) { "-1" }]?.dispatchName
                    ?: ""
            sendStopRemovalNotificationToAppLauncher(
                dispatchId,
                dispatchName,
                null
            )
            notificationList.remove(dispatchId)
        }
    }

    internal suspend fun sendStopRemovalNotificationToAppLauncher(
        dispatchId: String,
        dispatchName: String?,
        cls: Class<*>?
    ) {
        createNotificationChannel()
        buildNotification(
            BuildNotificationData(
                cls,
                application.getString(R.string.stop_removal_notification_title),
                "$dispatchName ${application.getString(R.string.stop_removal_notification_sub_text)}",
                NEW_DISPATCH_NOTIFICATION_ID,
                ContextCompat.getColor(application, android.R.color.holo_red_dark),
                HIGH_PRIORITY,
                false,
                AppNotificationDisplayPriority.EVERYWHERE.value,
                null
            )
        )
        Log.i(
            "$NOTIFICATION$STOP",
            "stop removal notification sent to AL $dispatchId $dispatchName")
    }

    suspend fun checkAndGetIfCurrentDispatchRemovalNotificationAvailable(
        notificationList: ConcurrentHashMap<String, NotificationStopRemovalData>,
        dispatchId: String
    ): NotificationStopRemovalData {
        if (isCurrentDispatchStopRemovalNotificationAvailableInTheNotificationMap(
                notificationList,
                dispatchId
            )
        ) {
            Log.i(
                tag,
                "Checking and getting if current dispatch removal notification is available",
                null,
                "dispatchId" to dispatchId,
                "notificationList" to notificationList,
                "Action" to "checkAndGetIfCurrentDispatchRemovalNotificationAvailable"
            )

            return getRemovedStopListOfDispatchKey(
                notificationList,
                dispatchId
            )
        }
        return NotificationStopRemovalData()
    }

    private fun cancelCurrentEditOrCreationTripNotificationOnScreen() {
        NotificationManagerCompat.from(application).cancel(
            NEW_DISPATCH_NOTIFICATION_ID
        )
    }

    override fun cancelEditOrCreationTripNotification() {
        cancelCurrentEditOrCreationTripNotificationOnScreen()
    }

    // This fun is used for sending NEW_TRIP, REMOVE_STOPS events.
    suspend fun sendWorkflowEventToThirdPartyApps(
        receivedFcmData: FcmData?,
        cacheNewDispatchData: suspend (String, String, String) -> HashMap<String, String>,
    ) {
        val eventName: WorkFlowEvents?
        receivedFcmData?.let { fcmData ->
            when (buildFCMDataState(fcmData)) {
                FcmDataState.IsStopAdded -> {
                    eventName = WorkFlowEvents.ADD_STOP_EVENT
                }
                FcmDataState.IsStopDeleted -> {
                    eventName = WorkFlowEvents.REMOVE_STOP_EVENT
                }
                FcmDataState.NewTrip -> {
                    eventName = WorkFlowEvents.NEW_TRIP_EVENT
                }
                else -> {
                    eventName = null
                }
            }

            eventName?.let {

                sendWorkflowEventsToAppUseCase.sendWorkflowEvent(
                    WorkflowEventDataParameters(
                        dispatchId = fcmData.dispatchId,
                        dispatchName = if (eventName == WorkFlowEvents.NEW_TRIP_EVENT) fcmData.dispatchName else EMPTY_STRING,
                        stopId = if (eventName == WorkFlowEvents.NEW_TRIP_EVENT) EMPTY_STRING else fcmData.stopId.toString(),
                        stopName = fcmData.stopName,
                        eventName = eventName,
                        reasonCode = EMPTY_STRING,
                        message = EMPTY_STRING,
                        timeStamp = System.currentTimeMillis()
                    ),
                    caller = tag
                )
                if (eventName == WorkFlowEvents.NEW_TRIP_EVENT)
                    sendAddStopEventsForNewDispatch(
                        fcmData = fcmData,
                        stopListFromCachedData = cacheNewDispatchData(
                            fcmData.cid,
                            fcmData.vid,
                            fcmData.dispatchId
                        )
                    )
            }
        } ?: Log.e("$DEVICE_FCM$NOTIFICATION", "Received FCM data is null")
    }

    suspend fun processReceivedFCMMessage(
        receivedFcmData: FcmData?,
        coroutineScope: CoroutineScope = appModuleCommunicator.getAppModuleApplicationScope(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        cacheStopAndActionData: suspend (String, String, String) -> Unit
    ) {
            receivedFcmData?.let { fcmData ->
                when (buildFCMDataState(fcmData)) {
                    FcmDataState.NewMessage -> {
                        coroutineScope.launch(CoroutineName(tag) + dispatcher) {
                            isNewMessageNotificationReceived = true
                            Log.n(
                                "$DEVICE_FCM$NOTIFICATION$INBOX",
                                "New Message.Cid: ${fcmData.cid} T#:${fcmData.vid}",
                                throwable = null,
                                "asn" to fcmData.asn
                            )
                            sendNewMessageAppNotification(fcmData.asn)
                            launch {
                                sendInboxMessageDeliveryConfirmation(fcmData)
                            }
                        }
                    }

                    FcmDataState.IsStopAdded -> {
                        Log.n(
                            "$DEVICE_FCM$NOTIFICATION$STOP",
                            "Stop add. Cid: ${fcmData.cid} T#:${fcmData.vid} DispatchId: ${fcmData.dispatchId} StopId: ${fcmData.stopId} StopName: ${fcmData.stopName}",
                            null,
                            KEY to DISPATCH_LIFECYCLE,
                            DISPATCHID to fcmData.dispatchId,
                            STOPID to fcmData.stopId,
                        )
                        coroutineScope.launch(CoroutineName(tag) + dispatcher) {
                            if (isStopManipulatedForActiveTrip(fcmData.dispatchId)) {
                                dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
                                removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(
                                    coroutineScope = coroutineScope,
                                    stopList = dispatchStopsUseCase.getAllActiveStopsAndActions(caller = "TripEditAddStop")
                                )
                            }
                        }
                        // For caching stop and action data on stop addition
                        cacheStopAndActionData(fcmData.cid, fcmData.vid, fcmData.dispatchId)
                    }

                    FcmDataState.IsStopDeleted -> {
                        Log.d(
                            "$DEVICE_FCM$NOTIFICATION$STOP",
                            "Stop delete. Cid: ${fcmData.cid} T#:${fcmData.vid} DispatchId: ${fcmData.dispatchId} StopId: ${fcmData.stopId} StopName: ${fcmData.stopName}",
                            null,
                            DISPATCHID to fcmData.dispatchId,
                            STOPID to fcmData.stopId,
                        )
                        coroutineScope.launch(CoroutineName(tag) + dispatcher) {
                            if (isStopManipulatedForActiveTrip(fcmData.dispatchId)) {
                                Log.n(
                                    "$DEVICE_FCM$NOTIFICATION$STOP",
                                    "Stop deleted for active dispatch DispatchId: ${fcmData.dispatchId} StopId: ${fcmData.stopId} StopName: ${fcmData.stopName}",
                                    null,
                                    KEY to DISPATCH_LIFECYCLE,
                                    DISPATCHID to fcmData.dispatchId,
                                    STOPID to fcmData.stopId,
                                    "stopName" to fcmData.stopName
                                )
                                dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
                                updateTripPanelMessage(coroutineScope = coroutineScope)
                            }
                            sendStopRemovalNotification(fcmData)
                            // For caching stop and action data on stop removal
                            cacheStopAndActionData(fcmData.cid, fcmData.vid, fcmData.dispatchId)
                        }
                    }

                    FcmDataState.NewTrip -> {
                        Log.n(
                            "$DEVICE_FCM$NOTIFICATION$TRIP",
                            "New Trip. Cid: ${fcmData.cid} T#:${fcmData.vid}",
                            throwable = null,
                            "dispatchId" to fcmData.dispatchId
                        )
                        //To schedule the dispatch for auto start
                        dispatchListUseCase.getDispatchesForTheTruckAndScheduleAutoStartTrip(fcmData.cid,fcmData.vid,AUTO_TRIP_START_CALLER_PUSH_NOTIFICATION )
                    }

                    FcmDataState.Ignore -> {
                        // Ignore
                    }
                }
            } ?: Log.e("$DEVICE_FCM$NOTIFICATION", "Received FCM data is null")
    }

    suspend fun isStopManipulatedForActiveTrip(dispatchId: String): Boolean {
        return appModuleCommunicator.getCurrentWorkFlowId("TripEditFCM") == dispatchId
    }

    /**
     * Get list of stop Ids from cached stop data when a new dispatch is received
     * Send ADD_STOP_EVENT to third-party apps for each stop Id
     */
    fun sendAddStopEventsForNewDispatch(fcmData: FcmData, stopListFromCachedData: HashMap<String, String>) {
        if (stopListFromCachedData.isNotEmpty()) {
            stopListFromCachedData.forEach { stopData ->
                sendWorkflowEventsToAppUseCase.sendWorkflowEvent(
                    WorkflowEventDataParameters(
                        dispatchId = fcmData.dispatchId,
                        dispatchName = EMPTY_STRING,
                        stopId = stopData.key,
                        stopName = stopData.value,
                        eventName = WorkFlowEvents.ADD_STOP_EVENT,
                        reasonCode = EMPTY_STRING,
                        timeStamp = System.currentTimeMillis()
                    ),
                    caller = tag
                )
            }
        } else {
            Log.w(NOTIFICATION, "stopListFromCachedData is empty")
        }
    }

    internal suspend fun processDispatchBlobData(
        dispatchBlobPushMessageMap: Map<String, String>,
        getDispatchBlob: suspend (cid: String, vehicleNumber: String, blodId: String) -> DispatchBlob,
        deleteDispatchBlobDocument: suspend (cid: String, vehicleNumber: String, blodId: String) -> Unit
    ) {
        try {
            //read dispatch blob and send to tanker app
            getDispatchBlob(
                dispatchBlobPushMessageMap[cid] ?: "",
                dispatchBlobPushMessageMap[vid] ?: "",
                dispatchBlobPushMessageMap[DISPATCH_BLOB_REF] ?: ""
            ).let {
                if(it.cid == 0L || it.vehicleNumber.isEmpty() || it.id.isEmpty()){
                    Log.e(
                        "$DEVICE_FCM$NOTIFICATION$DISPATCH_BLOB",
                        "Error in processing DispatchBlobData. DispatchBlob is empty"
                    )
                    return
                }
                Log.d(
                    "$DEVICE_FCM$NOTIFICATION$DISPATCH_BLOB",
                    "DispatchBlob: ${it.blobMessage}"
                )
                sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(
                    dispatchBlob = it,
                    eventName = WorkFlowEvents.DISPATCH_BLOB_EVENT,
                    timeStamp = System.currentTimeMillis(),
                    caller = tag
                )
                deleteDispatchBlobDocument(
                    dispatchBlobPushMessageMap[cid] ?: "",
                    dispatchBlobPushMessageMap[vid] ?: "",
                    dispatchBlobPushMessageMap[DISPATCH_BLOB_REF] ?: ""
                )
            }
        }catch (e: Exception){
            Log.e("$DEVICE_FCM$NOTIFICATION$DISPATCH_BLOB", "Error in processing DispatchBlobData ${e.stackTraceToString()}")
        }
    }

    suspend fun sendMessageDeliveryConfirmation(fcmData : FcmData){
        when (buildFCMDataState(fcmData)){
            FcmDataState.NewMessage -> {
                sendInboxMessageDeliveryConfirmation(fcmData)
            }else -> {
                //do nothing
            }
        }
    }

    fun buildFCMDataState(fcmData: FcmData): FcmDataState {
        return when {
            fcmData.asn > 0 -> {
                FcmDataState.NewMessage
            }
            fcmData.isStopAdded && fcmData.stopId > -1 -> {
                FcmDataState.IsStopAdded
            }
            fcmData.isStopRemovalNotification || (fcmData.isStopDeleted && fcmData.stopId > -1) -> {
                FcmDataState.IsStopDeleted
            }
            fcmData.dispatchCreateTime.isNotEmpty() && fcmData.dispatchReadyTime.isNotEmpty() -> {
                FcmDataState.NewTrip
            }
            else -> {
                FcmDataState.Ignore
            }
        }
    }


    suspend fun logTimeDifferenceIfFCMIsOfNewTrip(fcmData: FcmData?) {
        fcmData?.let { fcmPayloadNonNull ->
            val fcmDataState= buildFCMDataState(fcmPayloadNonNull)
            if (fcmDataState == FcmDataState.NewTrip) {
                val userLogInStatus = backboneUseCase.getLoggedInUsersStatus()
                val currentUserId = backboneUseCase.getCurrentUser()
                var currentUserLogInTimeInTimeStamp = 0L
                var currentUserLogInTime = EMPTY_STRING
                userLogInStatus.filter { it.userId == currentUserId }.map {
                    currentUserLogInTime = it.whenActive.toString()
                    currentUserLogInTimeInTimeStamp = DateUtil.convertDateInstanceToUTCTimestamp(
                        it.whenActive,
                        "timeDifferenceTrip"
                    )
                }
                Log.n(
                    TRIP_IS_READY,
                    "Trip FCM time difference",
                    throwable = null,
                    "TripId" to fcmPayloadNonNull.dispatchId,
                    "TripCreatedTime" to fcmPayloadNonNull.dispatchCreateTime,
                    "TripReadyTime" to fcmPayloadNonNull.dispatchReadyTime,
                    "UserLogInTime" to currentUserLogInTime,
                    "UserId" to currentUserId,
                    "UserLoginToTripReadyTimeSeconds" to DateUtil.getDifferenceInSeconds(
                        currentUserLogInTimeInTimeStamp, fcmPayloadNonNull.dispatchReadyTime
                    ),
                    "TripCreatedToReadyTimeSeconds" to DateUtil.getDifferenceBetweenTwoDatesInSeconds(
                        fcmPayloadNonNull.dispatchCreateTime, fcmPayloadNonNull.dispatchReadyTime
                    )
                )
            }
        }
    }

    private suspend fun updateTripPanelMessage(coroutineScope: CoroutineScope) {
        val stopList = dispatchStopsUseCase.getAllActiveStopsAndActions("TripEditStopRemoval")
        removeExpiredTripPanelMessageUseCase.updateStopInformationInTripPanel(
            coroutineScope = coroutineScope,
            stopList = stopList
        )
        if (appModuleCommunicator.getCurrentWorkFlowId("TripEditStopRemoval")
                .isNotEmpty()
        ) {
            removeExpiredTripPanelMessageUseCase.removeMessageFromTripPanelQueue(
                stopList = CopyOnWriteArrayList(stopList),
                dataStoreManager = dataStoreManager
            )
        }
    }

    suspend fun processDispatchDeletion(fcmData: FcmData){
        tripCompletionUseCase.processDeletedDispatchAndSendTripCompletionEventsToPFM(fcmData)
    }
}