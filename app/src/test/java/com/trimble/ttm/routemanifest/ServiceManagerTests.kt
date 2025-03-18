package com.trimble.ttm.routemanifest

import android.content.Context
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.FeatureFlagCacheRepo
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.TRUCK_NUMBER
import com.trimble.ttm.formlibrary.usecases.CacheGroupsUseCase
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.UpdateInspectionInformationUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ActionTypes
import com.trimble.ttm.routemanifest.model.LastSentTripPanelMessage
import com.trimble.ttm.commons.model.SiteCoordinate
import com.trimble.ttm.routemanifest.model.StopActionEventData
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import com.trimble.ttm.routemanifest.usecases.ArrivalReasonUsecase
import com.trimble.ttm.routemanifest.usecases.DispatchStopsUseCase
import com.trimble.ttm.routemanifest.usecases.FetchDispatchStopsAndActionsUseCase
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.utils.APPROACH
import com.trimble.ttm.routemanifest.utils.ARRIVED
import com.trimble.ttm.routemanifest.utils.CoroutineTestRule
import com.trimble.ttm.routemanifest.utils.DEPART
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.getGeofenceType
import com.trimble.ttm.routemanifest.utils.Utils.getStopDetailHashMap
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceManagerTests {

    private lateinit var serviceManager: ServiceManager
    @MockK
    private lateinit var updateInspectionInformationUseCase: UpdateInspectionInformationUseCase
    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager
    @MockK
    private lateinit var context: Context
    @MockK
    private lateinit var tripPanelEventRepo: TripPanelEventRepo
    @MockK
    private lateinit var featureFlagCacheRepo: FeatureFlagCacheRepo
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @RelaxedMockK
    private lateinit var cacheGroupsUseCase: CacheGroupsUseCase
    @MockK
    private lateinit var authenticateUseCase: AuthenticateUseCase
    @MockK
    private lateinit var edvirFormUseCase: EDVIRFormUseCase
    @MockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase
    @RelaxedMockK
    private lateinit var application: WorkflowApplication
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase
    @MockK
    private lateinit var tripCompletionUseCase: TripCompletionUseCase
    @MockK
    private lateinit var arrivalReasonUseCase: ArrivalReasonUsecase
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val testScope = TestScope(StandardTestDispatcher())

    private val modulesToInject = module {
        single { arrivalReasonUseCase }
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(WorkflowApplication)
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        mockkObject(Utils)

        startKoin {
            androidContext(application)
            loadKoinModules(modulesToInject)
        }
        serviceManager =
            spyk(ServiceManager(
                dataStoreManager,
                appModuleCommunicator,
                tripCompletionUseCase,
                tripPanelUseCase,
                tripPanelEventRepo,
                formDataStoreManager,
                featureFlagCacheRepo,
                authenticateUseCase,
                edvirFormUseCase,
                dispatchStopsUseCase,
                TestDispatcherProvider(),
                backboneUseCase = backboneUseCase,
            ))
        every { context.applicationContext } returns context
        every { context.filesDir } returns temporaryFolder.newFolder()
        val lastSentTripPanelMessage = LastSentTripPanelMessage(1, "Test not same message", 5)
        coEvery { tripPanelUseCase.lastSentTripPanelMessage } returns lastSentTripPanelMessage
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        every {
            getStopDetailHashMap(stopLocation = any(), isSeq = any(), geofenceType = any(), driverID = any(), eta = any())
        } returns HashMap<String, Any>()
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP,
                false
            )
        } just runs
        coEvery { backboneUseCase.getCurrentUser() } returns EMPTY_STRING
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0,0.0)
    }

    @Test
    fun `inspection cache is updated if vehicle is moving`() = runTest {

        coEvery { updateInspectionInformationUseCase.updateInspectionRequire(any()) } just runs
        coEvery { updateInspectionInformationUseCase.clearPreviousAnnotations() } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns EMPTY_STRING

        serviceManager.sendTripPanelMessageIfTruckStopped(true, updateInspectionInformationUseCase)

        coVerify(exactly = 1) {
            updateInspectionInformationUseCase.updateInspectionRequire(any())
            updateInspectionInformationUseCase.clearPreviousAnnotations()
        }
    }

    @Test
    fun `trip panel method is invoked if vehicle is not moving`() = runTest {

        coEvery {
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
        } just runs

        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "123456"

        serviceManager.sendTripPanelMessageIfTruckStopped(false, updateInspectionInformationUseCase)

        coVerify(exactly = 1) {
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
        }
    }

    @Test
    fun `verify whether trip panel method is not invoked and inspection cache is not updated when vehicle is not moving and there is no active dispatch id`() = runTest {
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns EMPTY_STRING

        serviceManager.sendTripPanelMessageIfTruckStopped(false, updateInspectionInformationUseCase)
        coVerify(exactly = 0) {
            tripPanelUseCase.putArrivedMessagesIntoPriorityQueue()
            updateInspectionInformationUseCase.updateInspectionRequire(any())
            updateInspectionInformationUseCase.clearPreviousAnnotations()
        }
    }

    @Test
    fun `cache form groups will not be executed if CID is empty`() = runTest {
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { appModuleCommunicator.doGetObcId() } returns "123456"
        coEvery {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns true

        serviceManager.cacheFormIdsAndUserIdsFromGroupUnits(
            testScope,
            cacheGroupsUseCase
        )

        coVerify(exactly = 0) {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `cache form groups will not be executed if OBC id is empty`() = runTest {
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
        coEvery { appModuleCommunicator.doGetCid() } returns "1234"
        coEvery { appModuleCommunicator.doGetObcId() } returns ""
        coEvery {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns true

        serviceManager.cacheFormIdsAndUserIdsFromGroupUnits(
            testScope,
            cacheGroupsUseCase
        )

        coVerify(exactly = 0) {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `cache form groups will not be executed if firebase is not authenticated`() = runTest {
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns false
        coEvery { appModuleCommunicator.doGetCid() } returns "1234"
        coEvery { appModuleCommunicator.doGetObcId() } returns "4567"
        coEvery {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns true

        serviceManager.cacheFormIdsAndUserIdsFromGroupUnits(
            testScope,
            cacheGroupsUseCase
        )

        coVerify(exactly = 0) {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `cache form groups will be executed`() = runTest {
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
        coEvery { appModuleCommunicator.doGetCid() } returns "1234"
        coEvery { appModuleCommunicator.doGetObcId() } returns "3214324"
        coEvery {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns true

        serviceManager.cacheFormIdsAndUserIdsFromGroupUnits(
            testScope,
            cacheGroupsUseCase
        )

        coVerify(exactly = 1) {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify newCid is set, old and new cid does not match`() = runTest {
        val newCid = "newCid"
        coEvery { appModuleCommunicator.doGetCid() } returns "oldCid"
        coEvery { appModuleCommunicator.doSetCid(any()) } just runs

        serviceManager.handleCidChange(newCid)

        coVerify(exactly = 1) { appModuleCommunicator.doSetCid(any()) }
    }

    @Test
    fun `verify newCid is not set, old and new cid matches`() = runTest {
        val newCid = "oldCid"
        coEvery { appModuleCommunicator.doGetCid() } returns "oldCid"

        serviceManager.handleCidChange(newCid)

        coVerify(exactly = 0) { appModuleCommunicator.doSetCid(any()) }
    }

    @Test
    fun `verify handle vehicle number change when it did not change`() = runTest {


        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123"
        serviceManager.handleVehicleNumberChange("123")

        coVerify(exactly = 0) {
            appModuleCommunicator.doSetTruckNumber(any())
        }
    }

    @Test
    fun `verify handle vehicle number change when it did change`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "123"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "1235"
        coEvery { appModuleCommunicator.doSetTruckNumber(any()) } just runs
        coEvery { dataStoreManager.getValue(TRUCK_NUMBER, EMPTY_STRING) } returns "1235"
        coEvery { authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(any(), any(), any()) } just runs


        serviceManager.handleVehicleNumberChange("123")

        coVerify(exactly = 1) {
            appModuleCommunicator.doSetTruckNumber(any())
            authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(any(), any(), any())
        }
    }


    @Test
    fun `verify method returns false if obc id did not change`() = runTest {

        coEvery { appModuleCommunicator.doGetObcId() } returns "123"
        val flag = serviceManager.handleDsnChange(
            testScope,
            cacheGroupsUseCase, "123"
        )

        assertFalse(flag)
        coVerify(exactly = 0) {
            appModuleCommunicator.doSetObcId(any())
        }
    }

    @Test
    fun `verify method returns false if obc id did change`() = runTest {
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
        coEvery { appModuleCommunicator.doGetCid() } returns "1234"
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { appModuleCommunicator.doSetObcId(any()) } just runs
        coEvery { edvirFormUseCase.isEDVIREnabled(any(), any()) } returns true
        coEvery { formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, any()) } just runs
        val flag = serviceManager.handleDsnChange(
            testScope,
            cacheGroupsUseCase, "123"
        )

        assertTrue(flag)
        coVerify(exactly = 1) {
            appModuleCommunicator.doSetObcId(any())
        }
    }


    @Test
    fun `edvir setting is not cached if CID is empty`() = runTest {

        coEvery { edvirFormUseCase.isEDVIREnabled(any(), any()) } returns true
        serviceManager.checkAndStoreEDVIRSettingsAvailability("", "123")

        coVerify(exactly = 0) {
            edvirFormUseCase.isEDVIREnabled(any(), any())
            formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, any())
        }
    }

    @Test
    fun `edvir setting is not cached if OBC ID is empty`() = runTest {
        coEvery { edvirFormUseCase.isEDVIREnabled(any(), any()) } returns true
        serviceManager.checkAndStoreEDVIRSettingsAvailability("123", "")

        coVerify(exactly = 0) {
            edvirFormUseCase.isEDVIREnabled(any(), any())
            formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, any())
        }
    }

    @Test
    fun `edvir setting is cached`() = runTest {
        coEvery { edvirFormUseCase.isEDVIREnabled(any(), any()) } returns true
        serviceManager.checkAndStoreEDVIRSettingsAvailability("1233", "123")

        coVerify(exactly = 1) {
            edvirFormUseCase.isEDVIREnabled(any(), any())
            formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, any())
        }
    }

    @Test
    fun `verify FCM token is registered`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "234"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "12345"
        coEvery {
            authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(any(),any(),any())
        } just runs

        serviceManager.fetchAndStoreFCMToken("")

        coVerify(exactly = 1) {
            authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(any(),any(),any())
        }
    }


    @Test
    fun `verify methods for READY TO PROCESS of HostApp State are executed when active dispatch exist`() =
        runTest {

            coEvery { dataStoreManager.hasActiveDispatch(any(),any()) } returns true
            coEvery { tripPanelUseCase.checkForCompleteFormMessages() } just runs
            coEvery {
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            } returns true
            serviceManager.handleLibraryConnectionState(HostAppState.READY_TO_PROCESS)

            coVerify(exactly = 1) {
                tripPanelUseCase.checkForCompleteFormMessages()
                dataStoreManager.hasActiveDispatch(any(),any())
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            }
        }

    @Test
    fun `verify methods for READY TO PROCESS of HostApp State are executed when active dispatch DOES NOT exist`() =
        runTest {

            coEvery { dataStoreManager.hasActiveDispatch(any(),any()) } returns false
            coEvery { tripPanelUseCase.checkForCompleteFormMessages() } just runs
            coEvery {
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            } returns true
            serviceManager.handleLibraryConnectionState(HostAppState.READY_TO_PROCESS)

            coVerify(exactly = 1) { dataStoreManager.hasActiveDispatch(any(),any()) }
            coVerify(exactly = 0) {
                tripPanelUseCase.checkForCompleteFormMessages()
                tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
            }
        }

    @Test
    fun `verify approach geofence logic is handled`() = runTest {
        val dispatchId = "101"
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            },
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                })
        )

        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery {
            dispatchStopsUseCase.getStopAndActions(
                any(),
                dataStoreManager,
                any()
            )
        } returns stopDetails[0]
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP,
                false
            )
        } returns false

        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs

        val stopActionEventData = StopActionEventData(
            0,
            ActionTypes.APPROACHING.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = false
        )

        coEvery {
            dispatchStopsUseCase.sendStopActionEvent(
                any(),
                stopActionEventData,
                any(),
                any()
            )
        } just runs
        coEvery {
            tripCompletionUseCase.checkForTripCompletionAndEndTrip(
                any(),
                any()
            )
        } just runs
        coEvery {
            dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(
                any(),
                any(),
                any()
            )
        } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchId
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery {
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(),
                any(),
                any(),
                any()
            )
        } just runs

        serviceManager.processGeoFenceEvents(
            dispatchId,
            APPROACH + "0",
            context,
            true
        )

        coVerify(exactly = 1) {
            dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(any(),any(),any())
            tripCompletionUseCase.checkForTripCompletionAndEndTrip(any(), any())
            dispatchStopsUseCase.sendStopActionEvent(any(),stopActionEventData, any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(),
                any(),
                ActionTypes.APPROACHING.ordinal,
                stopActionReasonTypes = StopActionReasonTypes.AUTO
            )
        }
    }

    @Test
    fun `verify methods for SERVICE DISCONNECTED of HostApp State are executed`() = runTest {

        coEvery { tripPanelEventRepo.retryConnection() } just runs

        serviceManager.handleLibraryConnectionState(HostAppState.SERVICE_DISCONNECTED)

        coVerify(exactly = 1) { tripPanelEventRepo.retryConnection() }
    }


    @Test
    fun `verify approach geofence logic is handled for deleted stop`() = runTest {
        val dispatchId = "101"
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1, deleted = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            },
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                })
        )

        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs

        val stopActionEventData = StopActionEventData(
            0,
            ActionTypes.APPROACHING.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = false
        )

        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { tripCompletionUseCase.checkForTripCompletionAndEndTrip(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(any(),any(),any()) } just runs
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchId
        coEvery { dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, false) } returns false

        serviceManager.processGeoFenceEvents(
            dispatchId,
            APPROACH + "0",
            context,
            true
        )

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(any(),any(),any())
            tripCompletionUseCase.checkForTripCompletionAndEndTrip(any(), any())
        }
        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.sendStopActionEvent(any(),stopActionEventData, "", any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(),
                any(),
                ActionTypes.APPROACHING.ordinal,
                stopActionReasonTypes = StopActionReasonTypes.AUTO
            )
        }
    }

    @Test
    fun `verify arrive geofence logic is handled for valid trip type`() = runTest {
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1, latitude = 0.0, longitude = 0.0, sequenced = 1, siteCoordinates = mutableListOf(
                SiteCoordinate(0.0,0.0)
            )).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 1,
                        responseSent = false,
                        eta = "0"
                    )
                )
            },
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                })
        )

        coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]
        coEvery { tripPanelUseCase.isValidStopForTripType(any()) } returns Pair(true, EMPTY_STRING)
        coEvery {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(
                any(), any()
            )
        } just runs
        coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(),any()) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
            any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO) } just runs
        coEvery {
            dispatchStopsUseCase.updateStopDetail(any(), any())
        } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any()) } just runs
        coEvery { dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, false) } returns false
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs

        serviceManager.processGeoFenceEvents(
            "101",
            ARRIVED + "1",
            context,
            true
        )
        advanceUntilIdle()
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(any(), any())
            arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO)
        }
    }

    @Test
    fun `verify arrive geofence logic is handled when the vehicle is started from stop 0 location and trigger came from map`() = runTest {
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            },
                StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            },
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                })
        )

        coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]
        coEvery { tripPanelUseCase.isValidStopForTripType(any()) } returns Pair(true, EMPTY_STRING)
        coEvery {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(
                any(), any()
            )
        } just runs
        coEvery {
            dispatchStopsUseCase.updateStopDetail(any(), any())
        } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
            any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO) } just runs
        dataStoreManager.setValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, true)

        serviceManager.processGeoFenceEvents(
            "101",
            ARRIVED + "0",
            context,
            true
        )
        advanceUntilIdle()

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.updateStopDetail(any(),any())
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO)
        }
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.setValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, false)
        }
    }


    @Test
    fun `verify arrive geofence logic is handled when the vehicle is started from stop 0 location and trigger came from workflow`() = runTest {
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1, latitude = 0.0, longitude = 0.0, sequenced = 1, siteCoordinates = mutableListOf(
                SiteCoordinate(0.0,0.0)
            )).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 1,
                        responseSent = false,
                        eta = "0"
                    )
                )
            },
                StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            },
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                })
        )

        coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]
        coEvery { tripPanelUseCase.isValidStopForTripType(any()) } returns Pair(true, EMPTY_STRING)
        coEvery {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(
                any(), any()
            )
        } just runs
        coEvery {
            dispatchStopsUseCase.updateStopDetail(any(), any())
        } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any()) } just runs
        coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(),any()) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
            any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO) } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs

        serviceManager.processGeoFenceEvents(
            "101",
            ARRIVED + "0",
            context,
            false
        )
        advanceUntilIdle()
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(any(), any())
            arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO)
        }
        assertTrue(dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, false))
    }

    @Test
    fun `verify arrive geofence logic is handled when the vehicle is not started from stop 0 location`() = runTest {
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1, latitude = 0.0, longitude = 0.0, sequenced = 1, siteCoordinates = mutableListOf(
                SiteCoordinate(0.0,0.0)
            )).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1,
                        responseSent = false,
                        eta = "0"
                    )
                )
            },
                StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            },
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                })
        )

        coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]
        coEvery { tripPanelUseCase.isValidStopForTripType(any()) } returns Pair(true, EMPTY_STRING)
        coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(),any()) } just runs
        coEvery {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(
                any(), any()
            )
        } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
            any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO) } just runs
        coEvery { dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, any()) } returns false
        coEvery {
            dispatchStopsUseCase.updateStopDetail(any(), any())
        } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs

        serviceManager.processGeoFenceEvents(
            "101",
            ARRIVED + "0",
            context,
            true
        )
        advanceUntilIdle()
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(any(), any())
            arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO)
        }
    }

    @Test
    fun `verify arrive geofence logic is handled when the vehicle is started from stop 1 location`() = runTest {
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1, latitude = 0.0, longitude = 0.0, sequenced = 1, siteCoordinates = mutableListOf(
                SiteCoordinate(0.0,0.0)
            )).apply {
                Actions.add(
                    Action(
                        actionType = 1,
                        stopid = 1,
                        responseSent = false,
                        eta = "0"
                    )
                )
            },
                StopDetail(stopid = 0).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 0,
                        responseSent = false
                    )
                )
            },
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2,
                            responseSent = false
                        )
                    )
                })
        )
        every { getGeofenceType(any(), isPolygonalOptOut = true) } returns "CIRCULAR"
        coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]
        coEvery { tripPanelUseCase.isValidStopForTripType(any()) } returns Pair(true, EMPTY_STRING)
        coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(),any()) } just runs
        coEvery {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(
                any(), any()
            )
        } just runs
        coEvery {
            dispatchStopsUseCase.updateStopDetail(any(), any())
        } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
            any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO) } just runs
        coEvery { dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, any()) } returns false

        serviceManager.processGeoFenceEvents(
            "101",
            ARRIVED + "1",
            context,
            true
        )
        advanceUntilIdle()
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(any(), any())
            arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO)
        }
        assertFalse(dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, true))
    }

    @Test
    fun `verify arrive geofence logic is handled for invalid trip type`() = runTest {
            val stopDetails = mutableListOf<StopDetail>()
            stopDetails.addAll(
                listOf(
                    StopDetail(stopid = 1, latitude = 0.0, longitude = 0.0, sequenced = 1, siteCoordinates = mutableListOf(
                        SiteCoordinate(0.0,0.0)
                    )),
                    StopDetail(stopid = 0).apply {
                        Actions.add(
                            Action(
                                actionType = 0,
                                stopid = 0,
                                responseSent = false
                            )
                        )
                    },
                    StopDetail(stopid = 2).apply {
                        Actions.add(
                            Action(
                                actionType = 0,
                                stopid = 2,
                                responseSent = false
                            )
                        )
                    })
            )
            every { getGeofenceType(any(), isPolygonalOptOut = true) } returns "CIRCULAR"
            coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]
            coEvery { tripPanelUseCase.isValidStopForTripType(any()) } returns Pair(true, EMPTY_STRING)
            coEvery { arrivalReasonUseCase.getArrivalReasonMap(any(),any(),any()) } returns hashMapOf()
            coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(),any()) } just runs
            coEvery {
                tripPanelUseCase.putArrivedGeoFenceTriggersIntoCache(
                    any(), any()
                )
            } just runs
            coEvery {
                dispatchStopsUseCase.updateStopDetail(any(), any())
            } just runs
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
            coEvery { arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any()) } just runs
            coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(
                any(), any(), ActionTypes.ARRIVED.ordinal, stopActionReasonTypes = StopActionReasonTypes.AUTO) } just runs
            coEvery { dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, any()) } returns false
            coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs

            serviceManager.processGeoFenceEvents(
                "101",
                ARRIVED + "1",
                context,
                true
            )
            advanceUntilIdle()
            coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                arrivalReasonUseCase.setArrivalReasonForCurrentStop(any(), any())
            }
    }

    @Test
    fun `verify departed geofence logic is handled`() = runTest {
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1).apply {
                Actions.add(
                    Action(
                        actionType = 2,
                        stopid = 1
                    )
                )
            })
        )
        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]

        val stopActionEventData = StopActionEventData(
            1,
            ActionTypes.DEPARTED.ordinal,
            context,
            hasDriverAcknowledgedArrivalOrManualArrival = false
        )

        coEvery { dispatchStopsUseCase.sendStopActionEvent(any(),stopActionEventData, any(), any()) } just runs
        coEvery { tripCompletionUseCase.checkForTripCompletionAndEndTrip(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns false
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(any(),any(),any(),any()) } just runs
        coEvery { dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, false) } returns false
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        serviceManager.processGeoFenceEvents(
            "101",
            DEPART + "1",
            context,
            true
        )

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any())
            dispatchStopsUseCase.sendStopActionEvent(any(), stopActionEventData, any(), any())
            tripCompletionUseCase.checkForTripCompletionAndEndTrip(any(), any())
        }
    }

    @Test
    fun `verify departed geofence logic is handled for deleted stop`() = runTest {
        val stopDetails = mutableListOf<StopDetail>()
        stopDetails.addAll(
            listOf(StopDetail(stopid = 1, deleted = 1).apply {
                Actions.add(
                    Action(
                        actionType = 0,
                        stopid = 1
                    )
                )
            },
                StopDetail(stopid = 2).apply {
                    Actions.add(
                        Action(
                            actionType = 0,
                            stopid = 2
                        )
                    )
                })
        )
        coEvery { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        coEvery { dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any()) } just runs
        coEvery { dispatchStopsUseCase.getStopAndActions(any(), dataStoreManager, any()) } returns stopDetails[0]
        coEvery { tripCompletionUseCase.checkForTripCompletionAndEndTrip(any(), any()) } just runs
        coEvery { dispatchStopsUseCase.processGeofenceTriggerForGeofenceRemoval(any(),any(),any()) } just runs
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns false
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs
        coEvery { dataStoreManager.getValue(DataStoreManager.IS_DRIVER_STARTS_FROM_FIRST_STOP, false) } returns false
        coEvery { dispatchStopsUseCase.updateStopActions(any(),any()) } just runs

        serviceManager.processGeoFenceEvents(
            "101",
            DEPART + "0",
            context,
            true
        )

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.postDepartureEventProcess(any(), any(), any(), any())
            tripCompletionUseCase.checkForTripCompletionAndEndTrip(any(), any())
        }
        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            dispatchStopsUseCase.sendStopActionEvent(any(),any(), any(), any())
            dispatchStopsUseCase.unMarkActiveDispatchStopManipulationAndStartRouteCalculationForStopActionCompletion(any(), any(), ActionTypes.DEPARTED.ordinal,
                stopActionReasonTypes = StopActionReasonTypes.AUTO)
        }
    }

    @Test
    fun `verify getEDVIRSettingsAvailabilityStatus is not checked when cid is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { appModuleCommunicator.doGetObcId() } returns "obcId"
        coEvery { edvirFormUseCase.isEDVIREnabled(any(), any()) } returns false
        coEvery { formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false) } just runs

        serviceManager.getEDVIRSettingsAvailabilityStatus()

        coVerify(exactly = 0) {
            edvirFormUseCase.isEDVIREnabled(any(), any())
            formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false)
        }
    }

    @Test
    fun `verify getEDVIRSettingsAvailabilityStatus is not checked when obcId is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "cid"
        coEvery { appModuleCommunicator.doGetObcId() } returns ""
        coEvery { edvirFormUseCase.isEDVIREnabled(any(), any()) } returns false
        coEvery { formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false) } just runs

        serviceManager.getEDVIRSettingsAvailabilityStatus()

        coVerify(exactly = 0) {
            edvirFormUseCase.isEDVIREnabled(any(), any())
            formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false)
        }
    }

    @Test
    fun `verify getEDVIRSettingsAvailabilityStatus is set when cid or obcId are not empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "cid"
        coEvery { appModuleCommunicator.doGetObcId() } returns "obcId"
        coEvery { edvirFormUseCase.isEDVIREnabled(any(), any()) } returns false
        coEvery { formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false) } just runs

        serviceManager.getEDVIRSettingsAvailabilityStatus()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            edvirFormUseCase.isEDVIREnabled(any(), any())
            formDataStoreManager.setValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false)
        }
    }

    @After
    fun after() {
        stopKoin()
        unmockkAll()
    }

}