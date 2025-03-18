package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import androidx.work.WorkManager
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.DISPATCH_NAME_KEY
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.commons.model.SiteCoordinate
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.TripStartInfo
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_TIMEOUT
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.getDistanceBetweenLatLongs
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class TripStartUseCaseTest {

    private lateinit var tripStartUseCase: TripStartUseCase
    private val dataStoreManager = mockk<DataStoreManager>(relaxed = true)
    private val fetchDispatchStopsAndActionsUseCase = mockk<FetchDispatchStopsAndActionsUseCase>()
    private val dispatchBaseUseCase = mockk<DispatchBaseUseCase>()
    private val dispatchStopsUseCase = mockk<DispatchStopsUseCase>()
    private val lateNotificationUseCase = mockk<LateNotificationUseCase>()
    private val backboneUseCase = mockk<BackboneUseCase>()
    private val tripPanelUseCase = mockk<TripPanelUseCase>()
    private val sendDispatchDataUseCase = mockk<SendDispatchDataUseCase>()
    private val routeETACalculationUseCase = mockk<RouteETACalculationUseCase>()
    private val sendWorkflowEventsToAppUseCase = mockk<SendWorkflowEventsToAppUseCase>()
    private val appModuleCommunicator = mockk<AppModuleCommunicator>()
    private val serviceManager = mockk<ServiceManager>()

    @RelaxedMockK
    private lateinit var application: WorkflowApplication
    @RelaxedMockK
    private lateinit var context: Context
    @RelaxedMockK
    private lateinit var workManager: WorkManager
    private val testScope = TestScope()


    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        mockkObject(WorkflowApplication)
        mockkObject(Utils)
        mockkObject(ApplicationContextProvider)
        every { application.applicationContext } returns application
        every { ApplicationContextProvider.getApplicationContext() } returns application.applicationContext
        mockkStatic(WorkManager::class)
        val mockedWorkManager = mockk<WorkManager>()
        every { WorkManager.getInstance(any()) } returns mockedWorkManager
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { appModuleCommunicator.doGetCid() } returns "1234"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "testVehicle1234"
        tripStartUseCase = spyk(TripStartUseCase(
            coroutineDispatcherProvider = TestDispatcherProvider(),
            dataStoreManager = dataStoreManager,
            fetchDispatchStopsAndActionsUseCase = fetchDispatchStopsAndActionsUseCase,
            dispatchBaseUseCase = dispatchBaseUseCase,
            dispatchStopsUseCase = dispatchStopsUseCase,
            lateNotificationUseCase = lateNotificationUseCase,
            backboneUseCase = backboneUseCase,
            tripPanelUseCase = tripPanelUseCase,
            sendWorkflowEventsToAppUseCase = sendWorkflowEventsToAppUseCase,
            sendDispatchDataUseCase = sendDispatchDataUseCase,
            routeETACalculationUseCase = routeETACalculationUseCase,
            appModuleCommunicator = appModuleCommunicator,
            serviceManager = serviceManager,
            context = context
        ))
    }


    @Test
    fun `test putStopsIntoPreference calls data store set if dispatch id started and active dispatch id matches`() = runTest {
        val tripStartInfo = TripStartInfo(
            stopDetailList = listOf(StopDetail()),
            timeInMillis = System.currentTimeMillis(),
            pfmEventsInfo = mockk(),
            dispatchId = "dispatchId",
            cid = "cid",
            vehicleNumber = "vehicleNumber",
            tripStartCaller = TripStartCaller.AUTO_TRIP_START_BACKGROUND,
            caller = "caller"
        )
        val stopList = CopyOnWriteArrayList(listOf(StopDetail(completedTime = "")))

        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "dispatchId"

        tripStartUseCase.putStopsIntoPreference(tripStartInfo, stopList)

        coVerify {dataStoreManager.setValue(DataStoreManager.STOPS_SERVICE_REFERENCE_KEY, any())}
    }

    @Test
    fun `test updateDispatchInfoInDataStore calls data store is updated with dispatchId when selected_dispatch_key is empty`() = runTest {
        val dispatchId = "123456789"
        val dispatchName = "testDispatch1234"
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.SELECTED_DISPATCH_KEY,
                EMPTY_STRING
            )
        } returns EMPTY_STRING
        tripStartUseCase.updateDispatchInfoInDataStore(dispatchId, dispatchName, "TEST_VALUE")

        coVerify { dataStoreManager.setValue(DataStoreManager.SELECTED_DISPATCH_KEY, dispatchId) }
        coVerify { dataStoreManager.setValue(DataStoreManager.DISPATCH_NAME_KEY, dispatchName) }
        coVerify { dataStoreManager.setValue(DataStoreManager.CURRENT_DISPATCH_NAME_KEY, dispatchName) }
    }

    @Test
    fun `test updateDispatchInfoInDataStore calls data store is not updated with dispatchId when selected_dispatch_key is not empty`() = runTest {
        val dispatchId = "123456789"
        val dispatchName = "testDispatch1234"
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.SELECTED_DISPATCH_KEY,
                EMPTY_STRING
            )
        } returns "444444444"
        tripStartUseCase.updateDispatchInfoInDataStore(dispatchId, dispatchName, "TEST_VALUE")

        coVerify(exactly = 0) { dataStoreManager.setValue(DataStoreManager.SELECTED_DISPATCH_KEY, dispatchId) }
        coVerify { dataStoreManager.setValue(DataStoreManager.DISPATCH_NAME_KEY, dispatchName) }
        coVerify { dataStoreManager.setValue(DataStoreManager.CURRENT_DISPATCH_NAME_KEY, dispatchName) }
    }

    @Test
    fun `test getDispatchStopsAndActionsAndStartTrip calls with the empty stop list and should not call any other calls after empty stop list`() = runTest {
        val cid = "1234"
        val dispatchId = "123456789"
        val vehicleNumber = "testVehicleNumber1234"
        coEvery {
            dispatchBaseUseCase.getTripEventReasonTypeAndGuf(any())
        } returns Pair(StopActionReasonTypes.TIMEOUT.name, true)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
                any(), any(), any(), any(), any()
            )
        } returns emptyList<StopDetail>()
        tripStartUseCase.getDispatchStopsAndActionsAndStartTrip(
            cid,
            dispatchId,
            vehicleNumber,
            NEGATIVE_GUF_TIMEOUT,
            "TEST_CALLER",
            TripStartCaller.AUTO_TRIP_START_BACKGROUND
        )

        coVerify(exactly = 0) { dispatchStopsUseCase.updateSequencedKeyInDataStore(any(), any()) }
    }

    @Test
    fun `test getDispatchStopsAndActionsAndStartTrip calls with the stop list data then should verify that further calls happened `() = runTest {
        val cid = "1234"
        val dispatchId = "123456789"
        val vehicleNumber = "testVehicleNumber1234"
        coEvery {
            dispatchBaseUseCase.getTripEventReasonTypeAndGuf(any())
        } returns Pair(StopActionReasonTypes.TIMEOUT.name, true)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
                any(), any(), any(), any(), any()
            )
        } returns listOf(StopDetail(123, stopid = 0, completedTime = "2021-09-01T12:00:00.000Z"))

        coEvery { dispatchStopsUseCase.updateSequencedKeyInDataStore(any(), any()) }just runs
        coEvery { WorkManager.getInstance(ApplicationContextProvider.getApplicationContext()) }returns workManager
        coEvery { tripStartUseCase.scheduleLateNotificationWorker(any(), any()) } just runs
        coEvery { tripStartUseCase.startTrip(any()) }just runs
        tripStartUseCase.getDispatchStopsAndActionsAndStartTrip(
            cid,
            dispatchId,
            vehicleNumber,
            NEGATIVE_GUF_TIMEOUT,
            "TEST_CALLER",
            TripStartCaller.AUTO_TRIP_START_BACKGROUND
        )


        coVerify { dispatchStopsUseCase.updateSequencedKeyInDataStore(any(), any()) }
        coVerify { tripStartUseCase.scheduleLateNotificationWorker(any(), any()) }
        coVerify { tripStartUseCase.startTrip(any()) }
    }

    @Test
    fun `test startTrip calls from the auto start trip from work manager and verify the needed calls`() = runTest {
        val tripStartInfo = getMockTripStartInfo(TripStartCaller.AUTO_TRIP_START_BACKGROUND)
        startTripMethodsCallsSetup(tripStartInfo)
        tripStartUseCase.startTrip(tripStartInfo)
        startTripVerifyCalls(tripStartInfo)

        //Verify the method calls specific to auto start trip
        coVerify { tripStartUseCase.putStopsIntoPreference(any(), any()) }
        coVerify { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(any()) }
        coVerify { tripStartUseCase.startRouteCalculation(any(), any()) }
    }

    @Test
    fun `test startTrip via clicking Yes alert calls inside the application and verify the needed calls`() = runTest {
        val tripStartInfo = getMockTripStartInfo(TripStartCaller.DISPATCH_DETAIL_SCREEN)
        WorkflowEventBus.postStopList(CopyOnWriteArrayList(listOf(StopDetail(stopid = 0, completedTime = "2021-09-01T12:00:00.000Z"),
            StopDetail(stopid = 1, completedTime = "2021-09-01T12:00:00.000Z"))))
        startTripMethodsCallsSetup(tripStartInfo)
        tripStartUseCase.startTrip(tripStartInfo)
        startTripVerifyCalls(tripStartInfo)

        //Verify the method calls specific start trip inside application
        coVerify(exactly = 0) { tripStartUseCase.startRouteCalculation(any(), any()) }
    }

    @Test
    fun `test startTrip via clicking StartTrip in DispatchDetailActivity calls inside the application and verify the needed calls`() = runTest {
        val tripStartInfo = getMockTripStartInfo(TripStartCaller.START_TRIP_BUTTON_PRESS_FROM_DISPATCH_DETAIL_SCREEN)
        WorkflowEventBus.postStopList(CopyOnWriteArrayList(listOf(StopDetail(stopid = 0, completedTime = "2021-09-01T12:00:00.000Z"),
            StopDetail(stopid = 1, completedTime = "2021-09-01T12:00:00.000Z"))))
        startTripMethodsCallsSetup(tripStartInfo)
        tripStartUseCase.startTrip(tripStartInfo)
        startTripVerifyCalls(tripStartInfo)

        //Verify the method calls specific start trip inside application
        coVerify(exactly = 1) { tripStartUseCase.startRouteCalculation(any(), any())  }
    }

    private fun getMockTripStartInfo(tripStartType: TripStartCaller): TripStartInfo {
        return TripStartInfo(
            stopDetailList = listOf(
                StopDetail(stopid = 0),
                StopDetail(stopid = 1, completedTime = "2021-09-01T12:00:00.000Z")
            ),
            timeInMillis = System.currentTimeMillis(),
            pfmEventsInfo = mockk(),
            dispatchId = "1234567890",
            cid = "cid",
            vehicleNumber = "vehicleNumber",
            tripStartCaller = tripStartType,
            caller = "caller"
        )
    }

    @Test
    fun `verify startRouteCalculation() calls the respective methods it is supposed to call, if stopDetailList is not empty and all stops are completed`() =
        runTest {
            coEvery {
                routeETACalculationUseCase.resetTripInfoWidget(any())
                sendDispatchDataUseCase.sendDispatchCompleteEvent()
                routeETACalculationUseCase.startRouteCalculation(any(), any())
            } just runs
            val stopDetailList = listOf(
                StopDetail(stopid = 0, completedTime = "2021-09-01T12:00:00.000Z"),
                StopDetail(stopid = 1, completedTime = "2021-09-01T12:00:00.000Z")
            )

            tripStartUseCase.startRouteCalculation("TestCaller", stopDetailList)

            coVerify(exactly = 1) {
                routeETACalculationUseCase.resetTripInfoWidget(any())
            }
            coVerify(exactly = 0) {
                sendDispatchDataUseCase.sendDispatchCompleteEvent()
                routeETACalculationUseCase.startRouteCalculation(stopDetailList, "TestCaller")
            }
        }

    @Test
    fun `verify startRouteCalculation() calls the respective methods it is supposed to call, if stopDetailList is not empty and all stops are not completed`() =
        runTest {
            coEvery {
                routeETACalculationUseCase.resetTripInfoWidget(any())
                sendDispatchDataUseCase.sendDispatchCompleteEvent()
                routeETACalculationUseCase.startRouteCalculation(any(), any())
            } just runs
            val stopDetailList = listOf(
                StopDetail(stopid = 0),
                StopDetail(stopid = 1)
            )
            coEvery {
                dataStoreManager.getValue(
                    DISPATCH_NAME_KEY, EMPTY_STRING)
            }returns EMPTY_STRING

            tripStartUseCase.startRouteCalculation("TestCaller", stopDetailList)

            coVerify(exactly = 0) {
                routeETACalculationUseCase.resetTripInfoWidget(any())
            }
            coVerify(exactly = 1) {
                sendDispatchDataUseCase.sendDispatchCompleteEvent()
                routeETACalculationUseCase.startRouteCalculation(stopDetailList, "TestCaller")
            }
        }

    @Test
    fun `verify startRouteCalculation() calls the respective methods it is supposed to call, if stopDetailList is not empty and one of the stops is not completed`() =
        runTest {
            coEvery {
                routeETACalculationUseCase.resetTripInfoWidget(any())
                sendDispatchDataUseCase.sendDispatchCompleteEvent()
                routeETACalculationUseCase.startRouteCalculation(any(), any())
            } just runs
            val stopDetailList = listOf(
                StopDetail(stopid = 0),
                StopDetail(stopid = 1, completedTime = "2021-09-01T12:00:00.000Z")
            )
            coEvery {
                dataStoreManager.getValue(
                    DISPATCH_NAME_KEY, EMPTY_STRING)
            }returns EMPTY_STRING

            tripStartUseCase.startRouteCalculation("TestCaller", stopDetailList)

            coVerify(exactly = 0) {
                routeETACalculationUseCase.resetTripInfoWidget(any())
            }
            coVerify(exactly = 1) {
                sendDispatchDataUseCase.sendDispatchCompleteEvent()
                routeETACalculationUseCase.startRouteCalculation(stopDetailList, "TestCaller")
            }
        }

    @Test
    fun `verify startRouteCalculation() calls the resetTripInfoWidget(), if stopDetailList is empty`() =
        runTest {
            coEvery {
                routeETACalculationUseCase.resetTripInfoWidget(any())
                sendDispatchDataUseCase.sendDispatchCompleteEvent()
                routeETACalculationUseCase.startRouteCalculation(any(), any())
            } just runs
            val stopDetailList = emptyList<StopDetail>()

            tripStartUseCase.startRouteCalculation("TestCaller", stopDetailList)

            coVerify(exactly = 1) {
                routeETACalculationUseCase.resetTripInfoWidget(any())
            }
            coVerify(exactly = 0) {
                sendDispatchDataUseCase.sendDispatchCompleteEvent()
                routeETACalculationUseCase.startRouteCalculation(stopDetailList, "TestCaller")
            }
        }

    private fun startTripMethodsCallsSetup(tripStartInfo:TripStartInfo){
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "1234567890"
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulation() } just runs
        coEvery { tripStartUseCase.checkAndTriggerArrivalForStop( any(),any()) } just runs
        coEvery { routeETACalculationUseCase.startRouteCalculation(any(), any()) } just runs
        coEvery {
            dispatchBaseUseCase.setStartTime(
                any(), any()
            )
        } just runs
        coEvery {
            dispatchBaseUseCase.storeActiveDispatchIdToDataStore(
                dataStoreManager,
                tripStartInfo.dispatchId
            )
        } returns "123567890"
        coEvery {
            WorkManager.getInstance(ApplicationContextProvider.getApplicationContext())
                .cancelAllWorkByTag(any())
        } returns mockk()
        every { tripStartUseCase.sendDispatchStartDataToBackbone(any(), any(), any()) } just runs
        every { tripStartUseCase.sendTripStartEventToThirdPartyApps(any(), any()) } just runs
        coEvery {
            dispatchStopsUseCase.sendTripStartEventToPFM(
                any(),
                any(),
                any(),
                any()
            )
        } just runs
        coEvery {
            dispatchStopsUseCase.setActiveDispatchFlagInFirestore(
                any(),
                any(),
                any()
            )
        } just runs
        coEvery { tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop() } returns true
        coEvery {
            dispatchStopsUseCase.getAllDispatchBlobData(
                any(), any()
            )
        } returns ArrayList(listOf(DispatchBlob(1234, "testVehicle1234", 4321, "blobMessage", Instant.now(),101,102)))

        every {
            sendWorkflowEventsToAppUseCase.sendDispatchBlobEventToThirdPartyApps(
                any(),any(),any(),any()
            )
        }just runs
        coEvery {
            dispatchStopsUseCase.deleteAllDispatchBlobDataForVehicle(any(), any(),any())
        }just runs

        coEvery { tripStartUseCase.putStopsIntoPreference(any(), any()) } just runs
        coEvery { sendDispatchDataUseCase.sendDispatchCompleteEvent() } just runs
        coEvery { dispatchStopsUseCase.setCurrentStopAndUpdateTripPanelForSequentialTrip(any()) } returns true
    }

    private fun startTripVerifyCalls(tripStartInfo: TripStartInfo){
        coVerify { tripStartUseCase.checkAndTriggerArrivalForStop( any(),any()) }
        coVerify { dispatchStopsUseCase.unMarkActiveDispatchStopManipulation() }
        coVerify { dispatchBaseUseCase.setStartTime(any(), any()) }
        coVerify {
            dispatchBaseUseCase.storeActiveDispatchIdToDataStore(
                dataStoreManager,
                tripStartInfo.dispatchId
            )
        }
        coVerify {
            WorkManager.getInstance(ApplicationContextProvider.getApplicationContext())
                .cancelAllWorkByTag(any())
        }
        verify { tripStartUseCase.sendDispatchStartDataToBackbone(any(), any(), any()) }
        verify { tripStartUseCase.sendTripStartEventToThirdPartyApps(any(), any()) }
        coVerify { dispatchStopsUseCase.sendTripStartEventToPFM(any(), any(), any(), any()) }
        coVerify { dispatchStopsUseCase.setActiveDispatchFlagInFirestore(any(), any(), any()) }
        coVerify { tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop() }
        coVerify { dispatchStopsUseCase.getAllDispatchBlobData(any(), any()) }
    }

    @Test
    fun `verify scheduleLateNotificationCheckWorker() is called`() {
        every {
            lateNotificationUseCase.scheduleLateNotificationCheckWorker(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } just runs

        tripStartUseCase.scheduleLateNotificationWorker("12345", "testCaller")

        verify(exactly = 1) {
            lateNotificationUseCase.scheduleLateNotificationCheckWorker(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `checkAndTriggerArrivalForFirstStop should log error when stopDetailList is empty`() = runTest {
        tripStartUseCase.checkAndTriggerArrivalForStop("dispatchId",StopDetail())
        coVerify(exactly = 0) {
            serviceManager.processGeoFenceEvents(any(), any(), any(), any())
        }
    }

    @Test
    fun `checkAndTriggerArrivalForFirstStop should trigger arrival when conditions are met`() = runTest {
        val stopDetailList = listOf(StopDetail(latitude = 44.8581, longitude = -93.3402, completedTime = "", stopid = 0, siteCoordinates = mutableListOf(
            SiteCoordinate(latitude = 0.0, longitude = 0.0)
        )).also {
            it.Actions.add(Action(actionType = ActionTypes.APPROACHING.ordinal, radius = 2000))
            it.Actions.add(Action(actionType = ActionTypes.ARRIVED.ordinal, radius = 2000))
            it.Actions.add(Action(actionType = ActionTypes.DEPARTED.ordinal, radius = 2000))
        })
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(44.858028, -93.340062)
        coEvery { backboneUseCase.getCurrentUser() } returns EMPTY_STRING
        coEvery { serviceManager.processGeoFenceEvents(any(), any(), any(), any()) } just runs

        tripStartUseCase.checkAndTriggerArrivalForStop( "dispatchId",
            stopDetailList[0]
        )

        coVerify(exactly = 1) { serviceManager.processGeoFenceEvents("dispatchId", "Arrived0", context, false) }
    }

    @Test
    fun `checkAndTriggerArrivalForFirstStop should log distance when conditions are not met`() = runTest {
        val stopDetailList = listOf(StopDetail(latitude = 1.0, longitude = 1.0, completedTime = ""))
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(2.0, 2.0)

        tripStartUseCase.checkAndTriggerArrivalForStop( "dispatchId",stopDetailList[0])
        coVerify(exactly = 0) {
            serviceManager.processGeoFenceEvents(any(), any(), any(), any())
        }
    }

    @Test
    fun `checkAndTriggerArrivalForFirstStop should log distance when distance between latLong is greater than radius`() =
        runTest {
            val stopDetailList = listOf(
                StopDetail(
                    latitude = 44.8581,
                    longitude = -93.3402,
                    completedTime = "",
                    stopid = 0,
                    siteCoordinates = mutableListOf(SiteCoordinate(latitude = 0.0, longitude = 0.0))
                ).also {
                    it.Actions.add(
                        Action(
                            actionType = ActionTypes.APPROACHING.ordinal,
                            radius = 10
                        )
                    )
                    it.Actions.add(Action(actionType = ActionTypes.ARRIVED.ordinal, radius = 10))
                    it.Actions.add(Action(actionType = ActionTypes.DEPARTED.ordinal, radius = 10))
                })

            coEvery { backboneUseCase.getCurrentLocation() } returns Pair(44.858028, -93.340062)
            coEvery { getDistanceBetweenLatLongs(any(), any()) } returns 100.0

            tripStartUseCase.checkAndTriggerArrivalForStop("dispatchId", stopDetailList[0])
            coVerify(exactly = 0) {
                serviceManager.processGeoFenceEvents(any(), any(), any(), any())
            }
        }

    @Test
    fun `checkAndTriggerArrivalForFirstStop should log distance when radius is 0`() = runTest {
        val stopDetailList = listOf(
            StopDetail(
                latitude = 44.8581,
                longitude = -93.3402,
                completedTime = "",
                stopid = 0,
                siteCoordinates = mutableListOf(SiteCoordinate(latitude = 0.0, longitude = 0.0))
            ).also {
                it.Actions.add(Action(actionType = ActionTypes.APPROACHING.ordinal, radius = 0))
                it.Actions.add(Action(actionType = ActionTypes.ARRIVED.ordinal, radius = 0))
                it.Actions.add(Action(actionType = ActionTypes.DEPARTED.ordinal, radius = 0))
            })

        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(44.858028, -93.340062)

        tripStartUseCase.checkAndTriggerArrivalForStop("dispatchId", stopDetailList[0])
        coVerify(exactly = 0) {
            serviceManager.processGeoFenceEvents(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify sendDispatchStartDataToBackbone() calls required methods`() = runTest {
        coEvery { backboneUseCase.setWorkflowStartAction(any()) } returns Unit

        tripStartUseCase.sendDispatchStartDataToBackbone(
            "testDispatchId",
            backboneUseCase,
            testScope
        )

        coVerify(exactly = 1) { backboneUseCase.setWorkflowStartAction(any()) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}