package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.launchercommunicationlib.commons.model.PanelDetails
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.ArrivedGeoFenceTriggerData
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.application.WorkflowApplication.Companion.dispatchActivityVisible
import com.trimble.ttm.routemanifest.customComparator.LauncherMessagePriorityComparator
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.routemanifest.managers.ResourceStringsManager
import com.trimble.ttm.routemanifest.managers.StringKeys
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.LastSentTripPanelMessage
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.TripPanelData
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepoImpl
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepoImpl
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.FILL_FORMS_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.INVALID_PRIORITY
import com.trimble.ttm.routemanifest.utils.NEXT_STOP_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_SELECT_STOP_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRUE
import com.trimble.ttm.routemanifest.utils.Utils
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.PriorityBlockingQueue
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TripPanelUseCaseTest {
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager
    @RelaxedMockK
    private lateinit var context: Context
    private val json =
        "[{\"actionId\":1,\"formClass\":0,\"formId\":18903,\"stopId\":0,\"stopName\":\"Stop1\"}]"
    private val jsonForMoreForms =
        "[{\"actionId\":1,\"formClass\":0,\"formId\":18903,\"stopId\":0,\"stopName\":\"Stop1\"}, " +
                "{\"actionId\":1,\"formClass\":0,\"formId\":18903,\"stopId\":1,\"stopName\":\"Stop1\"}," +
                "{\"actionId\":1,\"formClass\":0,\"formId\":18903,\"stopId\":2,\"stopName\":\"Stop1\"}," +
                "{\"actionId\":1,\"formClass\":0,\"formId\":18903,\"stopId\":3,\"stopName\":\"Stop1\"}]"
    private val dispatchId = "1000"
    private val didYouArriveAt1 = "did you arrive at 1"
    private val didYouArriveAt2 = "did you arrive at 2"
    private val didYouArriveAt3 = "did you arrive at 3"


    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @RelaxedMockK
    private lateinit var application: WorkflowApplication
    @MockK
    private lateinit var launcherCommunicator: AppLauncherCommunicator
    private lateinit var tripPanelEventRepo: TripPanelEventRepo
    @MockK
    private lateinit var panelDetails: PanelDetails
    @MockK
    private lateinit var tripPanelEventRepoMock: TripPanelEventRepo
    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase

    private lateinit var resourceStringsManager: ResourceStringsManager

    @MockK
    private lateinit var sendBroadCastUseCase: SendBroadCastUseCase

    @RelaxedMockK
    private lateinit var googleApiAvailability: GoogleApiAvailability

    @RelaxedMockK
    private lateinit var backboneUseCase: BackboneUseCase

    @RelaxedMockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase

    @MockK
    private lateinit var arriveTriggerDataStoreKeyManipulationUseCase: ArriveTriggerDataStoreKeyManipulationUseCase

    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase


    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo

    @RelaxedMockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    private lateinit var stopList: List<StopDetail>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        ApplicationContextProvider.init(application)
        every { dispatchFirestoreRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        mockkObject(FormUtils)
        mockkObject(com.trimble.ttm.commons.utils.FormUtils)
        mockkObject(Utils)
        mockkObject(Log)
        mockkObject(WorkflowEventBus)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { dispatchFirestoreRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        every {
            tripPanelEventRepoMock.sendEvent(any(), any())
        } just runs
        every {
            tripPanelEventRepoMock.retryConnection()
        } just runs
        tripPanelEventRepo = TripPanelEventRepoImpl()
        arriveTriggerDataStoreKeyManipulationUseCase = spyk(ArriveTriggerDataStoreKeyManipulationUseCase(localDataSourceRepo))
        dispatchStopsUseCase = spyk(
            DispatchStopsUseCase(
                mockk(),
                dispatchFirestoreRepo,
                DefaultDispatcherProvider(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                arriveTriggerDataStoreKeyManipulationUseCase,
                dataStoreManager,
                fetchDispatchStopsAndActionsUseCase
            )
        )
        every { context.filesDir } returns temporaryFolder.newFolder()
        every { context.applicationContext } returns context
        mockkStatic(GoogleApiAvailability::class)
        mockkObject(ApplicationContextProvider)
        every { GoogleApiAvailability.getInstance() } returns googleApiAvailability
        every { context.getString(R.string.did_you_arrive_at) } returns "Did you arrive at Stop 1"
        every { context.getString(R.string.select_stop_to_navigate) } returns "Select a stop to navigate to."
        every { context.getString(R.string.your_next_stop) } returns "Stop Name and Address"
        every { context.getString(R.string.yes) } returns "Yes"
        every { context.getString(R.string.no) } returns "No"
        every { context.getString(R.string.open) } returns "OPEN"
        every { context.getString(R.string.dismiss) } returns "DISMISS"
        every { context.getString(R.string.complete_form_for_stop) } returns "Complete Form For Stop 1"
        every { context.getString(R.string.complete_form_for_arrived_stops) } returns "Complete Form for stops"
        every { context.getString(R.string.ok_text) } returns "OK"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "100"
        coEvery { appModuleCommunicator.getSelectedDispatchId(any()) } returns "100"
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "cgvus"
        resourceStringsManager = ResourceStringsManager(context)
        every { sendBroadCastUseCase.sendLocalBroadCast(allAny(), any()) } just runs
        localDataSourceRepo =
            LocalDataSourceRepoImpl(dataStoreManager, formDataStoreManager, appModuleCommunicator)
        tripPanelUseCase = spyk(
            TripPanelUseCase(
                tripPanelEventRepoMock,
                backboneUseCase,
                resourceStringsManager,
                sendBroadCastUseCase,
                localDataSourceRepo, dispatchStopsUseCase, appModuleCommunicator,
                context = context,
                arriveTriggerDataStoreKeyManipulationUseCase = arriveTriggerDataStoreKeyManipulationUseCase,
                fetchDispatchStopsAndActionsUseCase = fetchDispatchStopsAndActionsUseCase
            )
        )
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(12.5, 30.0)
        stopList = mutableListOf(
            StopDetail(stopid = 1), StopDetail(stopid = 3),
            StopDetail(stopid = 4), StopDetail(stopid = 2)
        )
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                EMPTY_STRING
            )
        } returns EMPTY_STRING
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns EMPTY_STRING
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.CURRENT_STOP_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            Stop(dispId = "324", stopId = 1, stopName = "4234")
        )
        coEvery { localDataSourceRepo.setLastSentTripPanelMessageId(any()) } just runs
    }

    @Test
    fun `verify for did you arrive trip panel message`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        20
                    )
                )
            }
        val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
            Actions.add(
                Action(
                    actionType = 1,
                    stopid = 20,
                    responseSent = true
                )
            )
        }
        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(
                any(),
                any(),
                any(),
                any()
            )
        } returns mutableListOf(stopDetail)
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
        assertEquals(
            TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
            tripPanelUseCase.startSendingMessagesToLocationPanel()
        )
    }

    @Test
    fun `verify for complete form trip panel message`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "complete form for msg",
                        TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
                        21
                    )
                )
            }
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns json
        assertEquals(
            TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
            tripPanelUseCase.startSendingMessagesToLocationPanel()
        )
    }

    @Test
    fun `verify for select stop to navigate trip panel message`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "select stop to navigate",
                        TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                        21
                    )
                )
            }
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
        assertEquals(
            TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
            tripPanelUseCase.startSendingMessagesToLocationPanel()
        )
    }

    @Test
    fun `verify for next stop address trip panel message`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "addr",
                        TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                        25
                    )
                )
            }
        tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = false
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
        assertEquals(
            TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
            tripPanelUseCase.startSendingMessagesToLocationPanel()
        )
    }

    @Test
    fun `verify for invalid trip panel message`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority("addr", INVALID_PRIORITY, 21)
                )
            }
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
        assertEquals(
            INVALID_PRIORITY,
            tripPanelUseCase.startSendingMessagesToLocationPanel()
        )
    }

    @Test
    fun `verify for service disconnected`() = runTest {    //NOSONAR
        every { launcherCommunicator.getHostAppState() } returns HostAppState.SERVICE_DISCONNECTED
        assertEquals(
            Pair(HostAppState.SERVICE_DISCONNECTED, EMPTY_STRING),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for dead host state`() = runTest {    //NOSONAR
        every { launcherCommunicator.getHostAppState() } returns HostAppState.SERVICE_BINDING_DEAD
        assertEquals(
            Pair(HostAppState.SERVICE_BINDING_DEAD, EMPTY_STRING),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for not ready to process host state`() = runTest {    //NOSONAR
        every { launcherCommunicator.getHostAppState() } returns HostAppState.NOT_READY_TO_PROCESS
        assertEquals(
            Pair(HostAppState.NOT_READY_TO_PROCESS, EMPTY_STRING),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for service connected host state`() = runTest {    //NOSONAR
        every { launcherCommunicator.getHostAppState() } returns HostAppState.SERVICE_CONNECTED
        assertEquals(
            Pair(HostAppState.SERVICE_CONNECTED, EMPTY_STRING),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for ready to process host state`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority.clear()
        every { launcherCommunicator.getHostAppState() } returns HostAppState.READY_TO_PROCESS
        assertEquals(
            Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for isDispatchActivityVisible`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        21
                    )
                )
            }
        WorkflowApplication.setDispatchActivityResumed()
        every { launcherCommunicator.getHostAppState() } returns HostAppState.READY_TO_PROCESS
        tripPanelUseCase.prepareLocationPanelDataAndSend(
            launcherCommunicator
        )
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            sendBroadCastUseCase.sendLocalBroadCast(
                any(), any()
            )
        }
    }

    @Test
    fun `verify for complete form msg`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "complete form for stop1",
                        TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
                        21
                    )
                )
            }
        every { launcherCommunicator.getHostAppState() } returns HostAppState.READY_TO_PROCESS
        assertEquals(
            Pair(HostAppState.READY_TO_PROCESS, "complete form for stop1"),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for select stop msg`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "select stop to navigate to",
                        TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                        21
                    )
                )
            }
        every { launcherCommunicator.getHostAppState() } returns HostAppState.READY_TO_PROCESS
        assertEquals(
            Pair(HostAppState.READY_TO_PROCESS, "select stop to navigate to"),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for did you arrive at priority `() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "did you arrive at",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        21
                    )
                )
            }
        val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
            Actions.add(
                Action(
                    actionType = 1,
                    stopid = 20,
                    responseSent = true
                )
            )
        }
        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(
                any(),
                any(),
                any(),
                any()
            )
        } returns mutableListOf(stopDetail)
        every { launcherCommunicator.getHostAppState() } returns HostAppState.READY_TO_PROCESS
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns arrayListOf()
        assertEquals(
            Pair(HostAppState.READY_TO_PROCESS, "did you arrive at"),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for next stop address msg`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "title\nmessage",
                        TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                        21
                    )
                )
            }
        every { launcherCommunicator.getHostAppState() } returns HostAppState.READY_TO_PROCESS
        assertEquals(
            Pair(HostAppState.READY_TO_PROCESS, "message"),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for next stop address msg but blcoking queue is empty`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "title\nmessage",
                        TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                        21
                    )
                )
            }
        every { launcherCommunicator.getHostAppState() } returns HostAppState.READY_TO_PROCESS
        every { Utils.pollElementFromPriorityBlockingQueue(any()) } returns EMPTY_STRING
        assertEquals(
            Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify for empty msg`() = runTest {    //NOSONAR
        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority("", TRIP_PANEL_SELECT_STOP_MSG_PRIORITY, 21)
                )
            }
        every { launcherCommunicator.getHostAppState() } returns HostAppState.READY_TO_PROCESS
        assertEquals(
            Pair(HostAppState.READY_TO_PROCESS, ""),
            tripPanelUseCase.prepareLocationPanelDataAndSend(
                launcherCommunicator
            )
        )
    }

    @Test
    fun `verify last sent message not equals with the new message`() {    //NOSONAR
        val lastSentTripPanelMessage = LastSentTripPanelMessage(1, "Test not same message", 5)

        every { tripPanelUseCase.lastSentTripPanelMessage } returns lastSentTripPanelMessage

        assertEquals(
            tripPanelUseCase.didLastSentMessageEqualsWithTheNewMessage("same message"),
            false
        )
    }

    @Test
    fun `verify last sent message equals with the new message`() {    //NOSONAR
        val lastSentTripPanelMessage = LastSentTripPanelMessage(1, "Test not same message", 5)

        every { tripPanelUseCase.lastSentTripPanelMessage } returns lastSentTripPanelMessage

        assertEquals(
            tripPanelUseCase.didLastSentMessageEqualsWithTheNewMessage("Test not same message"),
            false
        )
    }

    @Test
    fun `verify last sent message responded`() {    //NOSONAR
        tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(
            LastSentTripPanelMessage(
                1,
                "Test",
                5
            )
        )

        tripPanelUseCase.setLastRespondedTripPanelMessage(1)

        assertEquals(tripPanelUseCase.didLastSentMessageResponded(), true)
    }

    @Test
    fun `verify complete form trip panel message trigger not invoked`() = runTest {    //NOSONAR
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns json

        coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs

        every { tripPanelUseCase.didLastSentMessageEqualsWithTheNewMessage(any()) } returns true

        every { tripPanelUseCase.didLastSentMessageResponded() } returns false

        coEvery {
            tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                any(), any()
            )
        } just runs

        tripPanelUseCase.addCompleteFormMessagesIntoQueue(
            message = "MESSAGE",
            mutableListOf(0)
        )

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                any(), any()
            )
        }
    }

    @Test
    fun `verify complete form trip panel message trigger not invoked when last sent message equals with the new message and it is not yet dismissed`() =
        runTest {    //NOSONAR

            tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(
                LastSentTripPanelMessage(
                    1,
                    "Test",
                    5
                )
            )

            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns json

            //every { StopsUtil.dismissTripPanelMessage(tripPanelEventRepo) } just runs

            every { tripPanelUseCase.didLastSentMessageEqualsWithTheNewMessage(any()) } returns false

            every { tripPanelUseCase.didLastSentMessageResponded() } returns false

            coEvery {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(), any(), any()
                )
            } just runs

            tripPanelUseCase.addCompleteFormMessagesIntoQueue(
                message = "MESSAGE",
                mutableListOf(0)
            )

            coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(), any(), any()
                )
            }
        }

    @Test
    fun `verify complete form trip panel message trigger invoked when last sent message not equals with the new message and it is dismissed`() =
        runTest {    //NOSONAR
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns json

            coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs

            every { tripPanelUseCase.didLastSentMessageEqualsWithTheNewMessage(any()) } returns true

            every { tripPanelUseCase.didLastSentMessageResponded() } returns true

            coEvery {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(), any(), any()
                )
            } just runs

            tripPanelUseCase.addCompleteFormMessagesIntoQueue(
                message = "MESSAGE",
                mutableListOf(0)
            )

            coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(), any(), any()
                )
            }
        }

    @Test
    fun `verify complete form trip panel message trigger invoked when last sent message equals with the new message and it is dismissed`() =
        runTest {    //NOSONAR
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns json

            coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs

            every { tripPanelUseCase.didLastSentMessageEqualsWithTheNewMessage(any()) } returns false

            every { tripPanelUseCase.didLastSentMessageResponded() } returns true

            coEvery {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(), any(), any()
                )
            } just runs

            tripPanelUseCase.addCompleteFormMessagesIntoQueue(
                message = "MESSAGE",
                mutableListOf(0)
            )

            coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(), any(), any()
                )
            }
        }

    @Test
    fun `verify  select stop msg is displayed if high priority message not displayed in the panel`() =
        runTest {    //NOSONAR
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns json

            tripPanelUseCase.listLauncherMessageWithPriority =
                PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                    add(
                        LauncherMessageWithPriority(
                            "select stop to navigate to",
                            TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                            21
                        )
                    )
                }
            tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = false
            tripPanelUseCase.isArrivedMessageDisplayedInPanel = false
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
            assertEquals(
                TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                tripPanelUseCase.startSendingMessagesToLocationPanel()
            )
        }

    @Test
    fun `verify  select stop msg is not displayed if high priority message is displayed in the panel`() =
        runTest {    //NOSONAR
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns json
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
            tripPanelUseCase.listLauncherMessageWithPriority =
                PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                    add(
                        LauncherMessageWithPriority(
                            "select stop to navigate to",
                            TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                            21
                        )
                    )
                }
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
            tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = true

            assertEquals(
                INVALID_PRIORITY,
                tripPanelUseCase.startSendingMessagesToLocationPanel()
            )
        }

    @Test
    fun `verify all relevant messages are deleted from the priority queue if delete message invoked`() {
        val messageId = 21
        val listOfLauncherMessage = PriorityBlockingQueue(
            1,
            LauncherMessagePriorityComparator
        )

        listOfLauncherMessage.add(
            LauncherMessageWithPriority(
                "select stop to navigate to",
                TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                messageId
            )
        )
        listOfLauncherMessage.add(
            LauncherMessageWithPriority(
                "select stop to navigate to",
                TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                messageId
            )
        )
        listOfLauncherMessage.add(
            LauncherMessageWithPriority(
                "select stop to navigate to",
                TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                messageId
            )
        )
        tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage
        tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(21)
        assertEquals(0, tripPanelUseCase.listLauncherMessageWithPriority.size)
    }

    @Test
    fun `verify did you arrive message is removed if the stop is completed`() =
        runTest {
            val preferenceList = ArrayList<ArrivedGeoFenceTriggerData>()
            preferenceList.add(
                ArrivedGeoFenceTriggerData(
                    0
                )
            )
            preferenceList.add(
                ArrivedGeoFenceTriggerData(
                    1
                )
            )
            preferenceList.add(
                ArrivedGeoFenceTriggerData(
                    2
                )
            )
            coEvery {
                dataStoreManager.setValue(
                    DataStoreManager.ARRIVED_TRIGGERS_KEY, any()
                )
            } just runs
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ARRIVED_TRIGGERS_KEY,
                    DataStoreManager.emptyArrivedGeoFenceTriggerList
                )
            } returns
                    GsonBuilder().setPrettyPrinting().create().toJson(preferenceList)
            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(
                StopDetail(
                    stopid = 0,
                    completedTime = "2020-02-16T13:19:54.765Z"
                )
            )
            stopDetailList.add(
                StopDetail(
                    stopid = 1,
                    completedTime = ""
                )
            )
            stopDetailList.add(
                StopDetail(
                    stopid = 2,
                    completedTime = ""
                )
            )
            val listOfLauncherMessage = PriorityBlockingQueue(
                1,
                LauncherMessagePriorityComparator
            )

            listOfLauncherMessage.add(
                LauncherMessageWithPriority(
                    "did you arrive at stop 2",
                    TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                    0
                )
            )
            listOfLauncherMessage.add(
                LauncherMessageWithPriority(
                    "did you arrive at stop 2",
                    TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                    0
                )
            )
            listOfLauncherMessage.add(
                LauncherMessageWithPriority(
                    "did you arrive at stop 1",
                    TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                    1
                )
            )
            coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns preferenceList
            tripPanelUseCase.listLauncherMessageWithPriority = listOfLauncherMessage
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
            } returns stopDetailList
            coEvery {
                dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString()
            } returns preferenceList

            coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""
            coEvery { appModuleCommunicator.doGetCid() } returns ""
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns ""
            coEvery { dispatchStopsUseCase.getStopsFromFirestoreCacheFirst(any(), any(), any(), any()) } returns stopDetailList
            coEvery { tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any()) } just runs
            tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted("fromTest")

            coVerify(exactly = 1) {
                tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any())
            }
        }

    @Test
    fun `verify removeDidYouArriveMessagesOfStopIfCompleted when there is no stop`() = runTest {
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""
        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns ""
        coEvery { dispatchStopsUseCase.getStopsFromFirestoreCacheFirst(any(), any(), any(), any()) } returns listOf()
        tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted("fromTest")
        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any())
        }
    }

    @Test
    fun `verify removeDidYouArriveMessagesOfStopIfCompleted for stop completion`() = runTest {
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "uyyu"
        coEvery { appModuleCommunicator.doGetCid() } returns "88878"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "8787878"

        coEvery { dispatchStopsUseCase.getStopsFromFirestoreCacheFirst(any(), any(), any(), any()) } returns mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 0, completedTime = "2020-02-16T13:19:54.765Z"))
        }

        tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted("fromTest")
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any())
            arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any(), any())
        }
    }

    @Test
    fun `verify removeDidYouArriveMessagesOfStopIfCompleted for stop deletion`() = runTest {
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "uyyu"
        coEvery { appModuleCommunicator.doGetCid() } returns "88878"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "8787878"

        coEvery { dispatchStopsUseCase.getStopsFromFirestoreCacheFirst(any(), any(), any(), any()) } returns mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 1))
        }
        tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted("fromTest")
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any())
            arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any(), any())
        }
    }

    @Test
    fun `verify trip panel message dismissed for complete form message when form list is empty`() {
        runTest {
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING

            tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(
                LastSentTripPanelMessage(
                    messageId = FILL_FORMS_MESSAGE_ID
                )
            )

            every {
                tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(
                    TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY
                )
            } just runs

            coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs

            tripPanelUseCase.checkForCompleteFormMessages()

            coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(
                    TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY
                )
                tripPanelUseCase.dismissTripPanelMessage(any())
            }
        }
    }

    @Test
    fun `verify complete form message is sent for validation when form list is not empty`() {
        runTest {
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns json

            coEvery {
                tripPanelUseCase.addCompleteFormMessagesIntoQueue(
                    any(),
                    any()
                )
            } just runs

            tripPanelUseCase.checkForCompleteFormMessages()

            coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.addCompleteFormMessagesIntoQueue(any(), any())
            }
        }
    }

    @Test
    fun `verify complete form message is sent for validation when form list has more than one form entry`() {
        runTest {
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns jsonForMoreForms

            coEvery {
                tripPanelUseCase.addCompleteFormMessagesIntoQueue(
                    any(),
                    any()
                )
            } just runs

            coEvery {
                dispatchStopsUseCase.getUncompletedFormsMessage(
                    any(),
                    any(),
                    any()
                )
            } returns "Test"
            tripPanelUseCase.checkForCompleteFormMessages()

            coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.addCompleteFormMessagesIntoQueue(any(), any())
                dispatchStopsUseCase.getUncompletedFormsMessage(any(), any(), any())
            }
        }
    }

    @Test
    fun `check sendPanelDetailsToLauncher func with some message`() = runTest {

        every { panelDetails.message } returns "SampleMessage"
        every { panelDetails.title } returns ""
        every { panelDetails.autoDismissTime } returns 1

        assertEquals(
            Pair(
                HostAppState.READY_TO_PROCESS,
                panelDetails.message
            ),
            tripPanelUseCase.sendPanelDetailsToLauncher(
                launcherMessagePriority = LauncherMessageWithPriority(),
                messageId = 0,
                panelDetails = panelDetails
            ),
        )
    }

    @Test
    fun `verify sendPanelDetailsToLauncher func with empty message`() = runTest {

        every { panelDetails.message } returns ""

        assertEquals(
            Pair(HostAppState.READY_TO_PROCESS, EMPTY_STRING),
            tripPanelUseCase.sendPanelDetailsToLauncher(
                launcherMessagePriority = LauncherMessageWithPriority(),
                messageId = 0,
                panelDetails = panelDetails
            ),
        )
    }


    @Test
    fun `verify did you arrive message priority function with dispatchActivityVisible true`() {
        dispatchActivityVisible = true
        assertTrue(tripPanelUseCase.checkMessagePriorityIsDidYouArrive(0))
        assertTrue(tripPanelUseCase.checkMessagePriorityIsDidYouArrive(1))
        assertFalse(tripPanelUseCase.checkMessagePriorityIsDidYouArrive(2))
    }

    @Ignore
    @Test
    fun `verify did you arrive message priority function with dispatchActivityVisible false`() {
        dispatchActivityVisible = false
        assertFalse(tripPanelUseCase.checkMessagePriorityIsDidYouArrive(0))
        assertFalse(tripPanelUseCase.checkMessagePriorityIsDidYouArrive(1))
        assertFalse(tripPanelUseCase.checkMessagePriorityIsDidYouArrive(2))
    }

    @Test
    fun `with the no arrival trigger available for current stop the method messageQueuePriorityHandler should break the validation flow`() = runTest {
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(any()) } returns false
            // Act
            tripPanelUseCase.handleTripPanelDYAUpdateForCurrentStop()

            // Assert
            coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                dataStoreManager.getValue(DataStoreManager.CURRENT_STOP_KEY, EMPTY_STRING)
                arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(1)
            }

            coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(any(), any(), any())
            }
        }

    @Test
    fun `with the arrival trigger available for current stop the method messageQueuePriorityHandler should keep the flow going`() =
        runTest {
            coEvery { arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(any()) } returns true
            // Act
            tripPanelUseCase.handleTripPanelDYAUpdateForCurrentStop()

            // Assert
            coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                dataStoreManager.getValue(DataStoreManager.CURRENT_STOP_KEY, EMPTY_STRING)
                arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(
                    1
                )
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(), any(), any()
                )
            }
        }

    @Test
    fun `with the invalid arrived geofence list the method stopDetailListHandler should break the flow`() =
        runTest {
            // Arrange
            val stopData = Stop(
                dispId = "324",
                stopId = 1,
                stopName = "4234",
                latitude = 0.0,
                longitude = 0.0
            )

            val listArrivedTriggerDataFromPreference = ArrayList<ArrivedGeoFenceTriggerData>()
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getStopData(any())
            } returns StopDetail()

            // Act
            tripPanelUseCase.handleTripPanelDYAUpdateForArrivedStops(
                listArrivedTriggerDataFromPreference
            )

            // Assert
            coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(),
                    "stopDetailListHandler",
                    stopData.stopId
                )
            }
        }

    @Test
    fun `with the valid arrived geofence list the method stopDetailListHandler should keep the flow going`() =
        runTest {
            // Arrange
            val stopData = Stop(
                dispId = "324",
                stopId = 1,
                stopName = "this is the stop name",
                latitude = 0.0,
                longitude = 0.0
            )

            val listArrivedTriggerDataFromPreference = getArrivedGeofenceData()
            val stopDetails = getStopDetails()

            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveSortedStopsWithoutActions(any())
            } returns stopDetails
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getSortedStopsDataWithoutActions(any())
            } returns StopDetail(stopid = 1)
            // Act
            tripPanelUseCase.handleTripPanelDYAUpdateForArrivedStops(
                listArrivedTriggerDataFromPreference
            )

            // Assert
            coVerify(atLeast = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                fetchDispatchStopsAndActionsUseCase.getSortedStopsDataWithoutActions(stopData.stopId)
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(),
                    any(),
                    stopData.stopId
                )
            }
        }

    @Test
    fun `verify that the same message from trip panel and cache is dismissed`() = runTest {
        tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(
            LastSentTripPanelMessage(
                10,
                "did you arrive",
                0,
                1
            )
        )
        coEvery { localDataSourceRepo.getLastSentTripPanelMessageId() } returns 10
        every { tripPanelEventRepoMock.dismissEvent(any()) } just runs
        tripPanelUseCase.dismissTripPanelMessage(10)
        verify(exactly = 2, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelEventRepoMock.dismissEvent(10)
        }
    }

    @Test
    fun `verify that the cached trip panel message is dismissed along with present message`() =
        runTest {
            tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(
                LastSentTripPanelMessage(
                    10,
                    "did you arrive",
                    0,
                    1
                )
            )
            coEvery { localDataSourceRepo.getLastSentTripPanelMessageId() } returns 11
            every { tripPanelEventRepoMock.dismissEvent(any()) } just runs
            tripPanelUseCase.dismissTripPanelMessage(10)
            verify(exactly = 2, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelEventRepoMock.dismissEvent(any())
            }
        }

    @Test
    fun `verify that the cached trip panel message is dismissed if present message is invalid`() =
        runTest {
            tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(
                LastSentTripPanelMessage(
                    -1,
                    "",
                    0,
                    1
                )
            )
            coEvery { localDataSourceRepo.getLastSentTripPanelMessageId() } returns 10
            every { tripPanelEventRepoMock.dismissEvent(any()) } just runs
            tripPanelUseCase.dismissTripPanelMessage(-1)
            verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelEventRepoMock.dismissEvent(10)
            }
        }

    @Test
    fun `verify that the present trip panel message is dismissed if cached message is invalid`() =
        runTest {
            tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(
                LastSentTripPanelMessage(
                    10,
                    "",
                    0,
                    1
                )
            )
            coEvery { localDataSourceRepo.getLastSentTripPanelMessageId() } returns -1
            every { tripPanelEventRepoMock.dismissEvent(any()) } just runs
            tripPanelUseCase.dismissTripPanelMessage(10)
            verify(exactly = 2, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelEventRepoMock.dismissEvent(10)
            }
        }

    @Test
    fun `verify that the trip panel message is not dismissed since present and cached message id is invalid`() =
        runTest {
            tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(
                LastSentTripPanelMessage(
                    -1,
                    "",
                    0,
                    1
                )
            )
            coEvery { localDataSourceRepo.getLastSentTripPanelMessageId() } returns -1
            every { tripPanelEventRepoMock.dismissEvent(any()) } just runs
            tripPanelUseCase.dismissTripPanelMessage(-1)
            verify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelEventRepoMock.dismissEvent(10)
            }
        }

    @Test
    fun `verify that the trip panel message on launch just runs`() {
        every { tripPanelEventRepoMock.dismissEvent(any()) } just runs
        tripPanelUseCase.dismissTripPanelOnLaunch()
        verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelEventRepoMock.dismissEvent(SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID)
            tripPanelEventRepoMock.dismissEvent(FILL_FORMS_MESSAGE_ID)
            tripPanelEventRepoMock.dismissEvent(NEXT_STOP_MESSAGE_ID)
        }
    }

    private fun getArrivedGeofenceData(): ArrayList<ArrivedGeoFenceTriggerData> {
        val listArrivedTriggerDataFromPreference = ArrayList<ArrivedGeoFenceTriggerData>()
        listArrivedTriggerDataFromPreference.add(
            ArrivedGeoFenceTriggerData(
                1
            )
        )

        return listArrivedTriggerDataFromPreference
    }

    @Test
    fun `verify remove Message From Priority Queue Based On StopId when there is no empty stop list`() {

        val listLauncherMessageWithPriority: PriorityBlockingQueue<LauncherMessageWithPriority> =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        30,
                        stopId = intArrayOf(1)
                    )
                )
            }

        val list = tripPanelUseCase.removeMessageFromPriorityQueueBasedOnStopId(
            listLauncherMessageWithPriority
        )
        assertTrue(list.isEmpty())
    }

    @Test
    fun `verify remove Message From Priority Queue Based On StopId`() {

        val listLauncherMessageWithPriority: PriorityBlockingQueue<LauncherMessageWithPriority> =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        20,
                        stopId = intArrayOf()
                    )
                )
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        30,
                        stopId = intArrayOf(1)
                    )
                )
            }

        val list = tripPanelUseCase.removeMessageFromPriorityQueueBasedOnStopId(
            listLauncherMessageWithPriority
        )
        assertTrue(list.count() == 1)
    }

    @Test
    fun `verify remove Message From Priority Queue Based On StopId for most stops`() {

        val listLauncherMessageWithPriority: PriorityBlockingQueue<LauncherMessageWithPriority> =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        20,
                        stopId = intArrayOf()
                    )
                )
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        322,
                        stopId = intArrayOf()
                    )
                )
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        33,
                        stopId = intArrayOf(3)
                    )
                )
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        32,
                        stopId = intArrayOf(4)
                    )
                )
                add(
                    LauncherMessageWithPriority(
                        "Did you arrive",
                        TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY,
                        40,
                        stopId = intArrayOf(6)
                    )
                )
            }

        val list = tripPanelUseCase.removeMessageFromPriorityQueueBasedOnStopId(
            listLauncherMessageWithPriority
        )
        assertTrue(list.count() == 2)
    }

    @Test
    fun `verify putArrivedGeoFenceTriggersIntoCache when stoplist is NOT empty`() = runTest {

        val stopDetailList = ArrayList<StopDetail>()
        stopDetailList.add(
            StopDetail(
                stopid = 0,
                completedTime = "2020-02-16T13:19:54.765Z"
            )
        )
        stopDetailList.add(
            StopDetail(
                stopid = 1,
                completedTime = "2020-02-16T13:19:54.765Z"
            )
        )
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveSortedStopsWithoutActions(any())
        } returns stopDetailList

        tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(dispatchId, 1)

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            arriveTriggerDataStoreKeyManipulationUseCase.putArrivedTriggerDataIntoPreference(any())
        }
    }

    @Test
    fun `verify putArrivedGeoFenceTriggersIntoCache when stoplist is NOT empty and completedTime is empty`() =
        runTest {

            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(
                StopDetail(
                    stopid = 0,
                    completedTime = "2020-02-16T13:19:54.765Z"
                )
            )
            stopDetailList.add(
                StopDetail(
                    stopid = 1,
                    completedTime = ""
                )
            )
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveSortedStopsWithoutActions(any())
            } returns stopDetailList
            coEvery {
                arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
            } returns arrayListOf()
            coEvery {
                arriveTriggerDataStoreKeyManipulationUseCase.putArrivedTriggerDataIntoPreference(any())
            } just runs
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(dispatchId, 1)

            coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
                arriveTriggerDataStoreKeyManipulationUseCase.putArrivedTriggerDataIntoPreference(any())
            }
        }

    @Test
    fun `verify putArrivedGeoFenceTriggersIntoCache when stoplist is empty`() = runTest {

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveSortedStopsWithoutActions(any())
        } returns listOf()

        tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(dispatchId, 1)

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            arriveTriggerDataStoreKeyManipulationUseCase.putArrivedTriggerDataIntoPreference(any())
        }
    }

    @Test
    fun `verify put arrived trigger into cache when there are messages`() = runTest {
        val arrivedListTriggers = arrayListOf(
            ArrivedGeoFenceTriggerData(1), ArrivedGeoFenceTriggerData
                (2)
        )

        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns arrivedListTriggers
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(any()) } returns false
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrivedListTriggers
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getSortedStopsDataWithoutActions(any())
        } returns StopDetail()
        tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.handleTripPanelDYAUpdateForCurrentStop()
            tripPanelUseCase.handleTripPanelDYAUpdateForArrivedStops(any())
        }
    }

    @Test
    fun `verify put arrived trigger into cache when there are NO messages`() = runTest {
        val arrivedListTriggers = arrayListOf<ArrivedGeoFenceTriggerData>()
        coEvery {
            tripPanelUseCase.handleTripPanelMsgUpdateWithCorrectMsgPriority()
        } just runs
        coEvery {
            arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
        } returns arrivedListTriggers
        tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.handleTripPanelDYAUpdateForCurrentStop()
            tripPanelUseCase.handleTripPanelDYAUpdateForArrivedStops(any())
        }
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.handleTripPanelMsgUpdateWithCorrectMsgPriority()
            arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
        }
    }

    @Test
    fun `verify sendMessageToLocationPanelBasedOnCurrentStop when the there is no active dispatch`() =
        runTest {
            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(StopDetail(stopid = 1))
            stopDetailList.add(StopDetail(stopid = 2))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )

            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns false
            assertFalse(
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            )

        }

    @Test
    fun `verify sendMessageToLocationPanelBasedOnCurrentStop when there is no stop`() =
        runTest {

            val stopDetailList = ArrayList<StopDetail>()
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )

            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns false
            assertFalse(
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            )

        }

    @Test
    fun `verify sendMessageToLocationPanelBasedOnCurrentStop when there is no current STOP AND Sequenced Trip`() =
        runTest {

            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(StopDetail(stopid = 1))
            stopDetailList.add(StopDetail(stopid = 2))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
                )
            } returns true

            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )
            coEvery {
                arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
            } returns arrayListOf()
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            assertFalse(
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            )

        }

    @Test
    fun `verify sendMessageToLocationPanelBasedOnCurrentStop when there is no current STOP`() =
        runTest {

            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(StopDetail(stopid = 1))
            stopDetailList.add(StopDetail(stopid = 2))
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
                )
            } returns false

            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns ""
            coEvery { tripPanelEventRepoMock.retryConnection() } just runs
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            coEvery {
                arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
            } returns arrayListOf()
            coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()

            coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(any(), any(), any())
                tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            }

        }

    @Test
    fun `verify sendMessageToLocationPanelBasedOnCurrentStop when there is a current STOP`() =
        runTest {

            coEvery { tripPanelEventRepoMock.retryConnection() } just runs
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(StopDetail(stopid = 1))
            stopDetailList.add(StopDetail(stopid = 2))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    Stop(
                        dispId = "324",
                        stopId = 1,
                        stopName = "",
                        latitude = 0.0,
                        longitude = 0.0
                    )
                )
            coEvery {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(),
                    any()
                )
            } just runs
            coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns arrayListOf()

            assertFalse(
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            )
        }

    @Test
    fun `verify sendMessageToLocationPanelBasedOnCurrentStop when there is a current STOP with VALID NAME`() =
        runTest {
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
            coEvery { appModuleCommunicator.getFeatureFlags() } returns emptyMap()
            coEvery { tripPanelEventRepoMock.retryConnection() } just runs
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(StopDetail(stopid = 1))
            stopDetailList.add(StopDetail(stopid = 2))
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )

            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    Stop(
                        dispId = "324",
                        stopId = 1,
                        stopName = "321321",
                        latitude = 0.0,
                        longitude = 0.0
                    )
                )
            coEvery {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    any(),
                    any()
                )
            } just runs
            coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns arrayListOf()
            coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
            assertTrue(
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            )
        }

    @Test
    fun `verify getFormPathToLoad returns blank when nothing is in dispatch list`() {
        runTest {
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING

            assertEquals(EMPTY_STRING, tripPanelUseCase.getFormPathToLoad())
        }
    }

    @Test
    fun `verify getFormPathToLoad returns the form stack when in dispatch list`() {
        runTest {
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns jsonForMoreForms

            assertEquals(jsonForMoreForms, tripPanelUseCase.getFormPathToLoad())
        }
    }

    @Test
    fun `verify concurrent trip panel message is sent`() = runTest {
        val stopAddressTripPanelData = TripPanelData(
            String.format(
                resourceStringsManager.getStringsForTripCacheUseCase()
                    .getOrDefault(StringKeys.YOUR_NEXT_STOP, ""),
                "4234"
            ),
            TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
            1,
            0.0,
            0.0
        )

        val completFormTripPanelData = TripPanelData(
            String.format(
                resourceStringsManager.getStringsForTripCacheUseCase()
                    .getOrDefault(StringKeys.COMPLETE_FORM_FOR_STOP, ""),
                "1"
            ),
            TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
            2,
            0.0,
            0.0
        )

        tripPanelUseCase.listLauncherMessageWithPriority =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                add(
                    LauncherMessageWithPriority(
                        "select a stop",
                        TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                        3
                    )
                )
            }

        tripPanelUseCase.isHighPriorityMessageDisplayedInPanel = false
        tripPanelUseCase.isArrivedMessageDisplayedInPanel = false
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery { tripPanelUseCase.isSafeToRespondMessages() } returns true
        coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
        val arrivalTripPanelJob = launch {
            tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(stopAddressTripPanelData, "", 1)
        }

        val completeFormtripPanelJob = launch {
            tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(completFormTripPanelData, "", 1)
        }

        arrivalTripPanelJob.join()
        completeFormtripPanelJob.join()

        coVerify(
            exactly = 2,
            timeout = TEST_DELAY_OR_TIMEOUT
        ) { tripPanelUseCase.startSendingMessagesToLocationPanel(any()) }

    }

    @Test
    fun `verify concurrent trip panel message is sent while concurrently removing a message from queue`() =
        runTest {
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
            val stopAddressTripPanelData = TripPanelData(
                String.format(
                    resourceStringsManager.getStringsForTripCacheUseCase()
                        .getOrDefault(StringKeys.YOUR_NEXT_STOP, ""),
                    "4234"
                ),
                TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                1,
                0.0,
                0.0
            )

            val completFormTripPanelData = TripPanelData(
                String.format(
                    resourceStringsManager.getStringsForTripCacheUseCase()
                        .getOrDefault(StringKeys.COMPLETE_FORM_FOR_STOP, ""),
                    "1"
                ),
                TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
                2,
                0.0,
                0.0
            )

            tripPanelUseCase.listLauncherMessageWithPriority =
                PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                    add(
                        LauncherMessageWithPriority(
                            "select a stop",
                            TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                            3
                        )
                    )
                }

            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            coEvery { tripPanelUseCase.isSafeToRespondMessages() } returns true
            coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
            val arrivalTripPanelJob = launch {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    stopAddressTripPanelData,
                    "",
                    1
                )
            }

            val removetripPanelMessageJob = launch {
                tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(3)
            }

            val completeFormtripPanelJob = launch {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    completFormTripPanelData,
                    "",
                    1
                )
            }

            arrivalTripPanelJob.join()
            completeFormtripPanelJob.join()
            removetripPanelMessageJob.join()

            coVerify(
                exactly = 2,
                timeout = TEST_DELAY_OR_TIMEOUT
            ) { tripPanelUseCase.startSendingMessagesToLocationPanel(any()) }

        }

    @Test
    fun `verify latest message is displayed in trip panel while sending concurrent trip panel messages`() =
        runTest {
            val stopAddressTripPanelData = TripPanelData(
                String.format(
                    resourceStringsManager.getStringsForTripCacheUseCase()
                        .getOrDefault(StringKeys.YOUR_NEXT_STOP, ""),
                    "4234"
                ),
                TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                1,
                0.0,
                0.0
            )

            val completFormTripPanelData = TripPanelData(
                String.format(
                    resourceStringsManager.getStringsForTripCacheUseCase()
                        .getOrDefault(StringKeys.COMPLETE_FORM_FOR_STOP, ""),
                    "1"
                ),
                TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
                2,
                0.0,
                0.0
            )

            tripPanelUseCase.listLauncherMessageWithPriority =
                PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                    add(
                        LauncherMessageWithPriority(
                            "select a stop",
                            TRIP_PANEL_SELECT_STOP_MSG_PRIORITY,
                            3
                        )
                    )
                }

            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns json
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopList
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            coEvery { tripPanelUseCase.isSafeToRespondMessages() } returns true
            coEvery { tripPanelUseCase.removeDidYouArriveMessagesOfStopIfCompletedOrDeleted(any()) } just runs
            val arrivalTripPanelJob = launch {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    stopAddressTripPanelData,
                    "",
                    1
                )
            }

            val completeFormtripPanelJob = launch {
                tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                    completFormTripPanelData,
                    "",
                    1
                )
            }

            arrivalTripPanelJob.join()
            completeFormtripPanelJob.join()

            assertEquals(
                TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
                tripPanelUseCase.startSendingMessagesToLocationPanel()
            )
        }

    @Test
    fun `verify trip panel dismiss`() =
        runTest {
            val stopAddressTripPanelData = TripPanelData(
                String.format(
                    resourceStringsManager.getStringsForTripCacheUseCase()
                        .getOrDefault(StringKeys.YOUR_NEXT_STOP, ""),
                    "4234"
                ),
                TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                1,
                0.0,
                0.0
            )
            tripPanelUseCase.listLauncherMessageWithPriority =
                PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                    add(
                        LauncherMessageWithPriority(
                            stopAddressTripPanelData.message,
                            TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                            stopAddressTripPanelData.messageId
                        )
                    )
                }
            val lastSentTripPanelMessage = LastSentTripPanelMessage(
                NEXT_STOP_MESSAGE_ID,
                stopAddressTripPanelData.message,
                0
            )
            every { tripPanelUseCase.lastSentTripPanelMessage } returns lastSentTripPanelMessage
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns emptyList()
            coEvery { tripPanelUseCase.isSafeToRespondMessages() } returns true
            tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                stopAddressTripPanelData,
                "",
                1
            )

            verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID)
            }
            verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(NEXT_STOP_MESSAGE_ID)
            }
            assertEquals(NEXT_STOP_MESSAGE_ID, tripPanelUseCase.lastSentTripPanelMessage.messageId)
        }

    @Test
    fun `verify message uniqueness of the priority queue if there is duplicate calls from the invoker, The duplicate calls will be replaced`() =
        runTest {
            val stopAddressTripPanelData = TripPanelData(
                String.format(
                    resourceStringsManager.getStringsForTripCacheUseCase()
                        .getOrDefault(StringKeys.YOUR_NEXT_STOP, EMPTY_STRING),
                    "4234"
                ),
                TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY,
                1
            )
            val completeFormTripPanelData = TripPanelData(
                String.format(
                    resourceStringsManager.getStringsForTripCacheUseCase()
                        .getOrDefault(StringKeys.COMPLETE_FORM_FOR_STOP, EMPTY_STRING),
                    "1"
                ),
                TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY,
                0
            )
            tripPanelUseCase.listLauncherMessageWithPriority =
                PriorityBlockingQueue(1, LauncherMessagePriorityComparator).apply {
                    add(
                        LauncherMessageWithPriority(
                            stopAddressTripPanelData.message,
                            stopAddressTripPanelData.priority,
                            stopAddressTripPanelData.messageId
                        )
                    )
                    add(
                        LauncherMessageWithPriority(
                            completeFormTripPanelData.message,
                            completeFormTripPanelData.priority,
                            completeFormTripPanelData.messageId
                        )
                    )
                }
            coEvery { tripPanelUseCase.isSafeToRespondMessages() } returns false

            tripPanelUseCase.putLauncherMessagesIntoPriorityQueue(
                stopAddressTripPanelData,
                "",
                1
            )

            assertEquals(2, tripPanelUseCase.listLauncherMessageWithPriority.size)
            assertEquals(
                completeFormTripPanelData.message, tripPanelUseCase.listLauncherMessageWithPriority.peek()?.message ?: EMPTY_STRING
            )
            assertEquals(2, tripPanelUseCase.listLauncherMessageWithPriority.size)
            assertEquals(
                completeFormTripPanelData.message,
                tripPanelUseCase.listLauncherMessageWithPriority.poll()?.message ?: EMPTY_STRING
            )
            assertEquals(
                stopAddressTripPanelData.message,
                tripPanelUseCase.listLauncherMessageWithPriority.poll()?.message ?: EMPTY_STRING
            )
            assertEquals(0, tripPanelUseCase.listLauncherMessageWithPriority.size)
        }

    private fun getStopDetails(): MutableList<StopDetail> {
        val stopDetails = mutableListOf<StopDetail>().apply {
            this.add(
                StopDetail(
                    stopid = 0,
                    sequenced = 0,
                    completedTime = ""
                )
            )
            this.add(
                StopDetail(
                    stopid = 1,
                    sequenced = 0,
                    completedTime = ""
                )
            )
            this.add(
                StopDetail(
                    stopid = 2,
                    sequenced = 1,
                    completedTime = ""
                )
            )
        }

        return stopDetails
    }

    @Test
    fun `verify negative guf timer returns false if stop data is null`() = runTest {
        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(
                any(),
                any(),
                any(),
                any()
            )
        } returns emptyList()
        assertFalse(tripPanelUseCase.needToShowNegativeGufTimerIfAvailable(1))
    }

    @Test
    fun `verify negative guf timer returns false if stop data is not having negative gu`() =
        runTest {
            val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 1,
                        responseSent = true,
                        gufType = 1
                    )
                )
            }
            coEvery {
                dispatchFirestoreRepo.getStopsFromFirestore(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns listOf(stopDetail)
            assertFalse(tripPanelUseCase.needToShowNegativeGufTimerIfAvailable(1))
        }

    @Test
    fun `verify negative guf timer returns true if stop data is not having correct negative guf`() =
        runTest {
            val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 1,
                        responseSent = true,
                        gufType = 2
                    )
                )
            }
            coEvery {
                dispatchFirestoreRepo.getStopsFromFirestore(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns listOf(stopDetail)
            assertTrue(tripPanelUseCase.needToShowNegativeGufTimerIfAvailable(1))
        }

    @Test
    fun `verify pollElementFromPriorityBlockingQueue removes the element irrespective of insertion` () {
        val listLauncherMessageWithPriority = PriorityBlockingQueue(1,LauncherMessagePriorityComparator)
        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt3, 1, 3, Pair(0.01, -0.01))
        )
        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt2, 1, 2, Pair(0.01, -0.01))
        )
        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt1, 0, 1)
        )
        assertEquals(
            didYouArriveAt1,
            Utils.pollElementFromPriorityBlockingQueue(listLauncherMessageWithPriority)
        )
    }

    @Test
    fun `verify pollElementFromPriorityBlockingQueue returns empty string when the queue is empty` () {
        val listLauncherMessageWithPriority = PriorityBlockingQueue(1,LauncherMessagePriorityComparator)
        assertEquals(
            EMPTY_STRING,
            Utils.pollElementFromPriorityBlockingQueue(listLauncherMessageWithPriority)
        )
    }

    @Test
    fun `verify setCurrentStopAndUpdateTripPanel for sequenced trip` () = runTest {
        val stopList = listOf(
            StopDetail(stopid = 1, sequenced = 1),
            StopDetail(stopid = 2, sequenced = 1)
        )
        coEvery { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(any()) } returns true
        tripPanelUseCase.setCurrentStopAndUpdateTripPanel(stopList)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(any())
        }
        coVerify(exactly = 0) {
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        }
    }

    @Test
    fun `verify setCurrentStopAndUpdateTripPanel for free float trip` () = runTest {
        val stopList = listOf(
            StopDetail(stopid = 1, sequenced = 0),
            StopDetail(stopid = 2, sequenced = 0)
        )
        coEvery { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(any()) } returns false
        coEvery { tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop() } returns true
        tripPanelUseCase.setCurrentStopAndUpdateTripPanel(stopList)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(any())
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        }
    }

    @Test
    fun `verify setCurrentStopAndUpdateTripPanel for sequenced trip when all stops are completed` () = runTest {
        val stopList = listOf(
            StopDetail(stopid = 1, sequenced = 1).apply { completedTime = "2024-02-16T13:19:54.765Z" },
            StopDetail(stopid = 2, sequenced = 1).apply { completedTime = "2024-02-16T13:19:58.765Z" }
        )
        tripPanelUseCase.setCurrentStopAndUpdateTripPanel(stopList)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(any())
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        }
    }

    @Test
    fun `verify setCurrentStopAndUpdateTripPanel for sequenced trip when all stops are deleted` () = runTest {
        val stopList = listOf(
            StopDetail(stopid = 1, sequenced = 1, deleted = 1),
            StopDetail(stopid = 2, sequenced = 1, deleted = 1)
        )
        tripPanelUseCase.setCurrentStopAndUpdateTripPanel(stopList)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(any())
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        }
    }

    @Test
    fun `verify removeMessageFromPriorityQueueAndUpdateTripPanelFlags with lastSentMessageId equals actual messageId` () = runTest {
        coEvery {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any())
        } just runs
        tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(LastSentTripPanelMessage(1, "", 1))
        tripPanelUseCase.removeMessageFromPriorityQueueAndUpdateTripPanelFlags(1)
        coVerify(exactly = 1) {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any())
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
        }
    }

    @Test
    fun `verify removeMessageFromPriorityQueueAndUpdateTripPanelFlags with lastSentMessageId not equals actual messageId` () = runTest {
        coEvery {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any())
        } just runs
        tripPanelUseCase.setLastSentTripPanelMessageForUnitTest(LastSentTripPanelMessage(2, "", 1))
        tripPanelUseCase.removeMessageFromPriorityQueueAndUpdateTripPanelFlags(1)
        coVerify(exactly = 1) {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any())
        }
        coVerify(exactly = 0) {
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
        }
    }

    @Test
    fun `verify scheduleBackgroundTimer start`() = runTest {
        every { tripPanelUseCase.countDownValue } returns 0
        every { tripPanelUseCase.isNegativeGufTimerRunning } returns false
        every { Log.d(any(), any()) } returns mockk()
        tripPanelUseCase.scheduleBackgroundTimer(0, "test")

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            Log.d(any(), any())
        }
    }

    @Test
    fun `verify cancelNegativeGufTimer execution`() {
        tripPanelUseCase.cancelNegativeGufTimer("test")

        verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            WorkflowEventBus.disposeNegativeGufTimerCacheOnTimerStop()
        }
    }

    @After
    fun tearDown() {
        unmockkObject(FormUtils)
        unmockkObject(com.trimble.ttm.commons.utils.FormUtils)
        unmockkObject(Utils)
        unmockkObject(Log)
        unmockkObject(WorkflowEventBus)
        unmockkAll()
    }
}