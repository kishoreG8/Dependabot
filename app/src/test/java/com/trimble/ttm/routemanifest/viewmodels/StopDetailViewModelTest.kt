package com.trimble.ttm.routemanifest.viewmodels

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.MutableLiveData
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.DEBOUNCE_INTERVAL
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.usecases.ArrivalReasonUsecase
import com.trimble.ttm.routemanifest.usecases.DispatchBaseUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.FetchDispatchStopsAndActionsUseCase
import com.trimble.ttm.routemanifest.usecases.RouteETACalculationUseCase
import com.trimble.ttm.routemanifest.usecases.SendDispatchDataUseCase
import com.trimble.ttm.routemanifest.usecases.StopDetentionWarningUseCase
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.usecases.UncompletedFormsUseCase
import com.trimble.ttm.routemanifest.utils.ADDED
import com.trimble.ttm.routemanifest.utils.CoroutineTestRule
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import com.trimble.ttm.routemanifest.utils.REMOVED
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.viewmodel.FormViewModel
import com.trimble.ttm.routemanifest.viewmodel.StopDetailViewModel
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.coVerifyOrder
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList


class StopDetailViewModelTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @RelaxedMockK
    private lateinit var application: Application

    private lateinit var stopDetailViewModel: StopDetailViewModel

    private lateinit var thisStopDetail: StopDetail

    @RelaxedMockK
    private lateinit var context: Context

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @MockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    private var dispatcherProvider: DispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var tripCompletionUseCase: TripCompletionUseCase

    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager

    @MockK
    private lateinit var dispatchBaseUseCase: DispatchBaseUseCase
    @MockK
    private lateinit var arrivalReasonUseCase: ArrivalReasonUsecase

    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase

    @MockK
    private lateinit var routeETACalculationUseCase: RouteETACalculationUseCase
    @MockK
    private lateinit var stopDetentionWarningUseCase: StopDetentionWarningUseCase
    @MockK
    private lateinit var sendDispatchDataUseCase: SendDispatchDataUseCase
    @MockK
    private lateinit var formViewModel: FormViewModel
    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase

    @MockK
    private lateinit var backboneUseCase : BackboneUseCase

    @MockK
    private lateinit var tripStartUseCase: TripStartUseCase



    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo


    private val arrive: String = "ARRIVE"
    private val arriveComplete = "ARRIVE-COMPLETE"
    private val depart = "DEPART"
    private val departComplete = "DEPART-COMPLETE"
    private val trailerDataFlow = MutableSharedFlow<List<String>>()
    private val shipmentDataFlow = MutableSharedFlow<List<String>>()
    private val testDispatcher = TestCoroutineScheduler()
    private val testScope = TestScope()
    private val modulesToInject = module {
        single { arrivalReasonUseCase }
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        arrivalReasonUseCase = mockk()
        every { localDataSourceRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        every { dispatchStopsUseCase.appModuleCommunicator } returns appModuleCommunicator
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.SHIPMENTS_IDS_KEY,
                EMPTY_STRING
            )
        } just runs
        coEvery { dataStoreManager.setValue(DataStoreManager.SHIPMENTS_IDS_KEY, any()) } just runs
        coEvery { dataStoreManager.setValue(DataStoreManager.TRAILER_IDS_KEY, any()) } just runs
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.SELECTED_DISPATCH_KEY,
                EMPTY_STRING
            )
        } returns "12345"
        startKoin {
            loadKoinModules(modulesToInject)
        }

        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "1234"
        every { application.getString(R.string.arrive_complete) } returns arriveComplete
        every { application.getString(R.string.arrive) } returns arrive
        every { application.getString(R.string.depart_complete) } returns departComplete
        every { application.getString(R.string.depart) } returns depart
        every { backboneUseCase.monitorTrailersData() } returns trailerDataFlow
        every { backboneUseCase.monitorShipmentsData() } returns shipmentDataFlow
        coEvery { dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any()) } returns EMPTY_STRING
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple("1", "2", true)
        coEvery { dispatchBaseUseCase.getDispatchActiveState(any(),any()) } returns DispatchActiveState.ACTIVE
        coEvery { tripCompletionUseCase.getStopsForDispatch(any(), any(), any()) } returns flow { }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf()
        coEvery { backboneUseCase.getCurrentUser() } returns EMPTY_STRING
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0,0.0)
        coEvery { dataStoreManager.fieldObserver(stringPreferencesKey(name = "activeDispatch")) } returns  flow { }
        coEvery { appModuleCommunicator.doGetCid() } returns "23223"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "344353"
        mockkObject(Log)
        mockkObject(UncompletedFormsUseCase)

        stopDetailViewModel = spyk(
            StopDetailViewModel(
                applicationInstance = application,
                routeETACalculationUseCase = routeETACalculationUseCase,
                tripCompletionUseCase = tripCompletionUseCase,
                dataStoreManager = dataStoreManager,
                formDataStoreManager = formDataStoreManager,
                dispatchBaseUseCase = dispatchBaseUseCase,
                dispatchStopsUseCase = dispatchStopsUseCase,
                sendDispatchDataUseCase = sendDispatchDataUseCase,
                tripPanelUseCase = tripPanelUseCase,
                dispatcherProvider = dispatcherProvider,
                stopDetentionWarningUseCase = stopDetentionWarningUseCase,
                dispatchValidationUseCase = mockk(),
                fetchDispatchStopsAndActionsUseCase = fetchDispatchStopsAndActionsUseCase,
                formViewModel = formViewModel,
                backboneUseCase = backboneUseCase,
                tripStartUseCase = tripStartUseCase
            )
            , recordPrivateCalls = true)
        coEvery { stopDetailViewModel.getActionsForStop(any()) } just runs
    }

    @Test
    fun `feature flag does not have the key when the cached map is empty`() {
        every { appModuleCommunicator.getFeatureFlags() } returns emptyMap()
        assertFalse(stopDetailViewModel.shouldDisplayPrePlannedArrival())
    }

    @Test
    fun `feature flag does not have the key but the cached map is not empty`() {
        val key1 = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        val value1 = FeatureFlagDocument(
            FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG.id,
            listOf(),
            true,
            true,
            ""
        )
        val key2 = FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_ADDRESS
        val value2 = FeatureFlagDocument(
            FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_ADDRESS.id,
            listOf(),
            true,
            true,
            ""
        )

        every { appModuleCommunicator.getFeatureFlags() } returns mapOf(
            key1 to value1,
            key2 to value2
        )
        assertFalse(stopDetailViewModel.shouldDisplayPrePlannedArrival())
    }

    @Test
    fun `feature flag does have the key but the pre planned arrival flag is turned OFF`() {
        val key1 = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        val value1 = FeatureFlagDocument(
            FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG.id,
            listOf(),
            true,
            true,
            ""
        )
        val key2 = FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_PREPLANNED_ARRIVAL
        val value2 = FeatureFlagDocument(
            FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_PREPLANNED_ARRIVAL.id,
            listOf(),
            true,
            false,
            ""
        )

        every { appModuleCommunicator.getFeatureFlags() } returns mapOf(
            key1 to value1,
            key2 to value2
        )
        assertFalse(stopDetailViewModel.shouldDisplayPrePlannedArrival())
    }

    @Test
    fun `feature flag does have the key and the pre planned arrival flag is turned ON`() {
        val key1 = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG
        val value1 = FeatureFlagDocument(
            FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG.id,
            listOf(),
            true,
            true,
            ""
        )
        val key2 = FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_PREPLANNED_ARRIVAL
        val value2 = FeatureFlagDocument(
            FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_PREPLANNED_ARRIVAL.id,
            listOf(),
            true,
            true,
            ""
        )

        every { appModuleCommunicator.getFeatureFlags() } returns mapOf(
            key1 to value1,
            key2 to value2
        )
        assertTrue(stopDetailViewModel.shouldDisplayPrePlannedArrival())
    }

    @Test
    fun `arrived is set to true only when exists`() {    //NOSONAR

        thisStopDetail =
            StopDetail(name = "Test")
        thisStopDetail.Actions.add(
            Action(
                actionType = 1
            )
        )
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.displayArrived.value)
            assertEquals(false, stopDetailViewModel.displayDeparted.value)
        }
    }

    @Test
    fun `departed action is set to true only when exists`() {    //NOSONAR

        thisStopDetail =
            StopDetail(name = "Test")
        thisStopDetail.Actions.add(
            Action(
                actionType = 2
            )
        )
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(false, stopDetailViewModel.displayArrived.value)
            assertEquals(true, stopDetailViewModel.displayDeparted.value)
        }
    }

    @Test
    fun `two actions can exist`() {    //NOSONAR

        thisStopDetail =
            StopDetail(name = "Test")
        thisStopDetail.Actions.add(
            Action(
                actionType = 2
            )
        )
        thisStopDetail.Actions.add(
            Action(
                actionType = 1
            )
        )
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.displayArrived.value)
            assertEquals(true, stopDetailViewModel.displayDeparted.value)
        }
    }

    @Test
    fun `no actions can exist`() {    //NOSONAR

        thisStopDetail =
            StopDetail(name = "Test")
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(false, stopDetailViewModel.displayArrived.value)
            assertEquals(false, stopDetailViewModel.displayDeparted.value)
        }
    }

    @Test
    fun `all actions can exist`() {    //NOSONAR

        thisStopDetail =
            StopDetail(name = "Test")
        thisStopDetail.Actions.add(
            Action(
                actionType = 0
            )
        )
        thisStopDetail.Actions.add(
            Action(
                actionType = 1
            )
        )
        thisStopDetail.Actions.add(
            Action(
                actionType = 2
            )
        )
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.displayArrived.value)
            assertEquals(true, stopDetailViewModel.displayDeparted.value)
        }
    }

    @Test
    fun `incorrect action type wont display any action`() {    //NOSONAR

        thisStopDetail =
            StopDetail(name = "Test")
        thisStopDetail.Actions.add(
            Action(
                actionType = -1
            )
        )
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(false, stopDetailViewModel.displayArrived.value)
            assertEquals(false, stopDetailViewModel.displayDeparted.value)
        }
    }

    @Test
    fun `previous stop not available when current stop index is 0`() {    //NOSONAR
        stopDetailViewModel.setStopsAvailableFlags(0, 2)

        assertEquals(false, stopDetailViewModel.previousStopAvailable.value)
    }

    @Test
    fun `next stop not available when current stop index equals total stop count`() {    //NOSONAR
        stopDetailViewModel.setStopsAvailableFlags(2, 2)

        assertEquals(true, stopDetailViewModel.previousStopAvailable.value)
        assertEquals(false, stopDetailViewModel.nextStopAvailable.value)
    }

    @Test
    fun `previous and next stops are not available when total stops count is 1`() {    //NOSONAR
        stopDetailViewModel.setStopsAvailableFlags(0, 1)

        assertEquals(false, stopDetailViewModel.previousStopAvailable.value)
        assertEquals(false, stopDetailViewModel.nextStopAvailable.value)
    }

    @Test
    fun `previous and next stops are available when total stops count is 3 and current stop index is 1`() {    //NOSONAR
        stopDetailViewModel.setStopsAvailableFlags(1, 3)

        assertEquals(true, stopDetailViewModel.previousStopAvailable.value)
        assertEquals(true, stopDetailViewModel.nextStopAvailable.value)
    }

    @Test
    fun `depart is readonly until the stop is arrived `() {    //NOSONAR
        thisStopDetail = StopDetail().apply {
            this.Actions.add(
                Action(
                    actionType = 1
                )
            )
        }
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(false, stopDetailViewModel.enableDepart.value)
        }
    }

    @Test
    fun `depart is enabled if the stop is arrived `() {    //NOSONAR
        thisStopDetail = StopDetail().apply {
            this.Actions.apply {
                add(
                    Action(
                        actionType = 1,
                        responseSent = true
                    )
                )
                add(
                    Action(
                        actionType = 2
                    )
                )
            }
        }
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.enableDepart.value)
        }
    }

    @Test
    fun `depart is enabled dynamically after the stop is arrived `() {    //NOSONAR
        thisStopDetail = StopDetail().apply {
            this.Actions.apply {
                add(
                    Action(
                        actionType = ActionTypes.ARRIVED.ordinal,
                        responseSent = false
                    )
                )
                add(
                    Action(
                        actionType = ActionTypes.DEPARTED.ordinal,
                        responseSent = false
                    )
                )
            }
        }
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            // Mock Arrived
            thisStopDetail.Actions[0].responseSent = true
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.enableDepart.value)
        }
    }

    @Test
    fun `depart is enable if only one action is exist and it is depart action`() {    //NOSONAR
        thisStopDetail = StopDetail().apply {
            this.Actions.apply {
                add(
                    Action(
                        actionType = 2
                    )
                )
            }
        }
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.enableDepart.value)
        }
    }

    @Test
    fun `arrived is set to true when stop has only arriving action`() {    //NOSONAR
        thisStopDetail =
            StopDetail(name = "Test")
        thisStopDetail.Actions.add(
            Action(
                actionType = ActionTypes.APPROACHING.ordinal
            )
        )
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.displayArrived.value)
            assertEquals(false, stopDetailViewModel.displayDeparted.value)
        }
    }

    @Test
    fun `depart display, enabled value is set to true when stop has only depart action`() {    //NOSONAR
        thisStopDetail =
            StopDetail(name = "Test")
        thisStopDetail.Actions.add(
            Action(
                actionType = ActionTypes.DEPARTED.ordinal
            )
        )
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.displayDeparted.value)
            assertEquals(true, stopDetailViewModel.enableDepart.value)
            assertEquals(false, stopDetailViewModel.displayArrived.value)
        }
    }

    @Test
    fun `arrive button text liveData value equals mutable value`() {
        thisStopDetail =
            StopDetail(name = "Test")
        stopDetailViewModel.setArriveButtonText("TestValue")
        testScope.launch {
            assertEquals("TestValue", stopDetailViewModel.arriveButtonText.value)
        }
    }

    @Test
    fun `depart display, enabled value is set to true when stop has no arrived action`() {    //NOSONAR
        thisStopDetail =
            StopDetail(name = "Test")
        thisStopDetail.Actions.add(
            Action(
                actionType = ActionTypes.APPROACHING.ordinal
            )
        )
        thisStopDetail.Actions.add(
            Action(
                actionType = ActionTypes.DEPARTED.ordinal
            )
        )
        testScope.launch {
            stopDetailViewModel.setActionButtons(thisStopDetail)
            assertEquals(true, stopDetailViewModel.displayDeparted.value)
            assertEquals(true, stopDetailViewModel.enableDepart.value)
            assertEquals(false, stopDetailViewModel.displayArrived.value)
        }
    }

    @Test
    fun `fetch arrived button text to display`() {    //NOSONAR
        every { application.getString(R.string.arrive_complete) } returns "ARRIVED-COMPLETE"
        every { application.getString(R.string.arrive) } returns "ARRIVED"
        thisStopDetail = StopDetail(stopid = 0, completedTime = "10:00:00")
        coEvery {
            dataStoreManager.getValue(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${thisStopDetail.stopid}"),
                ZERO
            )
        } returns ZERO

        testScope.launch {
            assertEquals(
                "ARRIVED-COMPLETE",
                stopDetailViewModel.getArriveButtonText(thisStopDetail)
            )
        }

        thisStopDetail = StopDetail(stopid = 1, completedTime = "")
        coEvery {
            dataStoreManager.getValue(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${thisStopDetail.stopid}"),
                ZERO
            )
        } returns 1

        testScope.launch {
            assertEquals("ARRIVED", stopDetailViewModel.getArriveButtonText(thisStopDetail))
        }
    }

    @Test
    fun `fetch departed button text to display`() {    //NOSONAR
        thisStopDetail = StopDetail()
        thisStopDetail.Actions.add(
            Action(
                actionType = ActionTypes.DEPARTED.ordinal,
                responseSent = true
            )
        )
        assertEquals(departComplete, stopDetailViewModel.getDepartButtonText(thisStopDetail))
    }

    @Test
    fun `fetch departed button text to display for uncompleted action`() {    //NOSONAR
        thisStopDetail = StopDetail()
        thisStopDetail.Actions.add(
            Action(
                actionType = ActionTypes.DEPARTED.ordinal,
                responseSent = false
            )
        )
        assertEquals(depart, stopDetailViewModel.getDepartButtonText(thisStopDetail))
    }

    @Test
    fun testSetStopsAvailableFlagsForNoStops() {
        stopDetailViewModel.setStopsAvailableFlags(0, 0)
        stopDetailViewModel.previousStopAvailable.value?.let { assertFalse(it) }
        stopDetailViewModel.nextStopAvailable.value?.let { assertFalse(it) }
    }

    @Test
    fun testSetStopsAvailableFlagsFor10StopsAtIndex0() {
        stopDetailViewModel.setStopsAvailableFlags(0, 10)
        stopDetailViewModel.previousStopAvailable.value?.let { assertFalse(it) }
        stopDetailViewModel.nextStopAvailable.value?.let { assertTrue(it) }
    }

    @Test
    fun testSetStopsAvailableFlagsFor10StopsAtIndex5() {
        stopDetailViewModel.setStopsAvailableFlags(5, 10)
        stopDetailViewModel.previousStopAvailable.value?.let { assertTrue(it) }
        stopDetailViewModel.nextStopAvailable.value?.let { assertTrue(it) }
    }

    @Test
    fun testSetStopsAvailableFlagsFor10StopsAtIndex10() {
        stopDetailViewModel.setStopsAvailableFlags(10, 10)
        stopDetailViewModel.previousStopAvailable.value?.let { assertTrue(it) }
        stopDetailViewModel.nextStopAvailable.value?.let { assertFalse(it) }
    }

    @Test
    fun testGetArrivedActionStatusForPendingApproachOnly(): Unit =
        StopDetail().apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = false))
                })
        }.run {
            assertEquals(
                arrive,
                stopDetailViewModel.getApproachActionStatus(this, Pair(false, false))
            )
        }

    @Test
    fun testGetArrivedActionStatusForApproachOnlyCompletion(): Unit =
        StopDetail().apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
                })
        }.run {
            assertEquals(
                arriveComplete,
                stopDetailViewModel.getApproachActionStatus(this, Pair(false, false))
            )
        }

    @Test
    fun testGetArrivedActionStatusForPendingArriveOnly(): Unit =
        StopDetail().apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = false))
                })
        }.run {
            assertEquals(
                arrive,
                stopDetailViewModel.getArrivedActionStatus(this, Pair(false, false))
            )
        }

    @Test
    fun testGetArrivedActionStatusForArrivedOnlyCompletion(): Unit =
        StopDetail().apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = true))
                })
        }.run {
            assertEquals(
                arriveComplete,
                stopDetailViewModel.getArrivedActionStatus(this, Pair(true, false))
            )
        }

    @Test
    fun testGetArrivedActionStatusForApproachCompletionAndPendingArrive(): Unit =
        StopDetail().apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
                    add(Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = false))
                })
        }.run {
            assertEquals(
                arrive,
                stopDetailViewModel.getArrivedActionStatus(this, Pair(false, false))
            )
        }

    @Test
    fun testGetArrivedActionStatusForPendingApproachAndArriveCompletion(): Unit =
        StopDetail().apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = false))
                    add(Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = true))
                })
        }.run {
            assertEquals(
                arriveComplete,
                stopDetailViewModel.getArrivedActionStatus(this, Pair(true, false))
            )
        }

    @Test
    fun testGetArrivedActionStatusForApproachAndArriveCompletion(): Unit =
        StopDetail().apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
                    add(Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = true))
                })
        }.run {
            assertEquals(
                arriveComplete,
                stopDetailViewModel.getArrivedActionStatus(this, Pair(true, false))
            )
        }

    @Test
    fun testGetArrivedActionStatusForApproachCompletionAndPendingArriveForSlowInternet() {
        val stop = StopDetail(stopid = 0).apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
                })
        }
        assertEquals(
            arriveComplete,
            stopDetailViewModel.getApproachActionStatus(stop, Pair(false, false))
        )
        stop.Actions.add(Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = false))
        assertEquals(arrive,
            stopDetailViewModel.cacheCompletedActions[stop.stopid]?.let {
                stopDetailViewModel.getArrivedActionStatus(
                    stop,
                    it
                )
            })
    }

    @Test
    fun testGetArrivedActionStatusForApproachCompletionAndPendingArriveForSlowInternetInReverseOrder() {
        val stop = StopDetail(stopid = 0).apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = false))
                })
        }
        assertEquals(arrive, stopDetailViewModel.getArrivedActionStatus(stop, Pair(false, false)))
        stop.Actions.add(Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
        assertEquals(
            arrive, stopDetailViewModel.getArrivedActionStatus(
                stop,
                Pair(true, false)
            )
        )
    }

    @Test
    fun testGetDepartActionStatusForApproachAndArriveCompletion(): Unit =
        StopDetail().apply {
            Actions.addAll(
                mutableListOf<Action>().apply {
                    add(Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
                    add(Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = true))
                })
        }.run {
            assertEquals(
                arriveComplete,
                stopDetailViewModel.getArrivedActionStatus(this, Pair(true, false))
            )
        }

    @Test
    fun `verify stop detail screen update upon action read completion`() = runTest {
        val stopId = 0
        val mockFlow = MutableSharedFlow<Int>(replay = 1).also { it.emit(stopId) }
        every { stopDetailViewModel.stopActionReadCompleteFlow } returns mockFlow
        every { stopDetailViewModel.setCurrentStop(any(), any()) } just runs

        stopDetailViewModel.listenForStopActionReadCompletion()

        verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            stopDetailViewModel.updateStopDetailScreenWithActions()
        }
    }

    @Test
    fun `verify stop detail screen infinite load if there is failure in action read`() = runTest {
        val stopId = 1
        val mockFlow = MutableSharedFlow<Int>(replay = 1).also { it.emit(stopId) }
        every { stopDetailViewModel.stopActionReadCompleteFlow } returns mockFlow
        every { stopDetailViewModel.updateStopDetailScreenWithActions() } returns Unit

        stopDetailViewModel.listenForStopActionReadCompletion()

        verify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            stopDetailViewModel.updateStopDetailScreenWithActions()
        }
    }

    @Test
    fun `verify currentIndex updation upon route calculation`() {
        stopDetailViewModel.setCurrentStopIndexAndSelectedStopId(0, 4)
        stopDetailViewModel.stopDetailList.addAll(
            listOf(
                StopDetail(stopid = 1),
                StopDetail(stopid = 2),
                StopDetail(stopid = 3),
                StopDetail(stopid = 4),
                StopDetail(stopid = 5)
            )
        )
        stopDetailViewModel.postStopsProcessing("test")
        assertEquals(3, stopDetailViewModel.getCurrentStopIndex())
    }

    @Test
    fun `verify trailer data handling`() = runTest {
        trailerDataFlow.emit(listOf("trailer1", "trailer2"))

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.setValue(DataStoreManager.TRAILER_IDS_KEY, "trailer1,trailer2")
        }
    }

    @Test
    fun `verify trailer empty data handling`() = runTest {
        trailerDataFlow.emit(listOf())

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.setValue(DataStoreManager.TRAILER_IDS_KEY, EMPTY_STRING)
        }
    }

    @Test
    fun `verify shipment data handling`() = runTest {
        shipmentDataFlow.emit(listOf("shipment1", "shipment2"))

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.setValue(DataStoreManager.SHIPMENTS_IDS_KEY, "shipment1,shipment2")
        }
    }

    @Test
    fun `verify shipment empty data handling`() = runTest {
        shipmentDataFlow.emit(listOf())

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.setValue(DataStoreManager.SHIPMENTS_IDS_KEY, EMPTY_STRING)
        }
    }

    @Test
    fun `verify setCurrentStop for invalid data`() {
        every { Log.e(any(), any(), any(), any(), any()) } returns Unit

        // Case 1
        stopDetailViewModel.setCurrentStop(currentStopIndex = null, "test")

        //Case 2
        stopDetailViewModel.setCurrentStop(currentStopIndex = -1, "test")

        //Case 3
        stopDetailViewModel.stopDetailList.addAll(listOf(StopDetail(stopid = 1)))
        stopDetailViewModel.setCurrentStop(currentStopIndex = 1, "test")

        coVerify(exactly = 3, timeout = TEST_DELAY_OR_TIMEOUT) {
            Log.e(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify setCurrentStop for 0 stops in stop list and retries`() = runTest {
        every { Log.w(any(), any()) } returns Unit
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "3434"

        stopDetailViewModel.setCurrentStop(currentStopIndex = 0, "test")

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            Log.w(any(), any())
        }

        advanceTimeBy(1000)

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            // Here retry count may vary in different systems. So, it is not possible to verify the exact count.
            stopDetailViewModel["setCurrentStopRelatedData"](any<Int>())
        }
    }

    @Test
    fun `verify setCurrentStop for valid data invokes expected functions`()= runTest {
        stopDetailViewModel.stopDetailList.addAll(
            listOf(
                StopDetail(stopid = 1),
                StopDetail(stopid = 2),
                StopDetail(stopid = 3),
                StopDetail(stopid = 4),
                StopDetail(stopid = 5)
            )
        )
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "3434"

            stopDetailViewModel.setCurrentStop(1, "test")

        advanceTimeBy(2 * DEBOUNCE_INTERVAL)

        coVerifyOrder {
            stopDetailViewModel.setStopsAvailableFlags(any(), any())
            stopDetailViewModel.setActionButtons(any())
            stopDetailViewModel["updateStopUIData"](any<Int>())
        }

    }

    @Test
    fun `verify processManualApproachOrArrival for only arrive with invalid cid`() {
        every { stopDetailViewModel.currentStop } returns MutableLiveData<StopDetail>().also { livedata ->
            livedata.value = StopDetail(stopid = 1).also {
                it.Actions.add(Action(actionid = 0, actionType = ActionTypes.ARRIVED.ordinal))
            }
        }
        coEvery { Log.logUiInteractionInInfoLevel(any(),any(),any())} just runs
        every { stopDetentionWarningUseCase.canDisplayDetentionWarning(any()) } returns true
        every { stopDetentionWarningUseCase.startDetentionWarningTimer(any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCid() } returns EMPTY_STRING
        every {  sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any()) } just runs
        coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any()) } just runs

        stopDetailViewModel.processManualApproachOrArrival(
            disableButtonsToPreventDoubleClick = {},
            startFormActivity = { _, _, _ -> },
            checkForTripCompletion = {}
        )

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            Log.e(any(), any())
        }
    }


    @Test
    fun `verify processManualApproachOrArrival for a stop with all 3 actions`() = runTest {
        val currentStop = StopDetail(stopid = 0, name = "stop1").also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal))
            it.Actions.add(
                Action(
                    actionid = 1,
                    actionType = ActionTypes.ARRIVED.ordinal,
                    driverFormid = 45454,
                    driverFormClass = 0,
                    responseSent = false
                )
            )
            it.Actions.add(Action(actionid = 2, actionType = ActionTypes.DEPARTED.ordinal))
        }
        every { stopDetailViewModel.currentStop } returns MutableLiveData<StopDetail>().also { livedata ->
            livedata.value = currentStop
        }
        coEvery {
            dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(
                any(),
                any()
            )
        } returns currentStop
        coEvery { Log.logUiInteractionInInfoLevel(any(),any(),any())} just runs
        every { stopDetentionWarningUseCase.canDisplayDetentionWarning(any()) } returns true
        every { stopDetentionWarningUseCase.startDetentionWarningTimer(any(), any()) } just runs
        coEvery { backboneUseCase.getCurrentUser() } returns EMPTY_STRING
        coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCid() } returns "76767"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "eve4r4"
        every { sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any()) } just runs
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { formViewModel.getFormData(any(), any(), any()) } returns UIFormResponse()

        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns "formData"
        every {
            UncompletedFormsUseCase.addFormToPreference(
                any(),
                any()
            )
        } returns "pendingFormsData"
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                any()
            )
        } just runs
        coEvery {
            dispatchStopsUseCase.handleStopEvents(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns "5454545"
        coEvery { dispatchStopsUseCase.sendApproachAction(any(), any()) } just runs
        coEvery { Log.logUiInteractionInInfoLevel(any(),any(),any())} just runs
        coEvery {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(),
                any(),
                any(),
                any()
            )
        } just runs
        stopDetailViewModel.processManualApproachOrArrival(
            disableButtonsToPreventDoubleClick = {},
            startFormActivity = { _, _, _ -> },
            checkForTripCompletion = {}
        )
        this.advanceUntilIdle()
        coVerify {
            stopDetentionWarningUseCase.canDisplayDetentionWarning(any())
            stopDetentionWarningUseCase.startDetentionWarningTimer(any(), any())
            stopDetailViewModel["canEnableNextAndPreviousButton"](any<Action>())
            sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any())
            stopDetailViewModel["saveFormPathToUncompletedDispatchFormStack"](
                any<String>(),
                any<DispatchFormPath>(),
                any<String>(),
                any<Boolean>()
            )
            stopDetailViewModel["sendActionResponseIfNotAlreadySent"](any<Action>())
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify processManualDeparture for a stop with completed depart action`() {
        every { stopDetailViewModel.currentStop } returns MutableLiveData<StopDetail>().also { livedata ->
            livedata.value = StopDetail(stopid = 0, name = "stop1").also {
                it.Actions.add(Action(actionid = 2, actionType = ActionTypes.DEPARTED.ordinal, responseSent = true))
            }
        }
        every { stopDetentionWarningUseCase.cancelDetentionWarningTimer() } just runs
        coEvery { Log.logUiInteractionInInfoLevel(any(),any(),any())} just runs
        every {  sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any()) } just runs

        stopDetailViewModel.processManualDeparture(
            disableButtonsToPreventDoubleClick = {},
            startFormActivity = { _, _, _ -> },
            checkForTripCompletion = {},
            showDepartureTime = {}
        )

        coVerifyOrder {
            stopDetentionWarningUseCase.cancelDetentionWarningTimer()
            stopDetailViewModel["canEnableNextAndPreviousButton"](any<Action>())
            sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any())
            stopDetailViewModel["sendActionResponseIfNotAlreadySent"](any<Action>())
        }
    }


    @Test
    fun `verify processManualDeparture for a stop with all 3 actions`() = runTest{
        val stop = StopDetail(stopid = 0, name = "stop1").also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal, driverFormid = 45454, driverFormClass = 0))
            it.Actions.add(Action(actionid = 2, actionType = ActionTypes.DEPARTED.ordinal))
        }
        every { stopDetailViewModel.currentStop } returns MutableLiveData<StopDetail>().also { livedata ->
            livedata.value = stop
        }
        coEvery { Log.logUiInteractionInInfoLevel(any(),any(),any())} just runs
        coEvery { dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst(any(),any()) } returns stop
        every { stopDetentionWarningUseCase.cancelDetentionWarningTimer() } just runs
        every {  sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any()) } just runs
        coEvery { dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any()) } returns "5454545"
        coEvery { dispatchStopsUseCase.sendApproachAction(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(any(), any(), any(), any()) } just runs
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf(stop)

        stopDetailViewModel.processManualDeparture(
            disableButtonsToPreventDoubleClick = {},
            startFormActivity = { _, _, _ -> },
            checkForTripCompletion = {},
            showDepartureTime = {}
        )

        this.advanceUntilIdle()
        coVerifyOrder {
            stopDetentionWarningUseCase.cancelDetentionWarningTimer()
            stopDetailViewModel["canEnableNextAndPreviousButton"](any<Action>())
            sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any())
            stopDetailViewModel["sendActionResponseIfNotAlreadySent"](any<Action>())
//            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any(), any())
//            dispatchStopsUseCase.sendApproachAction(any(), any())
//            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
//                any(),
//                any(),
//                any(),
//                any()
//            )
//            stopDetailViewModel["setDepartureTimeOfStop"](any<Int>())
        }


    }

    @Test
    fun `verify getNextStop execution from 1st stop - The 2nd stop action should be displayed after getNextStop call`() = runTest {
        stopDetailViewModel.setCurrentStopIndexAndSelectedStopId(0, 0)
        stopDetailViewModel.stopDetailList.addAll(
            listOf(
                StopDetail(stopid = 1),
                StopDetail(stopid = 2),
                StopDetail(stopid = 3),
                StopDetail(stopid = 4),
                StopDetail(stopid = 5)
            )
        )
        stopDetailViewModel.getNextStop()
        advanceTimeBy(2 * DEBOUNCE_INTERVAL)
        assertEquals(1, stopDetailViewModel.getCurrentStopIndex())
    }

    @Test
    fun `verify getNextStop execution from the last stop - The 5th stop action should be displayed after getNextStop call -- Nothing happens`() = runTest {
        stopDetailViewModel.setCurrentStopIndexAndSelectedStopId(4, 4)
        stopDetailViewModel.stopDetailList.addAll(
            listOf(
                StopDetail(stopid = 1),
                StopDetail(stopid = 2),
                StopDetail(stopid = 3),
                StopDetail(stopid = 4),
                StopDetail(stopid = 5)
            )
        )
        stopDetailViewModel.getNextStop()
        advanceTimeBy(2 * DEBOUNCE_INTERVAL)
        assertEquals(4, stopDetailViewModel.getCurrentStopIndex())
    }

    @Test
    fun `verify getPreviousStop execution from 2nd stop - The 1st stop action should be displayed after getPreviousStop call`() = runTest {
        stopDetailViewModel.setCurrentStopIndexAndSelectedStopId(1, 1)
        stopDetailViewModel.stopDetailList.addAll(
            listOf(
                StopDetail(stopid = 1),
                StopDetail(stopid = 2),
                StopDetail(stopid = 3),
                StopDetail(stopid = 4),
                StopDetail(stopid = 5)
            )
        )
        coEvery { stopDetailViewModel.setCurrentStopIndexAndSelectedStopId(any(), any()) } just runs

        stopDetailViewModel.getPreviousStop()

        advanceTimeBy(2 * DEBOUNCE_INTERVAL)

        assertEquals(0, stopDetailViewModel.getCurrentStopIndex())
    }

    @Test
    fun `verify getPreviousStop execution from the 1st stop - The 1st stop action should be displayed after getPreviousStop call -- Nothing happens`() = runTest {
        stopDetailViewModel.setCurrentStopIndexAndSelectedStopId(0, 0)
        stopDetailViewModel.stopDetailList.addAll(
            listOf(
                StopDetail(stopid = 1),
                StopDetail(stopid = 2),
                StopDetail(stopid = 3),
                StopDetail(stopid = 4),
                StopDetail(stopid = 5)
            )
        )
        coEvery { stopDetailViewModel["getErrorString"]() } answers { "test" }

        stopDetailViewModel.getPreviousStop()
        advanceTimeBy(2 * DEBOUNCE_INTERVAL)
        // The ui doesn't show previous stop button if the user is in stop 1
        assertEquals(-1, stopDetailViewModel.getCurrentStopIndex())
    }

    @Test
    fun `verify listenForStopAdditionAndRemoval execution for a stop removal`() = runTest {
        val dispatchId = "2323"
        val stopUpdateStatus = MutableSharedFlow<String>()
        every { stopDetailViewModel.stopUpdateStatus } returns stopUpdateStatus
        coEvery { stopDetailViewModel.getSelectedDispatchId(any()) } returns dispatchId
        coEvery { dispatchBaseUseCase.getInActiveStopCount(dispatchId) } returns 4
        coEvery { dispatchBaseUseCase.getActiveStopCount(dispatchId) } returns 1
        coEvery { dispatchBaseUseCase.getManipulatedStopCount(any(), any(), any(), any()) } returns Triple(4, 1, true)

        stopDetailViewModel.listenForStopAdditionAndRemoval()

        stopUpdateStatus.emit(REMOVED)

        coVerifyAll {
            with(dispatchBaseUseCase) {
                getCidAndTruckNumber(any())
                getInActiveStopCount(dispatchId)
                getActiveStopCount(dispatchId)
                getManipulatedStopCount(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `verify listenForStopAdditionAndRemoval execution for a stop addition`() = runTest {
        val dispatchId = "2323"
        val stopUpdateStatus = MutableSharedFlow<String>()
        every { stopDetailViewModel.stopUpdateStatus } returns stopUpdateStatus
        coEvery { stopDetailViewModel.getSelectedDispatchId(any()) } returns dispatchId
        coEvery { dispatchBaseUseCase.getInActiveStopCount(dispatchId) } returns 4
        coEvery { dispatchBaseUseCase.getActiveStopCount(dispatchId) } returns 1
        coEvery { dispatchBaseUseCase.getManipulatedStopCount(any(), any(), any(), any()) } returns Triple(4, 1, true)

        stopDetailViewModel.listenForStopAdditionAndRemoval()

        stopUpdateStatus.emit(ADDED)

        coVerifyAll {
            with(dispatchBaseUseCase) {
                getCidAndTruckNumber(any())
                getInActiveStopCount(dispatchId)
                getActiveStopCount(dispatchId)
                getManipulatedStopCount(any(), any(), any(), any())
            }
        }
    }


    @Test
    fun `manipulateDepartureGeofenceOnManualArrival updates stop detail and sends updated stoplist to maps wrapper when action type is ARRIVED`() = runTest {
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, completedTime = ""))
        stopList.add(StopDetail(stopid = 2, completedTime = ""))

        stopDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.ACTIVE)
        stopDetailViewModel.stopDetailList.addAll(stopList)

        val action = Action(actionType = ActionTypes.ARRIVED.ordinal, stopid = 2)
        val location = Pair(10.0, 20.0)

        stopList[1].Actions.add(action)

        stopDetailViewModel.setCurrentStop(currentStopIndex = 1, "test")

        coEvery { backboneUseCase.getCurrentLocation() } returns location
        coEvery { dispatchStopsUseCase.updateStopDetail(any(), any()) } just Runs
        coEvery { sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(any(),DispatchActiveState.ACTIVE, any() ) } just Runs

        stopDetailViewModel.manipulateDepartureGeofenceOnManualArrival()

        coVerify { sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(any(),DispatchActiveState.ACTIVE, any()) }
    }

    @Test
    fun `manipulateDepartureGeofenceOnManualArrival does not updates stop detail and sends updated stoplist to maps wrapper when arrive action is already performed and Manual ARRIVE action is performed`() = runTest {
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 1, completedTime = "COMPLETED_STOP"))
        stopList.add(StopDetail(stopid = 2, completedTime = "COMPLETED_STOP"))

        stopDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.ACTIVE)
        stopDetailViewModel.stopDetailList.addAll(stopList)

        val action = Action(actionType = ActionTypes.ARRIVED.ordinal, stopid = 2)
        val location = Pair(10.0, 20.0)

        stopList[1].Actions.add(action)

        stopDetailViewModel.setCurrentStop(currentStopIndex = 1, "test")

        coEvery { backboneUseCase.getCurrentLocation() } returns location
        coEvery { dispatchStopsUseCase.updateStopDetail(any(), any()) } just Runs
        coEvery { sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(any(),DispatchActiveState.ACTIVE, any()) } just Runs

        stopDetailViewModel.manipulateDepartureGeofenceOnManualArrival()

        coVerify(exactly = 0) { dispatchStopsUseCase.updateStopDetail(any(), any()) }
        coVerify(exactly = 0) { sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(any(),DispatchActiveState.ACTIVE, any()) }
    }

    @Test
    fun `determineAndUpdateAddressVisibility sets address visibility to true when conditions are met`() {
        every { stopDetailViewModel.shouldDisplayAddress() } returns true
        every { stopDetailViewModel.stopActionsAllowed.value } returns true

        stopDetailViewModel.determineAndUpdateAddressVisibility()

        verify { stopDetailViewModel.setAddressDisplayedLiveDataValue(true) }
    }

    @Test
    fun `determineAndUpdateAddressVisibility sets address visibility to false when shouldDisplayAddress is false`() {
        every { stopDetailViewModel.shouldDisplayAddress() } returns false
        every { stopDetailViewModel.stopActionsAllowed.value } returns true

        stopDetailViewModel.determineAndUpdateAddressVisibility()

        verify { stopDetailViewModel.setAddressDisplayedLiveDataValue(false) }
    }

    @Test
    fun `determineAndUpdateAddressVisibility sets address visibility to false when stopActionsAllowed is false`() {
        every { stopDetailViewModel.shouldDisplayAddress() } returns true
        every { stopDetailViewModel.stopActionsAllowed.value } returns false

        stopDetailViewModel.determineAndUpdateAddressVisibility()

        verify { stopDetailViewModel.setAddressDisplayedLiveDataValue(false) }
    }

    @Test
    fun `determineAndUpdateAddressVisibility sets address visibility to false when stopActionsAllowed is null`() {
        every { stopDetailViewModel.shouldDisplayAddress() } returns true
        every { stopDetailViewModel.stopActionsAllowed.value } returns null

        stopDetailViewModel.determineAndUpdateAddressVisibility()

        verify { stopDetailViewModel.setAddressDisplayedLiveDataValue(false) }
    }

    @After
    fun after() {
        stopKoin()
        unmockkAll()
    }
}