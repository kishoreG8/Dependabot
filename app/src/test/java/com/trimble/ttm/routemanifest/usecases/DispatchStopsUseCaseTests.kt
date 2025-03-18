package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trimble.ttm.backbone.api.Backbone
import com.trimble.ttm.backbone.api.BackboneFactory
import com.trimble.ttm.backbone.api.data.eld.Motion
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.DeepLinkUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.FormUtils
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.customComparator.LauncherMessagePriorityComparator
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.CURRENT_STOP_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.IS_DYA_ALERT_ACTIVE
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.NAVIGATION_ELIGIBLE_STOP_LIST_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.SELECTED_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.STOPS_SERVICE_REFERENCE_KEY
import com.trimble.ttm.routemanifest.managers.ResourceStringsManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.ArrivalReason
import com.trimble.ttm.routemanifest.model.JsonData
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.routemanifest.model.StopActionEventData
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.TripTypes
import com.trimble.ttm.routemanifest.repo.ArrivalReasonEventRepo
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.repo.FormsRepository
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepoImpl
import com.trimble.ttm.routemanifest.repo.TripMobileOriginatedEventsRepo
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepoImpl
import com.trimble.ttm.routemanifest.utils.AUTO_ARRIVED
import com.trimble.ttm.routemanifest.utils.AUTO_DEPARTED
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.CoroutineTestRule
import com.trimble.ttm.routemanifest.utils.FALSE
import com.trimble.ttm.routemanifest.utils.JsonDataConstructionUtils
import com.trimble.ttm.routemanifest.utils.MANUAL_ARRIVED
import com.trimble.ttm.routemanifest.utils.MANUAL_DEPARTED
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.TRUE
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.viewmodel.ACTIONS
import com.trimble.ttm.routemanifest.viewmodel.STOPS
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.PriorityBlockingQueue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal const val TEST = "test"
@OptIn(ExperimentalCoroutinesApi::class)
class DispatchStopsUseCaseTests : KoinTest {
    private val dispatchIDForStops = "100"

    private val didYouArriveAt1 = "did you arrive at 1"
    private val didYouArriveAt2 = "did you arrive at 2"
    private val didYouArriveAt3 = "did you arrive at 3"
    private val completeFormsFor2Stops = "complete forms for 2 stops"
    private val selectStopToNavigate = "select a stop to navigate"
    private val DEF_DISPATCH_ID = "234"


    @RelaxedMockK
    private lateinit var backbone: Backbone
    @RelaxedMockK
    private lateinit var application: WorkflowApplication
    @MockK
    private lateinit var context: Context
    @MockK
    private lateinit var tripMobileOriginatedEventsRepo: TripMobileOriginatedEventsRepo
    @MockK
    private lateinit var arrivalReasonEventRepo: ArrivalReasonEventRepo
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo
    @MockK
    private lateinit var featureGatekeeper: FeatureGatekeeper
    @RelaxedMockK
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder
    @MockK
    private lateinit var sendDispatchDataUseCase: SendDispatchDataUseCase
    @MockK
    private lateinit var stopDetentionWarningUseCase: StopDetentionWarningUseCase
    @MockK
    private lateinit var routeETACalculationUseCase: RouteETACalculationUseCase
    @MockK
    private lateinit var formsRepository: FormsRepository
    @MockK
    private lateinit var formUseCase: FormUseCase
    @MockK
    private lateinit var deepLinkUseCase: DeepLinkUseCase
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase
    @MockK
    private lateinit var sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase
    @MockK
    private lateinit var arriveTriggerDataStoreKeyManipulationUseCase: ArriveTriggerDataStoreKeyManipulationUseCase
    @MockK
    private lateinit var arrivalReasonUseCase: ArrivalReasonUsecase

    private val tripPanelUseCase: TripPanelUseCase by inject()

    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val testDispatcher = TestCoroutineScheduler()
    private val mockScope = TestScope()

    private var modulesRequiredForTest = module {
        single<TripPanelEventRepo> { TripPanelEventRepoImpl() }
        single<DispatcherProvider> { DefaultDispatcherProvider() }
        single { dataStoreManager }
        single { formDataStoreManager }
        single { dispatchStopsUseCase }
        single { featureGatekeeper }
        single { firebaseAnalyticEventRecorder }
        single { sendDispatchDataUseCase }
        factory { routeETACalculationUseCase }
        factory { backboneUseCase }
        factory { ResourceStringsManager(context) }
        single {
            TripPanelUseCase(
                get(),
                get(),
                get(),
                get(),
                get(),
                dispatchStopsUseCase,
                appModuleCommunicator,
                context = context,
                arriveTriggerDataStoreKeyManipulationUseCase = arriveTriggerDataStoreKeyManipulationUseCase,
                fetchDispatchStopsAndActionsUseCase = fetchDispatchStopsAndActionsUseCase
            )
        }
        factory { SendBroadCastUseCase(context) }
        factory { arrivalReasonUseCase }
        single { appModuleCommunicator }
        single { tripMobileOriginatedEventsRepo }
        single { arrivalReasonEventRepo }
        single<LocalDataSourceRepo> { LocalDataSourceRepoImpl(get(), get(), appModuleCommunicator) }
    }

