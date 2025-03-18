package com.trimble.ttm.routemanifest.usecases

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.launchercommunicationlib.commons.model.PanelDetails
import com.trimble.ttm.commons.logger.ARRIVAL_PROMPT_PANEL
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.NEGATIVE_GUF_BACKGROUND_TIMER
import com.trimble.ttm.commons.logger.TRIP_EDIT
import com.trimble.ttm.commons.logger.TRIP_PANEL
import com.trimble.ttm.commons.logger.TRIP_UNCOMPLETED_FORMS
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.ON_BACKGROUND_NEGATIVE_GUF_CALLER
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.commons.utils.isFeatureTurnedOn
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.customComparator.LauncherMessagePriorityComparator
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ARE_STOPS_SEQUENCED_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.CURRENT_STOP_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY
import com.trimble.ttm.routemanifest.managers.ResourceStringsManager
import com.trimble.ttm.routemanifest.managers.StringKeys
import com.trimble.ttm.routemanifest.model.Address
import com.trimble.ttm.commons.model.ArrivedGeoFenceTriggerData
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.routemanifest.model.DRIVER_NEGATIVE_GUF
import com.trimble.ttm.routemanifest.model.LastRespondedTripPanelMessage
import com.trimble.ttm.routemanifest.model.LastSentTripPanelMessage
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.TripPanelData
import com.trimble.ttm.routemanifest.model.getArrivedAction
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.model.ArrivalType
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.service.TripPanelNegativeActionShortForegroundService
import com.trimble.ttm.routemanifest.ui.activities.TripPanelPositiveActionTransitionScreenActivity
import com.trimble.ttm.routemanifest.utils.DEFAULT_TRIP_PANEL_MESSAGE_TIMEOUT_IN_SECONDS
import com.trimble.ttm.routemanifest.utils.FILL_FORMS_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.INCOMING_ARRIVED_TRIGGER
import com.trimble.ttm.routemanifest.utils.INVALID_PRIORITY
import com.trimble.ttm.routemanifest.utils.INVALID_TRIP_PANEL_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.NEXT_STOP_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.STOP_COUNT_CHANGE_LISTEN_DELAY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_AUTO_DISMISS_KEY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_COMPLETE_FORM_MSG_ICON_FILE_NAME
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_ICON_FILE_NAME
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_MESSAGE_ID_KEY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_MESSAGE_PRIORITY_KEY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_MILES_AWAY_MSG_ICON_FILE_NAME
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_SELECT_STOP_MSG_ICON_FILE_NAME
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_SELECT_STOP_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_POSITIVE_ACTION_COUNTDOWN_INTERVAL_IN_MILLISECONDS
import com.trimble.ttm.routemanifest.utils.TRIP_POSITIVE_ACTION_TIMEOUT_IN_MILLISECONDS
import com.trimble.ttm.routemanifest.utils.TRIP_POSITIVE_ACTION_TIMEOUT_IN_SECONDS
import com.trimble.ttm.routemanifest.utils.TRUE
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.fromJsonString
import com.trimble.ttm.routemanifest.utils.Utils.pollElementFromPriorityBlockingQueue
import com.trimble.ttm.routemanifest.utils.ext.getPanelMessageDisplayPriority
import com.trimble.ttm.routemanifest.utils.ext.isEqualTo
import com.trimble.ttm.routemanifest.utils.ext.isLessThan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.PriorityBlockingQueue
import kotlin.time.Duration.Companion.seconds

