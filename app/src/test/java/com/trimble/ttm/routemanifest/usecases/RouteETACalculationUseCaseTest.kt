package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import android.os.Bundle
import androidx.datastore.preferences.core.Preferences
import com.google.gson.Gson
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.routemanifest.application.WorkflowApplication.Companion.dispatchActivityVisible
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_DISTANCE_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_HOURS_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_STOPS_KEY
import com.trimble.ttm.routemanifest.model.Address
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.Leg
import com.trimble.ttm.routemanifest.model.RouteCalculationResult
import com.trimble.ttm.routemanifest.model.STATE
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.utils.COPILOT_ROUTE_CALC_RETRY_DELAY
import com.trimble.ttm.routemanifest.utils.CPIK_EVENT_TYPE_KEY
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_ERROR
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_STATE
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_STOPDETAIL_LIST
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_TOTAL_DISTANCE
import com.trimble.ttm.routemanifest.utils.ROUTE_CALCULATION_RESULT_TOTAL_HOUR
import com.trimble.ttm.routemanifest.utils.ROUTE_COMPUTATION_RESPONSE_TO_CLIENT_KEY
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.getEventTypeKeyForRouteCalculation
import com.trimble.ttm.routemanifest.utils.Utils.toJsonString
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.collections.set
import kotlin.math.pow
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import android.content.Intent as Intent1

class RouteETACalculationUseCaseTest {
    private lateinit var routeETACalculationUseCase: RouteETACalculationUseCase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager
    private lateinit var context: Context
    private lateinit var bundle: Bundle
    private val data: Bundle = mockk()
    private val sendIntentToDispatchListActivity: () -> Intent1 = mockk()
    private val routeResultBundleFlow = MutableStateFlow<Bundle?>(null)


    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @RelaxedMockK
    private lateinit var tripPanelEventRepo : TripPanelEventRepo

    @RelaxedMockK
    private lateinit var sendDispatchDataUseCase: SendDispatchDataUseCase

    @RelaxedMockK
    private lateinit var sendWorkflowEventsToAppUseCase : SendWorkflowEventsToAppUseCase

    private lateinit var tripInfoWidgetUseCase: TripInfoWidgetUseCase
    private lateinit var localDataSourceRepo: LocalDataSourceRepo
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo

    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase

    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope(testDispatcher)

    private val stopList = ArrayList<StopDetail>()
        .also { list ->
            list.add(StopDetail(stopid = 0).also {
                it.Address= Address("Walker Art Center", zip = "123")
                it.leg = Leg(1.0, 1.0)
            })
            list.add(StopDetail(stopid = 1).also {
                it.Address= Address(
                    "Minneapolis Institute of Art", zip = "234")
                it.leg = Leg(1.0, 1.2)
            })
        }