    private val actionsListWithAllActions = ArrayList<Action>().apply{
        add(Action(actionType = 0))
        add(Action(actionType = 1))
        add(Action(actionType = 2))
    }
    private val actionsListNoArriveAction = ArrayList<Action>().apply{
        add(Action(actionType = 0))
        add(Action(actionType = 2))
    }
    private val sequencedStopsWithActions = mutableListOf<StopDetail>().also {
        it.add(
            StopDetail(stopid = 0, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true
                )
            }
        )
        it.add(
            StopDetail(stopid = 1, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true,
                    ActionTypes.DEPARTED to true
                )
            }
        )
        it.add(
            StopDetail(stopid = 2, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true,
                    ActionTypes.DEPARTED to true
                )
            }
        )
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkObject(FormUtils)
        mockkObject(Utils)
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        startKoin {
            androidContext(application)
            loadKoinModules(modulesRequiredForTest)
        }
        every { dispatchFirestoreRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        fetchDispatchStopsAndActionsUseCase = spyk(FetchDispatchStopsAndActionsUseCase(dispatchFirestoreRepo))
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "234"
        mockkObject(JsonDataConstructionUtils.Companion)
        dispatchStopsUseCase = spyk(
            DispatchStopsUseCase(
                formsRepository,
                dispatchFirestoreRepo,
                DefaultDispatcherProvider(),
                stopDetentionWarningUseCase,
                routeETACalculationUseCase,
                featureGatekeeper,
                formUseCase,
                sendWorkflowEventsToAppUseCase,
                deepLinkUseCase,
                arriveTriggerDataStoreKeyManipulationUseCase,
                dataStoreManager,
                fetchDispatchStopsAndActionsUseCase
            )
        )
        every { context.filesDir } returns temporaryFolder.newFolder()
        every { context.applicationContext } returns context
        mockkObject(ApplicationContextProvider)
        every { ApplicationContextProvider.getApplicationContext() } returns application.applicationContext
        arrivalReasonUseCase = mockk()
        mockkStatic("com.trimble.ttm.backbone.api.BackboneFactory")
        every { BackboneFactory.backbone(any()) } returns backbone
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(12.5, 30.0)
        coEvery { backboneUseCase.getFuelLevel() } returns 50
        coEvery { backboneUseCase.getOdometerReading(any()) } returns 22.0
        coEvery {
            dataStoreManager.setValue(any(), any<String>())
        } returns Unit
        coEvery {dispatchFirestoreRepo.getActionsOfStop(any(), any(), any())} returns actionsListWithAllActions
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST751"
        coEvery { appModuleCommunicator.doGetObcId() } returns "34566"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchIDForStops
        coEvery { appModuleCommunicator.doGetVid() } returns 22323
        coEvery { appModuleCommunicator.getFeatureFlags() } returns mapOf()
        coEvery { dataStoreManager.getValue(SELECTED_DISPATCH_KEY, EMPTY_STRING) } returns dispatchIDForStops
        coEvery { dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns dispatchIDForStops
        coEvery {
            formUseCase.removeFormFromPreference(any(), any())
        } returns ""
    }

    @Test
    fun `validate action is retrieved from stop stored in preference`() = runTest(testDispatcher) {
        val action = Action(
            actionType = 0,
            stopid = 1
        )
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns ArrayList<Action>().also {
            it.add(
                action
            )
        }

        Assert.assertTrue(
            dispatchStopsUseCase.getActionDataFromStop(
                dispatchIDForStops,
                1,
                0
            )!!.stopid == 1
        )
    }


    @Test
    fun `validate action is retrieved from stop when there are more actions`() = runTest(testDispatcher) {
        val actions = ArrayList<Action>().apply {
            add(
                Action(
                    actionType = 0,
                    stopid = 1
                )
            )
            add(
                Action(
                    actionType = 1,
                    stopid = 1
                )
            )
            add(
                Action(
                    actionType = 2,
                    stopid = 1
                )
            )
        }
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.add(
            StopDetail(stopid = 1).apply {
                this.Actions.addAll(actions)
            })

        coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(),
            any()) } returns actions

        Assert.assertTrue(
            dispatchStopsUseCase.getActionDataFromStop(
                dispatchIDForStops,
                1,
                1
            )?.stopid == 1
        )
    }

    @Test
    fun `validate null is returned from stop when the requested action does not exist`() = runTest(testDispatcher) {
        val actions = ArrayList<Action>().apply {
            add(
                Action(
                    actionType = 2,
                    stopid = 1
                )
            )
        }
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.apply {
            add(StopDetail(stopid = 1).apply {
                this.Actions.addAll(actions)
            })
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(),
            any()) } returns actions
        Assert.assertTrue(
            dispatchStopsUseCase.getActionDataFromStop("1", 1, 1).isNull()
        )
    }

    @Test
    fun `validate action is retrieved when there are more stops`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { dispatchStopsUseCase.getActionDataFromStop(any(), any(), any()) } returns Action(
            actionType = 2,
            stopid = 1
        )
        Assert.assertTrue(
            dispatchStopsUseCase.getActionDataFromStop(
                "1",
                1,
                2
            )!!.stopid == 1
        )
    }

    @Test
    fun `validate action is retrieved when stop list is empty`() = runTest(testDispatcher) {    //NOSONAR
        coEvery {
            dispatchFirestoreRepo.getStop(
                any(),
                any(),
                any(),
                any()
            )
        } returns StopDetail()
        coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(),any()) } returns listOf()
        Assert.assertTrue(
            dispatchStopsUseCase.getActionDataFromStop("1", 1, 2).isNull()
        )

    }

    @Test
    fun `validate stop is retrieved since firestore fetched data returns default data`() =
        runTest(testDispatcher) {    //NOSONAR
            coEvery {
                dispatchFirestoreRepo.getStop(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns StopDetail()
            coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(),any()) } returns listOf()
            Assert.assertTrue(dispatchStopsUseCase.getStopAndActions(0, dataStoreManager, TEST).isNotNull())

        }

    @Test
    fun `validate stop is retrieved when only stop is empty`() =
        runTest(testDispatcher) {    //NOSONAR
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns listOf()
            coEvery {
                dispatchFirestoreRepo.getStop(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns StopDetail()
            coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any()) } returns listOf()
            Assert.assertEquals(-1, dispatchStopsUseCase.getStopAndActions(0, dataStoreManager, TEST).stopid)
        }

    @Test
    fun `validate stop is put into preference for first time`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { dataStoreManager.setValue<Any>(any(), any()) } just runs
        coEvery { dataStoreManager.containsKey(CURRENT_STOP_KEY) } returns FALSE
        coEvery { dataStoreManager.getValue(CURRENT_STOP_KEY, EMPTY_STRING) } returns Gson().toJson(
            Stop(stopId = 4)
        )
        coEvery { dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns DEF_DISPATCH_ID
        coEvery { dataStoreManager.setValue(CURRENT_STOP_KEY, Gson().toJson(Stop())) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns DEF_DISPATCH_ID
        Assert.assertTrue(
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                StopDetail(
                    name = "Test",
                    description = "Description",
                    stopid = 1,
                    dispid = DEF_DISPATCH_ID
                ),
                dataStoreManager
            )
        )
    }

    @Test
    fun `validate stop is put into preference when there is already a different valid stop`() =
        runTest(testDispatcher) {    //NOSONAR
            coEvery { dataStoreManager.setValue<Any>(any(), any()) } just runs
            coEvery { dataStoreManager.containsKey(CURRENT_STOP_KEY) } returns FALSE
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns Gson().toJson(
                Stop(
                    stopId = 3,
                    dispId = DEF_DISPATCH_ID
                )
            )
            coEvery { dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns DEF_DISPATCH_ID
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns DEF_DISPATCH_ID
            Assert.assertTrue(
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    StopDetail(
                        name = "Test",
                        description = "Description",
                        stopid = 1,
                        dispid = DEF_DISPATCH_ID
                    ),
                    dataStoreManager
                )
            )
        }

    @Test
    fun `validate existing stop is not put into preference`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { dataStoreManager.containsKey(CURRENT_STOP_KEY) } returns FALSE
        coEvery { dataStoreManager.setValue<Any>(any(), any()) } just runs
        coEvery { dataStoreManager.getValue(CURRENT_STOP_KEY, EMPTY_STRING) } returns Gson().toJson(
            Stop(stopId = 1)
        )
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns DEF_DISPATCH_ID
        Assert.assertFalse(
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                StopDetail(
                    name = "Test",
                    description = "Description",
                    stopid = 1,
                    dispid = DEF_DISPATCH_ID
                ),
                dataStoreManager
            )
        )
    }

    @Test
    fun `validate stop detail is copied into stop object`() = runTest(testDispatcher) {    //NOSONAR

        val stopDetail = StopDetail(
            stopid = 1,
            name = "Test",
            latitude = 98.768736,
            longitude = -98.586899
        ).apply {
            Actions.apply {
                add(
                    Action(
                        actionType = 0,
                        stopid = 1,
                        radius = 18000,
                    )
                )
                add(
                    Action(
                        actionType = 1,
                        stopid = 1,
                        radius = 3000,
                        driverFormid = 38234
                    )
                )
                add(
                    Action(
                        actionType = 2,
                        stopid = 1,
                        radius = 5000,
                        driverFormid = 38244
                    )
                )
            }
        }

        coEvery { dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns "11111"

        Assert.assertEquals(
            dispatchStopsUseCase.initStopDataAndUpdate(
                Stop(),
                stopDetail
            ).stopName, "Test"
        )

        Assert.assertEquals(
            dispatchStopsUseCase.initStopDataAndUpdate(
                Stop(),
                stopDetail
            ).latitude.toString(), "98.768736"
        )

        Assert.assertEquals(
            dispatchStopsUseCase.initStopDataAndUpdate(
                Stop(),
                stopDetail
            ).longitude.toString(), "-98.586899"
        )

        Assert.assertEquals(
            dispatchStopsUseCase.initStopDataAndUpdate(
                Stop(),
                stopDetail
            ).arrivedFormId, 38234
        )

        Assert.assertEquals(
            dispatchStopsUseCase.initStopDataAndUpdate(
                Stop(),
                stopDetail,
            ).departRadius, 5000
        )

        Assert.assertEquals(
            dispatchStopsUseCase.initStopDataAndUpdate(
                Stop(),
                stopDetail
            ).approachRadius, 18000
        )
    }

    @Test
    fun `validate stop has no pending action when all responses sent`() {    //NOSONAR

        Assert.assertTrue(
            dispatchStopsUseCase.isStopActionsAreTriggered(
                Stop(
                    approachRadius = 2000,
                    approachResponseSent = true
                )
            )
        )
    }

    @Test
    fun `validate stop has no pending action when no actions exist`() {    //NOSONAR

        Assert.assertTrue(
            dispatchStopsUseCase.isStopActionsAreTriggered(
                Stop(
                    approachRadius = 0,
                    approachResponseSent = false
                )
            )
        )
    }

    @Test
    fun `validate stop has no pending action when all actions are sent`() {    //NOSONAR

        Assert.assertTrue(
            dispatchStopsUseCase.isStopActionsAreTriggered(
                Stop(
                    approachRadius = 100,
                    arrivedRadius = 1000,
                    departRadius = 2000,
                    approachResponseSent = true,
                    arrivedResponseSent = true,
                    departResponseSent = true
                )
            )
        )
    }

    @Test
    fun `validate stop has pending action when one action is sent`() {    //NOSONAR

        Assert.assertFalse(
            dispatchStopsUseCase.isStopActionsAreTriggered(
                Stop(
                    approachRadius = 100,
                    arrivedRadius = 1000,
                    departRadius = 2000,
                    approachResponseSent = true,
                    arrivedResponseSent = true,
                    departResponseSent = false
                )
            )
        )
    }

    @Test
    fun `validate stop has pending actions when all action are not sent`() {    //NOSONAR

        Assert.assertFalse(
            dispatchStopsUseCase.isStopActionsAreTriggered(
                Stop(
                    approachRadius = 100,
                    arrivedRadius = 1000,
                    departRadius = 2000,
                    approachResponseSent = false,
                    arrivedResponseSent = false,
                    departResponseSent = false
                )
            )
        )
    }

    @Test
    fun `set stop eligibility for first time when FF stops on top `() = runTest(testDispatcher) {    //NOSONAR
        try{
            val stops = mutableListOf<StopDetail>().apply {
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
            val stopsIds = linkedSetOf("2")
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns stops
            coEvery {
                dataStoreManager.getValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                    mutableSetOf()
                )
            } returns stopsIds
            coEvery {
                dataStoreManager.setValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                    stopsIds
                )
            } just runs
            coEvery {
                dataStoreManager.setValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY, setOf()
                )
            } just runs
            val stopIds = dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                stops,
                dataStoreManager
            )
            Assert.assertTrue(
                stopIds.size == 1
            )
            assert(stopIds.contains("2"))
        } catch(e: Exception){
                println("Caught Exception $e")
        }
    }

    @Test
    fun `set stop eligibility for first time when stops are miixed`() = runTest(testDispatcher) {    //NOSONAR
        val stops = mutableListOf<StopDetail>().apply {
            this.add(
                StopDetail(
                    stopid = 0,
                    sequenced = 1,
                    completedTime = ""
                )
            )
            this.add(
                StopDetail(
                    stopid = 1,
                    sequenced = 1,
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

        val stopsIds = linkedSetOf("0")
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns stops
        coEvery {
            dataStoreManager.getValue(
                NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                mutableSetOf()
            )
        } returns stopsIds
        coEvery {
            dataStoreManager.setValue(
                NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                stopsIds
            )
        } just runs
        coEvery {
            dataStoreManager.setValue(
                NAVIGATION_ELIGIBLE_STOP_LIST_KEY, setOf()
            )
        } just runs

        val stopIds = dispatchStopsUseCase.setStopsEligibilityForFirstTime(
            stops,
            dataStoreManager
        )
        Assert.assertTrue(
            stopIds.size == 1
        )
        assert(stopIds.contains("0"))

    }

    @Test
    fun `dont set stop eligibility for first time only if all are FF `() =
        runTest(testDispatcher) {    //NOSONAR
            val stops = mutableListOf<StopDetail>().apply {
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
                        sequenced = 0,
                        completedTime = ""
                    )
                )
            }
            val stopsIds = linkedSetOf("0")
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns stops
            coEvery {
                dataStoreManager.getValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                    mutableSetOf()
                )
            } returns stopsIds
            coEvery {
                dataStoreManager.setValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                    stopsIds
                )
            } just runs
            coEvery {
                dataStoreManager.setValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY, setOf()
                )
            } just runs
            val stopIds = dispatchStopsUseCase.setStopsEligibilityForFirstTime(
                stops,
                dataStoreManager
            )
            Assert.assertTrue(
                stopIds.isEmpty()
            )
        }

    @Test
    fun `dont send message to location panel if there is no active dispatch`() =
        runTest(testDispatcher) {    //NOSONAR

            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns false
            Assert.assertFalse(
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            )
        }

    @Test
            /*
            TODO: This is passing in local set up so have to look at it
             */
    fun `dont send message to location panel if there is active dispatch but stops are sequenced`() =
        runTest(testDispatcher) {    //NOSONAR

            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    getStopsList()
                )
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns getStopsList()
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
                )
            } returns TRUE
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns Gson().toJson(Stop(stopId = 0))
            coEvery { backboneUseCase.fetchEngineMotion(any()) } returns false
            every { backbone.retrieveDataFor(Motion).fetch()?.data } returns false
            coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns arrayListOf()
            Assert.assertFalse(
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            )
        }

    @Test
    fun `verify sending message to location panel if there is active dispatch but stops are sequenced`() =
        runTest(testDispatcher) {    //NOSONAR

            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    getStopsList()
                )
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns getStopsList()
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
                )
            } returns TRUE
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns Gson().toJson(Stop(stopId = 0))
            coEvery { backboneUseCase.fetchEngineMotion(any()) } returns null
            every { backbone.retrieveDataFor(Motion).fetch()?.data } returns null
            coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns arrayListOf()
            Assert.assertFalse(
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            )
        }

    private fun getStopsList(): List<StopDetail> {
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.apply {
            add(StopDetail(stopid = 0).apply {
                Actions.apply {
                    add(
                        Action(
                            actionType = 1,
                            stopid = 0
                        )
                    )
                }
            })
            add(StopDetail(stopid = 1).apply {
                Actions.apply {
                    add(
                        Action(
                            actionType = 2,
                            stopid = 1
                        )
                    )
                }
            })
        }
        return stopDetails
    }

    @Test
    fun `validate stop detail returns based on the passed stop id`() = runTest(testDispatcher) {
        val stopDetails = mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 1))
            it.add(StopDetail(stopid = 2))
            it.add(StopDetail(stopid = 3))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns stopDetails
        //Assert stop data for passed incorrect stop id
        assertTrue(fetchDispatchStopsAndActionsUseCase.getStopData(-1).isNull())
        //Assert stop data for passed correct stop id
        assertTrue(fetchDispatchStopsAndActionsUseCase.getStopData(1)?.stopid == 1)
    }

    @Test
    fun `validate priorityQueue emitsDataBasedOnTheCurrentStop irrespective of the insertionOrder`() =
        runTest(testDispatcher) {    //NOSONAR
            try {
                val listLauncherMessageWithPriority =
                    PriorityBlockingQueue(
                        1,
                        LauncherMessagePriorityComparator
                    )

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

                Assert.assertTrue(listLauncherMessageWithPriority.remove().message == didYouArriveAt1)
            }catch(e: Exception){
                println("Caught exception: $e")
            }
        }

    @Test
    fun `validate priorityQueueEmitsCurrentStop irrespective of nearest stop from the current location `() {    //NOSONAR
        val listLauncherMessageWithPriority =
            PriorityBlockingQueue(
                1,
                LauncherMessagePriorityComparator
            )
        /**
         * CPick will send 0.00 as current location lat long if it is not running
         */
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
                (didYouArriveAt1, 0, 1, Pair(44.9448, -93.2881))
        )

        Assert.assertTrue(listLauncherMessageWithPriority.remove().message == didYouArriveAt1)
    }

    @Test
    fun `validate priorityQueueEmitsDataRespectiveOfNearestStop from the current location not based on the stop id `() {    //NOSONAR
        val listLauncherMessageWithPriority =
            PriorityBlockingQueue(
                1,
                LauncherMessagePriorityComparator
            )
        /**
         * Play services will send 0.00 as current location lat long if it is not running.Here message id is stop id
         */
        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt2, 1, 2, Pair(0.34, -0.20))
        )

        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt3, 1, 3, Pair(0.12, -0.10))
        )

        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt1, 1, 4, Pair(44.9448, -93.2881))
        )

        Assert.assertTrue(listLauncherMessageWithPriority.remove().message == didYouArriveAt3)
        Assert.assertTrue(listLauncherMessageWithPriority.remove().message == didYouArriveAt2)
        Assert.assertTrue(listLauncherMessageWithPriority.remove().message == didYouArriveAt1)
    }

    @Test
    fun `validate priorityQueueEmitsDataIfMoreThanTwoArrivedTriggersHavingSameDistanceFromTheCurrentLocation then based on the stop id `() {    //NOSONAR
        val listLauncherMessageWithPriority =
            PriorityBlockingQueue(
                1,
                LauncherMessagePriorityComparator
            )
        /**
         * CPick will send 0.00 as current location lat long if it is not running.Here message id is stop id
         */
        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt3, 1, 3, Pair(0.12, 0.10))
        )
        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt2, 1, 2, Pair(0.12, 0.10))
        )

        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                ("did you arrive at 4", 1, 4, Pair(0.12, 0.10))
        )

        Assert.assertTrue(listLauncherMessageWithPriority.remove().message == didYouArriveAt2)
        Assert.assertTrue(listLauncherMessageWithPriority.remove().message == didYouArriveAt3)
        Assert.assertTrue(listLauncherMessageWithPriority.remove().message == "did you arrive at 4")

    }

    @Test
    fun `validate priority queue comparator priority emit`() { //NOSONAR
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(80.25, 12.98)
        val listLauncherMessageWithPriority =
            PriorityBlockingQueue(
                1,
                LauncherMessagePriorityComparator
            )

        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (completeFormsFor2Stops, 2, 34)
        )

        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (selectStopToNavigate, 3, 33)
        )

        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                ("your next stop minnesota v drive is 5 miles away", 4, 32)
        )

        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt1, 1, 3, Pair(44.9526, -93.2881))
        )
        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt2, 1, 2, Pair(44.9526, -93.2881))
        )

        listLauncherMessageWithPriority.add(
            LauncherMessageWithPriority
                (didYouArriveAt3, 0, 1, Pair(44.9448, -93.2881))
        )


        var peekMessage: LauncherMessageWithPriority = listLauncherMessageWithPriority.remove()

        Assert.assertFalse(peekMessage.message == selectStopToNavigate)
        Assert.assertFalse(peekMessage.message == completeFormsFor2Stops)

        //Assert priority messages peek results in based on the priority
        Assert.assertTrue(peekMessage.message == didYouArriveAt3)

        peekMessage = listLauncherMessageWithPriority.remove()
        Assert.assertTrue(peekMessage.message == didYouArriveAt2)

        peekMessage = listLauncherMessageWithPriority.remove()
        Assert.assertTrue(peekMessage.message == didYouArriveAt1)

        peekMessage = listLauncherMessageWithPriority.remove()
        Assert.assertTrue(peekMessage.message == completeFormsFor2Stops)

        peekMessage = listLauncherMessageWithPriority.remove()
        Assert.assertTrue(peekMessage.message == selectStopToNavigate)

        peekMessage = listLauncherMessageWithPriority.remove()
        Assert.assertTrue(peekMessage.message == "your next stop minnesota v drive is 5 miles away")
    }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns true if triggered action is arrived`() =
        runTest(testDispatcher) {    //NOSONAR
            val action = Action(
                actionType = ActionTypes.ARRIVED.ordinal,
                stopid = 0
            )
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0).apply {
                    Actions.add(action)
                })
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns stopDetails
            Assert.assertEquals(
                true, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    action
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns false if StopList in preference is empty`() =
        runTest(testDispatcher) {    //NOSONAR
            coEvery { backboneUseCase.getCurrentLocation() } returns Pair(80.25, 12.98)
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns listOf()
            Assert.assertEquals(
                false, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    null
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns true triggered if action is departed and stop has departed action only`() =
        runTest(testDispatcher) {    //NOSONAR
            val departAction = Action(
                actionType = ActionTypes.DEPARTED.ordinal,
                stopid = 0
            )
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0))
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetails[0]
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.add(departAction)
            }
            Assert.assertEquals(
                true, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    departAction
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns true if triggered action is departed and stop has arriving and departed action only`() =
        runTest(testDispatcher) {    //NOSONAR
            val arrivingAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                stopid = 0
            )
            val departAction = Action(
                actionType = ActionTypes.DEPARTED.ordinal,
                stopid = 0
            )
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0))
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetails[0]
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.add(
                    arrivingAction
                )
                it.add(departAction)
            }
            Assert.assertEquals(
                true, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    departAction
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns true if triggered action is arriving and stop has arriving action only`() =
        runTest(testDispatcher) {    //NOSONAR
            val arrivingAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                stopid = 0
            )
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0))
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetails[0]
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.add(
                    arrivingAction
                )
            }
            Assert.assertEquals(
                true, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    arrivingAction
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns true if triggered action is arriving and stop has arriving and arrived action only`() =
        runTest(testDispatcher) {    //NOSONAR
            val arrivingAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                stopid = 0
            )
            val arrivedAction = Action(
                actionType = ActionTypes.ARRIVED.ordinal,
                stopid = 0
            )
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0))
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetails[0]
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.add(
                    arrivingAction
                )
                it.add(arrivedAction)
            }
            Assert.assertEquals(
                false, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    arrivingAction
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns true if triggered action is arriving and stop has arriving and departed action only`() =
        runTest(testDispatcher) {    //NOSONAR
            val arrivingAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                stopid = 0
            )
            val departAction = Action(
                actionType = ActionTypes.DEPARTED.ordinal,
                stopid = 0
            )
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0))
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetails[0]
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.add(
                    arrivingAction
                )
                it.add(departAction)
            }
            Assert.assertEquals(
                false, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    arrivingAction
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns true if triggered action is arriving and stop has all three actions`() =
        runTest(testDispatcher) {    //NOSONAR
            val arrivingAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                stopid = 0
            )
            val arrivedAction = Action(
                actionType = ActionTypes.ARRIVED.ordinal,
                stopid = 0
            )
            val departAction = Action(
                actionType = ActionTypes.DEPARTED.ordinal,
                stopid = 0
            )
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0))
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetails[0]
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.add(
                    arrivingAction
                )
                it.add(arrivedAction)
                it.add(departAction)
            }
            Assert.assertEquals(
                false, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    arrivingAction
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion returns false if the action id not matching with stop list in the data store`() =
        runTest(testDispatcher) {    //NOSONAR
            val arrivedAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                stopid = 1
            )
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0))
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetails[0]
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.add(
                    Action(
                        actionType = ActionTypes.ARRIVED.ordinal,
                        stopid = 1
                    )
                )
            }
            Assert.assertEquals(
                false, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    arrivedAction
                )
            )
        }


    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion fallback to datastore if the firestore cache returns empty data`() =
        runTest(testDispatcher) {    //NOSONAR
            val arrivedAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                stopid = 1
            )
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns  StopDetail()
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList()

            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 1))
            Assert.assertEquals(
                false, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    arrivedAction
                )
            )
        }

    @Test
    fun `verify canUpdateStopCompletionTimeBasedOnActionCompletion fallback to datastore and stop list is not matching with the incoming action`() =
        runTest(testDispatcher) {    //NOSONAR
            val arrivedAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                stopid = 1
            )
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns  StopDetail()
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList()

            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0))
            Assert.assertEquals(
                false, dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    arrivedAction
                )
            )
        }


    @Test
    fun `verify remaining uncompleted stops in stops service preference`() =
        runTest(testDispatcher) {    //NOSONAR
            val stopList = ArrayList<Stop>()
            stopList.add(Stop(stopId = 1))
            stopList.add(Stop(stopId = 2))
            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(StopDetail(stopid = 1))
            stopDetailList.add(StopDetail(stopid = 2))
            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )
            coEvery {
                dataStoreManager.setValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    any()
                )
            } just runs
            val updatedData =
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    dataStoreManager,
                    stopList.first()
                )
            Assert.assertEquals(2, updatedData.first().stopid)
        }

    @Test
    fun `check formList when cid and vid is empty`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            emptyList<DispatchFormPath>()
        )
        Assert.assertEquals(
            dispatchStopsUseCase.getDriverFormsToFill(
                dataStoreManager
            ), ArrayList<DispatchFormPath>()
        )
    }

    @Test
    fun `check formList when form stack key is empty`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "Swift"
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns ""
        Assert.assertEquals(
            dispatchStopsUseCase.getDriverFormsToFill(
                dataStoreManager
            ), ArrayList<DispatchFormPath>()
        )
    }

    @Test
    fun `check formList when form stack key is not empty`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "Swift"
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            listOf(DispatchFormPath("FORM_RESPONSE", 1, 2, 85212))
        )
        Assert.assertEquals(
            dispatchStopsUseCase.getDriverFormsToFill(
                dataStoreManager
            ).first().stopId,
            arrayListOf(DispatchFormPath("FORM_RESPONSE", 1, 2, 85212)).first().stopId
        )
    }

    @Test
    fun `verify no stops in stops service reference`() {
        runTest(testDispatcher) {
            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            assert(
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    dataStoreManager,
                    Stop(stopId = 1)
                ).isEmpty()
            )
        }
    }

    @Test
    fun `verify correct stop id is returned from stops service reference when stops are added in different order`() {
        runTest(testDispatcher) {
            coEvery {
                dataStoreManager.setValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    any()
                )
            } just runs
            val stopDetailList = listOf(StopDetail(stopid = 1), StopDetail(stopid = 4), StopDetail(stopid = 3))
            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )
            assert(
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    dataStoreManager,
                    Stop(stopId = 1)
                ).first().stopid == 3
            )
        }
    }

    @Test
    fun `verify empty list is returned from stops service reference when no stops are matching`() {
        runTest(testDispatcher) {
            coEvery {
                dataStoreManager.setValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    any()
                )
            } just runs
            val stopDetailList = ArrayList<StopDetail>()
            stopDetailList.add(StopDetail(stopid = 1))
            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns GsonBuilder().setPrettyPrinting()
                .create().toJson(
                    stopDetailList
                )
            assert(
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    dataStoreManager,
                    Stop(stopId = 1)
                ).isEmpty()
            )
        }
    }

    @Test
    fun `verify uncompleted form use case not called if the driver completed the form`() {
        runTest(testDispatcher) {
            mockkObject(UncompletedFormsUseCase)
            dispatchStopsUseCase.saveFormIdToDataStoreToAccountUncompletedForm(
                true, DispatchFormPath()
            )
            coVerify(exactly = 0) {
                formDataStoreManager.setValue(
                    any(),
                    any<String>()
                )
            }
            coVerify(exactly = 0) {
                formDataStoreManager.getValue(
                    any(),
                    any<String>()
                )
            }
            coVerify(exactly = 0) { UncompletedFormsUseCase.addFormToPreference(any(), any()) }
        }
    }

    @Test
    fun `action associated form stored into the datastore`() {
        runTest(testDispatcher) {
            mockkObject(UncompletedFormsUseCase)
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    EMPTY_STRING
                )
            } returns Gson().toJson(
                listOf(DispatchFormPath("FORM_RESPONSE", 1, 1, 85212))
            )
            coEvery {
                dataStoreManager.setValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    any()
                )
            } just runs

            dispatchStopsUseCase.saveFormIdToDataStoreToAccountUncompletedForm(
                false, DispatchFormPath()
            )
            coVerify {
                dataStoreManager.setValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    withArg { assertTrue { it.contains("85212") } })
            }
        }
    }

    @Test
    fun `no stops available to navigate in stop navigate eligibility`() {
        runTest(testDispatcher) {
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns listOf()
            coVerify(exactly = 0) {
                dataStoreManager.getValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                    mutableSetOf()
                )
            }
            coVerify(exactly = 0) {
                dataStoreManager.setValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY, any()
                )
            }
            coVerify(exactly = 0) {
                dispatchStopsUseCase.setStopsNavigateEligibility(-1, dataStoreManager)
            }
        }
    }

    @Test
    fun `stops available to navigate in stop navigate eligibility`() {
        runTest(testDispatcher) {
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0).apply {
                    Actions.apply {
                        add(
                            Action(
                                actionType = 0,
                                stopid = 1
                            )
                        )
                    }
                })
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns stopDetails
            coEvery {
                dataStoreManager.getValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                    mutableSetOf()
                )
            } returns linkedSetOf("0", "1", "2")

            coEvery {
                dataStoreManager.setValue(
                    NAVIGATION_ELIGIBLE_STOP_LIST_KEY,
                    any()
                )
            } just runs

            dispatchStopsUseCase.setStopsNavigateEligibility(1, dataStoreManager)
            coVerify {
                dispatchStopsUseCase.setSequencedStopsEligibility(withArg { assertTrue(it == 1) },
                    any(),
                    withArg { assertTrue(it.first() == "0") })
            }
            coVerify(exactly = 1) {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
            }
        }
    }

    @Test
    fun `stop depart action completion will not be considered if arrived action not completed`() {
        val stopActionEventData = StopActionEventData(
            stopId = 1, actionType = ActionTypes.DEPARTED.ordinal,
            context, hasDriverAcknowledgedArrivalOrManualArrival= true
        )
        val stopDetails =
            StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = ActionTypes.ARRIVED.ordinal,
                        stopid = 1
                    )
                )
            }
        assertEquals(
            true,
            dispatchStopsUseCase.doNotConsiderDepartEventTriggerIfArrivedActionNotCompleted(
                stopDetails,
                stopActionEventData
            )
        )

    }

    @Test
    fun `stop depart action completion is considered if arrived action not there or completed`() {
        val stopActionEventData = StopActionEventData(
            stopId = 1, actionType = ActionTypes.DEPARTED.ordinal,
            context, hasDriverAcknowledgedArrivalOrManualArrival= true
        )
        val stopDetails =
            StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = ActionTypes.ARRIVED.ordinal,
                        stopid = 1,
                        responseSent = true
                    )
                )
            }
        assertEquals(
            false,
            dispatchStopsUseCase.doNotConsiderDepartEventTriggerIfArrivedActionNotCompleted(
                stopDetails,
                stopActionEventData
            )
        )
    }

    @Test
    fun `action document path `() {
        runTest(testDispatcher) {
            val stop = Stop(stopId = 1)
            val action = Action(actionid = 1)
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY, EMPTY_STRING
                )
            } returns "1234"
            coEvery { appModuleCommunicator.doGetCid() } returns "10"
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "1234"
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns "ab"
            val actionPath = "Dispatches/10/ab/1234/Stops/1/Actions/1"
            assertEquals(dispatchStopsUseCase.buildActionDocumentPath(stop, action), actionPath)
        }
    }

    @Test
    fun `completed stop is removed from data store stop list`() {
        runTest(testDispatcher) {
            coEvery { dataStoreManager.removeItem(CURRENT_STOP_KEY) } returns mockk()
            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
                )
            } returns true
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "ActiveDispatch"
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns false

            val currentStopInDataStore =
                Stop(stopId = 0)

            dispatchStopsUseCase.removeCompletedStopAndSetTheCurrentStop(
                currentStopInDataStore,
                true
            )

            coVerify {
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    dataStoreManager,
                    withArg {
                        assertTrue(it.stopId == 0)
                    })
            }

            coVerify {
                dispatchStopsUseCase.setNextStopForTracking(
                    withArg { assertTrue(it.stopId == 0) },
                    dataStoreManager
                )
            }
        }
    }

    @Test
    fun `stop id 1 is set as current stop after stop 0 completion`() {
        runTest(testDispatcher) {
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 1).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 1
                        )
                    )
                })
            val currentStopInDatastore =
                Stop(stopId = 0)
            coEvery { dataStoreManager.setValue<Any>(any(), any()) } just runs
            coEvery { dataStoreManager.containsKey(CURRENT_STOP_KEY) } returns false
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    any(),
                    any()
                )
            } returns stopDetails
            dispatchStopsUseCase.setNextStopForTracking(currentStopInDatastore, dataStoreManager)
            coVerify {
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    withArg { assertTrue { it.stopid == 1} },
                    dataStoreManager
                )
            }
        }
    }

    @Test
    fun `verify expected stopDetail is passed as parameter when last stop is completed`() {
        runTest(testDispatcher) {
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 0).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 0
                        )
                    )
                })
            val currentStopInDatastore =
                Stop(stopId = 1)
            coEvery { dataStoreManager.setValue<Any>(any(), any()) } just runs
            coEvery { dataStoreManager.containsKey(CURRENT_STOP_KEY) } returns false
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    any(),
                    any()
                )
            } returns stopDetails
            dispatchStopsUseCase.setNextStopForTracking(currentStopInDatastore, dataStoreManager)
            coVerify(exactly = 0) {
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    any() ,
                    dataStoreManager
                )
            }
        }
    }

    @Test
    fun `verify expected stopDetail is passed as parameter when next stop completed before current stop`() {
        runTest(testDispatcher) {
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 2, completedTime = "Jan 01 01:02 AM").apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                }
            )
            val currentStopInDatastore =
                Stop(stopId = 1)
            coEvery { dataStoreManager.setValue<Any>(any(), any()) } just runs
            coEvery { dataStoreManager.containsKey(CURRENT_STOP_KEY) } returns false
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    any(),
                    any()
                )
            } returns stopDetails
            dispatchStopsUseCase.setNextStopForTracking(currentStopInDatastore, dataStoreManager)
            coVerify(exactly = 0) {
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    any() ,
                    dataStoreManager
                )
            }
        }
    }

    @Test
    fun `verify expected stopDetail is passed as parameter when last stop is deleted`() {
        runTest(testDispatcher) {
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 2, deleted = 1).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                }
            )
            val currentStopInDatastore =
                Stop(stopId = 1)
            coEvery { dataStoreManager.setValue<Any>(any(), any()) } just runs
            coEvery { dataStoreManager.containsKey(CURRENT_STOP_KEY) } returns false
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    any(),
                    any()
                )
            } returns stopDetails
            dispatchStopsUseCase.setNextStopForTracking(currentStopInDatastore, dataStoreManager)
            coVerify(exactly = 0) {
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    any() ,
                    dataStoreManager
                )
            }
        }
    }

    @Test
    fun `verify expected stopDetail is passed as parameter when some stops are not completed`() {
        runTest(testDispatcher) {
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 1).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 1
                        )
                    )
                }
            )
            stopDetails.add(
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                }
            )
            val currentStopInDatastore =
                Stop(stopId = 1)
            coEvery { dataStoreManager.setValue<Any>(any(), any()) } just runs
            coEvery { dataStoreManager.containsKey(CURRENT_STOP_KEY) } returns false
            coEvery {
                dataStoreManager.getValue(
                    CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(
                    any(),
                    any()
                )
            } returns stopDetails
            dispatchStopsUseCase.setNextStopForTracking(currentStopInDatastore, dataStoreManager)
            coVerify {
                dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                    withArg { assertTrue { it.stopid == 1 } },
                    dataStoreManager
                )
            }
        }
    }

    @Test
    fun `stop completion time returns empty if that stop can not be completed due to remaining uncompleted actions`() {
        runTest(testDispatcher) {
            coEvery {
                dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    any()
                )
            } returns false
            assert(
                dispatchStopsUseCase.updateStopCompletionTimeInFirestoreAndRemoveStopFromDataStore(
                    Action(), Stop
                        (), false
                ) == ""
            )
        }
    }

    @Test
    fun `stop completion time returns current time if that stop can be completed`() {
        runTest(testDispatcher) {
            coEvery {
                dataStoreManager.getValue(
                    STOPS_SERVICE_REFERENCE_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery {
                dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    dataStoreManager,
                    any()
                )
            } returns true
            coEvery {
                dispatchStopsUseCase.updateCompletionTimeInStopDocument(
                    any(),
                    any()
                )
            } returns "1653554468"
            assert(
                dispatchStopsUseCase.updateStopCompletionTimeInFirestoreAndRemoveStopFromDataStore(
                    Action(), Stop
                        (), false
                ) == "1653554468"
            )
        }
    }

    @Test
    fun `verify stop action completion data parameters`() {
        runTest {
            //for testing purpose the stop list are given as empty here
            mockActionCompletionEvents()
            val stops = mutableListOf<StopDetail>().also {
                it.add(
                    StopDetail(stopid = 0, sequenced = 1)
                )
                it.add(
                    StopDetail(stopid = 1, sequenced = 1)
                )
                it.add(
                    StopDetail(stopid = 2, sequenced = 1)
                )
            }
            val action = Action(actionid = 1, dispid = dispatchIDForStops)
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                reasonType = StopActionReasonTypes.AUTO.name,
                negativeGuf = false
            )
            coEvery {
                dispatchFirestoreRepo.getStop(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns StopDetail(stopid = 1, dispid = dispatchIDForStops)
            coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns getActions(isApproachRespSent = true, dispatchId = dispatchIDForStops)
            coEvery { context.startActivity(any()) } just runs
            coEvery {
                dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                    any(),
                    any(),
                    any()
                )
            } returns sequencedStopsWithActions
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stops
            coEvery {
                dispatchStopsUseCase.getActionDataFromStop(
                    any(),
                    any(),
                    any()
                )
            } returns action
            coEvery { sendDispatchDataUseCase.sendRemoveGeoFenceEvent(action) } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
            coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
            every { stopDetentionWarningUseCase.checkForDisplayingDetentionWarningAndStartDetentionWarningTimer(any()) } just runs
            every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(any(), any()) } just runs
            coEvery { backboneUseCase.fetchEngineMotion(any()) } returns false
            coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns arrayListOf()
            coEvery { dispatchStopsUseCase.restoreSelectedDispatch() } just runs
            coEvery { arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(), any()) } just runs
            dispatchStopsUseCase.performActionsAsDriverAcknowledgedArrivalOfStop(
                dispatchIDForStops,
                1,
                context,
                pfmEventsInfo, ""
            )

            coVerify {
                dispatchStopsUseCase.sendStopActionEvent(
                    withArg { assert(it == dispatchIDForStops) },
                    withArg {
                        assert(it.stopId == 1)
                        assert(pfmEventsInfo.reasonType == StopActionReasonTypes.AUTO.name)
                    },"", pfmEventsInfo)
            }

            coVerify {
                sendDispatchDataUseCase.sendRemoveGeoFenceEvent(action)
            }

            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.add(
                StopDetail(stopid = 1, name = "Stop1").apply {
                    Actions.apply {
                        add(
                            Action(
                                actionType = 1,
                                stopid = 1,
                                actionid = 1,
                                responseSent = false,
                                dispid = dispatchIDForStops
                            )
                        )
                        add(
                            Action(
                                actionType = 2,
                                stopid = 1,
                                actionid = 2,
                                dispid = dispatchIDForStops
                            )
                        )
                    }
                })
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns stopDetails
            dispatchStopsUseCase.performActionsAsDriverAcknowledgedArrivalOfStop(
                dispatchIDForStops,
                1,
                context,
                pfmEventsInfo,""
            )
            coVerify {
                dispatchStopsUseCase.sendStopActionEvent(
                    withArg { assert(it == dispatchIDForStops) },
                    withArg {
                        assert(it.stopId == 1)
                        assert(pfmEventsInfo.reasonType == StopActionReasonTypes.AUTO.name)
                    },"", pfmEventsInfo)
            }

            coVerify {
                dispatchStopsUseCase.doNotConsiderDepartEventTriggerIfArrivedActionNotCompleted( //TODO need to check this
                    withArg { assert(it.stopid == stopDetails.first().stopid) },
                    any()
                )
            }


            coVerify(exactly = 2) {
                dispatchStopsUseCase.getActionDataFromStop(
                    any(), any(), any()
                )
            }
            mockScope.advanceUntilIdle()
            coVerify {
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `stop has app added actions which did not came from dispatcher verify that action completion data`() {
        runTest(testDispatcher) {
            val dispatchId = "1"
            val stopId = 1
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                StopActionReasonTypes.AUTO.name
            )
            val stopActionEventData = StopActionEventData(
                stopId = stopId,
                actionType = ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            mockActionCompletionEvents()
            val stop = StopDetail(stopid = stopId, dispid = dispatchId)
            coEvery {
                dispatchFirestoreRepo.getStop(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns stop
            coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any()) } returns mutableListOf<Action>().also {
                it.add(Action(dispid = dispatchId, stopid = stopId))
            }
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf(stop)
            coEvery{ arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
            coEvery { arrivalReasonEventRepo.getCurrentStopArrivalReason(any()) } returns ArrivalReason()
            dispatchStopsUseCase.sendStopActionEvent(dispatchId, stopActionEventData,"", pfmEventsInfo)

            coVerify {
                dispatchStopsUseCase.handleArriveActionCompletion(
                    withArg { assert(it.stopId == stopActionEventData.stopId) },
                    withArg { assert(it.Actions.isNotEmpty()) }, any(), pfmEventsInfo)
            }
        }
    }

    @Test
    fun `check for depart ignoring event when arrival not marked for that stop`() {
        runTest(testDispatcher) {
            val dispatchId = "1"
            val stopId = 1
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(
                StopActionReasonTypes.AUTO.name
            )
            val stopActionEventData = StopActionEventData(
                stopId = stopId,
                actionType = ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            mockActionCompletionEvents()
            val stop = StopDetail(stopid = stopId, dispid = dispatchId)
            coEvery {
                dispatchFirestoreRepo.getStop(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns stop
            coEvery { dispatchStopsUseCase.doNotConsiderDepartEventTriggerIfArrivedActionNotCompleted(
                any(),
                any()
            ) } returns true
            coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0,0.0)
            coEvery { arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(),any()) } just runs
            coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any()) } returns mutableListOf<Action>().also {
                it.add(Action(dispid = dispatchId, stopid = stopId))
            }
            coEvery{ arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
            coEvery { arrivalReasonEventRepo.getCurrentStopArrivalReason(any()) } returns ArrivalReason()
            coEvery { arrivalReasonEventRepo.getArrivalReasonCollectionPath(any()) } returns EMPTY_STRING
            coEvery { arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(),any()) } just runs
            coEvery { dataStoreManager.getValue(IS_DYA_ALERT_ACTIVE, false) } returns true
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf(stop)
            coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns arrayListOf()
            dispatchStopsUseCase.sendStopActionEvent(dispatchId, stopActionEventData,"", pfmEventsInfo)

            coVerify {
                arrivalReasonUseCase.updateArrivalReasonForCurrentStop(any(),any())
            }
        }
    }

    // @Ignore
    @Test
    fun `stop has added actions which came from dispatcher verify that action completion data`() {
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val stops = mutableListOf<StopDetail>().also {
                it.add(
                    StopDetail(stopid = 0, sequenced = 1)
                )
                it.add(
                    StopDetail(stopid = 1, sequenced = 1)
                )
                it.add(
                    StopDetail(stopid = 2, sequenced = 1)
                )
            }
            val stopActionEventData = StopActionEventData(
                stopId = 1,
                actionType = ActionTypes.DEPARTED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            mockActionCompletionEvents()
            coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns getStopsList()

            coEvery {
                dispatchFirestoreRepo.getStop(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns StopDetail(stopid = 1, dispid = "1")
            coEvery {
                dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                    any(),
                    any(),
                    any()
                )
            } returns stops

            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(), any(), any())
            } returns getActions(isApproachRespSent = true, isArriveRespSent = true, dispatchId = dispatchIDForStops)
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
            coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
            dispatchStopsUseCase.sendStopActionEvent("1", stopActionEventData,"", pfmEventsInfo)
            coVerify(exactly = 0) {
                dispatchStopsUseCase.handleArriveActionCompletion(
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
            mockScope.advanceUntilIdle()
            coVerify {
                dispatchStopsUseCase.handleStopEvents(
                    action = withArg { assert(it.actionType == ActionTypes.DEPARTED.ordinal) },
                    stopActionEventData = withArg { assert(it.actionType == stopActionEventData.actionType) },
                    any(),
                    caller = "",
                    pfmEventsInfo = pfmEventsInfo
                )
            }
            coVerify(exactly = 1) {
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `verify handleStopEvents gets called when approach trigger came for stop with or without previous stop completion in sequential trip`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val stopActionEventData = StopActionEventData(
            stopId = 1,
            actionType = ActionTypes.APPROACHING.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= false
        )
        val stop = StopDetail(stopid = 1, dispid = dispatchIDForStops, sequenced = 1)
        stop.Actions.addAll(getActions(dispatchId = dispatchIDForStops, stopId = 1))
        coEvery {
            dispatchStopsUseCase.getStopAndActions(any(), any(), any())
        } returns stop
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns sequencedStopsWithActions

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(), any(), any())
        } returns getActions(dispatchId = dispatchIDForStops)
        mockActionCompletionEvents()
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.restoreSelectedDispatch() } just runs
        dispatchStopsUseCase.sendStopActionEvent("1", stopActionEventData,"", pfmEventsInfo)
        mockScope.advanceUntilIdle()
        coVerify(exactly = 1) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify handleStopEvents gets called when arrive trigger came for stop with previous stop completion in sequential trip`() = runTest {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val stopActionEventData = StopActionEventData(
            stopId = 1,
            actionType = ActionTypes.ARRIVED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= false
        )
        val stop = StopDetail(stopid = 1, dispid = dispatchIDForStops, sequenced = 1)
        stop.Actions.addAll(getActions(dispatchId = dispatchIDForStops, stopId = 1))
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        coEvery {
            dispatchStopsUseCase.getStopAndActions(any(), any(), any())
        } returns stop
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns sequencedStopsWithActions
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(), any(), any())
        } returns getActions(dispatchId = dispatchIDForStops, isArriveRespSent = true, isApproachRespSent = true, isDepartRespSent = true)
        mockActionCompletionEvents()
        coEvery { context.startActivity(any()) } just runs
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(any(), any()) } just runs
        coEvery { backboneUseCase.fetchEngineMotion(any()) } returns false
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns listOf()
        coEvery { dispatchStopsUseCase.restoreSelectedDispatch() } just runs
        coEvery{ arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
        coEvery { arrivalReasonEventRepo.getCurrentStopArrivalReason(any()) } returns ArrivalReason()
        coEvery{ arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
        coEvery { arrivalReasonEventRepo.getCurrentStopArrivalReason(any()) } returns ArrivalReason()
        dispatchStopsUseCase.sendStopActionEvent(dispatchIDForStops, stopActionEventData,"", pfmEventsInfo)

        coVerify(exactly = 1) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify handleStopEvents gets called when arrive trigger came for stop without previous stop completion in sequential trip`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val stopActionEventData = StopActionEventData(
            stopId = 1,
            actionType = ActionTypes.ARRIVED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= false
        )
        val stop = StopDetail(stopid = 1, dispid = dispatchIDForStops, sequenced = 1)
        stop.Actions.addAll(getActions(dispatchId = dispatchIDForStops, stopId = 1))
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        coEvery {
            dispatchStopsUseCase.getStopAndActions(any(), any(), any())
        } returns stop
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns sequencedStopsWithActions

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(), any(), any())
        } returns getActions(dispatchId = dispatchIDForStops)
        dispatchStopsUseCase.sendStopActionEvent(dispatchIDForStops, stopActionEventData,"", pfmEventsInfo)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify handleStopEvents gets called when depart trigger came for stop with or without previous stop completion in sequential trip`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        mockkObject(FormUtils)
        val stopActionEventData = StopActionEventData(
            stopId = 1,
            actionType = ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= false
        )
        val stop = StopDetail(stopid = 1, dispid = dispatchIDForStops, sequenced = 1)
        stop.Actions.addAll(getActions(dispatchId = dispatchIDForStops, stopId = 1, isArriveRespSent = true))
        coEvery {
            dispatchStopsUseCase.getStopAndActions(any(), any(), any())
        } returns stop
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns sequencedStopsWithActions

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(), any(), any())
        } returns getActions(dispatchId = dispatchIDForStops)
        mockActionCompletionEvents()
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any()) } returns ""
        dispatchStopsUseCase.sendStopActionEvent("1", stopActionEventData,"", pfmEventsInfo)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify handleStopEvents gets called when approach trigger came for stop without previous stop completion in free float trip`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0)
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 0)
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 0)
            )
        }
        val stopActionEventData = StopActionEventData(
            stopId = 1,
            actionType = ActionTypes.APPROACHING.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= true
        )
        val stop = StopDetail(stopid = 1, dispid = dispatchIDForStops, sequenced = 0)
        stop.Actions.addAll(getActions(dispatchId = dispatchIDForStops, stopId = 1))
        coEvery {
            dispatchStopsUseCase.getStopAndActions(any(), any(), any())
        } returns stop
        mockActionCompletionEvents()
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(), any(), any())
        } returns getActions(dispatchId = dispatchIDForStops)
        coEvery { dispatchStopsUseCase.restoreSelectedDispatch() } just runs
        dispatchStopsUseCase.sendStopActionEvent("1", stopActionEventData,"", pfmEventsInfo)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify handleStopEvents gets called when arrive trigger came for stop without previous stop completion in free float trip`() = runTest {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
        }
        val stopActionEventData = StopActionEventData(
            stopId = 1,
            actionType = ActionTypes.ARRIVED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= true
        )
        val stop = StopDetail(stopid = 1, dispid = dispatchIDForStops, sequenced = 0)
        stop.Actions.addAll(getActions(dispatchId = dispatchIDForStops, stopId = 1))
        coEvery {
            dispatchStopsUseCase.getStopAndActions(any(), any(), any())
        } returns stop
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        mockActionCompletionEvents()
        coEvery { context.startActivity(any()) } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(any(), any()) } just runs
        coEvery { backboneUseCase.fetchEngineMotion(any()) } returns false
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(), any(), any())
        } returns getActions(dispatchId = dispatchIDForStops)
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns listOf()
      //  coEvery { JsonDataConstructionUtils.getStopActionJson(any(), any(), any(), any()) } returns JsonData("data", "{\"actionId\":0,\"cid\":5097,\"createdDate\":\"2022-01-12T00:00:00.000Z\",\"dispId\":123434,\"dsn\":\"12345\",\"fuel\":34,\"lat\":12.5,\"lon\":30.0,\"mileType\":\"ecm\",\"negGuf\":\"false\",\"odom\":21404,\"odomType\":\"j1708\",\"quality\":\"good\",\"reason\":\"timeout\",\"stopId\":0,\"vid\":123456}")
        coEvery { dispatchStopsUseCase.restoreSelectedDispatch() } just runs
        dispatchStopsUseCase.sendStopActionEvent("1", stopActionEventData,"", pfmEventsInfo)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify handleStopEvents gets called when depart trigger came for stop without previous stop completion in free float trip`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0)
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 0)
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 0)
            )
        }
        val stopActionEventData = StopActionEventData(
            stopId = 1,
            actionType = ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= false
        )
        val stop = StopDetail(stopid = 1, dispid = dispatchIDForStops, sequenced = 0)
        stop.Actions.addAll(getActions(dispatchId = dispatchIDForStops, isArriveRespSent = true, stopId = 1))
        coEvery {
            dispatchStopsUseCase.getStopAndActions(any(), any(), any())
        } returns stop
        mockActionCompletionEvents()
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns EMPTY_STRING
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        coEvery { JsonDataConstructionUtils.getStopActionJson(any(), any(), any(), any(), any(), any(), any()) } returns JsonData("data", "{\"actionId\":0,\"cid\":5097,\"createdDate\":\"2022-01-12T00:00:00.000Z\",\"dispId\":123434,\"dsn\":\"12345\",\"fuel\":34,\"lat\":12.5,\"lon\":30.0,\"mileType\":\"ecm\",\"negGuf\":\"false\",\"odom\":21404,\"odomType\":\"j1708\",\"quality\":\"good\",\"reason\":\"timeout\",\"stopId\":0,\"vid\":123456}")
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(), any(), any())
        } returns getActions(dispatchId = dispatchIDForStops)
        coEvery { dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any()) } returns EMPTY_STRING
        dispatchStopsUseCase.sendStopActionEvent("1", stopActionEventData,"", pfmEventsInfo)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify isPreviousStopActionsAreCompleted return for approach action of both sequential and free float trip`() = runTest(testDispatcher) {
        val action = Action(actionType = ActionTypes.APPROACHING.ordinal, actionid = 0, stopid = 0)
        val expectedResult = Pair(true, EMPTY_STRING)
        val actualResult = dispatchStopsUseCase.isPreviousStopActionsComplete(action)
        assertEquals(expectedResult, actualResult)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.isValidStopForTripType(any())
        }
    }

    @Test
    fun `verify isPreviousStopActionsAreCompleted return for arrive action of a trip`() = runTest(testDispatcher) {
        val action = Action(actionType = ActionTypes.ARRIVED.ordinal, actionid = 1, stopid = 1)
        coEvery { dispatchStopsUseCase.isValidStopForTripType(any()) } returns Pair(true, EMPTY_STRING)
        val expectedResult = Pair(true, EMPTY_STRING)
        val actualResult = dispatchStopsUseCase.isPreviousStopActionsComplete(action)
        assertEquals(expectedResult, actualResult)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.isValidStopForTripType(any())
        }
    }

    @Test
    fun `verify isPreviousStopActionsAreCompleted return when previous stop action is not completed`() = runTest(testDispatcher) {
        val action = Action(actionType = ActionTypes.ARRIVED.ordinal, actionid = 1, stopid = 1)
        coEvery { dispatchStopsUseCase.isValidStopForTripType(any()) } returns Pair(false, "Previous stop actions are not completed")
        val expectedResult = Pair(false, "Previous stop actions are not completed")
        val actualResult = dispatchStopsUseCase.isPreviousStopActionsComplete(action)
        assertEquals(expectedResult, actualResult)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.isValidStopForTripType(any())
        }
    }
    @Test
    fun `verify isPreviousStopActionsAreCompleted return for depart action of both sequential and free float trip`() = runTest(testDispatcher) {
        val action = Action(actionType = ActionTypes.DEPARTED.ordinal, actionid = 2, stopid = 1)
        val expectedResult = Pair(true, EMPTY_STRING)
        val actualResult = dispatchStopsUseCase.isPreviousStopActionsComplete(action)
        assertEquals(expectedResult, actualResult)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.isValidStopForTripType(any())
        }
    }

    @Test
    fun `verify free float eligible stop ids`() {
        val stopList = mutableListOf<StopDetail>()
        stopList.add(
            StopDetail(stopid = 0).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 0
                    )
                )
            })
        stopList.add(
            StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            })
        stopList.add(
            StopDetail(stopid = 2, sequenced = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 2
                    )
                )
            })
        val uncompletedStopList = mutableListOf<StopDetail>()
        uncompletedStopList.add(
            StopDetail(stopid = 5).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 5
                    )
                )
            })
        val eligibleStopIdsForNavigation = linkedSetOf("3", "4")
        assert(
            dispatchStopsUseCase.getEligibleStopIdsForNextStop(
                0, stopList, uncompletedStopList,
                eligibleStopIdsForNavigation
            ).containsAll(setOf("3", "4", "5"))
        )
    }

    @Test
    fun `verify sequenced  stop ids eligible for navigation`() {
        val stopList = mutableListOf<StopDetail>()
        stopList.add(
            StopDetail(stopid = 0, sequenced = 1).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 0
                    )
                )
            })
        stopList.add(
            StopDetail(stopid = 1, sequenced = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            })
        val uncompletedStopList = mutableListOf<StopDetail>()
        uncompletedStopList.add(
            StopDetail(stopid = 2, sequenced = 1).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 2
                    )
                )
            })
        val eligibleStopIdsForNavigation = linkedSetOf("3", "4")
        assert(
            dispatchStopsUseCase.getEligibleStopIdsForNextStop(
                0, stopList, uncompletedStopList,
                eligibleStopIdsForNavigation
            ).containsAll(setOf("3", "4", "1"))
        )
    }

    @Test
    fun `stop information send as part of approach action response`() {
        runTest(testDispatcher) {
            val stopDetail = StopDetail(stopid = 0, name = "texas bulls")
            val action = Action(stopid = 0, actionType = ActionTypes.APPROACHING.ordinal)
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns EMPTY_STRING
            coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any()) } returns listOf()
            dispatchStopsUseCase.sendApproachActionResponse(stopDetail, action, pfmEventsInfo)

            coVerify {
                dispatchStopsUseCase.initStopDataAndUpdate(
                    any(),
                    withArg { assert(it.stopid == stopDetail.stopid) })
            }
            coVerify {
                dispatchStopsUseCase.sendActionResponse(
                    withArg { assert(it.stopId == stopDetail.stopid) },
                    withArg { assert(it.actionType == action.actionType) },
                    withArg { assert(it == pfmEventsInfo)}
                )
            }
        }
    }

    @Test
    fun `form Activity not opened ,no form Available in action`() {
        runTest(testDispatcher) {
            //Driver form id not valid/form not available
            val action = Action(actionid = 1, driverFormid = -1)
            val stop = StopDetail(stopid = 1)
            val stopActionEventData = StopActionEventData(
                stopId = 1,
                actionType = ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            coEvery {
                dispatchStopsUseCase.navigateToStopListActivityNoFormsFound(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.restoreSelectedDispatch() } just runs
            dispatchStopsUseCase.openFormActivityBasedOnFormAvailability(
                action,
                "10119",
                "TriFleet",
                stop,
                stopActionEventData
            )
            coVerify(exactly = 1) {
                dispatchStopsUseCase.navigateToStopListActivityNoFormsFound(
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }
    }

    @Test
    fun `form Activity opened action does have form id`() {
        runTest {
            //Driver form id not valid/form not available
            val driverFormIDAssociatedWithAction = 8767
            val action = Action(
                actionid = 1,
                driverFormid = driverFormIDAssociatedWithAction,
                actionType = 1
            )
            val stop = StopDetail(stopid = 1)
            val stopActionEventData = StopActionEventData(
                stopId = 1,
                actionType = ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            coEvery {
                dataStoreManager.setValue(
                    DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY,
                    any()
                )
            } just runs
            coEvery { context.startActivity(any()) } just runs
            coEvery {
                dispatchStopsUseCase.getFormSaveStatusFromFirestore(
                    any(),
                    any(),
                    any()
                )
            } returns false
            coEvery { featureGatekeeper.isFeatureTurnedOn(any(), any(), any()) } returns false
            dispatchStopsUseCase.openFormActivityBasedOnFormAvailability(
                action,
                "10119",
                "TriFleet",
                stop,
                stopActionEventData
            )
            coVerify {
                dispatchStopsUseCase.saveFormIdToDataStoreToAccountUncompletedForm(withArg {
                    assert(it.not())
                }, withArg { assert(it.actionId == action.actionid) })
            }
            coVerify {
                dispatchStopsUseCase.getArrivedActionFormDataToProceedToSave(withArg {
                    assert(
                        it.actionid == action.actionid
                    )
                }, any(), any(), withArg { assert(it.stopid == stop.stopid) }, any())
            }
        }
    }

    @Test
    fun `arrive action completion not triggered as incoming  action is approach`() {
        runTest(testDispatcher) {
            val stop = StopDetail(stopid = 1)
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val stopActionEventData = StopActionEventData(
                stopId = 1,
                actionType = ActionTypes.APPROACHING.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            mockDispatchKeysData()
            dispatchStopsUseCase.handleArriveActionCompletion(stopActionEventData, stop, caller = "", pfmEventsInfo)

            coVerify(exactly = 0) { dispatchStopsUseCase.handleStopEvents(any(), any(), any(), caller = "", pfmEventsInfo) }
        }
    }

    @Test
    fun `arrive action completion triggered as incoming  action is arrive`() {
        runTest(testDispatcher) {
            val stop = StopDetail(stopid = 1)
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val stopActionEventData = StopActionEventData(
                stopId = 1,
                actionType = ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            mockDispatchKeysData()

            dispatchStopsUseCase.handleArriveActionCompletion(stopActionEventData, stop, caller = "", pfmEventsInfo)
            coVerify(exactly = 1) { dispatchStopsUseCase.handleStopEvents(any(), any(), any(), caller = "", pfmEventsInfo) }
        }
    }

    @Test
    fun `navigate to stop list activity if action is arrive action and not an arrive action`() {
        runTest(testDispatcher) {
            val stopActionEventData = StopActionEventData(
                stopId = 1,
                actionType = ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val action = Action(actionType = ActionTypes.APPROACHING.ordinal)
            dispatchStopsUseCase.navigateToStopListActivityNoFormsFound(
                action,
                "",
                "TrimbleCar",
                stopActionEventData,
                UnconfinedTestDispatcher()
            )

            coVerify(exactly = 0) { stopActionEventData.context.startActivity(any()) }

            val arrivedAction = Action(actionType = ActionTypes.ARRIVED.ordinal)
            coEvery { stopActionEventData.context.startActivity(any()) } just runs
            coEvery { dispatchStopsUseCase.restoreSelectedDispatch() } just runs
            dispatchStopsUseCase.navigateToStopListActivityNoFormsFound(
                arrivedAction,
                "10119",
                "TrimbleFleet",
                stopActionEventData,
                UnconfinedTestDispatcher()
            )

            coVerify(exactly = 1) { stopActionEventData.context.startActivity(any()) }
        }
    }

    private fun mockDispatchKeysData() {
        coEvery { dataStoreManager.getValue(CURRENT_STOP_KEY, EMPTY_STRING) } returns EMPTY_STRING
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns listOf()
        coEvery {
            tripMobileOriginatedEventsRepo.setCompletedTimeForStop(
                any(),
                any(),
                any()
            )
        } just runs
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.COMPLETED_STOP_ID_SET_KEY,
                emptySet()
            )
        } returns mutableSetOf()
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.COMPLETED_STOP_ID_SET_KEY,
                any()
            )
        } just runs
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
            )
        } returns TRUE
        coEvery {
            dataStoreManager.getValue(
                STOPS_SERVICE_REFERENCE_KEY,
                EMPTY_STRING
            )
        } returns EMPTY_STRING
    }

    @Ignore
    @Test
    fun `driver arrived into current stop verify action completion sequence`() {
        runTest(testDispatcher) {
            val cid = "10119"
            val truckNum = "INST751"
            val arrivingAction = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                responseSent = false,
                stopid = 1,
                dispid = dispatchIDForStops
            )
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents("Manual")
            val sendStopActionEventData = StopActionEventData(
                stopId = 1,
                0,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            mockDispatchKeysData()
            mockActionCompletionEvents()
            val stop=StopDetail(stopid = 1)
            coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stop
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.add(
                    arrivingAction
                )
            }
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns getStopsList()
            coEvery {
                dataStoreManager.getValue(
                    ACTIVE_DISPATCH_KEY,
                    EMPTY_STRING
                )
            } returns "ActiveDispatch"
            coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
            coEvery { appModuleCommunicator.doGetCid() } returns cid
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns truckNum
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchIDForStops
            coEvery { dataStoreManager.getValue(CURRENT_STOP_KEY, EMPTY_STRING) } returns Gson().toJson(
                Stop(
                    stopId = 1
                )
            )
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns EMPTY_STRING
            coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
            coEvery { backboneUseCase.fetchEngineMotion(any()) } returns false
            coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns arrayListOf()
            dispatchStopsUseCase.handleStopEvents(
                arrivingAction,
                sendStopActionEventData,
                stop, caller = "",
                PFMEventsInfo.StopActionEvents("Manual"),
            )
            this.advanceUntilIdle()
            mockScope.advanceUntilIdle()
            coVerify {
                    dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                        withArg {
                            assert(
                                it.stopId == 1
                            )
                        },
                        withArg { assert(it.stopid == 1) },
                        pfmEventsInfo
                    )

                    dispatchStopsUseCase.sendActionResponse(
                        withArg { assert(it.stopId == stop.stopid) },
                        withArg { assert(it.actionType == arrivingAction.actionType) },
                        withArg { assert(pfmEventsInfo.reasonType == "Manual") }
                    )
            }
            val pathToSave =
                "$DISPATCH_COLLECTION/$cid/$truckNum/$dispatchIDForStops/$STOPS/${stop.stopid}/$ACTIONS/${arrivingAction.actionid}"

            coVerify {
                tripMobileOriginatedEventsRepo.updateActionPayload(
                    withArg { assert(it == pathToSave) },
                    withArg { assert(it.actionid == 0) })
            }
            coVerifyAll {
                dispatchFirestoreRepo.getAppModuleCommunicator()
                dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
                dispatchStopsUseCase.recordStopEventsInFirebaseMetrics(any(), any())
                dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any())
                dispatchStopsUseCase.sendActionResponse(any(), any(), any())
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any())
                dispatchStopsUseCase.buildActionDocumentPath(any(), any())
                arrivalReasonEventRepo.getCurrentStopArrivalReason(any())
                tripMobileOriginatedEventsRepo.saveStopActionResponse(any(), any(), any(), any())
                tripMobileOriginatedEventsRepo.updateActionPayload(
                    any(),
                    any()
                )
                dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                    any(),
                    any()
                )
                dispatchStopsUseCase.getStopAndActions(any(), any(), any())
                repeat(2) {
                    dispatchStopsUseCase.appModuleCommunicator
                }
                dispatchFirestoreRepo.getStop(any(), any(), any(), any())
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any())
                dispatchStopsUseCase.updateCompletionTimeInStopDocument(any(), any())
                tripMobileOriginatedEventsRepo.setCompletedTimeForStop(any(), any(), any())
                dispatchStopsUseCase.removeCompletedStopAndSetTheCurrentStop(any(), any())
                dispatchStopsUseCase.setNextStopForTracking(any(), any())
                dispatchStopsUseCase.removeCompletedStopFromStopsServicePreference(any(), any())
                arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
                dispatchStopsUseCase.getStopsFromFirestoreCacheFirst(any(), any(), any(), any())
                dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
                arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
                dispatchStopsUseCase.getStopsFromFirestoreCacheFirst(any(), any(), any(), any())
                dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
            }
        }
    }


    @Test
    fun `driver arrived into stop that is not current stop verify action completion sequence`() = runTest(testDispatcher) {
            val action = Action(
                actionType = ActionTypes.APPROACHING.ordinal,
                responseSent = false,
                stopid = 1,
                dispid = dispatchIDForStops
            )
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents("Manual")
            val sendStopActionEventData = StopActionEventData(
                stopId = 1,
                0,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val stop = StopDetail(stopid = 1, dispid = dispatchIDForStops)
            coEvery {
                dispatchFirestoreRepo.getStop(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns stop
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                    any(),
                    any(), any()
                )
            } returns ArrayList<Action>().also {
                it.clear()
                it.add(
                    action
                )
            }
            coEvery { dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(any(),any(),any()) } returns getStopsList()
            coEvery{ dispatchStopsUseCase.getStopAndActions(any(), any(), any()) } returns stop
            coEvery { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
            coEvery { dispatchStopsUseCase.updateStopCompletionTimeInFirestoreAndRemoveStopFromDataStore(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { dispatchStopsUseCase.canUpdateStopCompletionTimeBasedOnActionCompletion(
                any(),
                any()
            ) } returns true
            mockDispatchKeysData()
            mockActionCompletionEvents()
            dispatchStopsUseCase.handleStopEvents(
                action,
                sendStopActionEventData,
                stop, caller = "",
                pfmEventsInfo
            )
            mockScope.advanceUntilIdle()
            coVerify {
                ///dispatchFirestoreRepo.getAppModuleCommunicator()
                dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
                dispatchStopsUseCase.recordStopEventsInFirebaseMetrics(any(), any())
                dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any())
                dispatchStopsUseCase.getStopAndActions(any(), any(), any())
                fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any())
                dispatchStopsUseCase.initStopDataAndUpdate(any(), any())
                dispatchStopsUseCase.sendActionResponse(any(), any(), any())
                dispatchStopsUseCase.buildActionDocumentPath(any(), any())
                dispatchStopsUseCase.updateStopCompletionTimeInFirestoreAndRemoveStopFromDataStore(
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `validate Mixed Trip is retrieved from getTripType function`() = runTest(testDispatcher) {    //NOSONAR
        val stops = getMixedStopList(1)
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns stops
        assertEquals(dispatchStopsUseCase.getTripType(stops), TripTypes.MIXED.name)
    }

    @Test
    fun `validate Sequential Trip is retrieved from getTripType function`() = runTest(testDispatcher) {    //NOSONAR
        val stops = getSequentialStopList(1)
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns stops
        assertEquals(dispatchStopsUseCase.getTripType(stops), TripTypes.SEQUENTIAL.name)
    }

    @Test
    fun `validate Free Floating Trip is retrieved from getTripType function`() =
        runTest(testDispatcher) {
            val stops = getStopsList()//NOSONAR
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("")
            } returns stops
            assertEquals(dispatchStopsUseCase.getTripType(stops), TripTypes.FREE_FLOATING.name)
        }

    @Test
    fun `validate Stop For Free Floating Trip`() = runTest(testDispatcher) {    //NOSONAR
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
        }
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        val expectedResult = Pair(true, EMPTY_STRING)
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)

        assertEquals(expectedResult, actualResult)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(1)

        assertEquals(expectedResult, actualResult)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(2)

        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `validate Stop For Sequential Trip`() = runTest(testDispatcher) {    //NOSONAR
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns sequencedStopsWithActions
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isDepartRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(1)

        assertEquals(Pair(false, "Sequential Trip, Previous Seq Stop:0 Actions are not completed"), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isArriveRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(2)
        assertEquals(Pair(false, "Sequential Trip, Previous Seq Stop:1 Actions are not completed"), actualResult)
    }

    @Test
    fun `validate Stop For Sequential Trip - 2nd and 3rd stop does not have arrive action`() = runTest(testDispatcher) {    //NOSONAR
        val sequencedStopsWithActions = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 1).apply {
                    addActions(
                        ActionTypes.APPROACHING to true,
                        ActionTypes.ARRIVED to true
                    )
                }
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 1).apply {
                    addActions(
                        ActionTypes.APPROACHING to true,
                        ActionTypes.DEPARTED to true
                    )
                }
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 1).apply {
                    addActions(
                        ActionTypes.APPROACHING to true,
                        ActionTypes.DEPARTED to true
                    )
                }
            )
        }
        coEvery {dispatchFirestoreRepo.getActionsOfStop(any(), any(), any())} returns actionsListWithAllActions
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns sequencedStopsWithActions
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isDepartRespSent = true)
        coEvery {dispatchFirestoreRepo.getActionsOfStop(any(), any(), any())} returns actionsListNoArriveAction
        actualResult = dispatchStopsUseCase.isValidStopForTripType(1)

        assertEquals(Pair(false, "Arrive Triggered for A Stop With No Arrive Action"), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isArriveRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(2)
        assertEquals(Pair(false, "Arrive Triggered for A Stop With No Arrive Action"), actualResult)
    }

    @Test
    fun `validate Stop For Sequential Trip where 1st stop is complete`() = runTest(testDispatcher) {    //NOSONAR
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns sequencedStopsWithActions
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isArriveRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(1)
 
        assertEquals(Pair(false, "Sequential Trip, Previous Seq Stop:0 Actions are not completed"), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isArriveRespSent = true, isDepartRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(2)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
    }

    @Test
    fun `validate Stop For Mixed Trip`() = runTest(testDispatcher) {   //NOSONAR
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 1).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 3, sequenced = 1).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 4, sequenced = 1).apply {addActions(ActionTypes.ARRIVED to true)}
            )
        }
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(1)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(2)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions()
        actualResult = dispatchStopsUseCase.isValidStopForTripType(3)

        assertEquals(Pair(false, "Mixed Trip Previous Seq Stop:1 Actions are not completed"), actualResult)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(4)

        assertEquals(Pair(false, "Mixed Trip Previous Seq Stop:3 Actions are not completed"), actualResult)
    }

    @Test
    fun `validate Stop For Mixed Trip - 1st and 2nd stop not has arrive action`() = runTest(testDispatcher) {   //NOSONAR
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0)
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 1)
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 0)
            )
            it.add(
                StopDetail(stopid = 3, sequenced = 1)
            )
            it.add(
                StopDetail(stopid = 4, sequenced = 1)
            )
        }
        coEvery {dispatchFirestoreRepo.getActionsOfStop(any(), any(), any())} returns actionsListNoArriveAction
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)

        assertEquals(Pair(false, "Arrive Triggered for A Stop With No Arrive Action"), actualResult)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(1)

        assertEquals(Pair(false, "Arrive Triggered for A Stop With No Arrive Action"), actualResult)
        coEvery {dispatchFirestoreRepo.getActionsOfStop(any(), any(), any())} returns actionsListWithAllActions

        actualResult = dispatchStopsUseCase.isValidStopForTripType(2)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions()
        actualResult = dispatchStopsUseCase.isValidStopForTripType(3)

        assertEquals(Pair(false, "Mixed Trip Previous Seq Stop:1 Actions are not completed"), actualResult)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(4)

        assertEquals(Pair(false, "Mixed Trip Previous Seq Stop:3 Actions are not completed"), actualResult)
    }

    @Test
    fun `validate Stop For Mixed Trip where 1st FF and Seq stop is complete`() = runTest(testDispatcher) {    //NOSONAR
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0, completedTime = "01:02:02PM").apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 1, completedTime = "01:02:02PM", departedTime = "01:02:02PM").apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 0).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 3, sequenced = 1, completedTime = "01:02:02PM").apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 4, sequenced = 1).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 5, sequenced = 1).apply {addActions(ActionTypes.ARRIVED to true)}
            )
        }
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isArriveRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(1)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(2)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isArriveRespSent = true, isDepartRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(3)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true, isArriveRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(4)

        assertEquals(Pair(false, "Mixed Trip Previous Seq Stop:3 Actions are not completed"), actualResult)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns getActions(isApproachRespSent = true)
        actualResult = dispatchStopsUseCase.isValidStopForTripType(5)

        assertEquals(Pair(false, "Mixed Trip Previous Seq Stop:4 Actions are not completed"), actualResult)
    }

    @Test
    fun `validate isValidStopForTripType for current stop removal in Sequential Trip`() = runTest(testDispatcher) {    //NOSONAR
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 1, deleted = 1)
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 1)
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 1)
            )
        }
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)
        assertEquals(Pair(false, "Stop is deleted and not found in the stop list"), actualResult)
    }

    @Test
    fun `validate isValidStopForTripType for random stop removal in Sequential Trip`() = runTest(testDispatcher) {    //NOSONAR
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 1).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 1, deleted = 1).apply {addActions(ActionTypes.ARRIVED to true)}
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 1).apply {addActions(ActionTypes.ARRIVED to true)}
            )
        }
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)

        assertEquals(Pair(true, EMPTY_STRING), actualResult)
    }

    @Test
    fun `validate isValidStopForTripType for current stop removal in FreeFloat Trip`() = runTest(testDispatcher) {    //NOSONAR
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0, deleted = 1)
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 0)
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 0)
            )
        }
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)
        assertEquals(Pair(false, "Stop is deleted and not found in the stop list"), actualResult)
    }

    @Test
    fun `validate isValidStopForTripType for current stop removal in Mixed Trip`() = runTest(testDispatcher) {    //NOSONAR
        val stops = mutableListOf<StopDetail>().also {
            it.add(
                StopDetail(stopid = 0, sequenced = 0, deleted = 1)
            )
            it.add(
                StopDetail(stopid = 1, sequenced = 1)
            )
            it.add(
                StopDetail(stopid = 2, sequenced = 1)
            )
        }
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns stops
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(0)
        assertEquals(Pair(false, "Stop is deleted and not found in the stop list"), actualResult)
    }

    @Test
    fun `validate isValidStopForTripType when trigger came for the stop where previous stop actions are empty` () = runTest(testDispatcher) {
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs
        coEvery {
            dispatchFirestoreRepo.getAllStopsForDispatchIncludingDeletedStops(
                any(),
                any(),
                any()
            )
        } returns sequencedStopsWithActions
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(),
                any()
            )
        } returns listOf()
        var actualResult = dispatchStopsUseCase.isValidStopForTripType(2)

        assertEquals(Pair(false, "Previous Seq Stop Actions are empty"), actualResult)
    }

    private fun getActions(
        isApproachRespSent: Boolean = false,
        isArriveRespSent: Boolean = false,
        isDepartRespSent: Boolean = false,
        dispatchId: String = EMPTY_STRING,
        stopId : Int = 0
    ) = mutableListOf<Action>().also {
            it.add(
                Action(actionType = 0, responseSent = isApproachRespSent, dispid = dispatchId, stopid = stopId)
            )
            it.add(
                Action(actionType = 1, responseSent = isArriveRespSent, dispid = dispatchId, stopid = stopId)
            )
            it.add(
                Action(actionType = 2, responseSent = isDepartRespSent, dispid = dispatchId, stopid = stopId)
            )
        }

    private fun getMixedStopList(completedStops: Int) = mutableListOf<StopDetail>().apply {
        this.add(StopDetail(stopid = 0, sequenced = 1))
        this.add(StopDetail(stopid = 1, sequenced = 0))
        this.add(StopDetail(stopid = 2, sequenced = 0))
        this.add(StopDetail(stopid = 3, sequenced = 0))
        this.add(StopDetail(stopid = 4, sequenced = 1))
        this.forEachIndexed { i, it -> it.completedTime = (if (i < completedStops) "yes" else "") }
    }

    private fun getSequentialStopList(completedStops: Int) = mutableListOf<StopDetail>().apply {
        this.add(StopDetail(stopid = 0, sequenced = 1))
        this.add(StopDetail(stopid = 1, sequenced = 1))
        this.add(StopDetail(stopid = 2, sequenced = 1))
        this.forEachIndexed { i, it -> it.completedTime = (if (i < completedStops) "yes" else "") }
    }

    private fun mockActionCompletionEvents() {
        coEvery {
            dataStoreManager.getValue(
                CURRENT_STOP_KEY,
                EMPTY_STRING
            )
        } returns EMPTY_STRING  //set current stop to null.
        coEvery { dataStoreManager.getValue(DataStoreManager.VID_KEY, 0L) } returns 0L
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery {
            tripMobileOriginatedEventsRepo.saveStopActionResponse(
                any(),
                any(),
                any(),
                any(),
            )
        } just runs
        coEvery{
            arrivalReasonEventRepo.getCurrentStopArrivalReason(any())
        } returns ArrivalReason()
        coEvery { tripMobileOriginatedEventsRepo.updateActionPayload(any(), any()) } just runs
        coEvery { dataStoreManager.removeItem(CURRENT_STOP_KEY) } returns mockk()
        coEvery { dataStoreManager.getValue(DataStoreManager.VID_KEY, 0L) } returns 0L
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true

        coEvery {
            dataStoreManager.getValue(
                CURRENT_STOP_KEY,
                EMPTY_STRING
            )
        } returns EMPTY_STRING
        coEvery {
            tripMobileOriginatedEventsRepo.setCompletedTimeForStop(
                any(),
                any(),
                any()
            )
        } just runs
        coEvery { tripMobileOriginatedEventsRepo.setDepartedTimeForStop(any(), any(), any()) } just runs
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.COMPLETED_STOP_ID_SET_KEY,
                emptySet()
            )
        } returns mutableSetOf()
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.COMPLETED_STOP_ID_SET_KEY,
                any()
            )
        } just runs
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE
            )
        } returns TRUE
        coEvery {
            dataStoreManager.getValue(
                STOPS_SERVICE_REFERENCE_KEY,
                EMPTY_STRING
            )
        } returns EMPTY_STRING
        coEvery {
            dataStoreManager.setValue(
                STOPS_SERVICE_REFERENCE_KEY,
                any()
            )
        } just runs
    }

    @Test
    fun testSetFirstStopAsCurrentStopIfFreefloatTrip() = runTest(testDispatcher) {
        val stops = CopyOnWriteArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, sequenced = 1))
            it.add(StopDetail(stopid = 1, sequenced = 0))
            it.add(StopDetail(stopid = 2, sequenced = 1))
        }
        coEvery {
            dataStoreManager.containsKey(CURRENT_STOP_KEY)
        } returns false
        assertFalse(
            dispatchStopsUseCase.shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(
                dataStoreManager, stops
            )
        )
    }

    @Test
    fun testSetFirstStopAsCurrentStopIfSequentialTrip() = runTest(testDispatcher) {
        val stops = CopyOnWriteArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, sequenced = 1))
            it.add(StopDetail(stopid = 1, sequenced = 1))
            it.add(StopDetail(stopid = 2, sequenced = 1))
        }
        coEvery {
            dataStoreManager.containsKey(CURRENT_STOP_KEY)
        } returns false
        assertTrue(
            dispatchStopsUseCase.shouldSetFirstUncompletedStopAsCurrentStopIfSequentialTrip(
                dataStoreManager, stops
            )
        )
    }

    @Test
    fun testGetDistanceInFeet() = runTest(testDispatcher) {
        try {
            val stop = Stop(stopId = 0, latitude = 12.0, longitude = 30.0)
            assertEquals(182423.99999999997, dispatchStopsUseCase.getDistanceInFeet(stop))
        } catch(e: Exception){
            println("Caught Exception $e")
        }
    }

    @Test
    fun testGetDistanceInFeetForExactPosition() = runTest(testDispatcher) {
        val stop = Stop(stopId = 0, latitude = 12.0, longitude = 30.0)
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(12.0, 30.0)
        assertEquals(0.0, dispatchStopsUseCase.getDistanceInFeet(stop))
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for only approach action in a stop`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for unmark call`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal, responseSent = true))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for only approach action with invalid arrive action completion`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for only arrive action in a stop`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.ARRIVED.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for only arrive action with invalid approach action completion`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.ARRIVED.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for only depart action in a stop`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.DEPARTED.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for only depart action with invalid arrive action completion`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.DEPARTED.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for approach and arrive action in a stop`() = runTest(testDispatcher) {
        var stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal))
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for arrive and depart action in a stop`() = runTest(testDispatcher) {
        var stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.ARRIVED.ordinal))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.DEPARTED.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.ARRIVED.ordinal, responseSent = true))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.DEPARTED.ordinal))
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
        coVerify(exactly = 1) {
            fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for approach and depart action in a stop`() = runTest(testDispatcher) {
        var stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.DEPARTED.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.DEPARTED.ordinal))
        }
        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `verify unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion for approach, arrive and depart action in a stop`() = runTest(testDispatcher) {
        var stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal))
            it.Actions.add(Action(actionid = 2, actionType = ActionTypes.DEPARTED.ordinal))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("test") } returns listOf(stop)
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal))
            it.Actions.add(Action(actionid = 2, actionType = ActionTypes.DEPARTED.ordinal))
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal))
            it.Actions.add(Action(actionid = 2, actionType = ActionTypes.DEPARTED.ordinal))
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.APPROACHING.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal))
            it.Actions.add(Action(actionid = 2, actionType = ActionTypes.DEPARTED.ordinal))
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.ARRIVED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        stop = StopDetail(stopid = 0).also {
            it.Actions.add(Action(actionid = 0, actionType = ActionTypes.APPROACHING.ordinal, responseSent = true))
            it.Actions.add(Action(actionid = 1, actionType = ActionTypes.ARRIVED.ordinal, responseSent = true))
            it.Actions.add(Action(actionid = 2, actionType = ActionTypes.DEPARTED.ordinal))
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.AUTO)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }

        dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion("test", stop, ActionTypes.DEPARTED.ordinal,
            stopActionReasonTypes = StopActionReasonTypes.MANUAL)
        coVerify {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculation(any())
        }
    }

    @Test
    fun `check recordTimeDifferenceBetweenArriveAndDepartEvent gets called`() = runTest(testDispatcher) {
        val stop = Stop(stopId = 0, arrivedResponseSent = true)
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents("Auto")
        val action = Action(actionType = ActionTypes.DEPARTED.ordinal, dispid = "1234")

        coEvery {
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(
                any(),
                any(), any()
            )
        } returns ArrayList<Action>().also {
            it.add(
                action
            )
        }
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getStopData(any())
        } returns StopDetail()

        coEvery {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
        } just runs

        coEvery { dispatchStopsUseCase.sendStopActionEvent(any(), any(), any(), any()) } just runs

        coEvery {
            tripMobileOriginatedEventsRepo.saveStopActionResponse(
                any(),
                any(),
                any(),
                any(),
            )
        } just runs
        coEvery {
            arrivalReasonEventRepo.getCurrentStopArrivalReason(
                any()
            )
        } returns ArrivalReason()

        coEvery { tripMobileOriginatedEventsRepo.updateActionPayload(any(), any()) } just runs

        coEvery {
            dispatchFirestoreRepo.getStop(
                any(),
                any(),
                any(),
                any()
            )
        } returns StopDetail()

        coEvery { tripMobileOriginatedEventsRepo.setDepartedTimeForStop(any(), any(), any()) } just runs

        dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(stop, action, pfmEventsInfo)

        coVerify(exactly = 1) {
            dispatchStopsUseCase.recordTimeDifferenceBetweenArriveAndDepartEvent()
        }

    }

    @Test
    fun `check recordStopEvent gets called when Auto depart event arrives as auto`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 0,
            ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = true
        )
        val action = Action(actionType = ActionTypes.DEPARTED.ordinal)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            AUTO_DEPARTED) } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns EMPTY_STRING
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
        mockScope.advanceUntilIdle()
        coVerify(exactly = 1) {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(AUTO_DEPARTED)
        }
    }

    @Test
    fun `check recordStopEvent gets called when depart event arrives as manual`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 0,
            ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = true
        )
        val action = Action(actionType = ActionTypes.DEPARTED.ordinal)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            MANUAL_DEPARTED) } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns EMPTY_STRING
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
        mockScope.advanceUntilIdle()
        coVerify(exactly = 1) {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                MANUAL_DEPARTED)
        }
    }

    @Test
    fun `check recordStopEvent gets called when depart event arrives as normal`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.NORMAL.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 0,
            ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = true
        )
        val action = Action(actionType = ActionTypes.DEPARTED.ordinal)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            AUTO_DEPARTED) } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns EMPTY_STRING
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
        mockScope.advanceUntilIdle()
        coVerify(exactly = 1) {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(AUTO_DEPARTED)
        }
    }



    @Test
    fun `check recordStopEvent gets called when arrive event arrives as manual`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 0,
            ActionTypes.ARRIVED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = true
        )
        val action = Action(actionType = ActionTypes.ARRIVED.ordinal)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            MANUAL_ARRIVED) } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns EMPTY_STRING
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
            any(),
            any()
        ) } just runs
        dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
        mockScope.advanceUntilIdle()
        coVerify(exactly = 1) {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
            dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(),
                any())
        }
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                MANUAL_ARRIVED)
        }
    }

    @Test
    fun `check recordStopEvent gets called when arrive event arrives as auto`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 0,
            ActionTypes.ARRIVED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = true
        )
        val action = Action(actionType = ActionTypes.ARRIVED.ordinal)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            AUTO_ARRIVED) } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns EMPTY_STRING
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
            any(),
            any()
        ) } just runs
        dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
        mockScope.advanceUntilIdle()
        coVerify(exactly = 1) {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
            dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
        }
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                AUTO_ARRIVED)
        }
    }

    @Test
    fun `check recordStopEvent gets called when arrive event arrives as normal`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.NORMAL.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 0,
            ActionTypes.ARRIVED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = true
        )
        val action = Action(actionType = ActionTypes.ARRIVED.ordinal)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            AUTO_ARRIVED) } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns EMPTY_STRING
        every { deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
            any(), any()
        ) } just runs
        dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
        mockScope.advanceUntilIdle()
        coVerify(exactly = 1) {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
            dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
        }
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                AUTO_ARRIVED)
        }
    }

    @Test
    fun `check recordStopEventsInFirebaseMetrics when the action is arrival`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.NORMAL.name)
        val action = Action(actionType = ActionTypes.ARRIVED.ordinal)
        dispatchStopsUseCase.recordStopEventsInFirebaseMetrics(action, pfmEventsStopActionInfo)
        verify(exactly = 1) {
            dispatchStopsUseCase.recordArriveEventInFirebaseMetrics(any())
        }
    }

    @Test
    fun `check recordStopEventsInFirebaseMetrics when the action is depart`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.NORMAL.name)
        val action = Action(actionType = ActionTypes.DEPARTED.ordinal)
        dispatchStopsUseCase.recordStopEventsInFirebaseMetrics(action,pfmEventsStopActionInfo)
        verify(exactly = 1) {
            dispatchStopsUseCase.recordDepartEventInFirebaseMetrics(any())
        }
    }

    @Test
    fun `check recordStopEventsInFirebaseMetrics when the action is approach`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.NORMAL.name)
        val action = Action(actionType = ActionTypes.APPROACHING.ordinal)
        dispatchStopsUseCase.recordStopEventsInFirebaseMetrics(action,pfmEventsStopActionInfo)
        verify(exactly = 0) {
            dispatchStopsUseCase.recordArriveEventInFirebaseMetrics(any())
            dispatchStopsUseCase.recordDepartEventInFirebaseMetrics(any())
        }
    }

    @Test
    fun `check recordArriveEvent when the reason is manual`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            MANUAL_ARRIVED) } just runs
        dispatchStopsUseCase.recordArriveEventInFirebaseMetrics(pfmEventsStopActionInfo)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                MANUAL_ARRIVED)
        }
    }

    @Test
    fun `check recordArriveEvent when the reason is normal`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.NORMAL.name)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            AUTO_ARRIVED) } just runs
        dispatchStopsUseCase.recordArriveEventInFirebaseMetrics(pfmEventsStopActionInfo)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                AUTO_ARRIVED)
        }
    }

    @Test
    fun `check recordArriveEvent when the reason is auto`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            AUTO_ARRIVED) } just runs
        dispatchStopsUseCase.recordArriveEventInFirebaseMetrics(pfmEventsStopActionInfo)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                AUTO_ARRIVED)
        }
    }

    @Test
    fun `check recordDepartEvent when the reason is manual`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            MANUAL_DEPARTED) } just runs
        dispatchStopsUseCase.recordDepartEventInFirebaseMetrics(pfmEventsStopActionInfo)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                MANUAL_DEPARTED)
        }
    }

    @Test
    fun `check recordDepartEvent when the reason is normal`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.NORMAL.name)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            AUTO_DEPARTED) } just runs
        dispatchStopsUseCase.recordDepartEventInFirebaseMetrics(pfmEventsStopActionInfo)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                AUTO_DEPARTED)
        }
    }

    @Test
    fun `check recordDepartEvent when the reason is auto`() {
        val pfmEventsStopActionInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
            AUTO_DEPARTED) } just runs
        dispatchStopsUseCase.recordDepartEventInFirebaseMetrics(pfmEventsStopActionInfo)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(
                AUTO_DEPARTED)
        }
    }

    // @Ignore
    @Test
    fun `check deeplink configuration check method gets called when action is approach and response is not sent`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.APPROACHING.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival = true
            )
            val action = Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = false)
            every {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(),
                    any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
            dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
            mockScope.advanceUntilIdle()
            coVerify(exactly = 1) {
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
                dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any())
            }
            verify(exactly = 0) {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    context,
                    EMPTY_STRING
                )
            }
        }

    // @Ignore
    @Test
    fun `check deeplink configuration check method gets called when action is approach and response is sent`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.APPROACHING.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val action = Action(actionType = ActionTypes.APPROACHING.ordinal, responseSent = true)
            every {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(),
                    any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { tripMobileOriginatedEventsRepo.setCompletedTimeForStop(any(), any(), any()) } just runs
            dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
            verify(exactly = 0) {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    context,
                    EMPTY_STRING
                )
            }
        }

    // @Ignore
    @Test
    fun `check deeplink configuration check method gets called when action is auto arrived and response is not sent`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val action = Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = false)
            every {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(),
                    any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
            coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
            coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
                any(),
                any()
            ) } just runs

            dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
            mockScope.advanceUntilIdle()
            coVerify(exactly = 1) {
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
            }
            verify(exactly = 1) {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(), any()
                )
            }
            coVerify(exactly = 1) {
                dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
                    any(),
                    any()
                )
            }
        }

    // @Ignore
    @Test
    fun `check deeplink configuration check method gets called when action is manually arrived and response is not sent`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val action = Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = false)
            every {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(), any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
            coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
                any(), any()
            ) } just runs
            dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
            advanceUntilIdle()
            coVerify(exactly = 1) {

                dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
                dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any())
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())

            }
            verify(exactly = 1) {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                   any(), any()
                )
            }
        }



    // @Ignore
    @Test
    fun `check deeplink configuration check method gets called when action is auto arrived and response is sent`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val action = Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = true)
            every {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(),
                    any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { tripMobileOriginatedEventsRepo.setCompletedTimeForStop(any(), any(), any()) } just runs
            coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
                any(),
                any()
            ) } just runs
            dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
            verify(exactly = 0) {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    context,
                    EMPTY_STRING
                )
            }
            coVerify(exactly = 1) {
                dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
            }
        }

    // @Ignore
    @Test
    fun `check deeplink configuration check method gets called when action is manually arrived and response is sent`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.ARRIVED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val action = Action(actionType = ActionTypes.ARRIVED.ordinal, responseSent = true)
            every {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(),
                    any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { tripMobileOriginatedEventsRepo.setCompletedTimeForStop(any(), any(), any()) } just runs
            coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
                any(),
                any()
            ) } just runs
            dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
            verify(exactly = 0) {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    context,
                    EMPTY_STRING
                )
            }
            coVerify(exactly = 1) {
                dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
            }
        }

    // @Ignore
    @Test
    fun `check deeplink configuration check method gets called when action is depart and response is not sent`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.DEPARTED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val action = Action(actionType = ActionTypes.DEPARTED.ordinal, responseSent = false)
            every {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(),
                    any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs
            dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
            mockScope.advanceUntilIdle()
            coVerify(exactly = 1) {
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any())
            }
            verify(exactly = 0) {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    context,
                    EMPTY_STRING
                )
            }
        }

    // @Ignore
    @Test
    fun `check deeplink configuration check method gets called when action is depart and response is sent`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.DEPARTED.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival= true
            )
            val action = Action(actionType = ActionTypes.DEPARTED.ordinal, responseSent = true)
            every {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    any(),
                    any()
                )
            } just runs
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery { tripMobileOriginatedEventsRepo.setCompletedTimeForStop(any(), any(), any()) } just runs
            dispatchStopsUseCase.handleStopEvents(action, sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
            verify(exactly = 0) {
                deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                    context,
                    EMPTY_STRING
                )
            }
        }


    @Test
    fun `verify getStopAndActions returns default values of StopDetail data class in case of invalid creds - cid, trucknum and dispatchid`() = runTest(testDispatcher) {
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST751"
        coEvery { dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns EMPTY_STRING
        assertEquals(-1, dispatchStopsUseCase.getStopAndActions(1, dataStoreManager, TEST).stopid)
    }

    @Test
    fun `verify getStopAndActions proceeds with the code execution in case of valid creds - cid, trucknum and dispatchid`() = runTest(testDispatcher) {
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST751"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "76777557"
        coEvery { dispatchFirestoreRepo.getStop(any(), any(), any(), any()) } returns StopDetail(stopid = 0)
        coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any()) } returns listOf()
        dispatchStopsUseCase.getStopAndActions(1, dataStoreManager, TEST)
        coVerify(timeout = TEST_DELAY_OR_TIMEOUT, exactly = 1) {
            dispatchFirestoreRepo.getStop(any(), any(), any(), any())
            fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(),any())
        }
    }

    @Test
    fun `verify send the approach action along with arrival action if the response not sent`() = runTest(testDispatcher) {

        val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
            Actions.add(
                Action(
                    actionType = 0,
                    stopid = 1,
                    responseSent = false
                )
            )
        }
        coEvery { dispatchStopsUseCase.sendApproachActionResponse(
            any(),
            any(),
            PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        )  }just runs

        coEvery {
            dispatchStopsUseCase.sendActionResponse(
                any(),
                any(),
                any()
            )
        }just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(any(), any(), any()) } returns ""
        coEvery { dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(any(), any(), any(), any()) } just runs

        dispatchStopsUseCase.sendApproachAction(stopDetail, PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name))


        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.sendApproachActionResponse(
                any(),
                any(),
                PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name)
            )
            dispatchStopsUseCase.sendActionResponse(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify not send the approach action along with arrival action if the response sent`() = runTest(testDispatcher) {

        val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
            Actions.add(
                Action(
                    actionType = 0,
                    stopid = 1,
                    responseSent = true
                )
            )
        }

        dispatchStopsUseCase.sendApproachAction(stopDetail, PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name))


        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.sendApproachActionResponse(
                any(),
                any(),
                PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            )
        }
    }

    @Test
    fun `verify sendStopActionWorkflowEventsToThirdPartyApps when stopList is empty`() = runTest(testDispatcher) {
        val stopId = 1
        val action = Action(
            actionType = 0,
            stopid = stopId
        )
        val stop = StopDetail(stopid = stopId)
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopData(any()) } returns stop
        every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
        dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(action, "", 0, "")
        verify(exactly = 1) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any())
        }
    }

    @Test
    fun `verify sendStopActionWorkflowEventsToThirdPartyApps when stopList is not empty and matching the actionId`() = runTest(testDispatcher) {
        val stopId = 1
        val action = Action(
            actionType = 0,
            stopid = stopId
        )
        val stop = StopDetail(stopid = stopId)
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopData(any()) } returns stop
        every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
        dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(action, "", 0, "")
        verify(exactly = 1) {
            sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any())
        }
    }

    @Test
    fun `verify firestore cache returned stops behaviour if expected stop avl in cache`() = runTest(testDispatcher) {
        val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
            Actions.add(
                Action(
                    actionType = 1,
                    stopid = 1,
                    responseSent = true
                )
            )
        }
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "100"
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "cgvus"
        coEvery {dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())} returns mutableListOf(stopDetail)

        dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst("test",1)
            ?.let { assertEquals(1, it.stopid) }
    }

    @Test
    fun `verify firestore cache returned default stop behaviour if expected stop not avl in cache`() = runTest(testDispatcher) {
        val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
            Actions.add(
                Action(
                    actionType = 1,
                    stopid = 1,
                    responseSent = true
                )
            )
        }
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "100"
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "cgvus"
        coEvery {dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())} returns mutableListOf(stopDetail)
        dispatchStopsUseCase.getSpecificStopAndItsActionsFromFirestoreCacheFirst("test",2)
            ?.let { assertEquals(-1, it.stopid) }
    }

    @Test
    fun `verify processGeofenceTriggerForGeofenceRemoval for geofence removal event send call`() = runTest(testDispatcher) {
        val stopDetail = StopDetail(stopid = 1, deleted = 0).apply {
            Actions.add(
                Action(
                    actionType = ActionTypes.ARRIVED.ordinal,
                    stopid = 1,
                    responseSent = true
                )
            )
        }
        val stopActionEventData = StopActionEventData(
            stopId = 1, actionType = ActionTypes.ARRIVED.ordinal,
            context, hasDriverAcknowledgedArrivalOrManualArrival= true
        )
        every { sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any()) } just runs
        dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(
            stopDetail,
            dispatchIDForStops,
            stopActionEventData
        )

        verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            sendDispatchDataUseCase.sendRemoveGeoFenceEvent(any())
        }
    }

    @Test
    fun `verify setCurrentStopAndUpdateTripPanelForSequentialTrip for current stop and trip panel update`() = runTest(testDispatcher) {
        val seqStop = StopDetail(stopid = 0, deleted = 0, sequenced = 1).apply {
            Actions.add(
                Action(
                    actionType = ActionTypes.ARRIVED.ordinal,
                    stopid = 0
                )
            )
        }
        val ffStop = StopDetail(stopid = 1, deleted = 0, sequenced = 1).apply {
            Actions.add(
                Action(
                    actionType = ActionTypes.ARRIVED.ordinal,
                    stopid = 1
                )
            )
        }
        coEvery { dispatchStopsUseCase.doesStoreHasCurrentStop(dataStoreManager) } returns false

        dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
            CopyOnWriteArrayList<StopDetail>().also {
                it.add(seqStop)
                it.add(ffStop)
            }
        )

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(any(), any())
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        }
    }

    @Test
    fun `updateStopDetail updates stop detail with manual arrival location`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "gowtham"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId("updateStopDetail") } returns "1698"
        coEvery { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) } just Runs

        dispatchStopsUseCase.updateStopDetail(stop, valueMap)

        coVerify { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) }
    }


    @Test
    fun `updateStopDetail does not update stop detail when CID is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns ""

        dispatchStopsUseCase.updateStopDetail(stop, valueMap)

        coVerify(exactly = 0) { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) }
    }

    @Test
    fun `updateStopDetail does not update stop detail when truck number is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""

        dispatchStopsUseCase.updateStopDetail(stop, valueMap)

        coVerify(exactly = 0) { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) }
    }

    @Test
    fun `updateStopDetail does not update stop detail when workflow ID is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "gowtham"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId("updateStopDetail") } returns ""

        dispatchStopsUseCase.updateStopDetail(stop, valueMap)

        coVerify(exactly = 0) { tripMobileOriginatedEventsRepo.updateStopDetailWithManualArrivalLocation(any(), any()) }
    }

    @Test
    fun `verify autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived when first stop is manually arrived and there is no pending departure action`() = runTest(testDispatcher) {
        val stopActionEventData = StopActionEventData(stopId = 0, actionType = ActionTypes.ARRIVED.ordinal,context = context, hasDriverAcknowledgedArrivalOrManualArrival = true)
        coEvery { dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted(any()) } returns null
        dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
            "1000",stopActionEventData
        )
        coVerify(exactly = 0) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
            dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived when first stop is auto arrived and there is no pending departure action`() = runTest(testDispatcher) {
        val stopActionEventData = StopActionEventData(stopId = 0, actionType = ActionTypes.ARRIVED.ordinal,context = context, hasDriverAcknowledgedArrivalOrManualArrival = false)
        coEvery { dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted(any()) } returns null
        dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
            "1000",stopActionEventData
        )
        coVerify(exactly = 0) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
            dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived when stop is arrived and there is pending departure action`() = runTest(testDispatcher) {
        val stopActionEventData = StopActionEventData(stopId = 0, actionType = ActionTypes.ARRIVED.ordinal,context = context, hasDriverAcknowledgedArrivalOrManualArrival = false)
        coEvery { dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted(any()) } returns StopDetail(stopid = 0)
        coEvery { dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any()) } returns EMPTY_STRING
        coEvery { dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any()) } just runs
        dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
           "100",stopActionEventData
        )
        coVerify(exactly = 1) {
            dispatchStopsUseCase.handleStopEvents(any(), any(), any(), any(), any())
            dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any())
        }

    }

    @Test
    fun `verify autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived is getting called when first stop is approached`() =
        runTest(testDispatcher) {
            val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
            val sendStopActionEventData = StopActionEventData(
                stopId = 0,
                ActionTypes.APPROACHING.ordinal,
                context,
                hasDriverAcknowledgedArrivalOrManualArrival = false
            )
            val action = Action(actionType = ActionTypes.APPROACHING.ordinal)
            coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
                any(),
                any(),
                any()
            ) } returns EMPTY_STRING
            coEvery {
                dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } just runs
            dispatchStopsUseCase.handleStopEvents(
                action,
                sendStopActionEventData,
                caller = "",
                pfmEventsInfo = pfmEventsInfo
            )
            coVerify(exactly = 0) {
                dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
            }
        }

    @Test
    fun `verify autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived when first stop is departed`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 0,
            ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = false
        )
        val action = Action(actionType = ActionTypes.DEPARTED.ordinal)
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
            any(),
            any(),
            any()
        ) } returns EMPTY_STRING
        coEvery {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(
                any(),
                any(),
                any(),
                any()
            )
        } just runs
        dispatchStopsUseCase.handleStopEvents(
            action,
            sendStopActionEventData,
            caller = "",
            pfmEventsInfo = pfmEventsInfo
        )
        coVerify(exactly = 0) {
            dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
        }
    }

    @Test
    fun `verify autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived when action is null` () = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 0,
            ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = false
        )
        val action = null
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
            any(),
            any(),
            any()
        ) } returns EMPTY_STRING
        coEvery {
            dispatchStopsUseCase.sendStopActionWorkflowEventsToThirdPartyApps(
                any(),
                any(),
                any(),
                any()
            )
        } just runs
        coEvery { dispatchStopsUseCase.updateCompletionTimeInStopDocument(any(), any()) } returns EMPTY_STRING
        dispatchStopsUseCase.handleStopEvents(
            action = action,
            stopActionEventData = sendStopActionEventData,
            caller = "",
            pfmEventsInfo = pfmEventsInfo
        )
        coVerify(exactly = 0) {
            dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(),any())
        }
    }

    @Test
    fun `verify autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived when any stop is auto arrived and departure is pending for a stop`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.AUTO.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 1,
            ActionTypes.ARRIVED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= false
        )
        val action = Action(actionType = ActionTypes.ARRIVED.ordinal, stopid = 1)
        every {
            deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                any(),
                any()
            )
        } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
            any(),
            any(),
            any()
        ) } returns EMPTY_STRING
        coEvery { tripMobileOriginatedEventsRepo.setCompletedTimeForStop(any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
            any(), any()
        ) } just runs
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf()
        coEvery { dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted(any()) } returns StopDetail(stopid = 0)
        coEvery { dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any()) } just runs
        coEvery { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any())} just runs
        dispatchStopsUseCase.handleStopEvents(action = action, stopActionEventData = sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
        }
    }

    @Test
    fun `verify autoCompleteDepartureForTheCurrentStopWhenAnyStopIsArrived when any stop is manually arrived and departure is pending for a stop`() = runTest(testDispatcher) {
        val pfmEventsInfo = PFMEventsInfo.StopActionEvents(StopActionReasonTypes.MANUAL.name)
        val sendStopActionEventData = StopActionEventData(
            stopId = 1,
            ActionTypes.ARRIVED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival= true
        )
        val action = Action(actionType = ActionTypes.ARRIVED.ordinal, stopid = 1)
        every {
            deepLinkUseCase.checkAndHandleDeepLinkConfigurationForArrival(
                any(),
                any()
            )
        } just runs
        coEvery { dispatchStopsUseCase.checkStopDataOfEventTriggerAndProceed(
            any(),
            any(),
            any()
        ) } returns EMPTY_STRING
        coEvery { tripMobileOriginatedEventsRepo.setCompletedTimeForStop(any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(
            any(), any()
        ) } just runs
        coEvery { fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any()) } returns listOf()
        coEvery { dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted(any()) } returns StopDetail(stopid = 0)
        coEvery { dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any()) } just runs
        coEvery { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any())} just runs
        dispatchStopsUseCase.handleStopEvents(action = action, stopActionEventData = sendStopActionEventData, caller = "", pfmEventsInfo = pfmEventsInfo)
        coVerify(exactly = 1) {
            dispatchStopsUseCase.checkAndAutoDepartThePreviousStopOnArrival(any(), any())
        }
    }

    @Test
    fun `verify removeGeofenceAndMarkStopManipulationKey when the stop is not deleted`() = runTest(testDispatcher) {
        val stopDetail = StopDetail(stopid = 1).apply {
            Actions.add(
                Action(
                    actionType = 2,
                    stopid = 1
                )
            )
        }
        val stopActionEventData = StopActionEventData(
            1,
            ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = false
        )
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any()) } returns arrayListOf()
        coEvery { dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(any(), any(), any(), any())} just runs
        dispatchStopsUseCase.postDepartureEventProcess(stopDetail,1, "123", stopActionEventData)
        coVerify(exactly = 1) {
            arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any())
            dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(any(), any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify removeGeofenceAndMarkStopManipulationKey when the stop is deleted`() = runTest(testDispatcher) {
        val stopDetail = StopDetail(stopid = 1, deleted = 1).apply {
            Actions.add(
                Action(
                    actionType = 2,
                    stopid = 1
                )
            )
        }
        val stopActionEventData = StopActionEventData(
            1,
            ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = false
        )
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any()) } returns arrayListOf()
        coEvery { dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(any(), any(), any()) } just runs
        dispatchStopsUseCase.postDepartureEventProcess(stopDetail,1, "123", stopActionEventData)
        coVerify(exactly = 1) {
            arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any())
            dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(any(), any(), any())
        }
        coVerify(exactly = 0) {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is auto arrived in a trip and departure is pending for a stop`() = runTest(testDispatcher) {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
            completedTime = "2024-09-01T00:00:00Z"
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        val expectedStopDetail = StopDetail(stopid = 0, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
            completedTime = "2024-09-01T00:00:00Z"
        }
        assertEquals(expectedStopDetail, dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is manually arrived in a trip and departure is pending for a stop`() = runTest(testDispatcher) {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
            completedTime = "2024-09-01T00:00:00Z"
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        val expectedStopDetail = StopDetail(stopid = 0, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
            completedTime = "2024-09-01T00:00:00Z"
        }
        assertEquals(expectedStopDetail, dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is arrived in a trip and departure is pending for a deleted stop`() = runTest(testDispatcher) {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 1, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
            completedTime = "2024-09-01T00:00:00Z"
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        assertNull(dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is arrived in a trip and only approach action is pending for a stop without arrive and depart action`() = runTest(testDispatcher) {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = true, arriveActionNeeded = false, departActionNeeded = false)
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        assertNull(dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is arrived in a trip and only arrive action is pending`() = runTest(testDispatcher) {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = false)
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        assertNull(dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is arrived in a trip and no arrive action and only depart action is present for the previous stops`() = runTest(testDispatcher) {
        try{ val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = false, departActionNeeded = true)
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 1).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        assertNull(dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
        } catch(e: Exception){
            println("Caught Exception $e")
        }
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is arrived and there depart action is completed for the previous stops`() = runTest(testDispatcher) {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 0).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
            completedTime = "2024-09-01T00:00:00Z"
            departedTime = "2024-09-01T00:20:00Z"
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 0).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        assertNull(dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is arrived and there is no arrive action for the previous stop`() = runTest(testDispatcher) {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 0).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = false, departActionNeeded = true)
            completedTime = "2024-09-01T00:00:00Z"
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 0).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        assertNull(dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
    }

    @Test
    fun `verify getStopDetailWhereDepartureActionNeedsToBeCompleted when any stop is arrived and there is no depart action for the previous stop`() = runTest(testDispatcher) {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 0).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = false)
            completedTime = "2024-09-01T00:00:00Z"
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 0).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = false, arriveActionNeeded = true, departActionNeeded = true)
        })
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(), any(), any(), any(), any()) } returns stopDetailList
        assertNull(dispatchStopsUseCase.getStopDetailWhereDepartureActionNeedsToBeCompleted("12345"))
    }

    @Test
    fun `updateSequencedKeyInDataStore sets sequenced key to false when any stop is not sequenced`() = runTest(testDispatcher) {
        val stops = CopyOnWriteArrayList<StopDetail>().apply {
            add(StopDetail(sequenced = 1))
            add(StopDetail(sequenced = 0))
        }
        val stopDetail = StopDetail(sequenced = 1)

        coEvery { dataStoreManager.setValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, FALSE) } just Runs

        dispatchStopsUseCase.updateSequencedKeyInDataStore(stops, stopDetail)

        coVerify { dataStoreManager.setValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, FALSE) }
    }

    @Test
    fun `updateSequencedKeyInDataStore sets sequenced key to false when stopDetail is not sequenced`() = runTest(testDispatcher) {
        val stops = CopyOnWriteArrayList<StopDetail>().apply {
            add(StopDetail(sequenced = 1))
            add(StopDetail(sequenced = 1))
        }
        val stopDetail = StopDetail(sequenced = 0)

        coEvery { dataStoreManager.setValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, FALSE) } just Runs

        dispatchStopsUseCase.updateSequencedKeyInDataStore(stops, stopDetail)

        coVerify { dataStoreManager.setValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, FALSE) }
    }

    @Test
    fun `updateSequencedKeyInDataStore sets sequenced key to true when all stops are sequenced`() = runTest(testDispatcher) {
        val stops = CopyOnWriteArrayList<StopDetail>().apply {
            add(StopDetail(sequenced = 1))
            add(StopDetail(sequenced = 1))
        }
        val stopDetail = StopDetail(sequenced = 1)

        coEvery { dataStoreManager.setValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE) } just Runs

        dispatchStopsUseCase.updateSequencedKeyInDataStore(stops, stopDetail)

        coVerify { dataStoreManager.setValue(DataStoreManager.ARE_STOPS_SEQUENCED_KEY, TRUE) }
    }

    private fun getStopActions(
        actions: CopyOnWriteArrayList<Action>,
        stopId: Int,
        approachActionNeeded: Boolean,
        arriveActionNeeded: Boolean,
        departActionNeeded: Boolean,
    ) {
        if (approachActionNeeded) {
            actions.add(Action(actionType = ActionTypes.APPROACHING.ordinal, stopid = stopId))
        }
        if (arriveActionNeeded) {
            actions.add(Action(actionType = ActionTypes.ARRIVED.ordinal, stopid = stopId))
        }
        if (departActionNeeded) {
            actions.add(Action(actionType = ActionTypes.DEPARTED.ordinal, stopid = stopId))
        }
    }

    @Test
    fun `test getLastSequentialAllActionsCompletedStopId with empty list`() {
        val stops = emptyList<StopDetail>()
        val result = dispatchStopsUseCase.getLastSequentialCompletedStop(stops)
        assertEquals(null, result)
    }

    @Test
    fun testAllActionsCompletedForNonSequencedStops() {
        val stopDetailList = listOf(StopDetail(stopid = 0, deleted = 0, sequenced = 0).apply {
            getStopActions(actions = Actions, stopId = 0, approachActionNeeded = true, arriveActionNeeded = true, departActionNeeded = true)
            completedTime = "2024-09-01T00:00:00Z"
        }, StopDetail(stopid = 1, deleted = 0, sequenced = 0).apply {
            getStopActions(actions = Actions, stopId = 1, approachActionNeeded = true, arriveActionNeeded = true, departActionNeeded = true)
        })

        assertEquals(null, dispatchStopsUseCase.getLastSequentialCompletedStop(stopDetailList))
    }

    @Test
    fun testAllActionsCompletedForSequencedStopsTest() {
        val stopDetailList = listOf(
            StopDetail(stopid = 0, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true
                )
            },
            StopDetail(stopid = 1, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true,
                    ActionTypes.DEPARTED to true
                )
            }
        )

        assertEquals(1, dispatchStopsUseCase.getLastSequentialCompletedStop(stopDetailList)?.stopid)
    }

    @Test
    fun testAllActionsCompletedForSequencedAndMixedStops() {
        // Test case 1: Mixed stops with different combinations of actions
        val stopDetailList1 = listOf(
            StopDetail(stopid = 0, sequenced = 0).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true
                )
            },
            StopDetail(stopid = 1, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true,
                    ActionTypes.DEPARTED to true
                )
            },
            StopDetail(stopid = 2, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to false,
                    ActionTypes.ARRIVED to true,
                    ActionTypes.DEPARTED to true
                )
            }
        )

        assertEquals(1, dispatchStopsUseCase.getLastSequentialCompletedStop(stopDetailList1)?.stopid)

        // Test case 2: All actions are completed for all sequenced stops
        val stopDetailList2 = listOf(
            StopDetail(stopid = 0, sequenced = 0).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true,
                    ActionTypes.DEPARTED to true
                )
            },
            StopDetail(stopid = 1, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true,
                    ActionTypes.DEPARTED to true
                )
            },
            StopDetail(stopid = 2, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to true,
                    ActionTypes.ARRIVED to true,
                    ActionTypes.DEPARTED to true
                )
            }
        )

        assertEquals(2, dispatchStopsUseCase.getLastSequentialCompletedStop(stopDetailList2)?.stopid)

        // Test case 3: No actions are completed for any sequenced stops
        val stopDetailList3 = listOf(
            StopDetail(stopid = 0, sequenced = 0).apply {
                addActions(
                    ActionTypes.APPROACHING to false,
                    ActionTypes.ARRIVED to false,
                    ActionTypes.DEPARTED to false
                )
            },
            StopDetail(stopid = 1, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to false,
                    ActionTypes.ARRIVED to false,
                    ActionTypes.DEPARTED to false
                )
            },
            StopDetail(stopid = 2, sequenced = 1).apply {
                addActions(
                    ActionTypes.APPROACHING to false,
                    ActionTypes.ARRIVED to false,
                    ActionTypes.DEPARTED to false
                )
            }
        )

        assertEquals(null, dispatchStopsUseCase.getLastSequentialCompletedStop(stopDetailList3))


    }

    fun StopDetail.addActions(vararg actionPairs: Pair<ActionTypes, Boolean>) {
        actionPairs.forEachIndexed { index, pair ->
            val (actionType, responseSent) = pair
            this.Actions.add(Action(actionid = index, actionType = actionType.ordinal, responseSent = responseSent))
        }
    }



    @Test
    fun `verify getActionsOfStop`() = runTest{

        val actions = CopyOnWriteArrayList<Action>().apply {
            add(Action(actionType = ActionTypes.APPROACHING.ordinal, stopid = 123))
            add(Action(actionType = ActionTypes.ARRIVED.ordinal, stopid = 123))
            add(Action(actionType = ActionTypes.DEPARTED.ordinal, stopid = 123))
        }
        val stop = StopDetail(stopid = 0, dispid = "12")
        stop.Actions = actions
        coEvery { dispatchFirestoreRepo.getActionsOfStop(any(),any(),any()) } returns actions

        assertEquals(actions, dispatchStopsUseCase.getActionsOfStop("12",0, "Test function"))
    }

    @After
    fun after() {
        unloadKoinModules(modulesRequiredForTest)
        stopKoin()
        unmockkObject(FormUtils)
        unmockkObject(Utils)
        clearAllMocks()
        unmockkAll()
    }
}