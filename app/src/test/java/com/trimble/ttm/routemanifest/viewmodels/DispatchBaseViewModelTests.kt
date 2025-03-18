package com.trimble.ttm.routemanifest.viewmodels

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.WorkManager
import com.google.gson.Gson
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.ArrivedGeoFenceTriggerData
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.customComparator.LauncherMessagePriorityComparator
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus.postStopCountListener
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.Address
import com.trimble.ttm.routemanifest.model.DRIVER_NEGATIVE_GUF
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.LastSentTripPanelMessage
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.ui.activities.DispatchDetailActivity
import com.trimble.ttm.routemanifest.usecases.DispatchBaseUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchValidationUseCase
import com.trimble.ttm.routemanifest.usecases.FetchDispatchStopsAndActionsUseCase
import com.trimble.ttm.routemanifest.usecases.RouteETACalculationUseCase
import com.trimble.ttm.routemanifest.usecases.SendDispatchDataUseCase
import com.trimble.ttm.routemanifest.usecases.StopDetentionWarningUseCase
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.utils.ADDED
import com.trimble.ttm.routemanifest.utils.FALSE
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import com.trimble.ttm.routemanifest.utils.STOP_DETAIL_SCREEN_TIME
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.TIMELINE_VIEW_COUNT
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP
import com.trimble.ttm.routemanifest.utils.TRUE
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.EnumMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.PriorityBlockingQueue
import kotlin.collections.set
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class MainCoroutineRule(
    private val dispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
) : TestWatcher(), TestCoroutineScope by TestCoroutineScope(dispatcher) {

    override fun starting(description: Description) {
        super.starting(description)
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        cleanupTestCoroutines()
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DispatchBaseViewModelTests: KoinTest {

    private lateinit var dispatchViewModel: DispatchDetailViewModel
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var application: WorkflowApplication
    private lateinit var dataStoreManager: DataStoreManager
    @MockK
    private lateinit var routeETACalculationUseCase: RouteETACalculationUseCase
    @MockK
    private lateinit var tripCompletionUseCase: TripCompletionUseCase
    private lateinit var formDataStoreManager: FormDataStoreManager
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase
    @RelaxedMockK
    private lateinit var context: Context
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase
    @MockK
    private lateinit var dispatchBaseUseCase: DispatchBaseUseCase
    @MockK
    private lateinit var sendDispatchDataUseCase: SendDispatchDataUseCase
    @MockK
    private lateinit var dispatchValidationUseCase: DispatchValidationUseCase
    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo
    @MockK
    private lateinit var stopDetentionWarningUseCase: StopDetentionWarningUseCase
    @MockK
    private lateinit var tripStartUseCase: TripStartUseCase
    @RelaxedMockK
    private lateinit var workManager: WorkManager
    @RelaxedMockK
    private lateinit var featureGatekeeper: FeatureGatekeeper
    @RelaxedMockK
    private lateinit var formLibraryUseCase: FormLibraryUseCase

    private val coroutineDispatcherProvider = TestDispatcherProvider()

    private val testScope = TestScope()
    @MockK
    private lateinit var preferences: Preferences

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase

    private var defaultDispatchId = "43434"

    private val mockShowTripCompleteToast: () -> Unit = mockk(relaxed = true)

    @RelaxedMockK
    private lateinit var sendWorkflowEventsToAppUseCase : SendWorkflowEventsToAppUseCase
    private val modulesToInject = module {
        factory { backboneUseCase }
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        startKoin {
            loadKoinModules(modulesToInject)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        mockkStatic(Gson::class)
        mockkStatic(DispatchDetailActivity::class)
        mockkObject(WorkflowEventBus)
        mockkObject(AppLauncherCommunicator)
        mockkObject(Log)
        mockkObject(Utils)
        every {
            Log.logUiInteractionInInfoLevel(any(), any(), any(), any(), any())
        } returns Unit
        every { context.applicationContext } returns context
        every { application.applicationContext } returns context
        every { context.filesDir } returns temporaryFolder.newFolder()
        every { dispatchFirestoreRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        routeETACalculationUseCase =spyk(
            RouteETACalculationUseCase(
                TestScope(),
                mockk(),
                sendDispatchDataUseCase,
                mockk(),
                dispatchFirestoreRepo,
                mockk(),
                fetchDispatchStopsAndActionsUseCase,
                dataStoreManager,
                mockk(),
                sendWorkflowEventsToAppUseCase
            )
        )
        dispatchStopsUseCase = spyk(
            DispatchStopsUseCase(
                mockk(),
                dispatchFirestoreRepo,
                coroutineDispatcherProvider,
                mockk(),
                routeETACalculationUseCase,
                featureGatekeeper,
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                dataStoreManager,
                fetchDispatchStopsAndActionsUseCase
            )
        )
        every { dispatchStopsUseCase.appModuleCommunicator } returns appModuleCommunicator
        coEvery { dispatchStopsUseCase.getFirstUncompletedStopForSequentialTrip(any()) } returns StopDetail()
        every { application.applicationScope } returns testScope
        coEvery {
            dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(
                any(),
                any(),
                any()
            )
        } returns true
        every { sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any()) } just runs
        mockkStatic(Log::class)
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf()
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns listOf()
        coEvery {
            tripCompletionUseCase.listenToStopActions(
                any(),
                any(),
                any(),
                any()
            )
        } returns flow { }
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple("1", "2", true)
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns defaultDispatchId
        coEvery { appModuleCommunicator.getSelectedDispatchId(any()) } returns defaultDispatchId
        coEvery { tripCompletionUseCase.getStopsForDispatch(any(), any(), any()) } returns flow { }
        every {
            dispatchBaseUseCase.getDispatchActiveState(
                any(),
                any()
            )
        } returns DispatchActiveState.ACTIVE
        dispatchViewModel = spyk(
            DispatchDetailViewModel(
                applicationInstance = application,
                routeETACalculationUseCase = routeETACalculationUseCase,
                tripCompletionUseCase = tripCompletionUseCase,
                dataStoreManager = dataStoreManager,
                formDataStoreManager = formDataStoreManager,
                dispatchBaseUseCase = dispatchBaseUseCase,
                dispatchStopsUseCase = dispatchStopsUseCase,
                sendDispatchDataUseCase = sendDispatchDataUseCase,
                tripPanelUseCase = tripPanelUseCase,
                dispatchValidationUseCase = dispatchValidationUseCase,
                coroutineDispatcher = TestDispatcherProvider(),
                stopDetentionWarningUseCase = stopDetentionWarningUseCase,
                backboneUseCase = backboneUseCase,
                lateNotificationUseCase = mockk(),
                sendWorkflowEventsToAppUseCase = mockk(),
                fetchDispatchStopsAndActionsUseCase = fetchDispatchStopsAndActionsUseCase,
                tripStartUseCase = tripStartUseCase,
                formLibraryUseCase = formLibraryUseCase
            ), recordPrivateCalls = true
        )
        dispatchViewModel.setActiveDispatchFlowValue(DispatchActiveState.ACTIVE)
    }

    @Test
    fun `verify sendDispatchCompleteEvent`() {
        every { tripCompletionUseCase.sendDispatchCompleteEventToCPIK(any()) } just runs
        dispatchViewModel.sendDispatchCompleteEventToCPIK()
        coVerify {
            tripCompletionUseCase.sendDispatchCompleteEventToCPIK(any())
        }
    }

    @Test
    fun `verify isManualTripCompletionDisabled - cid and vehicle number available`() = runTest {
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple(
            "12",
            "234",
            true
        )
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.SELECTED_DISPATCH_KEY,
                EMPTY_STRING
            )
        } returns "123"
        coEvery {
            tripCompletionUseCase.isManualTripCompletionDisabled(
                any(),
                any(),
                any(),
                any()
            )
        } returns true

        val collectJob = launch(UnconfinedTestDispatcher()) {
            dispatchViewModel.isManualTripCompletionDisabled().collect()
        }

        coVerify {
            tripCompletionUseCase.isManualTripCompletionDisabled(any(), any(), any(), any())
        }

        collectJob.cancel()
    }

    @Test
    fun `verify isManualTripCompletionDisabled - cid and vehicle number unavailable`() =
        runTest {
            coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple(
                "12",
                "234",
                false
            )
            val collectJob = launch(UnconfinedTestDispatcher()) {
                dispatchViewModel.isManualTripCompletionDisabled().collect()
            }

            coVerify(exactly = 0) {
                tripCompletionUseCase.isManualTripCompletionDisabled(any(), any(), any(), any())
            }
            collectJob.cancel()
        }

    @Test
    fun `verify isManualTripCompletionDisabled dispatchId unavailable`() =
        runTest {
            coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple(
                "12",
                "234",
                true
            )
            coEvery {
                dispatchViewModel.getSelectedDispatchId("test")
            }returns EMPTY_STRING
            val collectJob = launch(UnconfinedTestDispatcher()) {
                dispatchViewModel.isManualTripCompletionDisabled().collect()
            }
            Assert.assertFalse(dispatchViewModel.isManualTripCompletionDisabled().first())

            coVerify(exactly = 0) {
                tripCompletionUseCase.isManualTripCompletionDisabled(any(), any(), any(), any())
            }
            collectJob.cancel()
        }

    @Test
    fun `verify isManualTripCompletionDisabled dispatchId available`() =
        runTest {
            coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple(
                "12",
                "234",
                true
            )
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.SELECTED_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns "123"
            coEvery {
                tripCompletionUseCase.isManualTripCompletionDisabled(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns true
            val collectJob = launch(UnconfinedTestDispatcher()) {
                dispatchViewModel.isManualTripCompletionDisabled().collect()
            }

            coVerify {
                tripCompletionUseCase.isManualTripCompletionDisabled(any(), any(), any(), any())
            }
            collectJob.cancel()
        }

    @Test
    fun `verify handleStopRemoval - basic flow`() = runTest {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING
            )
        } returns "100"

        coEvery {
            dispatchBaseUseCase.processStopRemoval(
                any(),
                any()
            )
        } returns CopyOnWriteArrayList<StopDetail>()
        coEvery {
            dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
        } returns "2242"
        with(dispatchViewModel.stopDetailList) {
            add(StopDetail(stopid = 0, sequenced = 1))
            add(StopDetail(stopid = 1, sequenced = 0))
            add(StopDetail(stopid = 2, sequenced = 1))
        }
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple("1", "2", true)
        dispatchViewModel.handleStopRemoval(
            StopDetail(sequenced = 1), setOf(),
            CopyOnWriteArrayList<StopDetail>(),
            true,
            1
        )

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchBaseUseCase.processStopRemoval(any(), any())
        }
    }

    @Test
    fun `verify handleStopRemoval - last updated stops count is greater`() = runTest {
        val dispatchId = "100"
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING
            )
        } returns dispatchId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING) } returns dispatchId
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchId
        coEvery { dispatchStopsUseCase.markActiveDispatchStopAsManipulated() } just runs

        coEvery {
            dispatchBaseUseCase.processStopRemoval(
                any(),
                any()
            )
        } returns CopyOnWriteArrayList<StopDetail>()
        coEvery { formDataStoreManager.containsKey(intPreferencesKey("formCountOfStops-1")) } returns true
        coEvery { formDataStoreManager.removeItem(intPreferencesKey("formCountOfStops-1")) } returns preferences
        coEvery {
            dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
        } returns "2242"
        with(dispatchViewModel.stopDetailList) {
            add(StopDetail(stopid = 0, sequenced = 1))
            add(StopDetail(stopid = 1, sequenced = 0))
            add(StopDetail(stopid = 2, sequenced = 1))
        }

        dispatchViewModel.handleStopRemoval(
            StopDetail(sequenced = 1), setOf(),
            CopyOnWriteArrayList<StopDetail>(),
            false,
            1
        )

        coVerify {
            dispatchBaseUseCase.processStopRemoval(any(), any())
            formDataStoreManager.containsKey(intPreferencesKey("formCountOfStops-1"))
            formDataStoreManager.removeItem(intPreferencesKey("formCountOfStops-1"))
        }
    }

    @Test
    fun `verify handleStopRemoval for inactive trip`() = runTest {

        val currentDispId = "100"
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING
            )
        } returns currentDispId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING) } returns "200"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns currentDispId
        coEvery { dispatchStopsUseCase.markActiveDispatchStopAsManipulated() } just runs

        coEvery {
            dispatchBaseUseCase.processStopRemoval(
                any(),
                any()
            )
        } returns CopyOnWriteArrayList<StopDetail>()
        coEvery { formDataStoreManager.containsKey(intPreferencesKey("formCountOfStops-1")) } returns true
        coEvery { formDataStoreManager.removeItem(intPreferencesKey("formCountOfStops-1")) } returns preferences
        coEvery {
            dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
        } returns "2242"
        with(dispatchViewModel.stopDetailList) {
            add(StopDetail(stopid = 0, sequenced = 1))
            add(StopDetail(stopid = 1, sequenced = 0))
            add(StopDetail(stopid = 2, sequenced = 1))
        }

        dispatchViewModel.handleStopRemoval(
            StopDetail(sequenced = 1), setOf(),
            CopyOnWriteArrayList<StopDetail>(),
            false,
            1
        )

        coVerify {
            dispatchBaseUseCase.processStopRemoval(any(), any())
            formDataStoreManager.containsKey(intPreferencesKey("formCountOfStops-1"))
            formDataStoreManager.removeItem(intPreferencesKey("formCountOfStops-1"))
        }
        coVerify(exactly = 0) { dispatchStopsUseCase.markActiveDispatchStopAsManipulated() }
    }

    @Test
    fun `verify handleStopRemoval for active trip`() = runTest {

        val currentDispId = "100"
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING
            )
        } returns currentDispId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING) } returns currentDispId
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns currentDispId
        coEvery { dispatchStopsUseCase.markActiveDispatchStopAsManipulated() } just runs

        coEvery {
            dispatchBaseUseCase.processStopRemoval(
                any(),
                any()
            )
        } returns CopyOnWriteArrayList<StopDetail>()
        coEvery { formDataStoreManager.containsKey(intPreferencesKey("formCountOfStops-1")) } returns true
        coEvery { formDataStoreManager.removeItem(intPreferencesKey("formCountOfStops-1")) } returns preferences
        coEvery {
            dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
        } returns "2242"
        with(dispatchViewModel.stopDetailList) {
            add(StopDetail(stopid = 0, sequenced = 1))
            add(StopDetail(stopid = 1, sequenced = 0))
            add(StopDetail(stopid = 2, sequenced = 1))
        }

        dispatchViewModel.handleStopRemoval(
            StopDetail(sequenced = 1), setOf(),
            CopyOnWriteArrayList<StopDetail>(),
            false,
            1
        )

        coVerify {
            dispatchBaseUseCase.processStopRemoval(any(), any())
            formDataStoreManager.containsKey(intPreferencesKey("formCountOfStops-1"))
            formDataStoreManager.removeItem(intPreferencesKey("formCountOfStops-1"))
            dispatchStopsUseCase.markActiveDispatchStopAsManipulated()
        }
    }

    /*  @Test
      fun `verify  handleStopRemoval - sendDispatchDataUseCase is called `() = runTest {

          val stopList = CopyOnWriteArrayList<StopDetail>()
          stopList.add(StopDetail(stopid = 1, name = "test"))
          coEvery {
              dispatchBaseUseCase.processStopRemoval(
                  any(),
                  any()
              )
          } returns CopyOnWriteArrayList<StopDetail>()
          coEvery {
              removeExpiredTripPanelMessageUseCase.removeMessageFromTripPanelQueue(
                  any(),
                  any(),
                  any()
              )
          } just runs
          coEvery {
              dataStoreManager.getValue(
                  DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING
              )
          } returns "1234"
          coEvery {
              sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(
                  any()
              )
          } just runs
          coEvery {
              dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
          } returns "2242"
          with(dispatchViewModel.stopDetailList) {
              add(StopDetail(stopid = 0, sequenced = 1))
              add(StopDetail(stopid = 1, sequenced = 0))
              add(StopDetail(stopid = 2, sequenced = 1))
          }

          dispatchViewModel.handleStopRemoval(
              StopDetail(sequenced = 1), setOf(),
              stopList,
              false,
              0, removeExpiredTripPanelMessageUseCase
          )

          coVerify {
              dispatchBaseUseCase.processStopRemoval(any(), any())
              removeExpiredTripPanelMessageUseCase.removeMessageFromTripPanelQueue(
                  any(),
                  any(),
                  any()
              )

              sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(
                  any()
              )
          }
      }*/

    @Test
    fun `verify handleStopAdditionOrUpdate  - basic flow`() = runTest {

        coEvery {
            dispatchBaseUseCase.processStopAdditionOrUpdate(
                any(),
                any()
            )
        } returns CopyOnWriteArrayList<StopDetail>()
        dispatchViewModel.handleStopAdditionOrUpdate(
            StopDetail(sequenced = 1),
            CopyOnWriteArrayList<StopDetail>(),
            true,
            1
        )

        verify {
            dispatchBaseUseCase.processStopAdditionOrUpdate(any(), any())
        }
    }

    @Test
    fun `verify handleStopAdditionOrUpdate for stop addition`() = runTest {
        val stops = CopyOnWriteArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 1, name = "test", sequenced = 1))
        }
        coEvery {
            dispatchBaseUseCase.processStopAdditionOrUpdate(
                any(),
                any()
            )
        } returns stops
        coEvery { postStopCountListener(any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "34343"

        dispatchViewModel.handleStopAdditionOrUpdate(
            stops[0],
            stops,
            false,
            0
        )

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            postStopCountListener(ADDED)
        }
    }

    @Test
    fun `verify handleStopAdditionOrUpdate  - basic flow with FF stops`() = runTest {

        coEvery {
            dispatchBaseUseCase.processStopAdditionOrUpdate(
                any(),
                any()
            )
        } returns CopyOnWriteArrayList<StopDetail>()
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, FALSE
            )
        } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "34343"
        coEvery {dispatchStopsUseCase.updateSequencedKeyInDataStore(any(), any())} just runs
        dispatchViewModel.handleStopAdditionOrUpdate(
            StopDetail(sequenced = 0, dispid = "34343"),
            CopyOnWriteArrayList<StopDetail>(),
            true,
            1
        )

        coVerify {
            dispatchBaseUseCase.processStopAdditionOrUpdate(any(), any())
            dispatchStopsUseCase.updateSequencedKeyInDataStore(any(), any())
        }
    }

    @Test
    fun `verify handleStopAdditionOrUpdate  - Not first read`() = runTest {

        coEvery {
            dispatchBaseUseCase.processStopAdditionOrUpdate(
                any(),
                any()
            )
        } returns CopyOnWriteArrayList<StopDetail>()
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, FALSE
            )
        } just runs
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns false
        coEvery {
            formDataStoreManager.getValue(
                intPreferencesKey(name = FORM_COUNT_FOR_STOP + "1"),
                ZERO
            )
        } returns 123
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "34343"
        coEvery {dispatchStopsUseCase.updateSequencedKeyInDataStore(any(), any())} just runs
        dispatchViewModel.handleStopAdditionOrUpdate(
            StopDetail(
                stopid = 1,
                sequenced = 0,
                completedTime = "test time",
                dispid = "34343"
            ), CopyOnWriteArrayList<StopDetail>(), false, 1
        )

        coVerify {
            dispatchBaseUseCase.processStopAdditionOrUpdate(any(), any())
            dispatchStopsUseCase.updateSequencedKeyInDataStore(any(), any())
            formDataStoreManager.getValue(
                intPreferencesKey(name = FORM_COUNT_FOR_STOP + "1"),
                ZERO
            )
        }
    }

    @Test
    fun `verify syncForms`() = runTest {
        coEvery { tripCompletionUseCase.getFormsTemplateListFlow() } returns flow {
            emit(
                arrayListOf(
                    FormTemplate(formDef = FormDef(cid = 1))
                )
            )
        }
        coEvery { tripCompletionUseCase.formsSync(any(), any()) } just runs
        coEvery { dataStoreManager.getValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, any()) } returns false
        coEvery { dataStoreManager.getValue(DataStoreManager.NAVIGATION_ELIGIBLE_STOP_LIST_KEY, any()) } returns emptySet()
        coEvery { dispatchStopsUseCase.setStopsEligibilityForFirstTime(any(), any()) } returns emptySet()

        dispatchViewModel.syncForms("12345", mutableSetOf(FormDef(cid = 1)), "100")

        coVerify(exactly = 1) {
            tripCompletionUseCase.getFormsTemplateListFlow()
            tripCompletionUseCase.formsSync(any(), any())
        }
    }

    @Test
    fun `verify syncForms - customer id is empty`() = runTest {
        coEvery { tripCompletionUseCase.getFormsTemplateListFlow() } returns flow { emit(arrayListOf()) }
        dispatchViewModel.syncForms("", mutableSetOf(), "100")

        coVerify(exactly = 0) { tripCompletionUseCase.getFormsTemplateListFlow() }
    }

    @Test
    fun `verify getFormData`() = runTest {

        coEvery { tripCompletionUseCase.isFormSaved(any(), any()) } returns true
        dispatchViewModel.getFormDataToGetFormSaveStatus("test", "1")

        coVerify(exactly = 1) {
            tripCompletionUseCase.isFormSaved(any(), any())
        }
    }

    @Test
    fun `verify putStopsIntoPreferenceForServiceReference`() = runTest {

        dispatchViewModel.putStopsIntoPreferenceForServiceReference("")

        coVerify(exactly = 1) {
            dataStoreManager.setValue(
                DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                any()
            )
        }
    }

    @Test
    fun `verify hasFirstStopAsCurrentStopHandled - first stop completed`() = runTest {

        val stopList = CopyOnWriteArrayList<StopDetail>()

        coEvery { appModuleCommunicator.hasActiveDispatch(any(), any()) } returns false
        coEvery { dispatchBaseUseCase.anyStopAlreadyCompleted(any()) } returns true
        val collectJob = launch(UnconfinedTestDispatcher()) {
            dispatchViewModel.hasFirstStopAsCurrentStopHandled(stopList).collect()
        }

        coVerify(exactly = 0) {
            dispatchStopsUseCase.shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(any(), any())
        }
        collectJob.cancel()
    }

    @Test
    fun `verify hasFirstStopAsCurrentStopHandled - current stop NA`() = runTest {

        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1))
        coEvery { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
            any()
        ) } returns true
        coEvery { dispatchStopsUseCase.shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(any(), any()) } returns true
        coEvery {
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                any(),
                any()
            )
        } returns true
        coEvery { dispatchBaseUseCase.anyStopAlreadyCompleted(any()) } returns false
        coEvery { dispatchStopsUseCase.doesStoreHasCurrentStop(any()) } returns false
        val collectJob = launch(UnconfinedTestDispatcher()) {
            dispatchViewModel.hasFirstStopAsCurrentStopHandled(stopList).collect()
        }

        coVerify(exactly = 0) {
            dispatchBaseUseCase.getCurrentStop(any())
        }
        collectJob.cancel()
    }

    @Test
    fun `verify hasFirstStopAsCurrentStopHandled - current stop Available`() = runTest {

        val results = mutableSetOf<Boolean>()
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1))
        coEvery { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
            any()
        ) } returns true
        coEvery { dispatchStopsUseCase.shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(any(), any()) } returns true
        coEvery {
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                any(),
                any()
            )
        } returns true
        coEvery { dispatchBaseUseCase.anyStopAlreadyCompleted(any()) } returns false
        coEvery { dispatchStopsUseCase.doesStoreHasCurrentStop(any()) } returns false
        coEvery { dispatchBaseUseCase.getCurrentStop(any()) } returns Stop(arrivedRadius = 2000)
        coEvery { dispatchStopsUseCase.getDistanceInFeet(any()) } returns 10000.00

        val collectJob = launch(UnconfinedTestDispatcher()) {
            dispatchViewModel.hasFirstStopAsCurrentStopHandled(stopList).collect {
                results.add(it)
            }
        }

        assertTrue(results.first())
        collectJob.cancel()
    }

    @Test
    fun `verify hasFirstStopAsCurrentStopHandled - current stops Action Not Available`() =
        runTest {
            val results = mutableSetOf<Boolean>()
            val stopList = CopyOnWriteArrayList<StopDetail>()
            stopList.add(StopDetail(stopid = 1))
            coEvery { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
                any()
            ) } returns true
            coEvery { dispatchStopsUseCase.shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(any(), any()) } returns true
            coEvery {
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    any(),
                    any()
                )
            } returns true
            coEvery { appModuleCommunicator.doGetCid() } returns "123"
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123"
            coEvery { dispatchStopsUseCase.handleStopEvents(null, any(), any(), any(), any()) } returns ""
            coEvery { dispatchBaseUseCase.anyStopAlreadyCompleted(any()) } returns false
            coEvery { dispatchStopsUseCase.doesStoreHasCurrentStop(any()) } returns true
            coEvery { dispatchBaseUseCase.getCurrentStop(any()) } returns Stop(
                arrivedRadius = 100,
                stopId = 1
            )
            coEvery { dispatchStopsUseCase.getDistanceInFeet(any()) } returns 50.00
            coEvery { dispatchStopsUseCase.getActionDataFromStop(any(), any(),any()) } returns null

            val collectJob = launch(UnconfinedTestDispatcher()) {
                dispatchViewModel.hasFirstStopAsCurrentStopHandled(stopList).collect {
                    results.add(it)
                }
            }

            assertFalse(results.first())
            coVerify {
                dispatchStopsUseCase.getDistanceInFeet(any())
            }
            collectJob.cancel()
        }

    @Test
    fun `verify hasFirstStopAsCurrentStopHandled - current stops Action Available is not calling handleStopEvents when featureFlag is enabled`() =
        runTest {
            val results = mutableSetOf<Boolean>()
            val action = Action(actionid = 1)
            val stopList = CopyOnWriteArrayList<StopDetail>()
            stopList.add(StopDetail(stopid = 1))
            coEvery { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
                any()
            ) } returns true
            coEvery { dispatchStopsUseCase.shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(any(), any()) } returns true
            coEvery {
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    any(),
                    any()
                )
            } returns true
            coEvery { appModuleCommunicator.doGetCid() } returns "123"
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123"
            coEvery { dispatchBaseUseCase.anyStopAlreadyCompleted(any()) } returns false
            coEvery { dispatchStopsUseCase.doesStoreHasCurrentStop(any()) } returns true
            coEvery { dispatchBaseUseCase.getCurrentStop(any()) } returns Stop(
                arrivedRadius = 2000,
                stopId = 1
            )
            coEvery { dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any()) } returns ""
            coEvery { dispatchStopsUseCase.getDistanceInFeet(any()) } returns 500.00
            coEvery {
                dispatchStopsUseCase.getActionDataFromStop(
                    any(),
                    any(),
                    any()
                )
            } returns action

            val collectJob = launch(UnconfinedTestDispatcher()) {
                dispatchViewModel.hasFirstStopAsCurrentStopHandled(stopList).collect {
                    results.add(it)
                }
            }

            assertFalse(results.first())

            coVerify(exactly = 0) {
                dispatchStopsUseCase.handleStopEvents(
                    any(), any(), any(), any(), any()
                )
            }

            collectJob.cancel()
        }

    @Test
    fun `verify hasFirstStopAsCurrentStopHandled - current stops Action Available is calling handleStopEvents when featureFlag is disabled`() =
        runTest {
            val results = mutableSetOf<Boolean>()
            val stopList = CopyOnWriteArrayList<StopDetail>()
            stopList.add(StopDetail(stopid = 1))
            coEvery { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
                any()
            ) } returns true
            coEvery { dispatchStopsUseCase.shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(any(), any()) } returns true
            coEvery {
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    any(),
                    any()
                )
            } returns true
            coEvery { appModuleCommunicator.doGetCid() } returns "123"
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123"
            coEvery { dispatchBaseUseCase.anyStopAlreadyCompleted(any()) } returns false
            coEvery { dispatchStopsUseCase.doesStoreHasCurrentStop(any()) } returns true
            coEvery { dispatchBaseUseCase.getCurrentStop(any()) } returns Stop(
                arrivedRadius = 2000,
                stopId = 1
            )
            coEvery { dispatchStopsUseCase.getDistanceInFeet(any()) } returns 500.00


            val collectJob = launch(UnconfinedTestDispatcher()) {
                dispatchViewModel.hasFirstStopAsCurrentStopHandled(stopList).collect {
                    results.add(it)
                }
            }

            assertFalse(results.first())

            coVerify {
                dispatchStopsUseCase.getDistanceInFeet(any())
            }

            collectJob.cancel()

        }


    private fun openDispatchActivityHelper(
        cid: String,
        vNumber: String,
        arrivedFormId: Int,
        verificationCount: Int,
        vehicleNumberCountVerify: Int,
        cidCountVerify: Int = 1,
        dispatchCountVerify: Int = 1
    ) = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns vNumber
        coEvery { dispatchStopsUseCase.isComposeFormFeatureFlagEnabled(any()) } returns false
        coEvery {
            dispatchStopsUseCase.addDispatchFormPathToFormStack(
                any(), any()
            )
        } just runs
        coEvery {
            appModuleCommunicator.getCurrentWorkFlowId(any())
        } returns "wert"
        every { application.applicationContext } returns context
        every { context.startActivity(any()) } just runs

        dispatchViewModel.openDispatchFormActivity(
            Stop(stopName = "test", stopId = 1, arrivedFormId = arrivedFormId),
            Action(actionid = 1, driverFormid = 1, driverFormClass = 1),
            false
        )

        coVerify(exactly = verificationCount) {
            dispatchStopsUseCase.addDispatchFormPathToFormStack(
                any(), any()
            )
        }

        coVerify(exactly = dispatchCountVerify) { appModuleCommunicator.getCurrentWorkFlowId(any()) }

        coVerify(exactly = cidCountVerify) {
            appModuleCommunicator.doGetCid()
        }

        coVerify(exactly = vehicleNumberCountVerify) {
            appModuleCommunicator.doGetTruckNumber()
        }
    }

    @Test
    fun `verify openDispatchFormActivity - all conditions satisfied`() = runTest {
        openDispatchActivityHelper("123", "32432", 4234, 1, 2, 3,1)
    }

    @Test
    fun `verify openDispatchFormActivity - cid empty`() = runTest {
        openDispatchActivityHelper("", "32432", 4234, 0, 0, 1,0)
    }

    @Test
    fun `verify openDispatchFormActivity - vehicle number empty`() = runTest {
        openDispatchActivityHelper("123", "", 4234, 0, 1, dispatchCountVerify = 0)
    }

    @Test
    fun `verify openDispatchFormActivity - arrived form id invalid`() = runTest {
        openDispatchActivityHelper("123", "32432", -1, 0, 1, dispatchCountVerify = 0)
    }

    @Test
    fun `verify handleNavigateClicked - there is no active dispatch`() = runTest {

        coEvery { dataStoreManager.containsKey(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns false
        dispatchViewModel.handleNavigateClicked(StopDetail(stopid = 1), context)

        coVerify(exactly = 0) { sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(
            any()
        ) }
    }

    @Test
    fun `verify handleNavigateClicked - there is an active freefloat dispatch`() = runTest {
        val dispatchId = "1234555"
        with(dispatchViewModel.stopDetailList) {
            add(StopDetail(stopid = 0, sequenced = 1))
            add(StopDetail(stopid = 1, sequenced = 0))
            add(StopDetail(stopid = 2, sequenced = 1))
        }
        coEvery {  dataStoreManager.getValue(
            DataStoreManager.ACTIVE_DISPATCH_KEY,
            EMPTY_STRING) } returns "1000"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchId
        coEvery { dispatchViewModel.getSelectedDispatchId(any()) } returns dispatchId
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                any(), any()
            )
        } returns true
        coEvery {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(
                any()
            )
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
        } just runs
        coEvery {
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        } returns true
        coEvery { sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(
            any()
        ) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulation() } just runs

        dispatchViewModel.handleNavigateClicked(dispatchViewModel.stopDetailList[1], context)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(any())
        }
    }

    @Test
    fun `verify handleNavigateClicked - there is no active stop`() = runTest {
        val dispatchId = "1234555"
        coEvery { dataStoreManager.hasActiveDispatch(any(), any()) } returns true
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, any()) } returns dispatchId
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchId
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                any(), any()
            )
        } returns true
        coEvery {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(
                any()
            )
        } just runs
        coEvery { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery {
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        } returns true
        coEvery { sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(
            any()
        ) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulation() } just runs
        dispatchViewModel.handleNavigateClicked(StopDetail(stopid = 1), context)
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                any(), any()
            )
            sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(
                any()
            )
        }
    }

    @Test
    fun `verify handleNavigateClicked - there is an active stop`() = runTest {
        val dispatchId = "1234555"
        coEvery { dataStoreManager.containsKey(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns true
        coEvery { dataStoreManager.containsKey(DataStoreManager.CURRENT_STOP_KEY) } returns true
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, any()) } returns dispatchId
        coEvery { dataStoreManager.hasActiveDispatch(any(), false) } returns true
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchId
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                any(), any()
            )
        } returns true
        coEvery {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(
                any()
            )
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
        } just runs
        coEvery {
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        } returns true
        coEvery { sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(
            any()
        ) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulation() } just runs
        dispatchViewModel.handleNavigateClicked(StopDetail(stopid = 1), context)

        coVerify(exactly = 1) {
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                any(), any()
            )
            sendDispatchDataUseCase.sendDispatchDataToMapsForSelectedFreeFloatStop(
                any()
            )
        }
    }

    @Test
    fun `verify getSelectedDispatchId selectedDispatchId was not initialised`() = runTest {

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.SELECTED_DISPATCH_KEY,
                any()
            )
        } returns "1234"
        assertTrue(dispatchViewModel.getSelectedDispatchId(EMPTY_STRING) == "1234")
    }

    @Test
    fun verifygetStopsForDispatchemptycidorvehicleid() = runTest {
        val stops = setOf(StopDetail(stopid = 1))
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple(
            "1234",
            "2345",
            true
        )
        coEvery { dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(any(), any(), any()) }  returns true
        coEvery { tripCompletionUseCase.getStopsForDispatch(any(), any(), any()) } returns flow {
            emit(
                stops
            )
        }
        dispatchViewModel.getStopsForDispatch("1234")
        coVerify(exactly = 2) {
            dispatchBaseUseCase.getCidAndTruckNumber(any())
        }
        coVerify {
            tripCompletionUseCase.getStopsForDispatch(any(), any(), any())
        }
    }

    @Test
    fun `verify getStopsForDispatch for stop removal`() = runTest {
        val stops = setOf(StopDetail(stopid = 1, deleted = 1))
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple(
            "1234",
            "2345",
            true
        )
        coEvery { tripCompletionUseCase.getStopsForDispatch(any(), any(), any()) } returns flow {
            emit(
                stops
            )
        }
        coEvery { dispatchViewModel.handleStopRemoval(any(), any(), any(), any(), any()) } just runs
        dispatchViewModel.setActiveDispatchFlowValue(DispatchActiveState.ACTIVE)
        dispatchViewModel.getStopsForDispatch("1234")
        coVerify(exactly = 2, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchBaseUseCase.getCidAndTruckNumber(any())
        }
        coVerify {
            tripCompletionUseCase.getStopsForDispatch(any(), any(), any())
        }
    }

    @Test
    fun `verify getStopsForDispatch `() = runTest {
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple(
            "",
            "2345",
            false
        )
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "345"
        dispatchViewModel.getStopsForDispatch("")

        coVerify(exactly = 2) { dispatchBaseUseCase.getCidAndTruckNumber(any()) }
        coVerify(exactly = 0) { tripCompletionUseCase.getStopsForDispatch(any(), any(), any()) }
    }

    @Test
    fun `verify getVIDFromDispatchData`() = runTest {

        coEvery {
            dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(
                any(),
                any(),
                any()
            )
        } returns true
        coEvery {
            dispatchViewModel.getSelectedDispatchId(any())
        } returns "1234"

        dispatchViewModel.initializeDispatchDetailViewModel()

        coVerify(exactly = 1) {
            dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(any(), any(), any())
        }
    }

    @Test
    fun `verify getVIDFromDispatchData with a empty dispatch ID`() = runTest {
        coEvery {
            dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(
                any(),
                any(),
                any()
            )
        } returns true
        coEvery {
            dispatchViewModel.getSelectedDispatchId(any())
        } returns ""

        dispatchViewModel.initializeDispatchDetailViewModel()

        coVerify(exactly = 0) {
            dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(any(), any(), any())
        }
    }

    @Test
    fun `verify sendDispatchStartDataToBackbone`() = runTest {

        coEvery { backboneUseCase.setWorkflowStartAction(any()) } just runs

        dispatchViewModel.sendDispatchStartDataToBackbone("123", this)

        coVerify {
            backboneUseCase.setWorkflowStartAction(any())
        }
    }

    @Test
    fun `verify obtainFormattedAddress valid address`() = runTest {
        val results = mutableSetOf<String>()
        val address = Address("AA", "BB", "CC", "DD", "EE", "FF", "HH")
        coEvery { dataStoreManager.getStopAddress(any()) } returns Utils.toJsonString(
            address,
            Gson()
        )!!
        val collectJob = launch(UnconfinedTestDispatcher()) {
            dispatchViewModel.obtainFormattedAddress(
                1,
                isShortFormNAStr = true,
                isLocaleAvailable = true
            ).collect {
                results.add(it)
            }
        }
        assertTrue( results.elementAt(0) == "BB\nCC, DD HH" )
        collectJob.cancel()
    }

    @Test
    fun `verify obtainFormattedAddress locale NA Short Form NA`() = runTest {
        val results = mutableSetOf<String>()
        val address = Address("AA", "BB", "CC", "DD", "EE", "FF", "HH")
        coEvery { dataStoreManager.getStopAddress(any()) } returns Utils.toJsonString(
            address,
            Gson()
        )!!

        every { application.getString(any()) } returns "N/A"
        val collectJob = launch(UnconfinedTestDispatcher()) {
            dispatchViewModel.obtainFormattedAddress(
                1,
                isShortFormNAStr = true,
                isLocaleAvailable = false
            ).collect {
                results.add(it)
            }
        }
        assertTrue( results.elementAt(0) == "N/A")
        collectJob.cancel()
    }

    @Test
    fun `verify obtainFormattedAddress locale NA Short Form Not Available`() = runTest {
        val results = mutableSetOf<String>()
        val address = Address("AA", "BB", "CC", "DD", "EE", "FF", "HH")
        coEvery { dataStoreManager.getStopAddress(any()) } returns Utils.toJsonString(
            address,
            Gson()
        )!!
        every { application.getString(any()) } returns "Not Available"
        val collectJob = launch(UnconfinedTestDispatcher()) {
            dispatchViewModel.obtainFormattedAddress(
                1,
                isShortFormNAStr = false,
                isLocaleAvailable = false
            ).collect {
                results.add(it)
            }
        }
        assertTrue(results.elementAt(0) == "Not Available")
        collectJob.cancel()
    }

    @Test
    fun `verify setStopsEligibilityForFirstTime - Stops not sequenced`() = runTest {

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
            )
        } returns false
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.NAVIGATION_ELIGIBLE_STOP_LIST_KEY, emptySet()
            )
        } returns setOf()
        coEvery {
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                any(), any()
            )
        } returns setOf()

        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, dispid = defaultDispatchId))
        dispatchViewModel.setStopsEligibilityForFirstTime(stopList, dispatchStopsUseCase)

        coVerify(exactly = 1) {
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                any(), any()
            )
        }
    }

    @Test
    fun `verify setStopsEligibilityForFirstTime - Stops are sequenced`() = runTest {

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
            )
        } returns true
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.NAVIGATION_ELIGIBLE_STOP_LIST_KEY, emptySet()
            )
        } returns setOf()
        coEvery {
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                any(), any()
            )
        } returns setOf()

        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, dispid = defaultDispatchId))
        dispatchViewModel.setStopsEligibilityForFirstTime(stopList, dispatchStopsUseCase)

        coVerify(exactly = 0) {
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                any(), any()
            )
        }
    }

    @Test
    fun `verify setStopsEligibilityForFirstTime - One uncompleted stop`() = runTest {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
            )
        } returns false
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.NAVIGATION_ELIGIBLE_STOP_LIST_KEY, emptySet()
            )
        } returns setOf()
        coEvery {
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                any(), any()
            )
        } returns setOf()

        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, dispid = defaultDispatchId))
        dispatchViewModel.setStopsEligibilityForFirstTime(stopList, dispatchStopsUseCase)

        coVerify(exactly = 1) {
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                any(), any()
            )
        }
    }

    @Test
    fun `verify setStopsEligibilityForFirstTime - all stops completed`() = runTest {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
            )
        } returns false
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.NAVIGATION_ELIGIBLE_STOP_LIST_KEY, emptySet()
            )
        } returns setOf()
        coEvery {
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                any(), any()
            )
        } returns setOf()

        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, completedTime = "asfd", dispid = defaultDispatchId))
        stopList.add(StopDetail(stopid = 2, completedTime = "asfd", dispid = defaultDispatchId))
        dispatchViewModel.setStopsEligibilityForFirstTime(stopList, dispatchStopsUseCase)

        coVerify(exactly = 0) {
            dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                any(), any()
            )
        }
    }

    @Test
    fun `verify setStopsEligibilityForFirstTime - eligible sequential Stops NOT empty`() =
        runTest {
            val stopSet = setOf("gsdgdf", "fafsdf")
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
                )
            } returns false
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.NAVIGATION_ELIGIBLE_STOP_LIST_KEY, emptySet()
                )
            } returns stopSet
            coEvery {
                dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                    any(), any()
                )
            } returns setOf()

            val stopList = CopyOnWriteArrayList<StopDetail>()
            stopList.add(StopDetail(stopid = 1, dispid = defaultDispatchId))
            dispatchViewModel.setStopsEligibilityForFirstTime(stopList, dispatchStopsUseCase)

            coVerify(exactly = 0) {
                dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                    any(), any()
                )
            }
        }

    @Test
    fun `verify getCompletedTime`() {
        assertTrue(dispatchViewModel.getCompletedTime("") == "")
    }

    @Test
    fun `verify updateTripPanelWithMilesAwayMessage`() = runTest {

        coEvery {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(
                any()
            )
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
        } just runs
        coEvery {
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        } returns true

        dispatchViewModel.updateTripPanelWithMilesAwayMessage(tripPanelUseCase)

        coVerify(exactly = 1) {
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(
                any()
            )
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any())
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        }
    }

    @Test
    fun `verify trip data not sent to maps if sent already`(){

        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, completedTime = "asfd"))
        stopList.add(StopDetail(stopid = 2, completedTime = "asfd"))

        dispatchViewModel.stopDetailList.clear()
        dispatchViewModel.stopDetailList.addAll(stopList)
        dispatchViewModel.setActiveDispatchFlowValue(DispatchActiveState.ACTIVE)
        val stopsToActionsReadMap = HashMap<Int,Boolean>()
        stopsToActionsReadMap[1] = true
        stopsToActionsReadMap[2] = true
        coEvery { dispatchViewModel.startRouteCalculation("sendStopsDataToMaps method") } just runs
        runTest {
            dispatchViewModel.isStopsGeofenceDataSentToMaps = true
            dispatchViewModel.sendStopsDataToMapsUponReadingActions(true, stopsToActionsReadMap)
            coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) { dispatchViewModel.startRouteCalculation("sendStopsDataToMaps method") }
        }
    }

    @Test
    fun `verify trip data sent to maps`(){

        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, completedTime = "asfd"))
        stopList.add(StopDetail(stopid = 2, completedTime = "asfd"))

        dispatchViewModel.stopDetailList.clear()
        dispatchViewModel.stopDetailList.addAll(stopList)

        val stopsToActionsReadMap = HashMap<Int,Boolean>()
        stopsToActionsReadMap[1] = true
        stopsToActionsReadMap[2] = true

        coEvery { sendDispatchDataUseCase.sendDispatchCompleteEvent()  } just runs

        coEvery { dispatchViewModel.startRouteCalculation("sendStopsDataToMaps method") } just runs

        runTest {
            safeLaunch {
                dispatchViewModel.isStopsGeofenceDataSentToMaps = false
                dispatchViewModel.sendStopsDataToMapsUponReadingActions(true, stopsToActionsReadMap)
                coVerify(exactly = 1) { dispatchViewModel.startRouteCalculation("sendStopsDataToMaps method") }
            }
        }
    }

    @Test
    fun `verify trip data not sent to maps if stops actions not read for all stops`(){

        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, completedTime = "asfd"))
        stopList.add(StopDetail(stopid = 2, completedTime = "asfd"))

        dispatchViewModel.stopDetailList.clear()
        dispatchViewModel.stopDetailList.addAll(stopList)

        val stopsToActionsReadMap = HashMap<Int,Boolean>()
        stopsToActionsReadMap[2] = true

        coEvery { dispatchViewModel.startRouteCalculation("sendStopsDataToMaps method") } just runs

        dispatchViewModel.sendStopsDataToMapsUponReadingActions(true, stopsToActionsReadMap)

        coVerify(exactly = 0) { dispatchViewModel.startRouteCalculation("sendStopsDataToMaps method") }

    }

    @Test
    fun `check logNewEventWithDefaultParameters gets called when event name is not empty`(){
        every { dispatchBaseUseCase.logNewEventWithDefaultParameters(any()) } just runs
        dispatchViewModel.logNewEventWithDefaultParameters(TIMELINE_VIEW_COUNT)
        verify(exactly = 1) {
            dispatchBaseUseCase.logNewEventWithDefaultParameters(any())
        }
    }

    @Test
    fun `check logNewEventWithDefaultParameters gets called when event name is empty`(){
        every { dispatchBaseUseCase.logNewEventWithDefaultParameters(any()) } just runs
        dispatchViewModel.logNewEventWithDefaultParameters(EMPTY_STRING)
        verify(exactly = 0) {
            dispatchBaseUseCase.logNewEventWithDefaultParameters(any())
        }
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is not empty`(){
        every { dispatchBaseUseCase.logScreenViewEvent(any()) } just runs
        dispatchViewModel.logScreenViewEvent(STOP_DETAIL_SCREEN_TIME)
        verify(exactly = 1) {
            dispatchBaseUseCase.logScreenViewEvent(any())
        }
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is empty`(){
        every { dispatchBaseUseCase.logScreenViewEvent(any()) } just runs
        dispatchViewModel.logScreenViewEvent(EMPTY_STRING)
        verify(exactly = 0) {
            dispatchBaseUseCase.logScreenViewEvent(any())
        }
    }

    @Test
    fun `verify if isTripCompleted emits false for active running trip`() = runTest {
        coEvery { tripCompletionUseCase.isTripComplete(any(), any()) } returns Pair(EMPTY_STRING,false)

        val flow = dispatchViewModel.isTripCompleted()

        assertEquals(false, flow.first().second)
    }

    @Test
    fun `verify runOnTripEnd execution`() = runTest {
        val dispId = "2323232"
        val tripEvents = PFMEventsInfo.TripEvents(reasonType = "test", negativeGuf = false)
        coEvery { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any()) } just runs
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns this

        dispatchViewModel.runOnTripEnd(dispatchId = dispId, caller = "test", pfmEventsInfo = tripEvents, workManager = workManager)

        dispatchViewModel.onTripEnd.value?.let {
            assertTrue(it)
        }
    }

    @Test
    fun `verify sendRemoveGeofenceEvent execution, which removes geofence from AL map instance`() {
        val action = Action(actionid = 1)
        coEvery { dispatchStopsUseCase.getActionDataFromStop(any(), any(), any()) } returns action

        dispatchViewModel.sendRemoveGeofenceEvent("23232", 0)

        verify { sendDispatchDataUseCase.sendRemoveGeoFenceEvent(action) }
    }

    @Test
    fun `verify listenToCopilotEvents code execution`() {
        every { AppLauncherCommunicator.sendMessage(any(), any(), any()) } just runs
        dispatchViewModel.listenToCopilotEvents()
        verify { AppLauncherCommunicator.sendMessage(101, any(), null) }
    }

    @Test
    fun `verify updateTripPanelMessagePriorityIfThereIsNoMoreArrivalTrigger for trip panel message priority update`() {
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { dataStoreManager.containsKey(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns true
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { tripPanelUseCase.putArrivedMessagesIntoPriorityQueue() } just runs
        coEvery { tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop() } returns true
        coEvery { tripPanelUseCase.checkForCompleteFormMessages() } just runs

        dispatchViewModel.updateTripPanelMessagePriorityIfThereIsNoMoreArrivalTrigger()

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            tripPanelUseCase.checkForCompleteFormMessages()
        }
    }

    @Test
    fun `verify doOnDidYouArrivePositiveButtonPress for proper code execution`() {
        var isDoOnArrivalCalled = false
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs

        dispatchViewModel.doOnDidYouArrivePositiveButtonPress {
            isDoOnArrivalCalled = true
        }

        verify(timeout = TEST_DELAY_OR_TIMEOUT) { tripPanelUseCase.cancelNegativeGufTimer(any()) }
        assertTrue { isDoOnArrivalCalled }
    }

    @Test
    fun `verify doOnDidYouArriveNegativeButtonPress for single geofence trigger`() {
        var isShowDYACalled = false
        var isDYADismissedCalled = false
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any()) } just runs
        coEvery { tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any()) } just runs
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()
        coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs
        coEvery { sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(any(), any(), any(), any()) } just runs

        dispatchViewModel.doOnDidYouArriveNegativeButtonPress (
            arriveTriggerData = LauncherMessageWithPriority(),
            dismissDidYouArriveAlert = { isDYADismissedCalled = true },
            showDidYouArrive = { isShowDYACalled = true }
        )

        coVerifyOrder {
            tripPanelUseCase.cancelNegativeGufTimer(any())
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any())
            tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any())
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
            sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay()
            sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(any(), any(), any(), any())
        }
        assertFalse { isShowDYACalled }
        assertTrue { isDYADismissedCalled }
    }

    @Test
    fun `verify doOnDidYouArriveNegativeButtonPress for multiple geofence trigger`() {
        var isShowDYACalled = false
        var isDYADismissedCalled = false
        every { tripPanelUseCase.cancelNegativeGufTimer(any()) } just runs
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any()) } just runs
        coEvery { tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any()) } just runs
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf(
            ArrivedGeoFenceTriggerData(messageId = 0), ArrivedGeoFenceTriggerData(messageId = 1)
        )
        coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs
        coEvery { sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(any(), any(), any(), any()) } just runs

        dispatchViewModel.doOnDidYouArriveNegativeButtonPress (
            arriveTriggerData = LauncherMessageWithPriority(),
            dismissDidYouArriveAlert = { isDYADismissedCalled = true },
            showDidYouArrive = { isShowDYACalled = true }
        )

        coVerifyOrder {
            tripPanelUseCase.cancelNegativeGufTimer(any())
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any())
            tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any())
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
            sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay()
            sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(any(), any(), any(), any())
        }
        assertTrue { isShowDYACalled }
        assertTrue { isDYADismissedCalled }
    }

    @Test
    fun `verify checkAndDisplayDidYouArriveIfTriggerEventAvailable to not to show Did You Arrive dialog again when there is a did you arrive dialog already`() {
        var shouldShowDidYouArrive = false
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf(
            ArrivedGeoFenceTriggerData(messageId = 0)
        )

        dispatchViewModel.checkAndDisplayDidYouArriveIfTriggerEventAvailable(
            isDidYouArriveDialogNotNull = true,
            showDidYouArriveDialog = { _, _, _ ->
                shouldShowDidYouArrive = true
            }
        )

        assertFalse { shouldShowDidYouArrive }
    }

    @Test
    fun `verify checkAndDisplayDidYouArriveIfTriggerEventAvailable to not to show Did You Arrive dialog when there is no geofence trigger`() {
        var shouldShowDidYouArrive = false
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()

        dispatchViewModel.checkAndDisplayDidYouArriveIfTriggerEventAvailable(
            isDidYouArriveDialogNotNull = false,
            showDidYouArriveDialog = { _, _, _ ->
                shouldShowDidYouArrive = true
            }
        )

        assertFalse { shouldShowDidYouArrive }
    }

    @Test
    fun `verify checkAndDisplayDidYouArriveIfTriggerEventAvailable to not to show Did You Arrive dialog when there is a different trip panel message than Did You Arrive`() {
        var shouldShowDidYouArrive = false
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf(
            ArrivedGeoFenceTriggerData(messageId = 0)
        )
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { tripPanelUseCase.putArrivedMessagesIntoPriorityQueue() } just runs
        every { tripPanelUseCase.listLauncherMessageWithPriority } returns PriorityBlockingQueue(1, LauncherMessagePriorityComparator).also {
            it.add(LauncherMessageWithPriority(message = "Complete form for stop1", messagePriority = TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY, stopId = intArrayOf(0)))
        }

        dispatchViewModel.checkAndDisplayDidYouArriveIfTriggerEventAvailable(
            isDidYouArriveDialogNotNull = false,
            showDidYouArriveDialog = { _, _, _ ->
                shouldShowDidYouArrive = true
            }
        )

        coVerifyOrder {
            dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString()
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(true)
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false)
        }
        assertFalse { shouldShowDidYouArrive }
    }

    @Test
    fun `verify checkAndDisplayDidYouArriveIfTriggerEventAvailable to show Did You Arrive dialog when there is a Did you arrive message for TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP priority`() {
        var shouldShowDidYouArrive = false
        val stopId = 0
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf(
            ArrivedGeoFenceTriggerData(messageId = stopId)
        )
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { tripPanelUseCase.putArrivedMessagesIntoPriorityQueue() } just runs
        every { tripPanelUseCase.listLauncherMessageWithPriority } returns PriorityBlockingQueue(1, LauncherMessagePriorityComparator).also {
            it.add(LauncherMessageWithPriority(message = "Did you arrive at stop1", messagePriority = TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP, stopId = intArrayOf(stopId)))
        }
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "43434"
        coEvery { appModuleCommunicator.getSelectedDispatchId(any()) } returns "43434"
        coEvery { dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(any(), any()) } returns StopDetail(stopid = stopId)

        dispatchViewModel.checkAndDisplayDidYouArriveIfTriggerEventAvailable(
            isDidYouArriveDialogNotNull = false,
            showDidYouArriveDialog = { _, _, _ ->
                shouldShowDidYouArrive = true
            }
        )

        coVerifyOrder {
            dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString()
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(true)
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any())
            dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(any(), any())
        }
        assertTrue { shouldShowDidYouArrive }
    }

    @Test
    fun `verify checkAndDisplayDidYouArriveIfTriggerEventAvailable to show Did You Arrive dialog when there is a Did you arrive message for TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY priority`() {
        var shouldShowDidYouArrive = false
        val stopId = 0
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf(
            ArrivedGeoFenceTriggerData(messageId = stopId)
        )
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(any()) } just runs
        coEvery { tripPanelUseCase.putArrivedMessagesIntoPriorityQueue() } just runs
        every { tripPanelUseCase.listLauncherMessageWithPriority } returns PriorityBlockingQueue(1, LauncherMessagePriorityComparator).also {
            it.add(LauncherMessageWithPriority(message = "Did you arrive at stop1", messagePriority = TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY, stopId = intArrayOf(stopId)))
        }
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "43434"
        coEvery { appModuleCommunicator.getSelectedDispatchId(any()) } returns "43434"
        coEvery { dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(any(), any()) } returns StopDetail(stopid = stopId)

        dispatchViewModel.checkAndDisplayDidYouArriveIfTriggerEventAvailable(
            isDidYouArriveDialogNotNull = false,
            showDidYouArriveDialog = { _, _, _ ->
                shouldShowDidYouArrive = true
            }
        )

        coVerifyOrder {
            dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString()
            tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(true)
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any())
            dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(any(), any())
        }
        assertTrue { shouldShowDidYouArrive }
    }

    @Test
    fun `verify restoreFormsWhenDraftFunctionalityIsTurnedOff to not show form when the feature flag is on`() = runTest {
        var shouldStartFormActivity = false
        coEvery { dataStoreManager.hasActiveDispatch(any(), any()) } returns true
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()
        coEvery { dispatchStopsUseCase.getDriverFormsToFill(dataStoreManager) } returns arrayListOf()
        every { appModuleCommunicator.getFeatureFlags() } returns EnumMap(FeatureGatekeeper.KnownFeatureFlags::class.java)
        coEvery { appModuleCommunicator.doGetCid() } returns "34343"
        every { dispatchStopsUseCase.getFeatureFlagGateKeeper() } returns featureGatekeeper
        coEvery { featureGatekeeper.isFeatureTurnedOn(
            FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG,
            any(),
            any()
        ) } returns true

        dispatchViewModel.restoreFormsWhenDraftFunctionalityIsTurnedOff(
            isDidYouArriveDialogNull = true,
            startDispatchFormActivity = { _, _, _, _, _ ->
                shouldStartFormActivity = true
            }
        )

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.getDriverFormsToFill(
                dataStoreManager
            )
        }
        assertFalse { shouldStartFormActivity }
    }

    @Test
    fun `verify restoreFormsWhenDraftFunctionalityIsTurnedOff to not show form when there is no active dispatch`() = runTest {
        var shouldStartFormActivity = false
        coEvery { dataStoreManager.hasActiveDispatch(any(), any()) } returns false
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()
        coEvery { dispatchStopsUseCase.getDriverFormsToFill(dataStoreManager) } returns arrayListOf()
        every { appModuleCommunicator.getFeatureFlags() } returns EnumMap(FeatureGatekeeper.KnownFeatureFlags::class.java)
        coEvery { appModuleCommunicator.doGetCid() } returns "34343"
        every { dispatchStopsUseCase.getFeatureFlagGateKeeper() } returns featureGatekeeper
        coEvery { featureGatekeeper.isFeatureTurnedOn(
            FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG,
            any(),
            any()
        ) } returns false

        dispatchViewModel.restoreFormsWhenDraftFunctionalityIsTurnedOff(
            isDidYouArriveDialogNull = true,
            startDispatchFormActivity = { _, _, _, _, _ ->
                shouldStartFormActivity = true
            }
        )

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.getDriverFormsToFill(
                dataStoreManager
            )
        }
        assertFalse { shouldStartFormActivity }
    }

    @Test
    fun `verify restoreFormsWhenDraftFunctionalityIsTurnedOff to not show form when there is a pending did you arrive message`() = runTest {
        var shouldStartFormActivity = false
        coEvery { dataStoreManager.hasActiveDispatch(any(), any()) } returns true
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf(
            ArrivedGeoFenceTriggerData(messageId = 0)
        )
        coEvery { dispatchStopsUseCase.getDriverFormsToFill(dataStoreManager) } returns arrayListOf()
        every { appModuleCommunicator.getFeatureFlags() } returns EnumMap(FeatureGatekeeper.KnownFeatureFlags::class.java)
        coEvery { appModuleCommunicator.doGetCid() } returns "34343"
        every { dispatchStopsUseCase.getFeatureFlagGateKeeper() } returns featureGatekeeper
        coEvery { featureGatekeeper.isFeatureTurnedOn(
            FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG,
            any(),
            any()
        ) } returns false

        dispatchViewModel.restoreFormsWhenDraftFunctionalityIsTurnedOff(
            isDidYouArriveDialogNull = true,
            startDispatchFormActivity = { _, _, _, _, _ ->
                shouldStartFormActivity = true
            }
        )

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.getDriverFormsToFill(
                dataStoreManager
            )
        }
        assertFalse { shouldStartFormActivity }
    }

    @Test
    fun `verify restoreFormsWhenDraftFunctionalityIsTurnedOff to not show form when there is a did you arrive dialog`() = runTest {
        var shouldStartFormActivity = false
        coEvery { dataStoreManager.hasActiveDispatch(any(), any()) } returns true
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()
        coEvery { dispatchStopsUseCase.getDriverFormsToFill(dataStoreManager) } returns arrayListOf()
        every { appModuleCommunicator.getFeatureFlags() } returns EnumMap(FeatureGatekeeper.KnownFeatureFlags::class.java)
        coEvery { appModuleCommunicator.doGetCid() } returns "34343"
        every { dispatchStopsUseCase.getFeatureFlagGateKeeper() } returns featureGatekeeper
        coEvery { featureGatekeeper.isFeatureTurnedOn(
            FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG,
            any(),
            any()
        ) } returns false

        dispatchViewModel.restoreFormsWhenDraftFunctionalityIsTurnedOff(
            isDidYouArriveDialogNull = false,
            startDispatchFormActivity = { _, _, _, _, _ ->
                shouldStartFormActivity = true
            }
        )

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.getDriverFormsToFill(
                dataStoreManager
            )
        }
        assertFalse { shouldStartFormActivity }
    }

    @Test
    fun `verify restoreFormsWhenDraftFunctionalityIsTurnedOff that it shows form when the save to draft feature flag is off`() = runTest {
        var shouldStartFormActivity = false
        coEvery { dataStoreManager.hasActiveDispatch(any(), any()) } returns true
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()
        coEvery { dispatchStopsUseCase.getDriverFormsToFill(dataStoreManager) } returns arrayListOf(
            DispatchFormPath(stopId = 0)
        )
        every { appModuleCommunicator.getFeatureFlags() } returns EnumMap(FeatureGatekeeper.KnownFeatureFlags::class.java)
        coEvery { appModuleCommunicator.doGetCid() } returns "34343"
        every { dispatchStopsUseCase.getFeatureFlagGateKeeper() } returns featureGatekeeper
        coEvery { featureGatekeeper.isFeatureTurnedOn(any(), any(), any()) } returns false
        coEvery { dispatchStopsUseCase.isComposeFormFeatureFlagEnabled(any()) } returns false
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "we34"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "3434"

        dispatchViewModel.restoreFormsWhenDraftFunctionalityIsTurnedOff(
            isDidYouArriveDialogNull = true,
            startDispatchFormActivity = { _, _, _, _, _ ->
                shouldStartFormActivity = true
            }
        )

        assertTrue { shouldStartFormActivity }
    }

    @Test
    fun `verify checkForTripCompletion for completed trip`() {
        var shouldShowTripCompleteToast = false
        coEvery { tripCompletionUseCase.isTripComplete(any(), any()) } returns Pair(EMPTY_STRING, true)
        every { dispatchViewModel.getWorkManagerInstance() } returns workManager
        every { dispatchViewModel.runOnTripEnd(any(), any(), any(), workManager = workManager) } just runs

        dispatchViewModel.checkForTripCompletion { shouldShowTripCompleteToast = true }

        assertTrue { shouldShowTripCompleteToast }
    }

    @Test
    fun `verify checkForTripCompletion for incomplete trip`() {
        var shouldShowTripCompleteToast = false
        coEvery { tripCompletionUseCase.isTripComplete(any(), any()) } returns Pair(EMPTY_STRING, false)
        every { dispatchViewModel.getWorkManagerInstance() } returns workManager
        every { dispatchViewModel.runOnTripEnd(any(), any(), any(), workManager = workManager) } just runs

        dispatchViewModel.checkForTripCompletion { shouldShowTripCompleteToast = true }

        assertFalse { shouldShowTripCompleteToast }
    }

    @Test
    fun `verify doOnArrival for single geofence`() {
        var shouldCheckForMoreGeofenceTriggers = false
        var shouldDismissDYAAlert = false
        var shouldCheckForTripCompletion = false
        every { stopDetentionWarningUseCase.canDisplayDetentionWarning(any()) } returns true
        every { stopDetentionWarningUseCase.startDetentionWarningTimer(any(), any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "3434"
        coEvery { dispatchStopsUseCase.getActionDataFromStop(any(), any(), any()) } returns Action(actionid = 1, driverFormid = 3434)
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any()) } just runs
        coEvery { tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any()) } just runs
        every { dispatchViewModel.sendRemoveGeofenceEvent(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.sendStopActionEvent(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false) } just runs
        coEvery { tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop() } returns true
        coEvery { routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps() } returns listOf()

        dispatchViewModel.doOnArrival(
            arriveTriggerData = LauncherMessageWithPriority(stopId = intArrayOf(0)),
            pfmEventsInfo = PFMEventsInfo.StopActionEvents(reasonType = "test", negativeGuf = false),
            checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch = {
                shouldCheckForMoreGeofenceTriggers = true
            },
            dismissDidYouArriveAlert = {
                shouldDismissDYAAlert = true
            },
            checkForTripCompletion = {
                shouldCheckForTripCompletion = true
            }
        )

        assertFalse { shouldCheckForMoreGeofenceTriggers }
        assertTrue { shouldDismissDYAAlert }
        assertTrue { shouldCheckForTripCompletion }
    }

    @Test
    fun `verify doOnArrival for multiple geofence`() {
        var shouldCheckForMoreGeofenceTriggers = false
        var shouldDismissDYAAlert = false
        var shouldCheckForTripCompletion = false
        every { stopDetentionWarningUseCase.canDisplayDetentionWarning(any()) } returns true
        every { stopDetentionWarningUseCase.startDetentionWarningTimer(any(), any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "3434"
        coEvery { dispatchStopsUseCase.getActionDataFromStop(any(), any(), any()) } returns Action(actionid = 1, driverFormid = 3434)
        every { tripPanelUseCase.removeMessageFromPriorityQueueIfAvailable(any()) } just runs
        coEvery { tripPanelUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(any()) } just runs
        every { dispatchViewModel.sendRemoveGeofenceEvent(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.sendStopActionEvent(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf(
            ArrivedGeoFenceTriggerData(messageId = 0), ArrivedGeoFenceTriggerData(messageId = 1)
        )
        every { tripPanelUseCase.updatePriorityOfTripPanelWhichIsCurrentlyDisplayed(false) } just runs
        coEvery { tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop() } returns true
        coEvery { routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps() } returns listOf()

        dispatchViewModel.doOnArrival(
            arriveTriggerData = LauncherMessageWithPriority(stopId = intArrayOf(0)),
            pfmEventsInfo = PFMEventsInfo.StopActionEvents(reasonType = "test", negativeGuf = false),
            checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch = {
                shouldCheckForMoreGeofenceTriggers = true
            },
            dismissDidYouArriveAlert = {
                shouldDismissDYAAlert = true
            },
            checkForTripCompletion = {
                shouldCheckForTripCompletion = true
            }
        )

        assertTrue { shouldCheckForMoreGeofenceTriggers }
        assertTrue { shouldDismissDYAAlert }
        assertTrue { shouldCheckForTripCompletion }
    }

    @Test
    fun `verify setDidYouArriveDialogListener for approach action`() {
        var shouldUpdateDYAPositiveButtonText = false
        var shouldDismissDYAAlert = false
        var shouldDoOnArrival = false
        val stop = StopDetail(stopid = 0).also { stop ->
            stop.Actions.add(Action(actionid = 1, driverFormid = 3434, actionType = ActionTypes.APPROACHING.ordinal))
        }
        every { tripPanelUseCase.lastSentTripPanelMessage } returns LastSentTripPanelMessage()
        coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs
        every { Log.w(any(), any(), any(), any(), any(), any()) } returns Unit

        dispatchViewModel.setDidYouArriveDialogListener(
            arriveTriggerData = LauncherMessageWithPriority(stopId = intArrayOf(0)),
            stopData = stop,
            activeDispatchId = "3434",
            updateDialogPositiveButtonText = {
                shouldUpdateDYAPositiveButtonText = true
            },
            dismissDialog = {
                shouldDismissDYAAlert = true
            },
            doOnArrival = { _, _, _ ->
                shouldDoOnArrival = true
            }
        )

        assertFalse { shouldUpdateDYAPositiveButtonText }
        assertFalse { shouldDismissDYAAlert }
        assertFalse { shouldDoOnArrival }
        verify(timeout = TEST_DELAY_OR_TIMEOUT) {
            Log.w(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify setDidYouArriveDialogListener for stop without negative guf`() {
        var shouldUpdateDYAPositiveButtonText = false
        var shouldDismissDYAAlert = false
        var shouldDoOnArrival = false
        val stop = StopDetail(stopid = 0).also { stop ->
            stop.Actions.add(Action(actionid = 1, driverFormid = 3434, actionType = ActionTypes.ARRIVED.ordinal))
        }
        every { tripPanelUseCase.lastSentTripPanelMessage } returns LastSentTripPanelMessage()
        coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs
        every {
            Log.logUiInteractionInInfoLevel(any(), any(), any(), any(), any())
        } returns Unit
        dispatchViewModel.setDidYouArriveDialogListener(
            arriveTriggerData = LauncherMessageWithPriority(stopId = intArrayOf(0)),
            stopData = stop,
            activeDispatchId = "3434",
            updateDialogPositiveButtonText = {
                shouldUpdateDYAPositiveButtonText = true
            },
            dismissDialog = {
                shouldDismissDYAAlert = true
            },
            doOnArrival = { _, _, _ ->
                shouldDoOnArrival = true
            }
        )

        assertFalse { shouldUpdateDYAPositiveButtonText }
        assertFalse { shouldDismissDYAAlert }
        assertFalse { shouldDoOnArrival }
        verify(timeout = TEST_DELAY_OR_TIMEOUT) {
            Log.d(any(), any(), any(), any(), any(), any())
        }
    }

//    Uncomment this test after replacing fixedRateTimer with a recurring timer which runs on dispatcher other than main
//    @Test
//    fun `verify setDidYouArriveDialogListener for stop with negative guf`() = runTest {
//        var shouldUpdateDYAPositiveButtonText = false
//        var shouldDismissDYAAlert = false
//        var shouldDoOnArrival = false
//        val didYouArriveTimerValue = 1 // 15 secs is not given here for testing purpose
//        val stop = StopDetail(stopid = 0).also { stop ->
//            stop.Actions.add(
//                Action(
//                    actionid = 1,
//                    driverFormid = 3434,
//                    actionType = ActionTypes.ARRIVED.ordinal,
//                    gufType = DRIVER_NEGATIVE_GUF
//                )
//            )
//        }
//        every { tripPanelUseCase.lastSentTripPanelMessage } returns LastSentTripPanelMessage()
//        coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs
//        every {
//            Log.logUiInteractionInInfoLevel(any(), any(), any(), any(), any())
//        } returns Unit
//        coEvery { tripPanelUseCase.scheduleBackgroundTimer(any(), any()) } just runs
//        coEvery { WorkflowEventBus.negativeGufTimerEvents } returns MutableSharedFlow<Int>(replay = 1).also {
//            it.emit(
//                didYouArriveTimerValue
//            )
//        }
//
//        dispatchViewModel.setDidYouArriveDialogListener(
//            arriveTriggerData = LauncherMessageWithPriority(stopId = intArrayOf(0)),
//            stopData = stop,
//            activeDispatchId = "3434",
//            updateDialogPositiveButtonText = {
//                shouldUpdateDYAPositiveButtonText = true
//            },
//            dismissDialog = {
//                shouldDismissDYAAlert = true
//            },
//            doOnArrival = { _, _, _ ->
//                shouldDoOnArrival = true
//            }
//        )
//
//        // To wait for lambda functions execution from real code. The advanceTimeBy or advanceUntilIdle is not working here
//        Thread.sleep(2000)
//
//        assertTrue { shouldUpdateDYAPositiveButtonText }
//        assertTrue { shouldDismissDYAAlert }
//        assertTrue { shouldDoOnArrival }
//    }

    @Test
    fun `verify listenToActionsOfNewlyAddedStop code flow execution`() = runTest {
        val stopId = 0
        dispatchViewModel.stopDetailList.addAll(
            listOf(
                StopDetail(stopid = 0)
            )
        )
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple(
            "3434",
            "we34",
            true
        )
        coEvery { dispatchViewModel.getSelectedDispatchId(any()) } returns "34343434"
        every { tripCompletionUseCase.listenToStopActions(any(), any(), any(), any()) } returns flow {
            emit(setOf(Action(actionid = 1, driverFormid = 3434, actionType = ActionTypes.ARRIVED.ordinal, gufType = DRIVER_NEGATIVE_GUF)))
        }
        every { dispatchBaseUseCase.handleActionAddAndUpdate(any(), any(), any()) } returns StopDetail()
        every { dispatchBaseUseCase.fetchAndAddFormsOfActionForFormSync(any(), any()) } returns mutableSetOf()
        every { dispatchViewModel.updateStopDetailList(any(), any()) } just runs
        coEvery { dispatchViewModel.setStopsEligibilityForFirstTime(any(), any()) } just runs
        coEvery { dispatchViewModel.putStopsIntoPreferenceForServiceReference(any()) } just runs
        coEvery { dispatchBaseUseCase.getDeepCopyOfStopDetailList(any()) } returns null
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns false
        every { dispatchViewModel.sendStopsDataToMapsUponReadingActions(any(), any()) } just runs

        dispatchViewModel.listenToActionsOfNewlyAddedStop(stopId)

        advanceTimeBy(2000)

        coVerifyOrder {
            dispatchBaseUseCase.getCidAndTruckNumber(any())
            dispatchViewModel.getSelectedDispatchId(any())
            dispatchBaseUseCase.handleActionAddAndUpdate(any(), any(), any())
            dispatchBaseUseCase.fetchAndAddFormsOfActionForFormSync(any(), any())
        }

    }

    @Test
    fun `verify checkForTripCompletion calls showTripCompleteToast and runOnTripEnd`() = runTest {
        // Mock the isTripCompleted function to return a flow that emits true
        coEvery { dispatchViewModel.isTripCompleted() } returns flow { emit(Pair("testDispatchId", true)) }

        // Mock the getSelectedDispatchId function
        coEvery { dispatchViewModel.getSelectedDispatchId(any()) } returns "testDispatchId"

        // Mock the runOnTripEnd function
        coEvery { dispatchViewModel.runOnTripEnd(any(), any(), any()) } returns Unit

        // Call the function
        dispatchViewModel.checkForTripCompletion(mockShowTripCompleteToast)

        // Verify that showTripCompleteToast is called
        coVerify { mockShowTripCompleteToast.invoke() }

        // Verify that runOnTripEnd is called with the correct parameters
        val pfmEventsInfoSlot = slot<PFMEventsInfo.TripEvents>()
        coVerify {
            dispatchViewModel.runOnTripEnd(
                dispatchId = "testDispatchId",
                caller = "checkForTripCompletion",
                pfmEventsInfo = capture(pfmEventsInfoSlot)
            )
        }
        assert(pfmEventsInfoSlot.captured.reasonType == StopActionReasonTypes.AUTO.name)
        assert(!pfmEventsInfoSlot.captured.negativeGuf)
    }

    @After
    fun after() {
        Dispatchers.resetMain()
        stopKoin()
        unmockkAll()
        unmockkStatic(Log::class)
    }


}