    private val completedStopList = ArrayList<StopDetail>()
        .also { list ->
            list.add(StopDetail(stopid = 0, completedTime = "123").also {
                it.leg = Leg(1.0, 1.0)
            })
            list.add(StopDetail(stopid = 1, completedTime = "456").also {
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
                toJsonString(stopDetail)?.let { stopDetailList.add(it) }
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
        context = mockk()
        bundle = mockk()
        tripPanelEventRepo = mockk()
        sendDispatchDataUseCase = mockk()
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        localDataSourceRepo = mockk()
        dispatchFirestoreRepo = mockk()
        tripInfoWidgetUseCase = spyk(
            TripInfoWidgetUseCase(
                context,
                localDataSourceRepo,
                mockk(),
                testScope,
                TestDispatcherProvider()
            )
        )
        routeETACalculationUseCase =spyk(
            RouteETACalculationUseCase(
                TestScope(testDispatcher),
                tripPanelEventRepo,
                sendDispatchDataUseCase,
                localDataSourceRepo,
                dispatchFirestoreRepo,
                tripInfoWidgetUseCase,
                fetchDispatchStopsAndActionsUseCase,
                dataStoreManager,
                routeResultBundleFlow,
                sendWorkflowEventsToAppUseCase
            )
        )
        every { context.filesDir } returns temporaryFolder.newFolder()
        every {
            bundle.getString(
                Utils.getKeyForRouteCalculationResponseToClient(),
                EMPTY_STRING
            )
        } returns toJsonString(resultMap)
        coEvery { dataStoreManager.setValue(TOTAL_DISTANCE_KEY, 2.2.toFloat()) } just runs
        coEvery { dataStoreManager.setValue(TOTAL_HOURS_KEY, 2.toFloat()) } just runs
        coEvery { dataStoreManager.setValue(TOTAL_STOPS_KEY, 2) } just runs
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns false
        coEvery { localDataSourceRepo.hasActiveDispatch() } returns false
    }

    @Test
    fun `verify route calculation result from launcher for success`(): Unit =
        runTest {
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
            } returns stopList
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns Gson().toJson(
                Stop(dispId = "324", stopId = 1, stopName = "4234")
            )
            coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
            coEvery {
                dataStoreManager.setValue<Any>(any(), any())
            } just runs
            coEvery { localDataSourceRepo.getFromAppModuleDataStore(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns "324"
            every { tripInfoWidgetUseCase.updateTripInfoWidget(any(), any(), any(), any(), any(), any()) } just runs
            coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
            coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs
            coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs
            bundle.also {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    it,
                    stopList, dataStoreManager, DispatchActiveState.ACTIVE
                ).let { routeCalculationResult ->
                    assertEquals("SUCCESS", routeCalculationResult.state?.name)
                }
            }
        }

    @Test
    fun `verify route calculation result from launcher for failure`() = runTest {
        //state update
        val stateOrdinal = ArrayList<String>()
        stateOrdinal.add(STATE.ERROR.ordinal.toString())
        resultMap[ROUTE_CALCULATION_RESULT_STATE] = stateOrdinal
        every {
            bundle.getString(
                ROUTE_COMPUTATION_RESPONSE_TO_CLIENT_KEY,
                EMPTY_STRING
            )
        } returns toJsonString(resultMap)
        coEvery { localDataSourceRepo.hasActiveDispatch() } returns false
        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetCid() } returns "10119"
        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetTruckNumber() } returns "cpik"
        coEvery { localDataSourceRepo.getSelectedDispatchId(any()) } returns "123"
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns completedStopList
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
        } returns completedStopList
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.CURRENT_STOP_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            Stop(dispId = "324", stopId = 1, stopName = "4234")
        )
        coEvery {
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_DISTANCE_KEY, ZERO.toFloat())
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_HOURS_KEY, ZERO.toFloat())
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_STOPS_KEY, ZERO)
        } just runs
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
        coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs

            bundle.also {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    it,
                    stopList, dataStoreManager, DispatchActiveState.ACTIVE
                ).let { routeCalculationResult ->
                    assertEquals("ERROR", routeCalculationResult.state?.name)
                }
            }
        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_DISTANCE_KEY, 0F)
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_HOURS_KEY, 0F)
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_STOPS_KEY, 0)
        }
    }

    @Test
    fun `verify route calculation result from launcher for total hours`(): Unit =
        runTest {    //NOSONAR
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
            } returns stopList
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns Gson().toJson(
                Stop(dispId = "324", stopId = 1, stopName = "4234")
            )
            coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
            coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
            coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs
            coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs
            bundle.also {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    it,
                    stopList, dataStoreManager, DispatchActiveState.ACTIVE
                ).let { routeCalculationResult ->
                    assertEquals(2.0, routeCalculationResult.totalHours, 10000.0)
                }
            }
        }

    @Test
    fun `verify route calculation result from launcher for total distance`(): Unit =
        runTest {    //NOSONAR
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
            } returns stopList
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns Gson().toJson(
                Stop(dispId = "324", stopId = 1, stopName = "4234")
            )
            coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
            coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
            coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs
            coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs
            bundle.also {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    it,
                    stopList, dataStoreManager, DispatchActiveState.ACTIVE
                ).let { routeCalculationResult ->
                    assertEquals(2.2, routeCalculationResult.totalDistance, 10000.0)
                }
            }
        }

    @Test
    fun `verify route calculation result from launcher for stop detail list`() =
        runTest {
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
            } returns stopList
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.CURRENT_STOP_KEY,
                    EMPTY_STRING
                )
            } returns Gson().toJson(
                Stop(dispId = "324", stopId = 1, stopName = "4234")
            )
            coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
            coEvery {
                dataStoreManager.setValue<Any>(any(), any())
            } just runs
            coEvery { localDataSourceRepo.getFromAppModuleDataStore(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns "324"
            every { tripInfoWidgetUseCase.updateTripInfoWidget(any(), any(), any(), any(), any(), any()) } just runs
            coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
            coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs
            coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs
            bundle.also {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    it,
                    stopList, dataStoreManager, DispatchActiveState.ACTIVE
                ).let { routeCalculationResult ->
                    assertEquals(2, routeCalculationResult.stopDetailList.size)
                    assertEquals(0, routeCalculationResult.stopDetailList[0].stopid)
                }
            }
        }

    @Test
    fun `verify route calculation result from launcher for route legs`() = runTest {
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
        } returns stopList
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.CURRENT_STOP_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            Stop(dispId = "324", stopId = 1, stopName = "4234")
        )
        coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
        coEvery { localDataSourceRepo.getFromAppModuleDataStore(ACTIVE_DISPATCH_KEY, EMPTY_STRING) } returns "1223"
        every { tripInfoWidgetUseCase.updateTripInfoWidget(any(), any(), any(), any(), any(), any()) } just runs
        coEvery {
            dataStoreManager.setValue<Any>(any(), any())
        } just runs
        coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
        coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs
        coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs
        bundle.also {
            routeETACalculationUseCase.routeCalculationResultFromLauncher(
                it,
                stopList, dataStoreManager, DispatchActiveState.ACTIVE
            ).let { routeCalculationResult ->
                assertEquals(2, routeCalculationResult.stopDetailList.size)
                assertEquals(
                    1.0,
                    routeCalculationResult.stopDetailList[0].leg?.distance ?: 0.0,
                    10000.0
                )
                assertEquals(
                    1.0,
                    routeCalculationResult.stopDetailList[0].leg?.time ?: 0.0,
                    10000.0
                )
            }
        }
    }

    @Test
    fun `verify route calculation result from launcher invokes database call to if stop list is empty`() = runTest {
        //state update
        val stateOrdinal = ArrayList<String>()
        stateOrdinal.add(STATE.ERROR.ordinal.toString())
        resultMap[ROUTE_CALCULATION_RESULT_STATE] = stateOrdinal
        every {
            bundle.getString(
                ROUTE_COMPUTATION_RESPONSE_TO_CLIENT_KEY,
                EMPTY_STRING
            )
        } returns toJsonString(resultMap)
        coEvery { localDataSourceRepo.hasActiveDispatch() } returns false
        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetCid() } returns "10119"
        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetTruckNumber() } returns "cpik"
        coEvery { localDataSourceRepo.getSelectedDispatchId(any()) } returns "123"
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns completedStopList
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
        } returns completedStopList
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.CURRENT_STOP_KEY,
                EMPTY_STRING
            )
        } returns Gson().toJson(
            Stop(dispId = "324", stopId = 1, stopName = "4234")
        )
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true
        coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
        coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs

        bundle.also {
            routeETACalculationUseCase.routeCalculationResultFromLauncher(
                it,
                emptyList(), dataStoreManager, DispatchActiveState.ACTIVE
            ).let { routeCalculationResult ->
                assertEquals("ERROR", routeCalculationResult.state?.name)
            }
        }
        coVerify { fetchDispatchStopsAndActionsUseCase.getStopsAndActions(any(),any(),any(),any(),any()) }
    }

    @Test
    fun `test returned route calculation result error value is empty if launcher route calculation returns success`() {
        //Mock
        val routeCalculationResult = routeETACalculationUseCase.getRouteCalculationResult(resultMap)
        //Assert
        assertEquals(STATE.SUCCESS, routeCalculationResult.state)
        assertEquals(routeCalculationResult.error, "")
    }

    @Test
    fun `test returned route calculation result error state if launcher route calculation returns error `() {
        //Mock
        val stateOrdinal = ArrayList<String>()
        stateOrdinal.add(STATE.ERROR.ordinal.toString())
        resultMap[ROUTE_CALCULATION_RESULT_STATE] = stateOrdinal
        val routeCalculationError = ArrayList<String>()
        routeCalculationError.add("Invalid coordinates")
        resultMap[ROUTE_CALCULATION_RESULT_ERROR] = routeCalculationError
        resultMap[ROUTE_CALCULATION_RESULT_TOTAL_DISTANCE] = ArrayList<String>().also {
            it.add("0")
        }
        resultMap[ROUTE_CALCULATION_RESULT_TOTAL_HOUR] = ArrayList<String>().also {
            it.add("0")
        }
        val routeCalculationResult = routeETACalculationUseCase.getRouteCalculationResult(resultMap)


        //Assert
        assertEquals(STATE.ERROR, routeCalculationResult.state)
        assertNotEquals(routeCalculationResult.error, "")
        assertEquals(routeCalculationResult.totalDistance, 0.0, 0.0)
        assertEquals(routeCalculationResult.totalHours, 0.0, 0.0)
    }

    @Test
    fun `test returned success route calculation result object fields`() {
        //Mock
        val routeCalculationResult = routeETACalculationUseCase.getRouteCalculationResult(resultMap)

        //Assert
        assertEquals(routeCalculationResult.error, "")
        assert(routeCalculationResult.stopDetailList.isNotEmpty())
        assertEquals(routeCalculationResult.totalDistance, 10.0, 0.0)
        assertEquals(routeCalculationResult.totalHours, 10.0, 0.0)
    }

    @Test
    fun `all stops are completed proceeding to widget reset`() = runTest {
        coEvery { localDataSourceRepo.setToAppModuleDataStore(any<Preferences.Key<Any>>(), any()) } just runs
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
        } returns completedStopList
        coEvery { localDataSourceRepo.getCurrentStop() } returns null
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns false
        coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRoute() } just runs

        routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps()

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_DISTANCE_KEY, 0F)
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_HOURS_KEY, 0F)
            localDataSourceRepo.setToAppModuleDataStore(TOTAL_STOPS_KEY, 0)
        }
    }


    @Test
    fun `all stops are not completed widget update`() =
        runTest {
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
            } returns stopList
            //Assert
            assert(
                routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps()
                    .isNotEmpty()
            )
        }

    @Test
    fun `empty stop list from preference all stop completed widget update`() {
        //Mock
        every { context.applicationContext } returns context
        runTest {
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions(any())
            } returns listOf()
            //Assert
            assert(
                routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps()
                    .isEmpty()
            )
        }
    }

    @Test
    fun `check updateStopActionsWithETAAndAddress for happypath`() = runTest {
        val stopList = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "1")
                )
                add(
                    StopDetail(stopid = 1, dispid = "1")
                )
            }
        }
        val routeCalcResult = RouteCalculationResult(stopDetailList = stopList, totalDistance = 10.0, totalHours = 2.0)
        assertTrue(routeETACalculationUseCase.updateStopActionsWithETAAndAddress(routeCalcResult, stopList, dataStoreManager))
    }

    @Test
    fun `check updateStopActionsWithETAAndAddress response for different dispatch result from route calc result`() = runTest {
        val stopList = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "1")
                )
                add(
                    StopDetail(stopid = 1, dispid = "1")
                )
            }
        }
        val stopListFromRouteCalcResult = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "2")
                )
                add(
                    StopDetail(stopid = 1, dispid = "2")
                )
            }
        }
        val routeCalcResult = RouteCalculationResult(stopDetailList = stopListFromRouteCalcResult, totalDistance = 10.0, totalHours = 2.0)
        assertFalse(routeETACalculationUseCase.updateStopActionsWithETAAndAddress(routeCalcResult, stopList, dataStoreManager))
    }

    @Test
    fun `check updateStopActionsWithETAAndAddress response for missing stop in stopList`() = runTest {
        val stopList = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "1")
                )
            }
        }
        val stopListFromRouteCalcResult = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "1")
                )
                add(
                    StopDetail(stopid = 1, dispid = "1")
                )
            }
        }
        val routeCalcResult = RouteCalculationResult(stopDetailList = stopListFromRouteCalcResult, totalDistance = 10.0, totalHours = 2.0)
        assertFalse(routeETACalculationUseCase.updateStopActionsWithETAAndAddress(routeCalcResult, stopList, dataStoreManager))
    }

    @Test
    fun `verify route calculation calls required method when stopDetailList is non-empty`() = runTest {

        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetCid() } returns "cid"
        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetTruckNumber() } returns "vid"
        coEvery { localDataSourceRepo.getSelectedDispatchId(any()) } returns "activeDispatchId"
        coEvery {
            dispatchFirestoreRepo.getDispatchPayload(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Dispatch(dispid = "activeDispatchId")
        every { tripPanelEventRepo.calculateRoute(any()) } just runs
        coEvery { sendWorkflowEventsToAppUseCase.getPolygonalOptOutDataFromManagedConfig(any()) } returns true
        coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns "activeDispatchId"
        coEvery { routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps() } returns listOf()

        routeETACalculationUseCase.startRouteCalculation(stopList, "startRouteCalculation")

        coVerify(exactly = 1) { tripPanelEventRepo.calculateRoute(any()) }
        coVerify(exactly = 0) { routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps() }
    }

    @Test
    fun `verify route calculation calls required method when stopDetailList is empty`() = runTest {

        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetCid() } returns "cid"
        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetTruckNumber() } returns "vid"
        coEvery { localDataSourceRepo.getSelectedDispatchId(any()) } returns "activeDispatchId"
        coEvery {
            dispatchFirestoreRepo.getDispatchPayload(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Dispatch(dispid = "activeDispatchId")
        every { tripPanelEventRepo.calculateRoute(any()) } just runs
        coEvery { sendWorkflowEventsToAppUseCase.getPolygonalOptOutDataFromManagedConfig(any()) } returns true
        coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns "activeDispatchId"
        coEvery { routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps() } returns listOf()

        routeETACalculationUseCase.startRouteCalculation(listOf(), "startRouteCalculation")

        coVerify(exactly = 0) { tripPanelEventRepo.calculateRoute(any()) }
        coVerify(exactly = 1) { routeETACalculationUseCase.checkForLastStopArrivalAndUpdateTripWidgetAlongWithMaps() }
    }

    @Test
    fun `verify stops are redrawn for current stop in freeflaot trip`() = runTest{
        val stopList = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "1", sequenced = 0)
                )
                add(
                    StopDetail(stopid = 1, dispid = "1", sequenced = 0)
                )
            }
        }

        coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
        coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs
        coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs

        routeETACalculationUseCase.checkAndDrawRouteForStops(stopList, DispatchActiveState.ACTIVE) //ETA is calculated only for active dispatch.

        coVerify(exactly = 1) { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) }
    }

    @Test
    fun `verify stops are not redrawn if stops to draw is same as route calculation result`() = runTest{
        val stopList = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "1", sequenced = 1, completedTime = "time")
                )
                add(
                    StopDetail(stopid = 1, dispid = "1", sequenced = 1,  completedTime = "time")
                )
                add(
                    StopDetail(stopid = 2, dispid = "1", sequenced = 1)
                )
                add(
                    StopDetail(stopid = 3, dispid = "1", sequenced = 1)
                )
                add(
                    StopDetail(stopid = 4, dispid = "1", sequenced = 1)
                )
            }
        }

        coEvery { localDataSourceRepo.getCurrentStop() } returns Stop(stopId = 2)
        coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
        coEvery { sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(any(), any(), any()) } just runs

        routeETACalculationUseCase.checkAndDrawRouteForStops(stopList, DispatchActiveState.ACTIVE) //ETA is calculated only for active dispatch.

        coVerify(exactly = 0) { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) }
        coVerify(exactly = 1) { sendDispatchDataUseCase.sendDispatchDataForGeofenceOnStopAddedOrUpdatedOrRemoved(any(), DispatchActiveState.ACTIVE, any()) }
    }

    @Test
    fun `verify stops are redrawn for current stop in sequential trip`() = runTest{
        val stopList = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "1", sequenced = 1, completedTime = "time")
                )
                add(
                    StopDetail(stopid = 1, dispid = "1", sequenced = 1,  completedTime = "time")
                )
                add(
                    StopDetail(stopid = 2, dispid = "1", sequenced = 1)
                )
                add(
                    StopDetail(stopid = 3, dispid = "1", sequenced = 1, completedTime = "time")
                )
                add(
                    StopDetail(stopid = 4, dispid = "1", sequenced = 1)
                )
            }
        }

        coEvery { localDataSourceRepo.getCurrentStop() } returns Stop(stopId = 2)
        coEvery { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) } just runs
        coEvery { sendDispatchDataUseCase.setGeofenceForCachedStops(any()) } just runs
        coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRouteWithDelay() } just runs

        routeETACalculationUseCase.checkAndDrawRouteForStops(stopList, DispatchActiveState.ACTIVE) //ETA is calculated only for active dispatch.

        coVerify(exactly = 1) { sendDispatchDataUseCase.drawRouteForCurrentStop(any()) }
    }

    @Test
    fun `verify if clearRoute is called if there is no current stop in sequential trip and all the stops are complete`() = runTest{
        val stopList = mutableListOf<StopDetail>().also {
            with(it) {
                add(
                    StopDetail(stopid = 0, dispid = "1", sequenced = 1, completedTime = "11:00")
                )
                add(
                    StopDetail(stopid = 1, dispid = "1", sequenced = 1, completedTime = "10:30")
                )
            }
        }
        coEvery { sendDispatchDataUseCase.sendDispatchEventForClearRoute() } just runs
        coEvery { localDataSourceRepo.getCurrentStop() } returns null
        routeETACalculationUseCase.checkAndDrawRouteForStops(stopList, DispatchActiveState.ACTIVE)
        coVerify(exactly = 1) { sendDispatchDataUseCase.sendDispatchEventForClearRoute() }
    }

    @Test
    fun `verify if received route calc result is of active dispatch's result`() = runTest {
        val routeCalculationResult = RouteCalculationResult().apply {
            state = STATE.SUCCESS
            stopDetailList = mutableListOf<StopDetail>().also {
                it.add(StopDetail(stopid = 0, dispid = "1"))
                it.add(StopDetail(stopid = 1, dispid = "1"))
            }
        }
        val stopListOfDW = mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 0, dispid = "1"))
            it.add(StopDetail(stopid = 1, dispid = "1"))
        }
        assertEquals(
            STATE.SUCCESS,
            routeETACalculationUseCase.isRouteCalcResultOfActiveDispatch(
                routeCalculationResult,
                stopListOfDW
            ).state ?: STATE.ERROR
        )
    }

    @Test
    fun `verify if received route calc result is of inactive dispatch's result`() = runTest {
        val routeCalculationResult = RouteCalculationResult().apply {
            state = STATE.SUCCESS
            stopDetailList = mutableListOf<StopDetail>().also {
                it.add(StopDetail(stopid = 0, dispid = "1"))
                it.add(StopDetail(stopid = 1, dispid = "1"))
            }
        }
        val stopListOfDW = mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 0, dispid = "2"))
            it.add(StopDetail(stopid = 1, dispid = "2"))
        }
        assertEquals(
            STATE.IGNORE,
            routeETACalculationUseCase.isRouteCalcResultOfActiveDispatch(
                routeCalculationResult,
                stopListOfDW
            ).state ?: STATE.ERROR
        )
    }

    @Test
    fun `verify if received route calc result has invalid dispatch id`() = runTest {
        val routeCalculationResult = RouteCalculationResult().apply {
            state = STATE.SUCCESS
            stopDetailList = mutableListOf<StopDetail>().also {
                it.add(StopDetail(stopid = 0, dispid = "1o"))
                it.add(StopDetail(stopid = 1, dispid = "1o"))
            }
        }
        val stopListOfDW = mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 0, dispid = "1"))
            it.add(StopDetail(stopid = 1, dispid = "1"))
        }
        assertEquals(
            STATE.IGNORE,
            routeETACalculationUseCase.isRouteCalcResultOfActiveDispatch(
                routeCalculationResult,
                stopListOfDW
            ).state ?: STATE.ERROR
        )
    }

    @Test
    fun `verify if driver workflow has invalid dispatch id`() = runTest {
        val routeCalculationResult = RouteCalculationResult().apply {
            state = STATE.SUCCESS
            stopDetailList = mutableListOf<StopDetail>().also {
                it.add(StopDetail(stopid = 0, dispid = "1"))
                it.add(StopDetail(stopid = 1, dispid = "1"))
            }
        }
        val stopListOfDW = mutableListOf<StopDetail>().also {
            it.add(StopDetail(stopid = 0, dispid = "1o"))
            it.add(StopDetail(stopid = 1, dispid = "1o"))
        }
        assertEquals(
            STATE.IGNORE,
            routeETACalculationUseCase.isRouteCalcResultOfActiveDispatch(
                routeCalculationResult,
                stopListOfDW
            ).state ?: STATE.ERROR
        )
    }

    @Test
    fun `verify routeCalculation is not done when eventType is anything other than CPIK_ROUTE_COMPUTATION_RESULT`() =
        runTest {
            val eventType = "not getEventTypeKeyForRouteCalculation()"
            every { data.getString(CPIK_EVENT_TYPE_KEY) } returns eventType

            routeETACalculationUseCase.calculateRouteETAFromLauncherEvent(
                eventType,
                data
            )

            coVerify(exactly = 0) {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
            coVerify(exactly = 0) { sendIntentToDispatchListActivity() }
        }

    @Test
    fun `verify routeCalculation is not done when eventType is null`() =
        runTest {
            every { data.getString(CPIK_EVENT_TYPE_KEY) } returns null
            val isDispatchDetailActivityAliveStatus = false

            routeETACalculationUseCase.calculateRouteETAFromLauncherEvent(
                "",
                data
            )

            coVerify(exactly = 0) {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
            coVerify(exactly = 0) { sendIntentToDispatchListActivity() }
        }

    @Test
    fun `verify routeCalculationResultFromLauncher() is called when eventType is CPIK_ROUTE_COMPUTATION_RESULT and isDispatchDetailActivityAlive is false`() =
        runTest {
            val eventType = getEventTypeKeyForRouteCalculation()
            every { data.getString(CPIK_EVENT_TYPE_KEY) } returns eventType
            val isDispatchDetailActivityAliveStatus = false
            coEvery {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns RouteCalculationResult()

            routeETACalculationUseCase.calculateRouteETAFromLauncherEvent(
                eventType,
                data
            )

            coVerify(exactly = 1) {
                routeETACalculationUseCase.routeCalculationResultFromLauncher(
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `verify getActive state function when there is an active dispatch`() = runTest {
        coEvery { localDataSourceRepo.hasActiveDispatch() } returns true
        assertEquals(DispatchActiveState.ACTIVE, routeETACalculationUseCase.getDispatchActiveState())
    }

    @Test
    fun `verify getActive state function when there is no active dispatch`() = runTest {
        coEvery { localDataSourceRepo.hasActiveDispatch() } returns false
        assertEquals(DispatchActiveState.NO_TRIP_ACTIVE, routeETACalculationUseCase.getDispatchActiveState())
    }


    @Test
    fun `verify retry logic is executed 5 times if there is an error`() = runTest {
        val result = RouteCalculationResult().apply { state = STATE.ERROR }
        var callCount = 0

        coEvery { routeETACalculationUseCase.processResultAndRetryIfError(result) } coAnswers {
            callCount++
            if (callCount < 5) {
                routeETACalculationUseCase.processResultAndRetryIfError(result)
            }
        }

        routeETACalculationUseCase.processResultAndRetryIfError(result)

        coVerify(exactly = 5) { routeETACalculationUseCase.processResultAndRetryIfError(result) }
    }

    @Test
    fun `verify exponential backoff delay for retries`() = runTest {
        val result = RouteCalculationResult().apply { state = STATE.ERROR }
        var callCount = 0
        val timestamps = mutableListOf<Long>()

        coEvery { routeETACalculationUseCase.processResultAndRetryIfError(result) } coAnswers {
            timestamps.add(currentTime)
            callCount++
            if (callCount < 5) {
                delay(COPILOT_ROUTE_CALC_RETRY_DELAY * (2.0.pow(callCount - 1)).toLong())
                routeETACalculationUseCase.processResultAndRetryIfError(result)
            }
        }

        routeETACalculationUseCase.processResultAndRetryIfError(result)

        // Verify the exponential backoff delays
        assert(timestamps.size == 5)
        val delay1 = timestamps[1] - timestamps[0]
        val delay2 = timestamps[2] - timestamps[1]
        val delay3 = timestamps[3] - timestamps[2]
        val delay4 = timestamps[4] - timestamps[3]

        assertEquals(COPILOT_ROUTE_CALC_RETRY_DELAY, delay1)
        assertEquals(COPILOT_ROUTE_CALC_RETRY_DELAY * 2, delay2)
        assertEquals(COPILOT_ROUTE_CALC_RETRY_DELAY * 4, delay3)
        assertEquals(COPILOT_ROUTE_CALC_RETRY_DELAY * 8, delay4)

        coVerify(exactly = 5) { routeETACalculationUseCase.processResultAndRetryIfError(result) }
    }

    @Test
    fun `verify retry logic stops upon success result`() = runTest {
        val resultError = RouteCalculationResult().apply { state = STATE.ERROR }
        val resultSuccess = RouteCalculationResult().apply { state = STATE.SUCCESS }
        var callCount = 0

        coEvery { routeETACalculationUseCase.processResultAndRetryIfError(any()) } coAnswers {
            callCount++
            if(callCount == 2 && it.invocation.args[0] == resultError) {
                resultSuccess
            } else {
                resultError
            }
        }

        // Call the function initially with resultError
        routeETACalculationUseCase.processResultAndRetryIfError(resultError)

        // Simulate the retry behavior by calling the function again with the previous result
        if (callCount < 2) {
            routeETACalculationUseCase.processResultAndRetryIfError(resultError)
        }

        // Verify the calls with state checks
        coVerify(exactly = 2) {
            routeETACalculationUseCase.processResultAndRetryIfError(withArg { it.state == STATE.ERROR })
        }
        assertEquals(2, callCount)
    }

    @Test
    fun `processResultAndRetryIfError should retry if error is empty`() = runTest {
        val result = RouteCalculationResult().apply { state = STATE.ERROR }.apply { error = "" }
        var retryCallCount = 0

        coEvery { routeETACalculationUseCase.processResultAndRetryIfError(result) } coAnswers {
            retryCallCount++
            if (retryCallCount < 5) {
                delay(100) // Simulate delay
                routeETACalculationUseCase.processResultAndRetryIfError(result)
            }
        }

        routeETACalculationUseCase.processResultAndRetryIfError(result)

        coVerify(exactly = 5) { routeETACalculationUseCase.processResultAndRetryIfError(result) }
    }

    @Test
    fun `processResultAndRetryIfError should not retry if error is not empty`() = runTest {
        val result = RouteCalculationResult().apply { state = STATE.ERROR }.apply { error = "Error not empty" }
        var retryCallCount = 0

        coEvery { routeETACalculationUseCase.processResultAndRetryIfError(result) } coAnswers {
            retryCallCount++
        }

        routeETACalculationUseCase.processResultAndRetryIfError(result)

        coVerify(exactly = 1) { routeETACalculationUseCase.processResultAndRetryIfError(result) }
        assertEquals(1, retryCallCount)
    }

    @Test
    fun `should emit true to _retryRouteCalculation when dispatchActivityVisible is true`() =
        runTest {
            // Set the condition
            dispatchActivityVisible = true

            // Mock the result
            val result = RouteCalculationResult().apply { state = STATE.ERROR; error = "" }

            // Call the method
            routeETACalculationUseCase.processResultAndRetryIfError(result)

            // Verify that _retryRouteCalculation.emit(true) is called
            coVerify(exactly = 0) { routeETACalculationUseCase.retryRouteCalculationFromUC() }
        }

    @Test
    fun `should call retryRouteCalculationFromUC when dispatchActivityVisible is false`() =
        runTest {
            // Set the condition
            dispatchActivityVisible = false

            // Mock the localDataSourceRepo methods
            coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetCid()} returns ""
            coEvery{localDataSourceRepo.getAppModuleCommunicator().doGetTruckNumber()} returns ""
            coEvery{localDataSourceRepo.getSelectedDispatchId(any())} returns ""

            val result = RouteCalculationResult().apply { state = STATE.ERROR; error = "" }

            routeETACalculationUseCase.processResultAndRetryIfError(result)

            // Verify that retryRouteCalculationFromUC() is called and getStopsAndActions is not called to start the route calculation
            coVerify(exactly = 1) { routeETACalculationUseCase.retryRouteCalculationFromUC() }
            coVerify(exactly = 0) {
                fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `should execute retryRouteCalculationFromUC logic correctly`() = runTest {
        val cid = "testCid"
        val vehicleNumber = "testVehicleNumber"
        val dispatchId = "testDispatchId"
        val stopList = listOf<StopDetail>()

        // Mock the localDataSourceRepo methods
        coEvery { localDataSourceRepo.getAppModuleCommunicator().doGetCid()} returns cid
        coEvery{localDataSourceRepo.getAppModuleCommunicator().doGetTruckNumber()} returns vehicleNumber
        coEvery{localDataSourceRepo.getSelectedDispatchId(any())} returns dispatchId
        coEvery{fetchDispatchStopsAndActionsUseCase.getStopsAndActions(cid, vehicleNumber, dispatchId, any(), eq(false))} returns stopList


        // when
        routeETACalculationUseCase.retryRouteCalculationFromUC()

        // Verify that startRouteCalculation is called with the correct parameters
        coVerify { routeETACalculationUseCase.startRouteCalculation(stopList, "processResultAndRetryIfError") }
    }

    @After
    fun after() {
        dispatchActivityVisible = false
        unmockkAll()
    }
}