class TripPanelUseCase(
    private val tripPanelEventRepo: TripPanelEventRepo,
    private val backboneUseCase: BackboneUseCase,
    private val resourceStringsManager: ResourceStringsManager,
    private val sendBroadCastUseCase: SendBroadCastUseCase,
    private val localDataSourceRepo: LocalDataSourceRepo,
    private val dispatchStopsUseCase: DispatchStopsUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val context: Context,
    private val arriveTriggerDataStoreKeyManipulationUseCase: ArriveTriggerDataStoreKeyManipulationUseCase,
    private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase
): KoinComponent {
    private val arrivalReasonUsecase: ArrivalReasonUsecase by inject()
    var lastSentTripPanelMessage = LastSentTripPanelMessage()
        private set

    private var lastRespondedTripPanelMessage = LastRespondedTripPanelMessage()

    var listLauncherMessageWithPriority =
        PriorityBlockingQueue(
            1,
            LauncherMessagePriorityComparator
        )
    var isHighPriorityMessageDisplayedInPanel = false
    var isArrivedMessageDisplayedInPanel = false
    private var negativeGufTimer: CountDownTimer? = null
    internal var countDownValue = 0 // Do not set this variable. This is exposed for testing
    internal var isNegativeGufTimerRunning: Boolean = false // Do not set this variable. This is exposed for testing
    private val coroutineScope = CoroutineScope(ioDispatcher)
    private var tripPanelCountDownTimerJob : Job? = null
    private var stopCountChangeTimer: CountDownTimer? = null

    fun updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(status: Boolean) {
        isArrivedMessageDisplayedInPanel = status
        isHighPriorityMessageDisplayedInPanel = status
    }

    internal suspend fun putLauncherMessagesIntoPriorityQueue(
        putLauncherMessagesIntoPriorityQueueData: TripPanelData,
        caller: String,
        vararg stopId: Int
    ) {
        Log.d(TRIP_PANEL,"A: Inside putLauncherMessagesIntoPriorityQueue, Caller: $caller, Trip panel Data: ${putLauncherMessagesIntoPriorityQueueData.message}")

        if (putLauncherMessagesIntoPriorityQueueData.priority == TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY || putLauncherMessagesIntoPriorityQueueData.priority == TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP) {
            listLauncherMessageWithPriority.forEachIndexed { index, launcherMessageWithPriority ->
                Log.d(TRIP_PANEL,"B: Inside putLauncherMessagesIntoPriorityQueue, listLauncherMessageWithPriority data${index}-> message: ${launcherMessageWithPriority.message} messagePriority: ${launcherMessageWithPriority.messagePriority} messageId: ${launcherMessageWithPriority.messageID}")
                if (launcherMessageWithPriority.messageID == putLauncherMessagesIntoPriorityQueueData.messageId && launcherMessageWithPriority.message == putLauncherMessagesIntoPriorityQueueData.message && launcherMessageWithPriority.messagePriority == putLauncherMessagesIntoPriorityQueueData.priority) {
                    Log.d(TRIP_PANEL,"B1: Inside putLauncherMessagesIntoPriorityQueue, Incoming Did you arrive message already exists inside listLauncherMessageWithPriority.. Hence returning..")
                    return
                }
            }
        }

        val launcherMessageWithPriority =
            LauncherMessageWithPriority(
                message = putLauncherMessagesIntoPriorityQueueData.message,
                messagePriority = putLauncherMessagesIntoPriorityQueueData.priority,
                messageID = putLauncherMessagesIntoPriorityQueueData.messageId,
                location = Pair(
                    putLauncherMessagesIntoPriorityQueueData.latitude,
                    putLauncherMessagesIntoPriorityQueueData.longitude
                ),
                currentLocation = backboneUseCase.getCurrentLocation(),
                stopId = *stopId
            )

        val priorityMessageIterator =
            listLauncherMessageWithPriority.iterator()
        Log.d(TRIP_PANEL,"C: Inside putLauncherMessagesIntoPriorityQueue, listLauncherMessageWithPriority size: ${listLauncherMessageWithPriority.size}")
        while (priorityMessageIterator.hasNext()) {
            val messageWithPriority = priorityMessageIterator.next()
            Log.d(TRIP_PANEL,"C1: Inside putLauncherMessagesIntoPriorityQueue, putLauncherMessagesIntoPriorityQueueData data-> message: ${putLauncherMessagesIntoPriorityQueueData.message} messagePriority: ${putLauncherMessagesIntoPriorityQueueData.priority} messageId: ${putLauncherMessagesIntoPriorityQueueData.messageId}")
            Log.d(TRIP_PANEL,"C2: Inside putLauncherMessagesIntoPriorityQueue, messageWithPriority data-> message: ${messageWithPriority.message} messagePriority: ${messageWithPriority.messagePriority} messageId: ${messageWithPriority.messageID}")
            if ((putLauncherMessagesIntoPriorityQueueData.priority == TRIP_PANEL_SELECT_STOP_MSG_PRIORITY || putLauncherMessagesIntoPriorityQueueData.priority == TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY || putLauncherMessagesIntoPriorityQueueData.priority == TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY) && messageWithPriority.messagePriority == putLauncherMessagesIntoPriorityQueueData.priority) {
                Log.d(TRIP_PANEL,"C3: Inside putLauncherMessagesIntoPriorityQueue, Incoming trip panel message:${putLauncherMessagesIntoPriorityQueueData.message} already exists inside listLauncherMessageWithPriority. Hence removing the message from listLauncherMessageWithPriority..")
                priorityMessageIterator.remove()
                break
            }
        }

        Log.d(TRIP_PANEL,"D: Inside TripPanelUseCase putLauncherMessagesIntoPriorityQueue Adding to PriorityQueue launcherMessageWithPriority:${launcherMessageWithPriority.message}")
        listLauncherMessageWithPriority.add(
            launcherMessageWithPriority
        )
        val isSafeToRespondMessages = isSafeToRespondMessages()
        Log.d(TRIP_PANEL,"E: Inside putLauncherMessagesIntoPriorityQueue, Motion Data from Backbone: ${isSafeToRespondMessages.not()}")
        if ((putLauncherMessagesIntoPriorityQueueData.priority == TRIP_PANEL_SELECT_STOP_MSG_PRIORITY || putLauncherMessagesIntoPriorityQueueData.priority == TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY || putLauncherMessagesIntoPriorityQueueData.priority == TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY) && isSafeToRespondMessages) {
            Log.d(TRIP_PANEL,"F: Inside putLauncherMessagesIntoPriorityQueue, Sending * ${putLauncherMessagesIntoPriorityQueueData.message} * to startSendingMessagesToLocationPanel method")
            startSendingMessagesToLocationPanel(caller)
            Log.d(TRIP_PANEL,"G: Inside putLauncherMessagesIntoPriorityQueue, * ${putLauncherMessagesIntoPriorityQueueData.message} * sent to startSendingMessagesToLocationPanel method")
        }
        Log.d(TRIP_PANEL,"H: Inside putLauncherMessagesIntoPriorityQueue, putLauncherMessagesIntoPriorityQueue function execution completed successfully.. listLauncherMessageWithPriority [messageId, message, messagePriority]: ${listLauncherMessageWithPriority.map { Triple(it.messageID, it.message, it.messagePriority)}}")
    }

    suspend fun isSafeToRespondMessages(): Boolean {
        backboneUseCase.fetchEngineMotion("isSafeToRespondMessages").let {
            return (it == null || it == false)  //If backbone motion value comes as null considering motion as false
        }
    }

    private suspend fun dismissStopMessagesIfAllStopsAreCompleted() {
        dispatchStopsUseCase.getStopsFromFirestoreCacheFirst(
            "dismissStopMessagesIfAllStopsAreCompleted",
            appModuleCommunicator.doGetTruckNumber(),
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.getCurrentWorkFlowId("dismissStopMessagesIfAllStopsAreCompleted")
        ).filter { stop -> stop.deleted == 0 }.all { stop -> stop.completedTime.isNotEmpty() }.also { areAllStopsComplete ->
            if (areAllStopsComplete.not()) return
            removeMessageFromPriorityQueueIfAvailable(SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID)
            removeMessageFromPriorityQueueIfAvailable(NEXT_STOP_MESSAGE_ID)
            if (lastSentTripPanelMessage.messageId == SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID || lastSentTripPanelMessage.messageId == NEXT_STOP_MESSAGE_ID) {
                dismissTripPanelMessage(lastSentTripPanelMessage.messageId)
            }
        }
    }

    suspend fun putArrivedMessagesIntoPriorityQueue() {
        arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
            .let { listArrivedTriggerDataFromPreference ->
                if (listArrivedTriggerDataFromPreference.isEmpty()) return@let
                Log.d(
                    TRIP_PANEL,
                    "Inside TripPanel UseCase putArrivedMessagesIntoPriorityQueue listArrivedTriggerDataFromPreference: ${listArrivedTriggerDataFromPreference[0].messageId} ***** list:${listArrivedTriggerDataFromPreference}"
                )
                handleTripPanelDYAUpdateForCurrentStop()
                handleTripPanelDYAUpdateForArrivedStops(listArrivedTriggerDataFromPreference)
            }
        handleTripPanelMsgUpdateWithCorrectMsgPriority()
    }

    suspend fun handleTripPanelDYAUpdateForCurrentStop() {
        localDataSourceRepo.getFromAppModuleDataStore(CURRENT_STOP_KEY, EMPTY_STRING).let { currentStopString ->
            Log.d(TRIP_PANEL, "Inside TripPanel UseCase handleTripPanelDYAUpdateForCurrentStop currentStopString: $currentStopString")
            if (currentStopString.isEmpty()) return
            fromJsonString<Stop>(currentStopString)?.let { currentStopFromPreference ->
                if (arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(
                        currentStopFromPreference.stopId
                    ).not()
                ) return
                putLauncherMessagesIntoPriorityQueue(
                    TripPanelData(
                        String.format(
                            resourceStringsManager.getStringsForTripCacheUseCase()
                                .getOrDefault(StringKeys.DID_YOU_ARRIVE_AT, ""),
                            currentStopFromPreference.stopName
                        ),
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP,
                        currentStopFromPreference.stopId,
                        currentStopFromPreference.latitude,
                        currentStopFromPreference.longitude,
                    ), "handleTripPanelDYAUpdateForCurrentStop", currentStopFromPreference.stopId
                )
            }
        }
    }

    suspend fun handleTripPanelDYAUpdateForArrivedStops(
        listArrivedTriggerDataFromPreference: ArrayList<ArrivedGeoFenceTriggerData>
    ) {
        listArrivedTriggerDataFromPreference.forEach { arrivedTriggerData ->
            fetchDispatchStopsAndActionsUseCase.getSortedStopsDataWithoutActions(arrivedTriggerData.messageId)
                ?.let { stop ->
                    Log.i(TRIP_PANEL, "Arrived stop id is ${stop.stopid}")
                    putLauncherMessagesIntoPriorityQueue(
                        TripPanelData(
                            String.format(
                                resourceStringsManager.getStringsForTripCacheUseCase()
                                    .getOrDefault(StringKeys.DID_YOU_ARRIVE_AT, ""),
                                stop.name
                            ), TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY, stop.stopid,
                            stop.latitude, stop.longitude,
                        ), "handleTripPanelDYAUpdateForArrivedStops", stop.stopid
                    )
                }
        }
    }

    suspend fun handleTripPanelMsgUpdateWithCorrectMsgPriority() {
        if (isSafeToRespondMessages().not()) return
        startSendingMessagesToLocationPanel("handleTripPanelMsgUpdateWithCorrectMsgPriority")
    }

    suspend fun removeArrivedTriggersFromPreferenceIfRespondedByUser(
        messageId: Int
    ) = arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(messageId) { stopId ->
        removeMessageFromPriorityQueueAndUpdateTripPanelFlags(stopId)
    }

    suspend fun startSendingMessagesToLocationPanel(
        caller: String = EMPTY_STRING
    ): Int {
        listLauncherMessageWithPriority.forEach {
            if (it.isNull()) return@forEach
            Log.d(
                TRIP_PANEL,
                "-----Inside TPUC startSendingMessagesToLocationPanel Message: - ${it.message} Priority: ${it.messagePriority} caller:$caller-----"
            )
        }
        dismissStopMessagesIfAllStopsAreCompleted()
        if (localDataSourceRepo.isKeyAvailableInAppModuleDataStore(ACTIVE_DISPATCH_KEY).not() || listLauncherMessageWithPriority.isEmpty()) {
            return INVALID_PRIORITY
        }
        removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(caller)
        val priority = listLauncherMessageWithPriority.peek()?.messagePriority
        Log.d(
            TRIP_PANEL,
            "-----Inside TPUC startSendingMessagesToLocationPanel PQ peek Priority: $priority isArrivedMessageDisplayedInPanel:${isArrivedMessageDisplayedInPanel}  caller:$caller"
        )
        priority?.let {
            when {
                priority.isLessThan(getLowestPriorityMsg()) and isArrivedMessageDisplayedInPanel.not()
                -> {
                    localDataSourceRepo.getFromAppModuleDataStore(
                        UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                        EMPTY_STRING
                    ).let {
                        var formList: ArrayList<DispatchFormPath> = ArrayList()
                        if (it.isNotEmpty()) {
                            formList = Gson().fromJson(
                                it,
                                object : TypeToken<ArrayList<DispatchFormPath>>() {}.type
                            )
                        }
                        Log.d(
                            TRIP_PANEL,
                            "TPUC startSendingMessagesToLocationPanel formList: $it"
                        )
                        return when {
                            priority.isEqualTo(getHighestPriorityMsg()) or priority.isEqualTo(
                                getHighPriorityMsg()
                            ) -> {
                                Log.d(
                                    TRIP_PANEL,
                                    "TPUC startSendingMessagesToLocationPanel calling prepareLocationPanelDataAndSend for getHighPriorityMsg"
                                )
                                listLauncherMessageWithPriority.peek()?.messageID.let { stopId ->
                                    stopId?.let {
                                        if (needToShowNegativeGufTimerIfAvailable(stopId)) {
                                            scheduleBackgroundTimer(
                                                stopId,
                                                TRIP_PANEL
                                            )
                                        }
                                    }
                                }

                                prepareLocationPanelDataAndSend(
                                    AppLauncherCommunicator,
                                    countDownValue
                                )
                                getHighPriorityMsg()
                            }

                            formList.isNotEmpty() and priority.isEqualTo(
                                getMediumPriorityMsg()
                            ) -> {
                                Log.d(
                                    TRIP_PANEL,
                                    "-----Inside TPUC startSendingMessagesToLocationPanel calling prepareLocationPanelDataAndSend for getMediumPriorityMsg"
                                )

                                prepareLocationPanelDataAndSend(
                                    AppLauncherCommunicator
                                )
                                getMediumPriorityMsg()
                            }

                            (formList.isEmpty() and priority.isEqualTo(getLowPriorityMsg())) or (formList.isNotEmpty() and priority.isEqualTo(
                                getLowPriorityMsg()
                            ) and isHighPriorityMessageDisplayedInPanel.not()) -> {
                                Log.d(
                                    TRIP_PANEL,
                                    "-----Inside TPUC startSendingMessagesToLocationPanel calling prepareLocationPanelDataAndSend for getLowPriorityMsg"
                                )

                                prepareLocationPanelDataAndSend(
                                    AppLauncherCommunicator
                                )
                                getLowPriorityMsg()
                            }

                            else -> INVALID_PRIORITY
                        }
                    }
                }

                isArrivedMessageDisplayedInPanel.not() and isHighPriorityMessageDisplayedInPanel.not()
                        and priority.isEqualTo(getLowestPriorityMsg())
                -> {
                    Log.d(
                        TRIP_PANEL,
                        "-----Inside TPUC startSendingMessagesToLocationPanel calling prepareLocationPanelDataAndSend for getLowestPriorityMsg"
                    )
                    prepareLocationPanelDataAndSend(
                        AppLauncherCommunicator
                    )
                    return getLowestPriorityMsg()
                }
            }
            return INVALID_PRIORITY
        }
        return INVALID_PRIORITY
    }

    suspend fun scheduleBackgroundTimer(
        stopId: Int,
        caller: String
    ) {
        if (countDownValue == 0 && isNegativeGufTimerRunning.not()) {
            withContext(mainDispatcher) {
                negativeGufTimer = object : CountDownTimer(
                    TRIP_POSITIVE_ACTION_TIMEOUT_IN_MILLISECONDS,
                    TRIP_POSITIVE_ACTION_COUNTDOWN_INTERVAL_IN_MILLISECONDS
                ) {
                    override fun onTick(millisUntilFinished: Long) {
                        if (tripPanelCountDownTimerJob?.isActive?.not() == true || tripPanelCountDownTimerJob == null) {
                            tripPanelCountDownTimerJob = coroutineScope.launch(
                                CoroutineName("backgroundTimerOnTick")
                            ) {
                                publishCountDownTimerValueBasedOnMotionStatus(millisUntilFinished)
                            }
                        }
                    }

                    override fun onFinish() {
                        coroutineScope.launch(
                            CoroutineName("backgroundTimerOnFinish")
                        ) {
                            if (isNegativeGufTimerRunning) {
                                cancelNegativeGufTimer("backgroundTimerOnFinish")

                                /** Adding the delay because onMessageDismissed needs to be mark the arrival first,
                                if not this background timer will mark the arrival **/
                                delay(5.seconds)

                                setLastRespondedTripPanelMessage(
                                    stopId
                                )

                                removeMessageFromPriorityQueueIfAvailable(
                                    stopId
                                )
                                removeArrivedTriggersFromPreferenceIfRespondedByUser(
                                    stopId
                                )
                                val activeDispatchId =
                                    appModuleCommunicator.getCurrentWorkFlowId("onMessageDismiss")

                                Log.d(
                                    NEGATIVE_GUF_BACKGROUND_TIMER,
                                    "backgroundTimerElapsed: Dispatch ID: $activeDispatchId Message ID: $stopId "
                                )

                                val arrivalReasonMap = arrivalReasonUsecase.getArrivalReasonMap(
                                    ArrivalType.TIMER_EXPIRED.toString(),
                                    stopId,
                                    true)
                                arrivalReasonUsecase.updateArrivalReasonForCurrentStop(stopId, arrivalReasonMap)

                                val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                                    reasonType = StopActionReasonTypes.TIMEOUT.name,
                                    negativeGuf = true
                                )
                                dispatchStopsUseCase.performActionsAsDriverAcknowledgedArrivalOfStop(
                                    activeDispatchId,
                                    stopId,
                                    context,
                                    pfmEventsInfo,
                                    ON_BACKGROUND_NEGATIVE_GUF_CALLER
                                )

                                updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(
                                    false
                                )
                            }
                        }

                    }

                }
                /** Starting the Background negative GUF timer only if not running already
                 * marking the isNegativeGufTimerRunning = true for having a check in onFinish
                 * set the countDownValue = TRIP_POSITIVE_ACTION_TIMEOUT_IN_SECONDS to run the timer
                 */
                isNegativeGufTimerRunning = true
                countDownValue = TRIP_POSITIVE_ACTION_TIMEOUT_IN_SECONDS
                negativeGufTimer?.start()
                Log.d(NEGATIVE_GUF_BACKGROUND_TIMER, "negativeGufTimer started caller $caller")
            }
        }
    }

    private suspend fun publishCountDownTimerValueBasedOnMotionStatus(
        millisUntilFinished: Long
    ) {
        if (isSafeToRespondMessages()) {
            countDownValue =
                (millisUntilFinished / TRIP_POSITIVE_ACTION_COUNTDOWN_INTERVAL_IN_MILLISECONDS).toInt()
            WorkflowEventBus.postNegativeGufTimerValue(countDownValue)
        } else {
            cancelNegativeGufTimer("vehicleInMotion")
        }
    }

    suspend fun updateStopInformationInTripPanel(
        scope: CoroutineScope,
        stopList: List<StopDetail>
    ): Unit = withContext(mainDispatcher) {
        Log.d(
            TRIP_EDIT,
            "-----Inside TripPanelUseCase starting the timer to update trip panel message on trip edit-----"
        )
        stopCountChangeTimer?.cancel()
        stopCountChangeTimer = object : CountDownTimer(
            STOP_COUNT_CHANGE_LISTEN_DELAY,
            STOP_COUNT_CHANGE_LISTEN_DELAY
        ) {
            override fun onTick(millisUntilFinished: Long) {
                // Ignore
            }

            override fun onFinish() {
                scope.launch(ioDispatcher) {
                    setCurrentStopAndUpdateTripPanel(stopList)
                    if (dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip()) {
                        Log.d(
                            TRIP_EDIT,
                            "listenForActiveDispatchStopManipulation - Stop manipulated when app was in the background. Ignoring call to copilot with updated trip stop data"
                        )
                        return@launch
                    }
                }
            }
        }
        stopCountChangeTimer?.start()
    }

    internal suspend fun setCurrentStopAndUpdateTripPanel(stopList: List<StopDetail>) {
        stopList.filter { it.completedTime.isEmpty() && it.deleted == 0 }
            .also { uncompletedStopList ->
                Log.d(
                    TRIP_EDIT,
                    "listenForActiveDispatchStopManipulation - uncompletedStopList - ${
                        uncompletedStopList.map {
                            Pair(
                                it.stopid,
                                it.dispid
                            )
                        }
                    }"
                )
                if (uncompletedStopList.isNotEmpty()) {
                    dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
                        CopyOnWriteArrayList<StopDetail>(uncompletedStopList)
                    ).let { isSequenceTrip ->
                        if (!isSequenceTrip) sendMessageToLocationPanelBasedOnCurrentStop()
                    }
                }
            }
    }

    fun cancelNegativeGufTimer(caller: String) {
        Log.d(
            NEGATIVE_GUF_BACKGROUND_TIMER,
            "cancelNegativeGufTimer invoked caller $caller countDownValue $countDownValue"
        )
        negativeGufTimer?.cancel()
        isNegativeGufTimerRunning = false
        countDownValue = 0
        WorkflowEventBus.disposeNegativeGufTimerCacheOnTimerStop()
        if(tripPanelCountDownTimerJob?.isActive == true) {
            tripPanelCountDownTimerJob?.cancel()
        }
    }

    suspend fun sendMessageToLocationPanelBasedOnCurrentStop(): Boolean {
        if (localDataSourceRepo.isKeyAvailableInAppModuleDataStore(ACTIVE_DISPATCH_KEY).not()) return false
        val currentStopString = localDataSourceRepo.getFromAppModuleDataStore(CURRENT_STOP_KEY, EMPTY_STRING)
        //if currentStopString is empty fromJsonString will return null. so, the Stop is nullable here
        val currentStop: Stop? = fromJsonString<Stop>(currentStopString)
        putArrivedMessagesIntoPriorityQueue()
        //TODO: check form completion if all the stops completed
        if (currentStop.isNull()) {
            if (localDataSourceRepo.getFromAppModuleDataStore(ARE_STOPS_SEQUENCED_KEY, TRUE)) return false
            putLauncherMessagesIntoPriorityQueue(
                TripPanelData(
                    resourceStringsManager.getStringsForTripCacheUseCase()
                        .getOrDefault(StringKeys.SELECT_STOP_TO_NAVIGATE, ""),
                    TRIP_PANEL_SELECT_STOP_MSG_PRIORITY, SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID,
                    0.toDouble(), 0.toDouble(),
                ), "sendMessageToLocationPanelBasedOnCurrentStop", -1
            )
            return true
        } else {
            if (currentStop!!.stopName.isEmpty()) return false
            putLauncherMessagesIntoPriorityQueue(
                TripPanelData(
                    String.format(
                        resourceStringsManager.getStringsForTripCacheUseCase()
                            .getOrDefault(StringKeys.YOUR_NEXT_STOP, ""),
                        currentStop.stopName,
                        getAddressBasedOnFeatureFlag(currentStop)
                    ),
                    TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                    NEXT_STOP_MESSAGE_ID,
                    0.toDouble(),
                    0.toDouble(),
                ), "sendMessageToLocationPanelBasedOnCurrentStop", currentStop.stopId
            )
            return true
        }
    }

    private suspend fun getAddressBasedOnFeatureFlag(
        currentStop: Stop
    ): String {
        if (appModuleCommunicator.getFeatureFlags().isFeatureTurnedOn(
                FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_ADDRESS
            )
        ) {
            fromJsonString<Address>(
                localDataSourceRepo.getStopAddressFromAppModuleDataStore(
                    currentStop.stopId
                )
            )?.let { stopAddress ->
                return Utils.getFormattedAddress(
                    stopAddress, true
                )
            }
        }
        return ""
    }

    suspend fun prepareLocationPanelDataAndSend(
        launcherCommunicator: AppLauncherCommunicator,
        negativeGufTimerValue: Int = TRIP_POSITIVE_ACTION_TIMEOUT_IN_SECONDS
    ): Pair<HostAppState, String> {
        Log.i(
            TRIP_PANEL,
            "Host app state is ${launcherCommunicator.getHostAppState().name} while sending trip panel message."
        )
        return when (launcherCommunicator.getHostAppState()) {
            HostAppState.SERVICE_DISCONNECTED -> {
                tripPanelEventRepo.retryConnection()
                Log.i(
                    TRIP_PANEL,
                    "Retrying connection to trip panel host app"
                )
                Pair(HostAppState.SERVICE_DISCONNECTED, EMPTY_STRING)
            }

            HostAppState.SERVICE_BINDING_DEAD -> {
                tripPanelEventRepo.retryConnection()
                Log.i(
                    TRIP_PANEL,
                    "Retrying connection to trip panel host app"
                )
                Pair(HostAppState.SERVICE_BINDING_DEAD, EMPTY_STRING)
            }

            HostAppState.NOT_READY_TO_PROCESS -> {
                Pair(HostAppState.NOT_READY_TO_PROCESS, EMPTY_STRING)
            }

            HostAppState.SERVICE_CONNECTED -> {
                Pair(HostAppState.SERVICE_CONNECTED, EMPTY_STRING)
            }

            HostAppState.READY_TO_PROCESS -> {
                val launcherMessagePriority = listLauncherMessageWithPriority.peek() ?: LauncherMessageWithPriority()
                if (launcherMessagePriority.messageID == INVALID_TRIP_PANEL_MESSAGE_ID) {
                    Log.d(
                        TRIP_PANEL,
                        "Inside TripPanelUseCase prepareLocationPanelDataAndSend listLauncherMessageWithPriority is empty"
                    )
                    return Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING)
                }
                // If the message is DYA and if app is in foreground, then local broadcast to show DYA in alert dialog and return.
                // DYA - Did You Arrive
                val messagePriority = launcherMessagePriority.messagePriority
                if (checkMessagePriorityIsDidYouArrive(messagePriority)) {
                        sendBroadCastUseCase.sendLocalBroadCast(
                            Intent(INCOMING_ARRIVED_TRIGGER),
                            "$TRIP_PANEL :Method:prepareLocationPanelDataAndSend"
                        )
                        return Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING)
                }
                // Poll the message from the priority queue and see if its message is empty.
                // If empty, then there is no message to display in trip panel. So, just return.
                var nextPriorityMessage: String = EMPTY_STRING
                var nextPriorityMessageWithStopTitleAndMessage: List<String> = listOf()
                if (messagePriority == TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY) {
                    nextPriorityMessageWithStopTitleAndMessage = pollStopTitleAndMessageFromPriorityBlockingQueue()
                    if (nextPriorityMessageWithStopTitleAndMessage.isEmpty()) {
                        Log.d(
                            TRIP_PANEL,
                            "Inside TripPanelUseCase prepareLocationPanelDataAndSend nextPriorityMessageWithStopTitleAndMessage is empty. So,returning."
                        )
                        return Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING)
                    }
                } else {
                    nextPriorityMessage = pollMessageFromPriorityBlockingQueue()
                    if (nextPriorityMessage.isEmpty()) {
                        Log.d(
                            TRIP_PANEL,
                            "Inside TripPanelUseCase prepareLocationPanelDataAndSend nextPriorityMessage is empty. So,returning."
                        )
                        return Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING)
                    }
                }
                return prepareMessageToDisplayInTheTripPanelBasedOnMessagePriority(
                    launcherMessagePriority,
                    messagePriority,
                    negativeGufTimerValue,
                    nextPriorityMessage,
                    nextPriorityMessageWithStopTitleAndMessage
                )
            }
        }
    }

    private suspend fun prepareMessageToDisplayInTheTripPanelBasedOnMessagePriority(
        launcherMessagePriority: LauncherMessageWithPriority,
        messagePriority: Int,
        negativeGufTimerValue: Int,
        nextPriorityMessage: String,
        nextPriorityMessageWithStopTitleAndMessage: List<String>
    ): Pair<HostAppState, String> {
        val messageId: Int = launcherMessagePriority.messageID

        var negativeActionPendingIntent: PendingIntent?
        var positiveActionPendingIntent: PendingIntent?
        var positiveActionAutoDismissPendingIntent: PendingIntent?
        val pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

            // Negative action pending intent
            val negativeActionIntent = Intent(context, TripPanelNegativeActionShortForegroundService::class.java).apply {
                putExtra(TRIP_PANEL_MESSAGE_ID_KEY, messageId.toString())
                putExtra(TRIP_PANEL_MESSAGE_PRIORITY_KEY, launcherMessagePriority.messagePriority.toString())
            }
            negativeActionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, 0, negativeActionIntent, pendingIntentFlag)
            } else PendingIntent.getService(context, 0, negativeActionIntent, pendingIntentFlag)

            // Positive action pending intent
            val positiveActionIntent = Intent(context, TripPanelPositiveActionTransitionScreenActivity::class.java).apply {
                putExtra(TRIP_PANEL_MESSAGE_ID_KEY, messageId.toString())
                putExtra(TRIP_PANEL_MESSAGE_PRIORITY_KEY, launcherMessagePriority.messagePriority.toString())
                putExtra(TRIP_PANEL_AUTO_DISMISS_KEY, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            positiveActionPendingIntent = PendingIntent.getActivity(context, 1, positiveActionIntent, pendingIntentFlag)

            // Positive action auto dismiss pending intent
            val positiveActionAutoDismissIntent = Intent(context, TripPanelPositiveActionTransitionScreenActivity::class.java).apply {
                putExtra(TRIP_PANEL_MESSAGE_ID_KEY, messageId.toString())
                putExtra(TRIP_PANEL_MESSAGE_PRIORITY_KEY, launcherMessagePriority.messagePriority.toString())
                putExtra(TRIP_PANEL_AUTO_DISMISS_KEY, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            positiveActionAutoDismissPendingIntent = PendingIntent.getActivity(context, 2, positiveActionAutoDismissIntent, pendingIntentFlag)

        when (messagePriority) {
            TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP, TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY -> {
                Log.d(
                    TRIP_PANEL,
                    "Inside TripPanel UseCase preparing Did you arrive trip panel message"
                )
                updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(true)
                var autoDismissTime = DEFAULT_TRIP_PANEL_MESSAGE_TIMEOUT_IN_SECONDS
                if (needToShowNegativeGufTimerIfAvailable(messageId)) {
                    autoDismissTime = negativeGufTimerValue
                }
                Log.n(
                    ARRIVAL_PROMPT_PANEL,
                    "Arrived trigger data in preference - ${
                        arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
                    }"
                )
                localDataSourceRepo.setIsDyaShownForStop(true)
                Log.n(ARRIVAL_PROMPT_PANEL, "DYA message sent to Launcher", null,
                    KEY to DISPATCH_LIFECYCLE,
                    "Launcher Message Priority" to launcherMessagePriority,
                    STOPID to launcherMessagePriority.messageID,
                    "messageId" to launcherMessagePriority.stopId, //logging message Id as well to refactor in future release
                    DISPATCHID to appModuleCommunicator.getCurrentWorkFlowId("prepareMessageToDisplayInTheTripPanelBasedOnMessagePriority")
                    )

                return sendPanelDetailsToLauncher(
                    panelDetails = PanelDetails(
                        message = nextPriorityMessage,
                        autoDismissTime = autoDismissTime,
                        imageSourceURI = Utils.getTripPanelIconUri(
                            TRIP_PANEL_DID_YOU_ARRIVE_MSG_ICON_FILE_NAME
                        ),
                        positiveButtonText = resourceStringsManager.getStringsForTripCacheUseCase()
                            .getOrDefault(StringKeys.YES, EMPTY_STRING),
                        negativeButtonText = resourceStringsManager.getStringsForTripCacheUseCase()
                            .getOrDefault(StringKeys.NO, EMPTY_STRING),
                        displayPriority = messagePriority.getPanelMessageDisplayPriority(),
                        negativeIntent =negativeActionPendingIntent,
                        positiveIntent = positiveActionPendingIntent,
                        autoDismissIntent = positiveActionAutoDismissPendingIntent
                    ), launcherMessagePriority, messageId
                )
            }

            TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY -> {
                Log.d(
                    TRIP_PANEL,
                    "Inside TripPanel UseCase preparing complete form trip panel message"
                )
                isHighPriorityMessageDisplayedInPanel = true
                return sendPanelDetailsToLauncher(
                    panelDetails = PanelDetails(
                        message = nextPriorityMessage,
                        imageSourceURI = Utils.getTripPanelIconUri(
                            TRIP_PANEL_COMPLETE_FORM_MSG_ICON_FILE_NAME
                        ),
                        positiveButtonText = resourceStringsManager.getStringsForTripCacheUseCase()
                            .getOrDefault(StringKeys.OPEN, EMPTY_STRING),
                        negativeButtonText = resourceStringsManager.getStringsForTripCacheUseCase()
                            .getOrDefault(StringKeys.DISMISS, EMPTY_STRING),
                        displayPriority = messagePriority.getPanelMessageDisplayPriority(),
                        negativeIntent = negativeActionPendingIntent,
                        positiveIntent = positiveActionPendingIntent
                    ), launcherMessagePriority, messageId
                )
            }

            TRIP_PANEL_SELECT_STOP_MSG_PRIORITY -> {
                Log.d(
                    TRIP_PANEL,
                    "Inside TripPanel UseCase preparing select a stop to navigate trip panel message"
                )
                isHighPriorityMessageDisplayedInPanel = true
                return sendPanelDetailsToLauncher(
                    panelDetails = PanelDetails(
                        message = nextPriorityMessage,
                        imageSourceURI = Utils.getTripPanelIconUri(
                            TRIP_PANEL_SELECT_STOP_MSG_ICON_FILE_NAME
                        ),
                        positiveButtonText = resourceStringsManager.getStringsForTripCacheUseCase()
                            .getOrDefault(StringKeys.OK, EMPTY_STRING),
                        displayPriority = messagePriority.getPanelMessageDisplayPriority(),
                        positiveIntent = positiveActionPendingIntent
                    ), launcherMessagePriority, messageId
                )
            }

            TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY -> {
                Log.d(
                    TRIP_PANEL,
                    "Inside TripPanel UseCase preparing next stop address trip panel message"
                )
                updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
                return sendPanelDetailsToLauncher(
                    panelDetails = PanelDetails(
                        title = nextPriorityMessageWithStopTitleAndMessage.first(),
                        message = nextPriorityMessageWithStopTitleAndMessage.last(),
                        imageSourceURI = Utils.getTripPanelIconUri(
                            TRIP_PANEL_MILES_AWAY_MSG_ICON_FILE_NAME
                        ),
                        displayPriority = messagePriority.getPanelMessageDisplayPriority()
                    ), launcherMessagePriority, messageId
                )
            }

            else -> {
                Log.e(TRIP_PANEL, "Invalid trip panel type. $messagePriority")
                return Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING)
            }
        }
    }

    private fun pollMessageFromPriorityBlockingQueue(): String = pollElementFromPriorityBlockingQueue(listLauncherMessageWithPriority)

    private fun pollStopTitleAndMessageFromPriorityBlockingQueue(): List<String> {
        val messageData = pollMessageFromPriorityBlockingQueue()
        return if (messageData.isEmpty()) emptyList()
        else {
            val stopNameAndAddress = messageData.split("\n")
            Log.d(
                TRIP_PANEL,
                "Inside TripPanelUseCase preparePanelDataForNextStopAddressMsg, stopNameAndAddress: $stopNameAndAddress"
            )
            stopNameAndAddress
        }
    }

    internal fun checkMessagePriorityIsDidYouArrive(messagePriority: Int) =
        (messagePriority == TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY || messagePriority == TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP) && WorkflowApplication.isDispatchActivityVisible()

    internal suspend fun sendPanelDetailsToLauncher(
        panelDetails: PanelDetails,
        launcherMessagePriority: LauncherMessageWithPriority,
        messageId: Int,
    ): Pair<HostAppState, String> {
        if (panelDetails.message.isEmpty()) return Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING)
        lastSentTripPanelMessage = LastSentTripPanelMessage(
            messageId,
            panelDetails.message,
            *launcherMessagePriority.stopId
        )
        localDataSourceRepo.setLastSentTripPanelMessageId(messageId)
        tripPanelEventRepo.sendEvent(messageId, panelDetails)
        Log.i(
            TRIP_PANEL,
            "Trip panel message: ${panelDetails.title} ${panelDetails.message} ${if (panelDetails.autoDismissTime > 0) "${panelDetails.autoDismissTime} s" else ""}"
        )
        return Pair(HostAppState.READY_TO_PROCESS, panelDetails.message)
    }

    suspend fun needToShowNegativeGufTimerIfAvailable(
        messageId: Int
    ):Boolean {
        val currentStop = dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(
            ARRIVAL_PROMPT_PANEL,
            stopId = messageId
        )
        var isGufAvailable = false
        currentStop?.getArrivedAction()?.gufType?.let {
            isGufAvailable = if (it == DRIVER_NEGATIVE_GUF) {
                true
            } else {
                Log.d(
                    ARRIVAL_PROMPT_PANEL,
                    "Guf type is not negative guf",
                    throwable = null,
                    "dispatchId" to currentStop.dispid,
                    "stopId" to messageId,
                    "negGufType" to it
                )
                false
            }
        } ?:run {
            Log.w(
                ARRIVAL_PROMPT_PANEL,
                "stop/arrived action/guf type is null",
                throwable = null,
                "dispatchId" to currentStop?.dispid,
                "stopId" to messageId,
                "stopData" to currentStop
            )
            isGufAvailable = false
        }
        return isGufAvailable
    }

    suspend fun putArrivedGeoFenceTriggersIntoCache(
        activeDispatchId: String,
        triggeredGeoFenceStopId: Int,
    ) {
        Log.d(TRIP_PANEL, "GeofenceEventIntoCache D$activeDispatchId S$triggeredGeoFenceStopId")
        withContext(defaultDispatcher) {
            fetchDispatchStopsAndActionsUseCase.getAllActiveSortedStopsWithoutActions("putArrivedGeoFenceTriggersIntoCache").let { stopList ->
                if (stopList.isEmpty()) {
                    Log.e(
                        TRIP_PANEL,
                        "GeofenceEventIntoCacheStopsEmpty D$activeDispatchId S$triggeredGeoFenceStopId"
                    )
                } else {
                    stopList.find { stopDetail -> stopDetail.stopid == triggeredGeoFenceStopId }
                        ?.let { stopDetail ->
                            if (stopDetail.completedTime.isEmpty()) {
                                Log.d(
                                    TRIP_PANEL,
                                    "GeofenceEventGetArrivedTriggerData D$activeDispatchId S$triggeredGeoFenceStopId"
                                )
                                arriveTriggerDataStoreKeyManipulationUseCase.putArrivedTriggerDataIntoPreference(triggeredGeoFenceStopId)
                            }
                        }
                }
            }
        }
    }

    suspend fun removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(caller: String) {
        Log.d(TRIP_PANEL, "invoked removeDidYouArriveMessagesOfStopIfCompletedOrDeleted caller : $caller")
        dispatchStopsUseCase.getStopsFromFirestoreCacheFirst(
            "removeDidYouArriveMessagesOfStopIfCompletedOrDeleted",
            appModuleCommunicator.doGetTruckNumber(),
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.getCurrentWorkFlowId("removeDidYouArriveMessagesOfStopIfCompletedOrDeleted")
        ).filter { stop -> stop.completedTime.isNotEmpty() || stop.deleted == 1 }.map {
            removeArrivedTriggersFromPreferenceIfRespondedByUser(
                it.stopid
            )
        }
    }

    fun removeMessageFromPriorityQueueAndUpdateTripPanelFlags(messageId: Int) {
        removeMessageFromPriorityQueueIfAvailable(messageId)
        if (lastSentTripPanelMessage.messageId == messageId) {
            updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(
                false
            )
        }
    }

    suspend fun checkForCompleteFormMessages() {
        localDataSourceRepo.getFromAppModuleDataStore(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING)
            .let { formStackKey ->
                var formList: ArrayList<DispatchFormPath> = ArrayList()
                if (formStackKey.isNotEmpty()) {
                    formList = Gson().fromJson(
                        formStackKey,
                        object : TypeToken<ArrayList<DispatchFormPath>>() {}.type
                    )
                }

                if (formList.isEmpty() && lastSentTripPanelMessage.messageId == FILL_FORMS_MESSAGE_ID) {
                    removeMessageFromPriorityQueueIfAvailable(
                        TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY
                    )
                    dismissTripPanelMessage(lastSentTripPanelMessage.messageId)
                }
                if (formList.isNotEmpty()) {
                    Log.i(
                        "${TRIP_PANEL}${TRIP_UNCOMPLETED_FORMS}",
                        "StopIds: ${formList.map { it.stopId }} Forms: ${formList.map { it.formId }}"
                    )
                    val stopIdList = mutableListOf<Int>()
                    if (formList.size > 1) {
                        formList.forEach { dispatchFormPath ->
                            stopIdList.add(dispatchFormPath.stopId)
                        }
                        val message = dispatchStopsUseCase.getUncompletedFormsMessage(
                            formList,
                            resourceStringsManager.getStringsForTripCacheUseCase()
                                .getOrDefault(StringKeys.COMPLETE_FORM_FOR_STOP, ""),
                            resourceStringsManager.getStringsForTripCacheUseCase()
                                .getOrDefault(StringKeys.COMPLETE_FORM_FOR_ARRIVED_STOPS, ""),
                        )

                        addCompleteFormMessagesIntoQueue(
                            message,
                            stopIdList
                        )
                    } else {
                        formList.let { formListIterator ->
                            formListIterator.iterator().forEach { dispatchFormPath ->
                                val stopName = dispatchFormPath.stopName
                                val message = String.format(
                                    resourceStringsManager.getStringsForTripCacheUseCase()
                                        .getOrDefault(StringKeys.COMPLETE_FORM_FOR_STOP, ""),
                                    stopName
                                )
                                stopIdList.add(dispatchFormPath.stopId)
                                addCompleteFormMessagesIntoQueue(
                                    message,
                                    stopIdList
                                )
                            }
                        }
                    }
                }
            }
    }

    suspend fun addCompleteFormMessagesIntoQueue(
        message: String,
        stopIdList: MutableList<Int>
    ) {
        Log.d(TRIP_PANEL, "----Inside addCompleteFormMessagesIntoQueue message: $message")
        if (didLastSentMessageEqualsWithTheNewMessage(message).not() || (didLastSentMessageEqualsWithTheNewMessage(
                message
            ) && didLastSentMessageResponded())
        ) {
            Log.d(
                TRIP_PANEL,
                "----Inside addCompleteFormMessagesIntoQueue putLauncherMessagesIntoPriorityQueue: $message"
            )
            putLauncherMessagesIntoPriorityQueue(
                TripPanelData(
                    message,
                    TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
                    FILL_FORMS_MESSAGE_ID,
                    0.toDouble(),
                    0.toDouble(),
                ), "addCompleteFormMessagesIntoQueue", *stopIdList.toIntArray()
            )
        }
    }

    fun removeMessageFromPriorityQueueIfAvailable(messageId: Int) {
        if (listLauncherMessageWithPriority.isNotEmpty()) {
            listLauncherMessageWithPriority.filter { launcherMessage -> launcherMessage?.messageID == messageId }
                .let {
                    if (it.isNotEmpty()) {
                        listLauncherMessageWithPriority.removeAll(it.toSet())
                    }
                }
        }
    }

    fun removeMessageFromPriorityQueueBasedOnStopId(listLauncherMessageWithPriority: PriorityBlockingQueue<LauncherMessageWithPriority>): PriorityBlockingQueue<LauncherMessageWithPriority> {
        if (listLauncherMessageWithPriority.isNotEmpty()) {
            listLauncherMessageWithPriority.removeIf {
                it.stopId.isNotNull() && it.stopId.isNotEmpty()
            }
        }
        return listLauncherMessageWithPriority
    }

    fun didLastSentMessageResponded(): Boolean {
        return lastSentTripPanelMessage.messageId == lastRespondedTripPanelMessage.messageId
    }

    internal fun setLastRespondedTripPanelMessage(messageId: Int) {
        lastRespondedTripPanelMessage = LastRespondedTripPanelMessage(messageId)
    }

    fun didLastSentMessageEqualsWithTheNewMessage(newMessage: String): Boolean {
        return lastSentTripPanelMessage.message == newMessage
    }

    internal suspend fun dismissTripPanelMessage(messageId: Int) = withContext(ioDispatcher) {
        dismissTripPanelMessageAndInvalidateLastSentTripPanelMessageId(messageId)
        var cachedId = messageId
        if (cachedId <= 0) {
            cachedId = localDataSourceRepo.getLastSentTripPanelMessageId()
        }
        dismissTripPanelMessageAndInvalidateLastSentTripPanelMessageId(cachedId)
        if (cachedId > INVALID_TRIP_PANEL_MESSAGE_ID) localDataSourceRepo.setLastSentTripPanelMessageId(
            INVALID_TRIP_PANEL_MESSAGE_ID
        )
    }

    private fun dismissTripPanelMessageAndInvalidateLastSentTripPanelMessageId(messageId: Int) {
        if (messageId > INVALID_TRIP_PANEL_MESSAGE_ID) {
            tripPanelEventRepo.dismissEvent(messageId)
            lastSentTripPanelMessage.messageId = INVALID_TRIP_PANEL_MESSAGE_ID
        }
    }

    internal fun dismissTripPanelOnLaunch() {
        tripPanelEventRepo.dismissEvent(SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID)
        tripPanelEventRepo.dismissEvent(FILL_FORMS_MESSAGE_ID)
        tripPanelEventRepo.dismissEvent(NEXT_STOP_MESSAGE_ID)
    }

    internal fun setLastSentTripPanelMessageForUnitTest(lastSentTripPanelMsg: LastSentTripPanelMessage) {
        lastSentTripPanelMessage = lastSentTripPanelMsg
    }

    suspend fun isValidStopForTripType(stopId: Int): Pair<Boolean, String> =
        dispatchStopsUseCase.isValidStopForTripType(stopId)

    suspend fun getFormPathToLoad(): String {
        localDataSourceRepo.getFromAppModuleDataStore(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING)
            .let { formStack ->
                if (formStack.isNotEmpty()) {
                    Log.d(TRIP_PANEL, "getFormPathToLoad() Form Stack: [$formStack]")
                    return formStack
                }
            }
        Log.w(TRIP_PANEL, "getFormPathToLoad() did not find any forms")
        return EMPTY_STRING
    }

}

fun getHighestPriorityMsg() = TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP
fun getHighPriorityMsg() = TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY
fun getMediumPriorityMsg() = TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY
fun getLowPriorityMsg() = TRIP_PANEL_SELECT_STOP_MSG_PRIORITY
fun getLowestPriorityMsg() = TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY

