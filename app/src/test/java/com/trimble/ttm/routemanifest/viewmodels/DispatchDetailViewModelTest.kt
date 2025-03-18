package com.trimble.ttm.routemanifest.viewmodels

import android.content.Context
import android.os.Bundle
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.work.WorkManager
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.HotKeys
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.SELECTED_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_DISTANCE_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_HOURS_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_STOPS_KEY
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.JsonData
import com.trimble.ttm.routemanifest.model.Leg
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.RouteCalculationResult
import com.trimble.ttm.routemanifest.model.STATE
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.usecases.DispatchBaseUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchValidationUseCase
import com.trimble.ttm.routemanifest.usecases.FetchDispatchStopsAndActionsUseCase
import com.trimble.ttm.routemanifest.usecases.LateNotificationUseCase
import com.trimble.ttm.routemanifest.usecases.RouteETACalculationUseCase
import com.trimble.ttm.routemanifest.usecases.SendDispatchDataUseCase
import com.trimble.ttm.routemanifest.usecases.StopDetentionWarningUseCase
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartCaller
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.utils.ADDED
import com.trimble.ttm.routemanifest.utils.JsonDataConstructionUtils
import com.trimble.ttm.routemanifest.utils.REMOVED
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_ERROR
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_STATE
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_STOPDETAIL_LIST
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_TOTAL_DISTANCE
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_TOTAL_HOUR
import com.trimble.ttm.routemanifest.utils.ROUTE_COMPUTATION_RESPONSE_TO_CLIENT_KEY
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import io.mockk.MockKAnnotations
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DispatchDetailViewModelTest {
    @MockK
    private lateinit var routeETACalculationUseCase: RouteETACalculationUseCase
    private lateinit var dispatchDetailViewModel: DispatchDetailViewModel
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager
    @MockK
    private lateinit var sendDispatchDataUseCase: SendDispatchDataUseCase
    @RelaxedMockK
    private lateinit var context: Context
    @MockK
    private lateinit var bundle: Bundle
    private val testScope = TestScope()
    @MockK
    private lateinit var dispatchValidationUseCase: DispatchValidationUseCase
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var tripCompletionUseCase: TripCompletionUseCase
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase
    @MockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase
    @MockK
    private lateinit var dispatchBaseUseCase: DispatchBaseUseCase
    @MockK
    private lateinit var stopDetentionWarningUseCase: StopDetentionWarningUseCase
    @MockK
    private lateinit var sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase
    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase
    @RelaxedMockK
    private lateinit var workManager: WorkManager
    @MockK
    private lateinit var lateNotificationUseCase: LateNotificationUseCase
    @MockK
    private lateinit var tripStartUseCase: TripStartUseCase
    @RelaxedMockK
    private lateinit var formLibraryUseCase: FormLibraryUseCase
    @RelaxedMockK
    private lateinit var hotKeysObserver: Observer<Boolean>


    private val DEF_DISPATCH_ID = "1343"

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @RelaxedMockK
    private lateinit var application: WorkflowApplication

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    @RelaxedMockK
    lateinit var verticalTimeStringObserver : Observer<String>

    @RelaxedMockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo

    private val stopList = ArrayList<StopDetail>()
        .also { list ->
            list.add(StopDetail(stopid = 0).also {
                it.leg = Leg(1.0, 1.0)
            })
            list.add(StopDetail(stopid = 1).also {
                it.leg = Leg(1.0, 1.2)
            })
        }

    private var resultMap: LinkedHashMap<String, ArrayList<String>> =
        LinkedHashMap<String, ArrayList<String>>().also { resultMap ->
            //state creation
            val stateOrdinal = ArrayList<String>()
            stateOrdinal.add(STATE.SUCCESS.ordinal.toString())
            resultMap[ROUTE_CALCULATION_RESULT_STATE] = stateOrdinal
            //error msg creation
            val errorMsg = ArrayList<String>()
            errorMsg.add("")
            resultMap[ROUTE_CALCULATION_RESULT_ERROR] = errorMsg
            //stopDetailList creation
            val stopDetailList = ArrayList<String>()
            stopList.forEach { stopDetail ->
                Utils.toJsonString(stopDetail)?.let { stopDetailList.add(it) }
            }
            resultMap[ROUTE_CALCULATION_RESULT_STOPDETAIL_LIST] = stopDetailList
            resultMap[ROUTE_CALCULATION_RESULT_TOTAL_HOUR] = ArrayList<String>().also {
                it.add("10")
            }
            resultMap[ROUTE_CALCULATION_RESULT_TOTAL_DISTANCE] = ArrayList<String>().also {
                it.add("10")
            }
        }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkObject(com.trimble.ttm.commons.utils.FormUtils)
        mockkObject(JsonDataConstructionUtils.Companion)
        mockkObject(WorkflowEventBus)
        every { application.applicationContext } returns context
        every { context.applicationContext } returns context
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        every {
            bundle.getString(
                ROUTE_COMPUTATION_RESPONSE_TO_CLIENT_KEY,
                EMPTY_STRING
            )
        } returns Utils.toJsonString(
            resultMap
        )
        coEvery { dataStoreManager.getValue(SELECTED_DISPATCH_KEY, EMPTY_STRING) } returns DEF_DISPATCH_ID
        coEvery { dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns DEF_DISPATCH_ID
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        every { dispatchStopsUseCase.appModuleCommunicator } returns appModuleCommunicator
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns DEF_DISPATCH_ID
        coEvery { appModuleCommunicator.getSelectedDispatchId(any()) } returns DEF_DISPATCH_ID
        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple("12", "234", true)
        coEvery { dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(any(), any(), any()) } returns true
        coEvery { tripCompletionUseCase.getStopsForDispatch(any(), any(), any()) } returns flow {  }
        coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any() )} returns listOf()
        coEvery{dispatchBaseUseCase.getDispatchActiveState(any(), any())} returns DispatchActiveState.ACTIVE
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
        dispatchDetailViewModel = spyk(
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
                lateNotificationUseCase = lateNotificationUseCase,
                sendWorkflowEventsToAppUseCase = sendWorkflowEventsToAppUseCase,
                fetchDispatchStopsAndActionsUseCase = fetchDispatchStopsAndActionsUseCase,
                tripStartUseCase = tripStartUseCase,
                formLibraryUseCase = formLibraryUseCase
            ), recordPrivateCalls = true // for testing private methods
        )

        coEvery { dispatchDetailViewModel.getActionsForStop(any()) } just runs

        dispatchDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.ACTIVE)
    }

    @Test
    fun `verify route calculation success`() = runTest {    //NOSONAR
        coEvery {
            dataStoreManager.setValue(any<Preferences.Key<Float>>(), any())
            dataStoreManager.setValue(any<Preferences.Key<Int>>(), any())
        } returns Unit
        with(dispatchDetailViewModel) {
            stopDetailList.clear()
            stopDetailList.addAll(stopList)
            coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
            startRouteCalculation("")

            coVerify { routeETACalculationUseCase.startRouteCalculation(any(), any()) }
        }
    }

    @Test
    fun `verify route calculation failure`() = runTest {    //NOSONAR
        dispatchDetailViewModel.stopDetailList.clear()
        dispatchDetailViewModel.stopDetailList.addAll(stopList)
        coEvery {
           dataStoreManager.setValue(TOTAL_DISTANCE_KEY, ZERO.toFloat())
            dataStoreManager.setValue(TOTAL_HOURS_KEY, ZERO.toFloat())
            dataStoreManager.setValue(TOTAL_STOPS_KEY, ZERO)
        } just runs
        coEvery { dataStoreManager.containsKey(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns false
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs

        dispatchDetailViewModel.startRouteCalculation("")

        coVerify { routeETACalculationUseCase.startRouteCalculation(any(), any()) }
    }

    @Test
    fun `check when hasOnlyOneDispatch is called hasOnlyOneDispatchOnList is called also`() = runTest {
        coEvery {
            dispatchValidationUseCase.hasOnlyOne()
        } returns true
        coEvery {
            dispatchValidationUseCase.hasAnActiveDispatch()
        } returns true
        dispatchDetailViewModel.checkIfHasOnlyOneDispatchAndIsActive {}
        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchValidationUseCase.hasOnlyOne()
        }

    }

    @Test
    fun `check when saveSelectedTripName is called updateNameOnSelected is called also`() = runTest {
        val DISPATCH_NAME = ""
        dispatchDetailViewModel.dispatchName = DISPATCH_NAME
        coEvery {
            dispatchValidationUseCase.updateNameOnSelected(DISPATCH_NAME)
        } returns Unit
        dispatchDetailViewModel.saveSelectedTripName()
        coVerify {
            dispatchValidationUseCase.updateNameOnSelected(DISPATCH_NAME)
        }
    }

//    @Test
//    fun `verify dispatch data sent to maps after dispatch started`() =
//        runTest {
//            val stopList = CopyOnWriteArrayList<StopDetail>()
//            stopList.add(StopDetail(stopid = 1, completedTime = "asfd"))
//            stopList.add(StopDetail(stopid = 2, completedTime = "asfd"))
//            every { sendDispatchDataUseCase.sendDispatchCompleteEvent() } just runs
//            coEvery { sendDispatchDataUseCase.sendDispatchDataAfterDispatchStarted(any()) } just runs
//            dispatchDetailViewModel.sendDispatchDataAfterDispatchStarted(stopList)
//            advanceTimeBy(2 * COPILOT_ROUTE_CLEARING_TIME_SECONDS)
//            coVerify(
//                exactly = 1,
//                timeout = TEST_DELAY_OR_TIMEOUT
//            ) {
//                sendDispatchDataUseCase.sendDispatchCompleteEvent()
//                sendDispatchDataUseCase.sendDispatchDataAfterDispatchStarted(any())
//            }
//        }

//    @Test
//    fun `verify dispatch data sent to maps after dispatch started not sent before`(){
//        runTest {
//            val stopList = CopyOnWriteArrayList<StopDetail>()
//            stopList.add(StopDetail(stopid = 1, completedTime = "asfd"))
//            stopList.add(StopDetail(stopid = 2, completedTime = "asfd"))
//
//            coEvery { sendDispatchDataUseCase.sendDispatchCompleteEvent() } just runs
//
//            coEvery { sendDispatchDataUseCase.sendDispatchDataAfterDispatchStarted(
//                any()
//            ) } just runs
//
//            dispatchDetailViewModel.sendDispatchDataAfterDispatchStarted(stopList)
//
//            coVerify(timeout = TEST_DELAY_OR_TIMEOUT) { sendDispatchDataUseCase.sendDispatchCompleteEvent() }
//
//            delay(COPILOT_ROUTE_CLEARING_TIME_SECONDS)
//
//            coVerify(timeout = TEST_DELAY_OR_TIMEOUT) { sendDispatchDataUseCase.sendDispatchDataAfterDispatchStarted(
//                stopList
//            ) }
//
//        }
//    }

    @Test
    fun `check verticalLineTimeString live data works as expected`() = runTest {
        val calendarToRetrieveTime = Calendar.getInstance()
        val dateTimeFormat = SimpleDateFormat("HH:mm")
        coEvery { dispatchBaseUseCase.getTimeString(
            coroutineDispatcher = any(),
            calendarToRetrieveTime = any(),
            dateTimeFormat = any(),
            dispatchId = any(),
            tag = any(),
            is24HourTimeFormat = any()
        ) } returns "12.00"
        dispatchDetailViewModel.verticalLineTimeString.observeForever(verticalTimeStringObserver)
        dispatchDetailViewModel.getTimeString(
            calendarToRetrieveTime = calendarToRetrieveTime,
            dateTimeFormat = dateTimeFormat,
            tag = "Test",
            numberOfHours = 2,
            is24HourTimeFormat = true
        )
        coVerify(exactly = 2) {
            verticalTimeStringObserver.onChanged(any())
        }
    }

    @Test
    fun `verify hasActiveDispatchKey`() = runTest {
        coEvery {
            dataStoreManager.containsKey(DataStoreManager.ACTIVE_DISPATCH_KEY)
        } returns true
        coEvery {
            dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
        } returns "1234"
        coEvery { dispatchBaseUseCase.hasActiveDispatchKey(dataStoreManager) } returns flow { emit(true) }
        dispatchDetailViewModel.hasActiveDispatchKey().safeCollect("tag"){
            assertTrue(it)
        }
    }

    @Test
    fun `verify startTrip`() = runTest(UnconfinedTestDispatcher()) {
        val startTime = 10L
        val jsonData = JsonData("","")
        val dispatchKey = "dispatchKey"

        coEvery {
            dispatchBaseUseCase.setStartTime(any(), dataStoreManager)
        } just runs
        coEvery { sendDispatchDataUseCase.sendDispatchCompleteEvent() } just runs
        coEvery {
            dispatchBaseUseCase.storeActiveDispatchIdToDataStore(dataStoreManager, any())
        } returns dispatchKey
        coEvery{
            appModuleCommunicator.doGetCid()
        } returns ""
        coEvery{
            appModuleCommunicator.doGetTruckNumber()
        } returns ""
        coEvery {
            dataStoreManager.getValue(SELECTED_DISPATCH_KEY, EMPTY_STRING)
        } returns dispatchKey
        coEvery {
            dataStoreManager.setValue(
                any(),
                dispatchKey
            )
        } just runs
        coEvery {
            JsonDataConstructionUtils.Companion.getTripEventJson(
                dispatchId = dispatchKey,
                pfmEventsInfo = PFMEventsInfo.TripEvents("normal", false),
                fuelLevel = 0,
                odometerReading = 1.1,
                currentLocationLatLong = Pair(1.0, 1.0),
                customerIdObcIdVehicleId = Triple(1, "1", "T1")
            )
        } returns jsonData
        coEvery {
            dispatchStopsUseCase.sendTripStartEventToPFM(
                any(),
                any(),
                any(),
                any()
            )
        } just runs
        coEvery {
            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
        } returns true
        coEvery { dispatchStopsUseCase.setActiveDispatchFlagInFirestore(any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulation() } just runs
        coEvery { dispatchValidationUseCase.updateNameOnSelected(any()) } just runs
        coEvery { dispatchBaseUseCase.anyStopAlreadyCompleted(any()) } returns true
        coEvery { appModuleCommunicator.hasActiveDispatch(any(), any()) } returns true
        coEvery {
            dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(
                any()
            )
        } returns true
        coEvery {
            dispatchStopsUseCase.doesStoreHasCurrentStop(dataStoreManager)
        } returns false
        coEvery { backboneUseCase.setWorkflowStartAction(any()) } just runs
        coEvery { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs

        coEvery { dispatchBaseUseCase.getCidAndTruckNumber(any()) } returns Triple("", "", false)

        val dispatchBlobList = ArrayList<DispatchBlob>()
        with(dispatchBlobList){
            add(DispatchBlob(cid = 10119, vehicleNumber = "1234", blobMessage = "blobMessage").also { it.id = Random.nextInt().toString() })
            add(DispatchBlob(cid = 10119, vehicleNumber = "1234", blobMessage = "blobMessage2").also { it.id = Random.nextInt().toString() })
            add(DispatchBlob(cid = 10119, vehicleNumber = "1234", blobMessage = "blobMessage3").also { it.id = Random.nextInt().toString() })
        }

        coEvery { dispatchStopsUseCase.getAllDispatchBlobData(any(),any()) } returns dispatchBlobList
        coEvery { sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(any(),any(),any(),any()) } just runs
        coEvery { dispatchStopsUseCase.deleteAllDispatchBlobDataForVehicle(any(),any(), any()) } just runs
        coEvery { tripStartUseCase.startTrip(any()) } just runs

        dispatchDetailViewModel.startTrip(startTime, pfmEventsInfo = PFMEventsInfo.TripEvents("normal", false),
            tripStartCaller = TripStartCaller.DISPATCH_DETAIL_SCREEN
        )

        coVerify(exactly = 1) {
            tripStartUseCase.startTrip(any())
        }
    }

    @Test
    fun `verify calculatedRouteResult success execution`() = runTest {
        val successfulResult = RouteCalculationResult().also { it.state = STATE.SUCCESS }
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            routeETACalculationUseCase.routeCalculationResultFromLauncher(
                any(), any(), any(), DispatchActiveState.ACTIVE
            )
        } returns successfulResult

        dispatchDetailViewModel.calculatedRouteResult(RouteCalculationResult().apply { state = STATE.SUCCESS })

        coVerify { dispatchDetailViewModel["handleRouteCalculationSuccess"](successfulResult) }
    }

    @Test
    fun `verify calculatedRouteResult error execution for actual error from AL`() = runTest {
        val errorResult = RouteCalculationResult().also {
            it.state = STATE.ERROR
            it.error = "error calculating route"
        }
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            routeETACalculationUseCase.routeCalculationResultFromLauncher(
                any(), any(), any(), DispatchActiveState.ACTIVE
            )
        } returns errorResult

        dispatchDetailViewModel.calculatedRouteResult(RouteCalculationResult().apply { state = STATE.ERROR })

        coVerify { dispatchDetailViewModel.postRouteCalculationError(errorResult) }
    }

    @Test
    fun `verify calculatedRouteResult error execution for empty error msg from AL`() = runTest {
        val errorResult = RouteCalculationResult().also { it.state = STATE.ERROR }
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            routeETACalculationUseCase.routeCalculationResultFromLauncher(
                any(), any(), any(), DispatchActiveState.ACTIVE
            )
        } returns errorResult
        every { dispatchDetailViewModel.routeCalculationRetryCount } returns 0

        dispatchDetailViewModel.calculatedRouteResult(RouteCalculationResult().apply { state = STATE.ERROR })

        coVerify { dispatchDetailViewModel.postRouteCalculationError(errorResult) }
    }

    @Test
    fun `verify calculatedRouteResult for retry upon erroneous result from AL`() = runTest {
        val errorResult = RouteCalculationResult().also { it.state = STATE.ERROR }
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            routeETACalculationUseCase.routeCalculationResultFromLauncher(
                any(), any(), any(), DispatchActiveState.ACTIVE
            )
        } returns errorResult
        every { dispatchDetailViewModel.routeCalculationRetryCount } returns 1
        coEvery { dispatchDetailViewModel.startRouteCalculation(any()) } just runs

        dispatchDetailViewModel.calculatedRouteResult(RouteCalculationResult().apply { state = STATE.SUCCESS })

        coVerify(exactly = 0) { dispatchDetailViewModel.postRouteCalculationError(errorResult) }
    }

    @Test
    fun `verify calculatedRouteResult for ignore state result`() = runTest {
        val ignoreResult = RouteCalculationResult().also { it.state = STATE.IGNORE }
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            routeETACalculationUseCase.routeCalculationResultFromLauncher(any(), any(), any(), DispatchActiveState.ACTIVE)
        } returns ignoreResult

        assertEquals(ignoreResult, dispatchDetailViewModel.calculatedRouteResult(RouteCalculationResult().apply { state = STATE.IGNORE }))
    }

    @Test
    fun `verify calculatedRouteResult for invalid state result`() = runTest {
        coEvery { appModuleCommunicator.hasActiveDispatch(any(),any()) } returns true
        coEvery {
            routeETACalculationUseCase.routeCalculationResultFromLauncher(any(), any(), any(), DispatchActiveState.ACTIVE)
        } returns RouteCalculationResult()

        assertEquals(RouteCalculationResult(), dispatchDetailViewModel.calculatedRouteResult(RouteCalculationResult().apply { state = STATE.IGNORE }))
    }

    @Test
    fun `verify scheduleLateNotificationCheckWorker for empty stoplist`() {
        every { dispatchDetailViewModel.stopDetailList } returns CopyOnWriteArrayList()
        dispatchDetailViewModel.scheduleLateNotificationCheckWorker(caller = "test", isFromTripStart = false, workManager)
        coVerify(exactly = 0) { lateNotificationUseCase.scheduleLateNotificationCheckWorker(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `verify scheduleLateNotificationCheckWorker for valid stoplist`() {
        every { dispatchDetailViewModel.stopDetailList } returns CopyOnWriteArrayList<StopDetail>().also { it.add(StopDetail()) }
        every { lateNotificationUseCase.scheduleLateNotificationCheckWorker(any(), any(), any(), any(), any()) } just runs
        dispatchDetailViewModel.scheduleLateNotificationCheckWorker(caller = "test", isFromTripStart = false, workManager)
        coVerify(exactly = 1) { lateNotificationUseCase.scheduleLateNotificationCheckWorker(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `verify handleMapClearForInActiveDispatchTripPreview while having no active trip`() = runTest {
       coEvery { dataStoreManager.hasActiveDispatch(any(), any()) } returns false
        coEvery { dataStoreManager.getValue(SELECTED_DISPATCH_KEY, EMPTY_STRING) } returns "2121"
        every { tripCompletionUseCase.sendDispatchCompleteEventToCPIK(any()) } just runs
        dispatchDetailViewModel.handleMapClearForInActiveDispatchTripPreview()
        verify {
            tripCompletionUseCase.sendDispatchCompleteEventToCPIK(any())
        }
    }

    @Test
    fun `verify handleMapClearForInActiveDispatchTripPreview while having an active trip`() = runTest {
        coEvery { dataStoreManager.hasActiveDispatch(any(), any()) } returns true
        coEvery { dataStoreManager.getValue(SELECTED_DISPATCH_KEY, EMPTY_STRING) } returns "2121"
        every { tripCompletionUseCase.sendDispatchCompleteEventToCPIK(any()) } just runs
        dispatchDetailViewModel.handleMapClearForInActiveDispatchTripPreview()
        verify(timeout = TEST_DELAY_OR_TIMEOUT, exactly = 0) {
            tripCompletionUseCase.sendDispatchCompleteEventToCPIK(any())
        }
    }

    @Test
    fun `verify postStopsProcessing for unedited trip with no active stop`() {
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns false
        coEvery { fetchDispatchStopsAndActionsUseCase.getActionsOfStop(any(), any(), any()) } returns mutableListOf<Action>().also {
            it.add(Action(actionid = 0))
            it.add(Action(actionid = 1))
        }
        coEvery { dispatchBaseUseCase.handleActionAddAndUpdate(any(), any(), any()) } returns StopDetail()

        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "2024-03-03T05:36:04.934Z"))
            it.add(StopDetail(stopid = 1, completedTime = "2024-03-03T05:37:04.934Z"))
        }

        dispatchDetailViewModel.postStopsProcessing("test")

        coVerifyOrder {
            dispatchDetailViewModel.getSelectedDispatchId(any())
            dispatchDetailViewModel.updateStopDetailList(any(), any())
            dispatchDetailViewModel.sendStopsDataToMapsForRouteCalculation(any())
            dispatchDetailViewModel["updateRouteDataMapOnAllStopsCompleted"]()
        }
    }

    @Test
    fun `verify postStopsProcessing for edited trip with no active stop`() {
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns true

        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "2024-03-03T05:36:04.934Z"))
            it.add(StopDetail(stopid = 1, completedTime = "2024-03-03T05:37:04.934Z"))
        }

        dispatchDetailViewModel.postStopsProcessing("test")

        coVerifyOrder {
            dispatchDetailViewModel.getSelectedDispatchId(any())
            dispatchDetailViewModel.updateStopDetailList(any(), any())
            dispatchDetailViewModel["updateRouteDataMapOnAllStopsCompleted"]()
        }
    }

    @Test
    fun `verify postStopsProcessing for edited trip with an active stop`() {
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns false

        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "2024-03-03T05:36:04.934Z"))
            it.add(StopDetail(stopid = 1, completedTime = "")) // An active ongoing stop
        }

        dispatchDetailViewModel.postStopsProcessing("test")

        coVerifyOrder {
            dispatchDetailViewModel.getSelectedDispatchId(any())
            dispatchDetailViewModel.updateStopDetailList(any(), any())
        }
    }

    @Test
    fun `verify stop addition`() = runTest {
        val mockSharedFlow = MutableSharedFlow<String>()
        every { WorkflowEventBus.stopCountListenerEvents } returns mockSharedFlow
        mockkObject(Log)
        every { Log.d(any(), any()) } returns Unit

        dispatchDetailViewModel.registerDataStoreListener()

        mockSharedFlow.emit(ADDED)

        assertEquals(1, mockSharedFlow.subscriptionCount.value)

        verify(exactly = 1) {
            Log.d("DispatchDetailVM", any())
        }
    }

    @Test
    fun `verify stop removal`() = runTest {
        val mockSharedFlow = MutableSharedFlow<String>()
        every { WorkflowEventBus.stopCountListenerEvents } returns mockSharedFlow
        mockkObject(Log)
        every { Log.d(any(), any()) } returns Unit

        dispatchDetailViewModel.registerDataStoreListener()

        mockSharedFlow.emit(REMOVED)

        assertEquals(1, mockSharedFlow.subscriptionCount.value)

        verify(exactly = 1) {
            Log.d("DispatchDetailVM", any())
        }
    }

    @Test
    fun `verify for no duplicate datastore listener`() = runTest {
        val mockSharedFlow = MutableSharedFlow<String>()
        every { WorkflowEventBus.stopCountListenerEvents } returns mockSharedFlow

        repeat(3) {
            dispatchDetailViewModel.registerDataStoreListener()
        }

        assertEquals(1, mockSharedFlow.subscriptionCount.value)
    }

    @Test
    fun `verify startRouteCalculation for empty stops in a trip`() = runTest {
        every { routeETACalculationUseCase.resetTripInfoWidget(any()) } just runs

        dispatchDetailViewModel.startRouteCalculation("test")

        coVerify {
            routeETACalculationUseCase.resetTripInfoWidget(any())
        }
    }

    @Test
    fun `verify startRouteCalculation for 2 completed stops in a trip - The trip has only 2 stops`() = runTest {
        every { routeETACalculationUseCase.resetTripInfoWidget(any()) } just runs

        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "wewe"))
            it.add(StopDetail(stopid = 1, completedTime = "wewe"))
        }

        dispatchDetailViewModel.startRouteCalculation("test")

        coVerify {
            routeETACalculationUseCase.resetTripInfoWidget(any())
        }
    }

    @Test
    fun `verify startRouteCalculation for one active stops in a trip`() = runTest {
        every { routeETACalculationUseCase.resetTripInfoWidget(any()) } just runs
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs

        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "wewe"))
            it.add(StopDetail(stopid = 1, completedTime = ""))
        }

        dispatchDetailViewModel.startRouteCalculation("test")

        coVerify(exactly = 0) {
            routeETACalculationUseCase.resetTripInfoWidget(any())
        }
    }

    @Test
    fun `verify startRouteCalculation not invoked for preview trip state`() = runTest {
        dispatchDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.PREVIEWING)
        every { routeETACalculationUseCase.resetTripInfoWidget(any()) } just runs
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs

        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "wewe"))
            it.add(StopDetail(stopid = 1, completedTime = ""))
        }

        dispatchDetailViewModel.startRouteCalculation("test")

        coVerify(exactly = 0) {
            routeETACalculationUseCase.resetTripInfoWidget(any())
            routeETACalculationUseCase.startRouteCalculation(any(), any())
        }
    }

    @Test
    fun `verify startRouteCalculation invoked for NO_ACTIVE_TRIP state`() = runTest {
        dispatchDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.NO_TRIP_ACTIVE)
        every { routeETACalculationUseCase.resetTripInfoWidget(any()) } just runs
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs

        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "wewe"))
            it.add(StopDetail(stopid = 1, completedTime = ""))
        }

        dispatchDetailViewModel.startRouteCalculation("test")

        coVerify(exactly = 1) {
            routeETACalculationUseCase.startRouteCalculation(any(), any())
        }
    }

    @Test
    fun `verify startRouteCalculation not invoked for active trip state`() = runTest {
        dispatchDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.ACTIVE)
        every { routeETACalculationUseCase.resetTripInfoWidget(any()) } just runs
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs

        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "wewe"))
            it.add(StopDetail(stopid = 1, completedTime = ""))
        }

        dispatchDetailViewModel.startRouteCalculation("test")

        coVerify(exactly = 1) {
            routeETACalculationUseCase.startRouteCalculation(any(), any())
        }
    }

    @Test
    fun `verify selectStop for previous stops availability`() {
        val mockStopList = MutableLiveData<List<StopDetail>>()

        every { dispatchDetailViewModel.rearrangedStops } returns mockStopList

        mockStopList.value = mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 0))
            it.add(StopDetail(stopid = 1))
            it.add(StopDetail(stopid = 2))
        }

        dispatchDetailViewModel.selectStop(stopIndex = 1)

        assertEquals(true, dispatchDetailViewModel.previousStopAvailable.value)
    }

    @Test
    fun `verify handleMapClearForInActiveDispatchTripPreview is called in startRouteCalculation during active trip`() = runTest {
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        coEvery { dispatchDetailViewModel.handleMapClearForInActiveDispatchTripPreview() } just runs
        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = "12:00:00"))
            it.add(StopDetail(stopid = 1, completedTime = ""))
        }
        dispatchDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.ACTIVE)
        dispatchDetailViewModel.startRouteCalculation("test")
        coVerify(exactly = 1) {
            dispatchDetailViewModel.handleMapClearForInActiveDispatchTripPreview()
            routeETACalculationUseCase.startRouteCalculation(any(), any())
        }
    }

    @Test
    fun `verify handleMapClearForInActiveDispatchTripPreview is called in startRouteCalculation during non active trip`() = runTest {
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        coEvery { dispatchDetailViewModel.handleMapClearForInActiveDispatchTripPreview() } just runs
        dispatchDetailViewModel.stopDetailList.let {
            it.add(StopDetail(stopid = 0, completedTime = ""))
            it.add(StopDetail(stopid = 1, completedTime = ""))
        }
        dispatchDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.NO_TRIP_ACTIVE)
        dispatchDetailViewModel.startRouteCalculation("test")
        coVerify(exactly = 1) {
            dispatchDetailViewModel.handleMapClearForInActiveDispatchTripPreview()
            routeETACalculationUseCase.startRouteCalculation(any(), any())
        }
    }

    @Test
    fun `verify handleMapClearForInActiveDispatchTripPreview is called in startRouteCalculation during preview trip when there is active trip`() = runTest {
        dispatchDetailViewModel.setActiveDispatchFlowValue(DispatchActiveState.PREVIEWING)
        dispatchDetailViewModel.startRouteCalculation("test")
        coVerify(exactly = 0) {
            dispatchDetailViewModel.handleMapClearForInActiveDispatchTripPreview()
            routeETACalculationUseCase.startRouteCalculation(any(), any())
        }
    }

    @Test
    fun `verify selectStop for previous stops availability - The selected stop is 0`() {
        val mockStopList = MutableLiveData<List<StopDetail>>()

        every { dispatchDetailViewModel.rearrangedStops } returns mockStopList

        mockStopList.value = mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 0))
            it.add(StopDetail(stopid = 1))
            it.add(StopDetail(stopid = 2))
        }

        dispatchDetailViewModel.selectStop(stopIndex = 0)

        assertEquals(false, dispatchDetailViewModel.previousStopAvailable.value)
    }

    @Test
    fun `verify resetTripInfoWidgetIfThereIsNoActiveTrip for inactive dispatch`() {
        every { dispatchBaseUseCase.hasActiveDispatchKey(dataStoreManager) } returns flow { emit(false) }
        every { routeETACalculationUseCase.resetTripInfoWidget(any()) } just runs

        dispatchDetailViewModel.resetTripInfoWidgetIfThereIsNoActiveTrip()

        verify(timeout = TEST_DELAY_OR_TIMEOUT) {
            routeETACalculationUseCase.resetTripInfoWidget(any())
        }
    }

    @Test
    fun `verify resetTripInfoWidgetIfThereIsNoActiveTrip for active dispatch`() {
        every { dispatchBaseUseCase.hasActiveDispatchKey(dataStoreManager) } returns flow { emit(true) }
        every { routeETACalculationUseCase.resetTripInfoWidget(any()) } just runs

        dispatchDetailViewModel.resetTripInfoWidgetIfThereIsNoActiveTrip()

        verify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            routeETACalculationUseCase.resetTripInfoWidget(any())
        }
    }

    @Test
    fun `verify processEndTrip execution`() {
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulation() } just runs
        every { dispatchDetailViewModel.getWorkManagerInstance() } returns workManager
        coEvery { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any()) } just runs

        dispatchDetailViewModel.processEndTrip()

        coVerifyOrder {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulation()
            tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify listenForStopAdditionAndRemoval execution for a stop removal`() = runTest {
        val dispatchId = "2323"
        val stopUpdateStatus = MutableSharedFlow<String>()
        every { dispatchDetailViewModel.stopUpdateStatus } returns stopUpdateStatus
        coEvery { dispatchDetailViewModel.getSelectedDispatchId(any()) } returns dispatchId
        coEvery { dispatchBaseUseCase.getInActiveStopCount(dispatchId) } returns 4
        coEvery { dispatchBaseUseCase.getActiveStopCount(dispatchId) } returns 1
        coEvery { dispatchBaseUseCase.getManipulatedStopCount(any(), any(), any(), any()) } returns Triple(4, 1, true)
        coEvery { dispatchBaseUseCase.getDispatchActiveState(any(), any()) } returns DispatchActiveState.ACTIVE

        dispatchDetailViewModel.listenForStopAdditionAndRemoval()

        stopUpdateStatus.emit(REMOVED)

        coVerifyAll {
            with(dispatchBaseUseCase) {
                fetchAndStoreVIDFromDispatchData(any(), any(), any())
                getCidAndTruckNumber(any())
                getInActiveStopCount(dispatchId)
                getActiveStopCount(dispatchId)
                getManipulatedStopCount(any(), any(), any(), any())
                getDispatchActiveState(any(), any())
            }
        }
    }

    @Test
    fun `verify listenForStopAdditionAndRemoval execution for a stop addition`() = runTest {//verify why init is also verified
        val dispatchId = "2323"
        val stopUpdateStatus = MutableSharedFlow<String>()
        every { dispatchDetailViewModel.stopUpdateStatus } returns stopUpdateStatus
        coEvery { dispatchDetailViewModel.getSelectedDispatchId(any()) } returns dispatchId
        coEvery { dispatchBaseUseCase.getInActiveStopCount(dispatchId) } returns 4
        coEvery { dispatchBaseUseCase.getActiveStopCount(dispatchId) } returns 1
        coEvery { dispatchBaseUseCase.getManipulatedStopCount(any(), any(), any(), any()) } returns Triple(4, 1, true)

        dispatchDetailViewModel.listenForStopAdditionAndRemoval()

        stopUpdateStatus.emit(ADDED)

        coVerifyAll {
            with(dispatchBaseUseCase) {
                fetchAndStoreVIDFromDispatchData(any(), any(), any())
                getCidAndTruckNumber(any())
                getInActiveStopCount(dispatchId)
                getActiveStopCount(dispatchId)
                getManipulatedStopCount(any(), any(), any(), any())
                getDispatchActiveState(any(), any())
            }
        }
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is not empty and the hotkeys count is greater than 0`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { formLibraryUseCase.getHotKeysWithoutDescription(any(), any()) } returns flow { emit(
            mutableSetOf(HotKeys(hkId = 1), HotKeys(hkId = 2))
        ) }
        dispatchDetailViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        dispatchDetailViewModel.canShowHotKeysMenu()
        assertTrue(dispatchDetailViewModel.isHotKeysAvailable.value!!)
        dispatchDetailViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is not empty and the hotkeys count is 0`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { formLibraryUseCase.getHotKeysWithoutDescription(any(), any()) } returns flow { emit(
            mutableSetOf()
        ) }
        dispatchDetailViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        dispatchDetailViewModel.canShowHotKeysMenu()
        assertFalse(dispatchDetailViewModel.isHotKeysAvailable.value!!)
        dispatchDetailViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns EMPTY_STRING
        dispatchDetailViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        dispatchDetailViewModel.canShowHotKeysMenu()
        assertFalse(dispatchDetailViewModel.isHotKeysAvailable.value!!)
        dispatchDetailViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
        coVerify(exactly = 0) {
            formLibraryUseCase.getHotKeysWithoutDescription(any(), any())
        }
    }

    @After
    fun after() {
        unmockkAll()
    }

}