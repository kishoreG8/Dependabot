package com.trimble.ttm.routemanifest.usecases

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_PANEL
import com.trimble.ttm.commons.model.FormActivityIntentActionData
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.ON_MESSAGE_DISMISSED_CALLER
import com.trimble.ttm.commons.utils.ON_POSITIVE_BUTTON_CLICKED_CALLER
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.model.ArrivalType
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.ui.activities.DispatchDetailActivity
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_TO_RENDER
import com.trimble.ttm.routemanifest.utils.FILL_FORMS_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.Utils.isMessageFromDidYouArrive
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.resume

class TripPanelActionHandleUseCase(
    private val tripPanelUseCase: TripPanelUseCase,
    private val sendDispatchDataUseCase: SendDispatchDataUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val dispatchStopsUseCase: DispatchStopsUseCase,
    private val routeETACalculationUseCase: RouteETACalculationUseCase,
    private val arriveTriggerDataStoreKeyManipulationUseCase: ArriveTriggerDataStoreKeyManipulationUseCase,
    private val remoteConfigGatekeeper: FeatureGatekeeper,
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val backboneUseCase: BackboneUseCase,
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : KoinComponent {

    private val arrivalReasonUsecase: ArrivalReasonUsecase by inject()
    suspend fun handleTripPanelNegativeAction(messageId: Int, logTag: String) = suspendCancellableCoroutine { continuation ->
        tripPanelUseCase.setLastRespondedTripPanelMessage(messageId)
        tripPanelUseCase.cancelNegativeGufTimer("onNegativeButtonClick")
        coroutineScope.launch(coroutineDispatcherProvider.main() + CoroutineName("onNegativeButtonClick")) {
            val activeDispatchId =
                appModuleCommunicator.getCurrentWorkFlowId("onNegativeButtonClick")
            with(tripPanelUseCase) {
                if (messageId != FILL_FORMS_MESSAGE_ID) {
                    Log.d(TRIP_PANEL, "trip panel negative action. not fill form")
                    checkForCompleteFormMessages()
                }
                val location = backboneUseCase.getCurrentLocation()
                Log.logUiInteractionInNoticeLevel(
                    logTag,
                    "trip panel negative action",
                    throwable = null,
                    "Location Info" to  "${location.first},${location.second}",
                    KEY to DISPATCH_LIFECYCLE,
                    DISPATCHID to activeDispatchId,
                    STOPID to messageId
                )
                removeMessageFromPriorityQueueIfAvailable(messageId)
                removeArrivedTriggersFromPreferenceIfRespondedByUser(messageId)
                updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
                sendMessageToLocationPanelBasedOnCurrentStop()
                startSendingMessagesToLocationPanel(logTag)
            }
            if (messageId != FILL_FORMS_MESSAGE_ID) {
                Log.d(TRIP_PANEL, "trip panel negative action. not fill form")
                // On responding "No" in Did you arrive trip panel, we need to send the trip data again to maps as copilot map is clearing the crossed stop without actual arrival.
                sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay()
                sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(
                    shouldRegisterGeofence = false,
                    shouldRedrawCopilotRoute = true,
                    caller = "No Clicked in DYA - Trip Panel- Geofence Willnot be set"
                )
            }
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    suspend fun handleTripPanelPositiveAction(
        messageId: Int,
        isAutoDismissed: Boolean,
        messagePriority: Int
    ) = suspendCancellableCoroutine { continuation ->
        val isDYAMessage = isMessageFromDidYouArrive(messagePriority)
        if (isAutoDismissed) handleTripPanelPositiveActionForExpiredTimer(messageId, continuation, isDYAMessage)
         else handleTripPanelPositiveActionForNonExpiredTimer(messageId, continuation, isDYAMessage)
    }

    private fun handleTripPanelPositiveActionForExpiredTimer(messageId: Int, continuation: CancellableContinuation<Unit>, isDYAMessage: Boolean) {
        tripPanelUseCase.setLastRespondedTripPanelMessage(messageId)
        coroutineScope.launch(coroutineDispatcherProvider.io() + CoroutineName("onMessageDismiss")) {
            val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("onMessageDismiss")
            if(isDYAMessage){
                val arrivalReasonMap = arrivalReasonUsecase.getArrivalReasonMap(
                ArrivalType.TIMER_EXPIRED.toString(),
                messageId,
                true)
                arrivalReasonUsecase.updateArrivalReasonForCurrentStop(messageId, arrivalReasonMap)
                Log.logUiInteractionInNoticeLevel(
                    TRIP_PANEL, "Trip Panel DYA Auto Dismiss - Positive Action: D$activeDispatchId M$messageId",
                    null,
                    KEY to DISPATCH_LIFECYCLE,
                    DISPATCHID to activeDispatchId,
                    STOPID to messageId
                )
            }else {
                Log.logUiInteractionInNoticeLevel(
                    TRIP_PANEL,
                    "Trip Panel Auto Dismiss - Positive Action: D$activeDispatchId M$messageId",
                    null,
                    KEY to DISPATCH_LIFECYCLE,
                    DISPATCHID to activeDispatchId,
                    "messageId" to messageId
                )
            }
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId)
            /** arrival occurred = StopActionReasonTypes.TIMEOUT.name, neg guf timeout Event
             * Arrived the stop with timer expired for did you arrive prompt
             */
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                reasonType = StopActionReasonTypes.TIMEOUT.name,
                negativeGuf = true
            )
            updateLocationPanelAndSendActionTriggerDataToFireBase(
                messageId,
                pfmEventsInfo,
                ON_MESSAGE_DISMISSED_CALLER
            )
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    private fun handleTripPanelPositiveActionForNonExpiredTimer(messageId: Int, continuation: CancellableContinuation<Unit>, isDYAMessage: Boolean) {
        tripPanelUseCase.setLastRespondedTripPanelMessage(messageId)
        tripPanelUseCase.cancelNegativeGufTimer("onPositiveButtonClick")
        coroutineScope.launch(coroutineDispatcherProvider.io() + CoroutineName("onPositiveButtonClick")) {
            val activeDispatchId =
                appModuleCommunicator.getCurrentWorkFlowId("onPositiveButtonClick")
            if(isDYAMessage) {
                val arrivalReasonMap = arrivalReasonUsecase.getArrivalReasonMap(
                    ArrivalType.DRIVER_CLICKED_YES.toString(),
                    messageId,
                    true
                )
                arrivalReasonUsecase.updateArrivalReasonForCurrentStop(messageId, arrivalReasonMap)
                Log.logUiInteractionInNoticeLevel(
                    TRIP_PANEL, "Trip Panel DYA Manual Dismiss - Positive Action: D$activeDispatchId M$messageId",
                    null,
                    KEY to DISPATCH_LIFECYCLE,
                    DISPATCHID to activeDispatchId,
                    STOPID to messageId
                )
            }else {
                Log.logUiInteractionInNoticeLevel(
                    TRIP_PANEL,
                    "Trip Panel Manual Dismiss - Positive Action: D$activeDispatchId M$messageId",
                    null,
                    KEY to DISPATCH_LIFECYCLE,
                    DISPATCHID to activeDispatchId,
                    "messageId" to messageId
                )
            }
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId)

            if (messageId == SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID || messageId == FILL_FORMS_MESSAGE_ID) {
                Log.d(TRIP_PANEL, "trip panel positive action with no expired timer. select a stop or fill form")
                sendPendingIntentForStopSelectionOrFillFormTripPanelMessage(messageId, activeDispatchId)
                tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = false
                if (continuation.isActive) continuation.resume(Unit)
                return@launch
            }
            /** arrival occurred = StopActionReasonTypes.NORMAL.name, required guf confirm event
             * Driver arrives on tapping yes on did you arrive trigger
             */
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                reasonType = StopActionReasonTypes.NORMAL.name,
                negativeGuf = false
            )
            updateLocationPanelAndSendActionTriggerDataToFireBase(
                messageId,
                pfmEventsInfo,
                ON_POSITIVE_BUTTON_CLICKED_CALLER
            )
            routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps()
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    private suspend fun sendPendingIntentForStopSelectionOrFillFormTripPanelMessage(
        messageId: Int,
        activeDispatchId: String
    ) {
        if (messageId == FILL_FORMS_MESSAGE_ID) {
            Log.d(TRIP_PANEL, "trip panel positive action with no expired timer. fill form")
            val formToSend = tripPanelUseCase.getFormPathToLoad()
            if (formToSend.isNotEmpty()) {
                Log.d(TRIP_PANEL, "sendPendingIntent has data [$formToSend]")
                sendPendingIntentToStartActivity(
                    buildDraftFormIntent(
                        formToSend,
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetTruckNumber(),
                        activeDispatchId,
                        isComposeFeatureOn()
                    ), activeDispatchId = activeDispatchId
                )
            } else {
                Log.d(TRIP_PANEL, "sendPendingIntent no data found")
                sendPendingIntentToStartActivity(activeDispatchId = activeDispatchId)
            }
        } else {
            Log.d(
                TRIP_PANEL,
                "NOT FILL FORMS MESSAGE ID $messageId... trip panel positive action with no expired timer. select a stop"
            )
            //There is a chance that Trip Panel response(Yes/No/ok) action can occur while previewing a trip, so on responding to it,
            // we need to display active dispatch details, so restore active dispatch data.
            appModuleCommunicator.restoreSelectedDispatch()
            sendPendingIntentToStartActivity(activeDispatchId = activeDispatchId)
        }
    }

    internal suspend fun updateLocationPanelAndSendActionTriggerDataToFireBase(
        messageId: Int,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents,
        caller: String
    ) {
        if (WorkflowApplication.isDispatchActivityVisible().not()) {
            arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(
                messageId
            ) { stopId -> tripPanelUseCase.removeMessageFromPriorityQueueAndUpdateTripPanelFlags(stopId) }
            val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("getActionsOfStop")
            dispatchStopsUseCase.performActionsAsDriverAcknowledgedArrivalOfStop(
                activeDispatchId,
                messageId,
                context,
                pfmEventsInfo,
                caller
            )
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
        }
    }

    private fun sendPendingIntentToStartActivity(sendIntent: Intent? = null, activeDispatchId: String) {
        val intent: Intent? = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent.isNull()) {
            buildDispatchDetailActivityIntent(activeDispatchId)
        } else if (sendIntent.isNull()) {
            buildDispatchDetailActivityIntent(activeDispatchId)
        } else {
            sendIntent?.apply {
                putExtra(DISPATCH_ID_TO_RENDER, activeDispatchId)
                this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also {
                    PendingIntent.getActivity(
                        context, 0, this,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    ).send()
                }
            }
        }
    }

    fun buildDispatchDetailActivityIntent(activeDispatchId: String): Intent {
        return Intent(context, DispatchDetailActivity::class.java).apply {
            putExtra(DISPATCH_ID_TO_RENDER, activeDispatchId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also {
                PendingIntent.getActivity(
                    context, 0, this,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ).send()
            }
        }
    }

    fun buildDraftFormIntent(
        formListSerialized: String,
        customerId: String,
        vehicleId: String,
        workflowId: String,
        isComposeEnabled: Boolean,
        intent: Intent? = null
    ): Intent? {
        try {
            UncompletedFormsUseCase.deserializeFormList(formListSerialized).first().let { currentForm ->
                val path =
                    "$INBOX_FORM_RESPONSE_COLLECTION/$customerId/$vehicleId/$workflowId/${currentForm.stopId}/${currentForm.actionId}"
                return FormActivityIntentActionData(
                    isComposeEnabled = isComposeEnabled,
                    containsStopData = true,
                    customerId = null,
                    formId = null,
                    formResponse = FormResponse(),
                    driverFormPath = currentForm,
                    isSecondForm = false,
                    dispatchFormSavePath = path,
                    isFormFromTripPanel = true,
                    isFormResponseSentToServer = false
                ).buildIntent(intent = intent).apply { `package` = context.packageName}
            }
        } catch (e: NoSuchElementException) {
            Log.w(TRIP_PANEL, "No form to send [$formListSerialized]", e)
            return null
        }
    }

    private suspend fun isComposeFeatureOn() : Boolean = remoteConfigGatekeeper.isFeatureTurnedOn(
        FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG,
        appModuleCommunicator.getFeatureFlags(),
        appModuleCommunicator.doGetCid()
    )



}