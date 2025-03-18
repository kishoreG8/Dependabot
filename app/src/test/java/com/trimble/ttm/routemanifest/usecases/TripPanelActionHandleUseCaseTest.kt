package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import android.content.Intent
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.utils.composeFormActivityIntentAction
import com.trimble.ttm.commons.utils.formActivityIntentAction
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.CoroutineTestRule
import com.trimble.ttm.routemanifest.utils.FILL_FORMS_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.INVALID_PRIORITY
import com.trimble.ttm.routemanifest.utils.SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.isMessageFromDidYouArrive
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

class TripPanelActionHandleUseCaseTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    @MockK
    private lateinit var context: Context
    @RelaxedMockK
    private lateinit var resultIntent: Intent
    private val formListSerialized= "[{\"actionId\":1,\"formClass\":0,\"formId\":21245,\"stopId\":0,\"stopName\":\"Dollar General\"}]"
    private val customerId = "customerId"
    private val vehicleId = "vehicleId"
    private val workflowId = "workflowId"
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var featureGatekeeper: FeatureGatekeeper
    @MockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase
    @MockK
    private lateinit var arrivalReasonUseCase: ArrivalReasonUsecase
    @MockK
    private lateinit var sendDispatchDataUseCase: SendDispatchDataUseCase
    @MockK
    private lateinit var routeETACalculationUseCase: RouteETACalculationUseCase
    @MockK
    private lateinit var arriveTriggerDataStoreKeyManipulationUseCase: ArriveTriggerDataStoreKeyManipulationUseCase
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase
    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo
    @MockK
    private lateinit var dataStoreManager: DataStoreManager
    @MockK
    private lateinit var formDataStoreManager: FormDataStoreManager

    private lateinit var tripPanelActionHandleUseCase: TripPanelActionHandleUseCase
    private var dispatchProvider: DispatcherProvider = TestDispatcherProvider()
    private val testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val testScope = TestScope()

    private val modulesToInject = module {
        single { arrivalReasonUseCase }
    }


    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(WorkflowApplication)
        mockkObject(Utils)
        every { context.packageName } returns "com.trimble.ttm.routemanifest"
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        localDataSourceRepo = mockk()
        startKoin {
            loadKoinModules(modulesToInject)
        }
        tripPanelActionHandleUseCase = spyk(
            TripPanelActionHandleUseCase(
                tripPanelUseCase = tripPanelUseCase,
                sendDispatchDataUseCase = sendDispatchDataUseCase,
                appModuleCommunicator = appModuleCommunicator,
                dispatchStopsUseCase = dispatchStopsUseCase,
                routeETACalculationUseCase = routeETACalculationUseCase,
                arriveTriggerDataStoreKeyManipulationUseCase = arriveTriggerDataStoreKeyManipulationUseCase,
                remoteConfigGatekeeper = featureGatekeeper,
                context = context,
                coroutineScope = testScope,
                coroutineDispatcherProvider = dispatchProvider,
                backboneUseCase = backboneUseCase
            ), recordPrivateCalls = true
        )
    }

    @Test
    fun `updateLocationPanelAndSendActionTriggerDataToFireBase is NOT FIRING calls if no activity is visible`() =
        runTest {
            every { WorkflowApplication.isDispatchActivityVisible() } returns true

            tripPanelActionHandleUseCase.updateLocationPanelAndSendActionTriggerDataToFireBase(
                1,
                PFMEventsInfo.StopActionEvents(""),
                ""
            )

            coVerify(exactly = 0) {
                tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any())
                dispatchStopsUseCase.performActionsAsDriverAcknowledgedArrivalOfStop(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
                tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
                tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            }
        }

    @Test
    fun `updateLocationPanelAndSendActionTriggerDataToFireBase is FIRING calls if no activity is visible`() =
        runTest {
            every { WorkflowApplication.isDispatchActivityVisible() } returns false
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "1"
            coEvery {
                arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any(), any())
                dispatchStopsUseCase.performActionsAsDriverAcknowledgedArrivalOfStop(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
                tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
                tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            } just runs

            tripPanelActionHandleUseCase.updateLocationPanelAndSendActionTriggerDataToFireBase(
                1,
                PFMEventsInfo.StopActionEvents(""),
                ""
            )

            coVerify(exactly = 1) {
                arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any(), any())
                dispatchStopsUseCase.performActionsAsDriverAcknowledgedArrivalOfStop(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
                tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
                tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            }
        }

    @Test
    fun `validate action created when building draft intent`() {
        tripPanelActionHandleUseCase.buildDraftFormIntent(formListSerialized, customerId, vehicleId, workflowId, false, resultIntent)
        verify { resultIntent.setAction(formActivityIntentAction) }
    }


    @Test
    fun `validate we get the correct action based on compose flag on`() {
        tripPanelActionHandleUseCase.buildDraftFormIntent(formListSerialized, customerId, vehicleId, workflowId, true, resultIntent)
        verify { resultIntent.setAction(composeFormActivityIntentAction) }
    }


    @Test
    fun `verify handleTripPanelPositiveAction for positive action of did you arrive with timer`() = runTest {
        val messageId = 0
        val messagePriority = TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP
        every { tripPanelUseCase.setLastRespondedTripPanelMessage(messageId) } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId) } just runs
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(messageId, any()) } just runs
        coEvery { dispatchStopsUseCase.performActionsAsDriverAcknowledgedArrivalOfStop(any(), any(), any(), any(), any()) } just runs
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { tripPanelUseCase.putArrivedMessagesIntoPriorityQueue() } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "1"
        coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(), any(), any()) } returns hashMapOf()
        coEvery { arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(), any())  } just runs
        every { isMessageFromDidYouArrive(messagePriority) } returns true

        tripPanelActionHandleUseCase.handleTripPanelPositiveAction(messageId = messageId, isAutoDismissed = true, messagePriority)

        verify {
            isMessageFromDidYouArrive(messagePriority)
            tripPanelActionHandleUseCase["handleTripPanelPositiveActionForExpiredTimer"](messageId, any<CancellableContinuation<Unit>>(), true)
        }
        coVerifyAll {
            tripPanelUseCase.setLastRespondedTripPanelMessage(messageId)
            appModuleCommunicator.getCurrentWorkFlowId(any())
            arrivalReasonUseCase.getArrivalReasonMap(any(), any(), any())
            arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(), any())
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId)
            appModuleCommunicator.getCurrentWorkFlowId(any())
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
        }
    }

    @Test
    fun `verify handleTripPanelPositiveAction for positive action of did you arrive without timer or next stop name trip panel message`() = runTest {
        val messageId = 0
        val messagePriority = TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP
        every { tripPanelUseCase.setLastRespondedTripPanelMessage(messageId) } just runs
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(), any(), any()) } returns hashMapOf()
        coEvery { arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(), any())  } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId) } just runs
        coEvery { tripPanelActionHandleUseCase.updateLocationPanelAndSendActionTriggerDataToFireBase(messageId, any(), any()) } just runs
        coEvery { routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps() } returns listOf()
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "1"
        every { isMessageFromDidYouArrive(messagePriority) } returns true

        tripPanelActionHandleUseCase.handleTripPanelPositiveAction(messageId = messageId, isAutoDismissed = false, messagePriority)

        verify {
            tripPanelActionHandleUseCase["handleTripPanelPositiveActionForNonExpiredTimer"](messageId, any<CancellableContinuation<Unit>>(), true)
        }
        coVerifyAll {
            isMessageFromDidYouArrive(messagePriority)
            with(tripPanelUseCase) {
                setLastRespondedTripPanelMessage(messageId)
                cancelNegativeGufTimer(any())
            }
            arrivalReasonUseCase.getArrivalReasonMap(any(), any(), any())
            arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(), any())
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId)

            routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps()
        }
    }


    @Test
    fun `verify handleTripPanelPositiveAction for positive action of select a stop or fill form trip panel message`() = runTest {
        val messageId = SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
        val activeDispatchId = "1"
        val messagePriority = INVALID_PRIORITY
        every { tripPanelUseCase.setLastRespondedTripPanelMessage(messageId) } just runs
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId) } just runs
        coEvery { tripPanelActionHandleUseCase.updateLocationPanelAndSendActionTriggerDataToFireBase(messageId, any(), any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns activeDispatchId
        coEvery { tripPanelActionHandleUseCase["sendPendingIntentForStopSelectionOrFillFormTripPanelMessage"](messageId, activeDispatchId) } answers { nothing }
        every { tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = false } just runs
        every { isMessageFromDidYouArrive(messagePriority) } returns false

        tripPanelActionHandleUseCase.handleTripPanelPositiveAction(messageId = messageId, isAutoDismissed = false, messagePriority = messagePriority)

        verify {
            tripPanelActionHandleUseCase["handleTripPanelPositiveActionForNonExpiredTimer"](messageId, any<CancellableContinuation<Unit>>(), false)
        }
        coVerifyAll {
            isMessageFromDidYouArrive(messagePriority)
            with(tripPanelUseCase) {
                setLastRespondedTripPanelMessage(messageId)
                cancelNegativeGufTimer(any())
            }
            with(tripPanelUseCase){
                removeMessageFromPriorityQueueIfAvailable(messageId)
                isHighPriorityMessageDisplayedInPanel = false
            }
        }
    }


    @Ignore
    @Test
    fun `verify handleTripPanelNegativeAction code execution for fill form trip panel message`() = runTest(testDispatcher) {
        val messageId = FILL_FORMS_MESSAGE_ID
        val activeDispatchId = "1"
        every { tripPanelUseCase.setLastRespondedTripPanelMessage(messageId) } just runs
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns activeDispatchId
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId) } just runs
        coEvery { tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(messageId) } just runs
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop() } returns true
        coEvery { tripPanelUseCase.startSendingMessagesToLocationPanel(any()) } returns 0

        tripPanelActionHandleUseCase.handleTripPanelNegativeAction(messageId = messageId, "test")

        coVerifyAll {
            with(tripPanelUseCase) {
                setLastRespondedTripPanelMessage(messageId)
                cancelNegativeGufTimer(any())
            }
            with(tripPanelUseCase) {
                removeMessageFromPriorityQueueIfAvailable(messageId)
                removeArrivedTriggersFromPreferenceIfRespondedByUser(messageId)
                updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
                sendMessageToLocationPanelBasedOnCurrentStop()
                startSendingMessagesToLocationPanel(any())
            }
        }
    }


    @Ignore
    @Test
    fun `verify handleTripPanelNegativeAction code execution for negative actions except fill form trip panel message`() = runTest(testDispatcher) {
        val messageId = SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
        val activeDispatchId = "1"
        every { tripPanelUseCase.setLastRespondedTripPanelMessage(messageId) } just runs
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        coEvery { arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(), any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns activeDispatchId
        coEvery { tripPanelUseCase.checkForCompleteFormMessages() } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId) } just runs
        coEvery { tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(messageId) } just runs
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop() } returns true
        coEvery { tripPanelUseCase.startSendingMessagesToLocationPanel(any()) } returns 0
        coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs
        coEvery { sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(any(), any(), any(), any()) } just runs

        tripPanelActionHandleUseCase.handleTripPanelNegativeAction(messageId = messageId, "test")

        coVerify{
            with(tripPanelUseCase) {
                setLastRespondedTripPanelMessage(messageId)
                cancelNegativeGufTimer(any())
            }
            with(tripPanelUseCase) {
                removeMessageFromPriorityQueueIfAvailable(messageId)
                removeArrivedTriggersFromPreferenceIfRespondedByUser(messageId)
                updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
                sendMessageToLocationPanelBasedOnCurrentStop()
                startSendingMessagesToLocationPanel(any())
            }
            with(sendDispatchDataUseCase) {
                sendDispatchEventForClearRouteWithDelay()
                sendCurrentDispatchDataToMaps(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `verify sendPendingIntentForStopSelectionOrFillFormTripPanelMessage for positive action except fill form trip panel message`() = runTest {
        val messageId = SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
        val messagePriority = INVALID_PRIORITY
        val activeDispatchId = "1"
        every { tripPanelUseCase.setLastRespondedTripPanelMessage(messageId) } just runs
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId) } just runs
        coEvery { tripPanelActionHandleUseCase.updateLocationPanelAndSendActionTriggerDataToFireBase(messageId, any(), any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns activeDispatchId
        coEvery { tripPanelActionHandleUseCase["sendPendingIntentToStartActivity"](any<Intent>(), any<String>()) } answers { nothing }
        coEvery { tripPanelActionHandleUseCase["sendPendingIntentForStopSelectionOrFillFormTripPanelMessage"](any<Int>(), any<String>()) } answers { nothing }
        every { tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = false } just runs
        every { isMessageFromDidYouArrive(messagePriority) } returns false

        tripPanelActionHandleUseCase.handleTripPanelPositiveAction(messageId = messageId, isAutoDismissed = false, messagePriority)

        verify {
            isMessageFromDidYouArrive(messagePriority)
            tripPanelActionHandleUseCase["sendPendingIntentForStopSelectionOrFillFormTripPanelMessage"](SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID, "1")
        }
    }

    @Test
    fun `verify sendPendingIntentForStopSelectionOrFillFormTripPanelMessage for positive action of fill form trip panel message with pending form`() = runTest {
        val messageId = FILL_FORMS_MESSAGE_ID
        val messagePriority = INVALID_PRIORITY
        val activeDispatchId = "1"
        every { tripPanelUseCase.setLastRespondedTripPanelMessage(messageId) } just runs
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId) } just runs
        coEvery { tripPanelActionHandleUseCase.updateLocationPanelAndSendActionTriggerDataToFireBase(messageId, any(), any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns activeDispatchId
        coEvery { tripPanelActionHandleUseCase["sendPendingIntentToStartActivity"](any<Intent>(), any<String>()) } answers { nothing }
        every { tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = false } just runs
        coEvery { tripPanelUseCase.getFormPathToLoad() } returns "formPath"
        coEvery { appModuleCommunicator.doGetCid() } returns "cid"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "vehicleId"
        coEvery { appModuleCommunicator.getFeatureFlags() } returns mapOf()
        coEvery { featureGatekeeper.isFeatureTurnedOn(any(), any(), any()) } returns true
        every { tripPanelActionHandleUseCase.buildDraftFormIntent(any(), any(), any(), any(), any(), resultIntent) } returns resultIntent
        every { isMessageFromDidYouArrive(messagePriority) } returns false

        tripPanelActionHandleUseCase.handleTripPanelPositiveAction(messageId = messageId, isAutoDismissed = false, messagePriority)

        verify {
            isMessageFromDidYouArrive(messagePriority)
            tripPanelActionHandleUseCase["sendPendingIntentToStartActivity"](any<Intent>(), any<String>())
        }
    }

    @Test
    fun `verify sendPendingIntentForStopSelectionOrFillFormTripPanelMessage for positive action of fill form trip panel message with no pending form`() = runTest {
        val messageId = FILL_FORMS_MESSAGE_ID
        val messagePriority = INVALID_PRIORITY
        val activeDispatchId = "1"
        every { tripPanelUseCase.setLastRespondedTripPanelMessage(messageId) } just runs
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(messageId) } just runs
        coEvery { tripPanelActionHandleUseCase.updateLocationPanelAndSendActionTriggerDataToFireBase(messageId, any(), any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns activeDispatchId
        coEvery { tripPanelActionHandleUseCase["sendPendingIntentToStartActivity"](any<Intent>(), any<String>()) } answers { nothing }
        every { tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = false } just runs
        coEvery { tripPanelUseCase.getFormPathToLoad() } returns EMPTY_STRING
        every { isMessageFromDidYouArrive(messagePriority) } returns false

        tripPanelActionHandleUseCase.handleTripPanelPositiveAction(messageId = messageId, isAutoDismissed = false, messagePriority = messagePriority)

        verify {
            isMessageFromDidYouArrive(messagePriority)
            tripPanelActionHandleUseCase["sendPendingIntentToStartActivity"](any<Intent>(), any<String>())
        }
    }

    @Test
    fun `verify sendPendingIntentForStopSelectionOrFillFormTripPanelMessage() restores activeDispatch state when action is done when previewing a trip`() = runTest {
        val testMessageId = 300
        val testActiveDispatchId = "testActiveDispatch"
        coEvery { appModuleCommunicator.restoreSelectedDispatch() } answers { nothing }
        coEvery { tripPanelActionHandleUseCase["sendPendingIntentToStartActivity"](any<Intent>(), any<String>()) } returns Unit

        val getSendPendingIntentForStopSelectionOrFillFormTripPanelMessageMethod = tripPanelActionHandleUseCase::class.declaredFunctions.find { it.name == "sendPendingIntentForStopSelectionOrFillFormTripPanelMessage" }
        getSendPendingIntentForStopSelectionOrFillFormTripPanelMessageMethod?.isAccessible = true
        getSendPendingIntentForStopSelectionOrFillFormTripPanelMessageMethod?.callSuspend(
            tripPanelActionHandleUseCase,
            testMessageId,
            testActiveDispatchId
        )

        coVerify(exactly = 1) {
            appModuleCommunicator.restoreSelectedDispatch()
            tripPanelActionHandleUseCase["sendPendingIntentToStartActivity"](any<Intent>(), any<String>())
        }
    }

    @After
    fun after() {
        testScope.cancel()
        stopKoin()
        unmockkAll()
    }
}