package com.trimble.ttm.routemanifest.usecases

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.work.WorkManager
import com.google.common.base.CharMatcher
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.FcmData
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.repo.FireStoreCacheRepository
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.commons.utils.*
import com.trimble.ttm.routemanifest.repo.SendDispatchDataRepo
import com.trimble.ttm.routemanifest.repo.TripMobileOriginatedEventsRepo
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import com.trimble.ttm.routemanifest.utils.JsonDataConstructionUtils
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripCompletionUseCaseTests {

    @RelaxedMockK
    private lateinit var dataStoreManager: DataStoreManager
    @RelaxedMockK
    private lateinit var context: Context
    private lateinit var formDataStoreManager: FormDataStoreManager
    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo
    @MockK
    private lateinit var tripMobileOriginatedEventsRepo: TripMobileOriginatedEventsRepo
    @MockK
    private lateinit var formUseCase: FormUseCase
    @MockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    private lateinit var sendDispatchDataUseCase: SendDispatchDataUseCase
    @MockK
    private lateinit var stopDetentionWarningUseCase: StopDetentionWarningUseCase
    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase
    @RelaxedMockK
    private lateinit var application: Application
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private val defaultDispatcherProvider = TestDispatcherProvider()
    private lateinit var tripCompletionUseCase: TripCompletionUseCase
    private lateinit var dispatchListUseCase: DispatchListUseCase
    private lateinit var tripCacheUseCase: TripCacheUseCase
    @MockK
    private lateinit var fireStoreCacheRepository: FireStoreCacheRepository
    @MockK
    private lateinit var sendDispatchDataRepo : SendDispatchDataRepo
    @MockK
    private lateinit var sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase
    @MockK
    private lateinit var dispatchListUseCaseTest: DispatchListUseCase
    @MockK
    private lateinit var managedConfigurationRepo: ManagedConfigurationRepo
    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo
    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase

    @MockK
    private lateinit var workManager: WorkManager
    private val cid = "10119"
    private val truckNumber = "TN606"
    private val dispatchId = "123"
    private var activeDispatchId = "1234"
    private val at20200216T111954765Z = "2020-02-16T11:19:54.765Z"
    private val pfmEventsInfo = PFMEventsInfo.TripEvents("", false)

    @Before
    fun setup(){
        MockKAnnotations.init(this)
        every { localDataSourceRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        sendDispatchDataUseCase = spyk(SendDispatchDataUseCase(mockk(), sendDispatchDataRepo, localDataSourceRepo, sendWorkflowEventsToAppUseCase))
        dispatchListUseCase = DispatchListUseCase(dispatchFirestoreRepo, mockk(), localDataSourceRepo, tripPanelUseCase, mockk())
        tripCacheUseCase = spyk(
            TripCacheUseCase(
                fireStoreCacheRepository = fireStoreCacheRepository,
                dataStoreManager = dataStoreManager,
                workflowAppNotificationUseCase = mockk(),
                fetchDispatchStopsAndActionsUseCase = fetchDispatchStopsAndActionsUseCase,
                dispatchListUseCase = dispatchListUseCase,
                appModuleCommunicator = appModuleCommunicator,
                dispatchFirestoreRepo = mockk()
            )
        )
        every { tripMobileOriginatedEventsRepo.saveTripActionResponse(any(), any(), any()) } just runs
        tripCompletionUseCase = spyk(
            TripCompletionUseCase(
                dispatchFirestoreRepo = dispatchFirestoreRepo,
                tripMobileOriginatedEventsRepo = tripMobileOriginatedEventsRepo,
                formUseCase = formUseCase,
                draftUseCase =  mockk(),
                dispatchStopsUseCase = dispatchStopsUseCase,
                sendDispatchDataUseCase = sendDispatchDataUseCase,
                stopDetentionWarningUseCase = stopDetentionWarningUseCase,
                tripPanelUseCase = tripPanelUseCase,
                backboneUseCase = backboneUseCase,
                localDataSourceRepo = localDataSourceRepo,
                tripInfoWidgetUseCase = mockk(),
                sendWorkflowEventsToAppUseCase = sendWorkflowEventsToAppUseCase,
                managedConfigurationRepo = managedConfigurationRepo,
                dispatchListUseCase =dispatchListUseCaseTest,
                coroutineDispatcherProvider = defaultDispatcherProvider
            ), recordPrivateCalls = true
        )
        formDataStoreManager = spyk(FormDataStoreManager(context))
        mockkObject(WorkflowApplication)
        mockkObject(ApplicationContextProvider)
        every { ApplicationContextProvider.getApplicationContext() } returns application.applicationContext
        mockkStatic(WorkManager::class)
        val mockedWorkManager = mockk<WorkManager>()
        every { WorkManager.getInstance(any()) } returns mockedWorkManager
        dataStoreManager = spyk(DataStoreManager(context))
        mockkObject(JsonDataConstructionUtils)
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns truckNumber
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns activeDispatchId
        mockkObject(FormUtils)
        mockkClass(JsonDataConstructionUtils::class) {
            coEvery { JsonDataConstructionUtils.Companion.getTripEventJson(any(),any(),any(),any(),any(),any()) } returns mockk()
        }
    }

    @Test
    fun `verify sendTripEvent`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns ""
        coEvery { appModuleCommunicator.doGetVid() } returns 0L
        coEvery { appModuleCommunicator.doGetCid() } returns "1"
        coEvery { backboneUseCase.getFuelLevel() } returns 0
        coEvery { backboneUseCase.getOdometerReading(any()) } returns 0.0
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0, 0.0)
        coEvery { tripCompletionUseCase.saveTripEndActionResponse(any(), any(), any()) } just runs
        coEvery { tripPanelUseCase.dismissTripPanelOnLaunch() } just runs
        val flagName = FeatureGatekeeper.KnownFeatureFlags.SHOULD_USE_CONFIGURABLE_ODOMETER
        val featureMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> =
            mapOf(flagName to FeatureFlagDocument(flagName.id, shouldEnableFeature = true))

        coEvery { appModuleCommunicator.getFeatureFlags() } returns featureMap

        tripCompletionUseCase.sendTripEndEventToPFM(
            cid = cid,
            truckNumber = "606",
            dispatchId = dispatchId,
            pfmEventsInfo = pfmEventsInfo,
            caller = "test"
        )

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            tripCompletionUseCase.saveTripEndActionResponse(any(), any(), any())
        }
    }

    @Test
    fun `verify sendDispatchEndDataToBackbone`() = runTest {

        coEvery { backboneUseCase.setWorkflowEndAction(any()) } just runs

        tripCompletionUseCase.sendDispatchEndDataToBackbone("123")

        coVerify {
            backboneUseCase.setWorkflowEndAction(any())
        }
    }

    @Test
    fun `verify isTripComplete returns true when there is no active stop in a trip`() =
        runTest {
            val stopDetailList = ArrayList<StopDetail>()

            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 0,
                    completedTime = at20200216T111954765Z,
                    deleted = 1
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 1,
                    completedTime = at20200216T111954765Z,
                    deleted = 1
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 2,
                    completedTime = at20200216T111954765Z,
                    deleted = 1
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList

            assertTrue(
                tripCompletionUseCase.isTripComplete(
                CharMatcher.any().toString(),
                CharMatcher.any().toString()
            ).second)
        }

    @Test
    fun `verify isTripComplete returns false when there is no stop from firestore`() =
        runTest {
            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns emptyList()

            assertFalse(
                tripCompletionUseCase.isTripComplete(
                    CharMatcher.any().toString(),
                    CharMatcher.any().toString()
                ).second
            )
        }

    @Test
    fun `verify isTripComplete returns true when all stops has completed time and respective form count is zero`() =
        runTest {
            val stopDetailList = ArrayList<StopDetail>()

            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 0,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 1,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 2,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )

            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[0].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[1].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[2].stopid}"),
                    ZERO
                )
            } returns ZERO

            assertTrue(
                tripCompletionUseCase.isTripComplete(
                    CharMatcher.any().toString(),
                    CharMatcher.any().toString()
                ).second
            )
        }

    @Test
    fun `verify isTripComplete returns false when all stops has completed time and 1 stop form is incomplete`() =
        runTest {
            val stopDetailList = ArrayList<StopDetail>()

            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 0,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 1,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 2,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )

            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[0].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[1].stopid}"),
                    ZERO
                )
            } returns 1
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[2].stopid}"),
                    ZERO
                )
            } returns ZERO

            assertFalse(
                tripCompletionUseCase.isTripComplete(
                    CharMatcher.any().toString(),
                    CharMatcher.any().toString()
                ).second
            )
        }

    @Test
    fun `verify isTripComplete returns false when all stop forms are complete and has an incomplete stop`() =
        runTest {
            val stopDetailList = ArrayList<StopDetail>()

            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 0,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 1,
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 2,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )

            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[0].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[1].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[2].stopid}"),
                    ZERO
                )
            } returns ZERO

            assertFalse(
                tripCompletionUseCase.isTripComplete(
                    CharMatcher.any().toString(),
                    CharMatcher.any().toString()
                ).second
            )
        }

    @Test
    fun `verify isTripComplete returns true when all stops are complete along with it forms and has an deleted stop`() =
        runTest {
            val stopDetailList = ArrayList<StopDetail>()

            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 0,
                    completedTime = at20200216T111954765Z,
                    deleted = 1
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 1,
                    completedTime = at20200216T111954765Z,
                    ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 2,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )

            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[0].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[1].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[2].stopid}"),
                    ZERO
                )
            } returns ZERO

            assertTrue(
                tripCompletionUseCase.isTripComplete(
                    CharMatcher.any().toString(),
                    CharMatcher.any().toString()
                ).second
            )
        }

    @Test
    fun `verify isTripComplete returns false when all stops are complete along with it forms and has an empty action in 1 stop`() =
        runTest {
            val stopDetailList = ArrayList<StopDetail>()

            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 0,
                    completedTime = at20200216T111954765Z,
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 1,
                    completedTime = at20200216T111954765Z,
                )
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 2,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )

            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[0].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[1].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[2].stopid}"),
                    ZERO
                )
            } returns ZERO

            assertFalse(
                tripCompletionUseCase.isTripComplete(
                    CharMatcher.any().toString(),
                    CharMatcher.any().toString()
                ).second
            )
        }

    @Test
    fun `verify isTripComplete returns false when all stops are complete along with it forms and has an pending action in 1 stop`() =
        runTest {
            val stopDetailList = ArrayList<StopDetail>()

            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 0,
                    completedTime = at20200216T111954765Z,
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                    Actions.add(
                        Action(
                            actionType = 1
                        )
                    )
                    Actions.add(
                        Action(
                            actionType = 2,
                            responseSent = false
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 1,
                    completedTime = at20200216T111954765Z,
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )
            stopDetailList.add(
                StopDetail(
                    dispid = activeDispatchId,
                    stopid = 2,
                    completedTime = at20200216T111954765Z
                ).apply {
                    Actions.add(
                        Action(
                            actionType = 0
                        )
                    )
                }
            )

            coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[0].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[1].stopid}"),
                    ZERO
                )
            } returns ZERO
            coEvery {
                localDataSourceRepo.getFromFormLibModuleDataStore(
                    intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[2].stopid}"),
                    ZERO
                )
            } returns ZERO

            assertFalse(
                tripCompletionUseCase.isTripComplete(
                    CharMatcher.any().toString(),
                    CharMatcher.any().toString()
                ).second
            )
        }

    @Test
    fun `verify runOnTripEnd marks inactive trip as completed in firestore when there is an different inactive trip`() =
        runTest {
            coEvery {
                tripCompletionUseCase.isDispatchAlreadyCompleted(
                    any(),
                    any(),
                    any()
                )
            } returns false
            coEvery { tripCompletionUseCase.updateTripCompletedFlagToFirestore(any(), any()) } just runs
            tripCompletionUseCase.runOnTripEnd("1235", "test", workManager, pfmEventsInfo)
            coVerify(exactly = 1) {
                tripCompletionUseCase.updateTripCompletedFlagToFirestore(any(), "1235")
            }
        }


    @Test
    fun `verify runOnTripEnd execution for a proper active trip completion`() = runTest {
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(),any(),any(),any(),any())} returns mutableListOf()
        coEvery { localDataSourceRepo.removeAllKeysOfAppModuleDataStore() } just runs
        coEvery { localDataSourceRepo.isKeyAvailableInAppModuleDataStore(DataStoreManager.IS_ACTIVE_DISPATCH_STOP_MANIPULATED) } returns true
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns false
        every { sendDispatchDataUseCase.sendDispatchCompleteEvent() } just runs
        every { dispatchFirestoreRepo.unRegisterFirestoreLiveListeners() } just runs
        every { tripCompletionUseCase.disposeStopListEventsCacheOnTripEnd(any()) } just runs
        every { stopDetentionWarningUseCase.cancelDetentionWarningTimer() } just runs
        every { workManager.cancelAllWorkByTag(any()) } returns mockk()
        coEvery { tripCompletionUseCase.sendTripEndEventToPFM(any(), any(), any(), any(), any()) } just runs
        coEvery { tripCompletionUseCase.sendDispatchEndDataToBackbone(any()) } just runs
        coEvery { tripCompletionUseCase.updateTripCompletedFlagToFirestore(any(), any()) } just runs
        coEvery { tripCompletionUseCase.updateTripInfoWidgetOnTripCompletion() } just runs
        every { tripPanelUseCase.lastSentTripPanelMessage.messageId } returns 1
        every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
        coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs
        coEvery { localDataSourceRepo.removeAllKeysOfAppModuleDataStore() } just runs
        every { managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration(any()) } returns true
        coEvery { dispatchListUseCaseTest.getDispatchesForTheTruckAndScheduleAutoStartTrip(any(),any(),any()) } just runs
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any(), any()) } returns mutableListOf()
        coEvery {
            tripCompletionUseCase.isDispatchAlreadyCompleted(
                any(),
                any(),
                any()
            )
        } returns false

        tripCompletionUseCase.runOnTripEnd(activeDispatchId, "test", workManager, pfmEventsInfo)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripCompletionUseCase.sendDispatchCompleteEventToCPIK(any(), any())
            dispatchFirestoreRepo.unRegisterFirestoreLiveListeners()
            tripCompletionUseCase.disposeStopListEventsCacheOnTripEnd(any())
            stopDetentionWarningUseCase.cancelDetentionWarningTimer()
            tripCompletionUseCase.cancelAllLateNotificationCheckWorksByTag(any(), any())
            tripCompletionUseCase.sendTripEndEventToPFM(any(), any(), any(), any(), any())
            tripCompletionUseCase.sendDispatchEndDataToBackbone(any())
            tripCompletionUseCase.updateTripCompletedFlagToFirestore(any(), any())
            tripCompletionUseCase.updateTripInfoWidgetOnTripCompletion()
            sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any())
            tripPanelUseCase.dismissTripPanelMessage(any())
            localDataSourceRepo.removeAllKeysOfAppModuleDataStore()
            dispatchListUseCaseTest.getDispatchesForTheTruckAndScheduleAutoStartTrip(any(),any(),any())
        }
        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            sendDispatchDataUseCase.removeAllGeofences()
        }
    }


    @Test
    fun `verify runOnTripEnd execution for trip stop manipulation`() = runTest {
        every { managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration(any()) } returns true
        val stopDetailList = ArrayList<StopDetail>()

        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 0,
                completedTime = at20200216T111954765Z,
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
                Actions.add(
                    Action(
                        actionType = 1
                    )
                )
                Actions.add(
                    Action(
                        actionType = 2,
                        responseSent = false
                    )
                )
            }
        )
        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 1,
                completedTime = at20200216T111954765Z,
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
            }
        )
        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 2,
                completedTime = at20200216T111954765Z
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
            }
        )
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(),any(),any(),any(),any())} returns mutableListOf()
        coEvery { localDataSourceRepo.removeAllKeysOfAppModuleDataStore() } just runs
        coEvery { localDataSourceRepo.isKeyAvailableInAppModuleDataStore(DataStoreManager.IS_ACTIVE_DISPATCH_STOP_MANIPULATED) } returns true
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns true
        every { sendDispatchDataUseCase.sendDispatchCompleteEvent() } just runs
        every { dispatchFirestoreRepo.unRegisterFirestoreLiveListeners() } just runs
        every { tripCompletionUseCase.disposeStopListEventsCacheOnTripEnd(any()) } just runs
        every { stopDetentionWarningUseCase.cancelDetentionWarningTimer() } just runs
        every { workManager.cancelAllWorkByTag(any()) } returns mockk()
        coEvery { tripCompletionUseCase.sendTripEndEventToPFM(any(), any(), any(), any(), any()) } just runs
        coEvery { tripCompletionUseCase.sendDispatchEndDataToBackbone(any()) } just runs
        coEvery { tripCompletionUseCase.updateTripCompletedFlagToFirestore(any(), any()) } just runs
        coEvery { tripCompletionUseCase.updateTripInfoWidgetOnTripCompletion() } just runs
        every { tripPanelUseCase.lastSentTripPanelMessage.messageId } returns 1
        every { sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any()) } just runs
        coEvery { tripPanelUseCase.dismissTripPanelMessage(any()) } just runs
        coEvery { localDataSourceRepo.removeAllKeysOfAppModuleDataStore() } just runs
        every { sendDispatchDataUseCase.removeAllGeofences() } just runs
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any(), any()) } returns mutableListOf()
        coEvery {
            tripCompletionUseCase.isDispatchAlreadyCompleted(
                any(),
                any(),
                any()
            )
        } returns false
        coEvery { dispatchListUseCaseTest.getDispatchesForTheTruckAndScheduleAutoStartTrip(any(),any(),any()) } just runs


        tripCompletionUseCase.runOnTripEnd(activeDispatchId, "test", workManager, pfmEventsInfo)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            sendDispatchDataUseCase.removeAllGeofences()
            dispatchFirestoreRepo.unRegisterFirestoreLiveListeners()
            tripCompletionUseCase.disposeStopListEventsCacheOnTripEnd(any())
            stopDetentionWarningUseCase.cancelDetentionWarningTimer()
            tripCompletionUseCase.cancelAllLateNotificationCheckWorksByTag(any(), any())
            tripCompletionUseCase.sendTripEndEventToPFM(any(), any(), any(), any(), any())
            tripCompletionUseCase.sendDispatchEndDataToBackbone(any())
            tripCompletionUseCase.updateTripCompletedFlagToFirestore(any(), any())
            tripCompletionUseCase.updateTripInfoWidgetOnTripCompletion()
            sendWorkflowEventsToAppUseCase.sendWorkflowEvent(any(), any())
            tripPanelUseCase.dismissTripPanelMessage(any())
            localDataSourceRepo.removeAllKeysOfAppModuleDataStore()
            dispatchListUseCaseTest.getDispatchesForTheTruckAndScheduleAutoStartTrip(any(),any(),any())
        }
        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripCompletionUseCase.sendDispatchCompleteEventToCPIK(any(), any())
        }
    }

    @Test
    fun `verify checkForTripCompletionAndEndTrip execution for a proper active trip background completion`() = runTest {
        val stopDetailList = ArrayList<StopDetail>()
        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 0,
                completedTime = at20200216T111954765Z
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
            }
        )
        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 1,
                completedTime = at20200216T111954765Z
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
            }
        )
        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 2,
                completedTime = at20200216T111954765Z
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
            }
        )
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
        coEvery {
            localDataSourceRepo.getFromFormLibModuleDataStore(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[0].stopid}"),
                ZERO
            )
        } returns ZERO
        coEvery {
            localDataSourceRepo.getFromFormLibModuleDataStore(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[1].stopid}"),
                ZERO
            )
        } returns ZERO
        coEvery {
            localDataSourceRepo.getFromFormLibModuleDataStore(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[2].stopid}"),
                ZERO
            )
        } returns ZERO

        coEvery { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any()) } just runs

        every { tripCompletionUseCase.getWorkManager(any()) } returns workManager

        tripCompletionUseCase.checkForTripCompletionAndEndTrip( "test", context)
        advanceUntilIdle()
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify checkForTripCompletionAndEndTrip execution for an incomplete trip background completion`() = runTest {
        coEvery { dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip() } returns false
        val stopDetailList = ArrayList<StopDetail>()
        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 0,
                completedTime = at20200216T111954765Z
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
            }
        )
        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 1,
                completedTime = at20200216T111954765Z
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
            }
        )
        stopDetailList.add(
            StopDetail(
                dispid = activeDispatchId,
                stopid = 2,
            ).apply {
                Actions.add(
                    Action(
                        actionType = 0
                    )
                )
            }
        )
        coEvery { dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any()) } returns stopDetailList
        coEvery {
            localDataSourceRepo.getFromFormLibModuleDataStore(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[0].stopid}"),
                ZERO
            )
        } returns ZERO
        coEvery {
            localDataSourceRepo.getFromFormLibModuleDataStore(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[1].stopid}"),
                ZERO
            )
        } returns ZERO
        coEvery {
            localDataSourceRepo.getFromFormLibModuleDataStore(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stopDetailList[2].stopid}"),
                ZERO
            )
        } returns ZERO

        tripCompletionUseCase.checkForTripCompletionAndEndTrip( "test", context)

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any())
        }
    }

    @Test
    fun `isManualTripCompletionDisabled returns true when tripStartDisableManual is set`() = runTest {
        // Arrange
        val caller = "testCaller"
        val cid = "123"
        val vehicleId = "4567"
        val dispatchId = "345345"

        coEvery {
            dispatchFirestoreRepo.getDispatchPayload(caller, cid, vehicleId, dispatchId)
        }returns Dispatch(
            tripStartDisableManual = 1
        )

        // Act
        val result = tripCompletionUseCase.isManualTripCompletionDisabled(caller, cid, vehicleId, dispatchId)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `isManualTripCompletionDisabled returns false when tripStartDisableManual has default value`() = runTest {
        // Arrange
        val caller = "testCaller"
        val cid = "123"
        val vehicleId = "4567"
        val dispatchId = "345345"

        coEvery {
            dispatchFirestoreRepo.getDispatchPayload(caller, cid, vehicleId, dispatchId)
        }returns Dispatch(
            tripStartDisableManual = 0
        )

        // Act
        val result = tripCompletionUseCase.isManualTripCompletionDisabled(caller, cid, vehicleId, dispatchId)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `verify updateTripCompletedFlagToFirestore() does not set completed flag on firestore for the trip`() = runTest {
        coEvery { tripCompletionUseCase["getCidAndTruckNumber"]("testCaller") } returns Triple("cid","truckNumber", false)

        tripCompletionUseCase.updateTripCompletedFlagToFirestore("testCaller", "12345")

        coVerify(exactly = 0) { tripMobileOriginatedEventsRepo.setIsCompleteFlagForTrip(any(), any()) }
    }

    @Test
    fun `verify updateTripCompletedFlagToFirestore() sets completed flag on firestore for the trip`() = runTest {
        coEvery { tripCompletionUseCase["getCidAndTruckNumber"]("testCaller") } returns Triple("cid","truckNumber", true)
        coEvery { tripMobileOriginatedEventsRepo.setIsCompleteFlagForTrip(any(), any()) } just runs

        tripCompletionUseCase.updateTripCompletedFlagToFirestore("testCaller", "12345")

        coVerify(exactly = 1) { tripMobileOriginatedEventsRepo.setIsCompleteFlagForTrip(any(), any()) }
    }

    @Test
    fun `runOnTripEnd does not execute subsequent functions if dispatch is already completed`() =
        runTest {
            // Arrange
            val dispatchId = "123"
            val caller = "testCaller"
            val pfmEventsInfo = PFMEventsInfo.TripEvents(StopActionReasonTypes.AUTO.name, false)
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns dispatchId
            coEvery { appModuleCommunicator.doGetCid() } returns "cid"
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns "truckNumber"
            coEvery {
                tripCompletionUseCase.isDispatchAlreadyCompleted(
                    any(),
                    any(),
                    any()
                )
            } returns true

            // Act
            tripCompletionUseCase.runOnTripEnd(dispatchId, caller, workManager, pfmEventsInfo)

            // Assert
            coVerify(exactly = 0) {
                tripCompletionUseCase.updateTripCompletedFlagToFirestore(any(), any())
                tripCompletionUseCase.sendTripEndEventToPFM(any(), any(), any(), any(), any())
                tripCompletionUseCase.sendDispatchEndDataToBackbone(any())
            }
        }

    @Test
    fun `areAllStopsDeleted returns false when stop count is greater than zero`() = runTest {
        // Arrange
        val cid = "5097"
        val truckNumber = "truck01"
        val dispatchId = "1412"
        coEvery { dispatchFirestoreRepo.getStopCountOfDispatch(cid, truckNumber, dispatchId) } returns 5

        // Act
        val result = tripCompletionUseCase.areAllStopsDeleted(cid, truckNumber, dispatchId)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `areAllStopsDeleted returns true when stop count is zero`() = runTest {
        // Arrange
        val cid = "cid"
        val truckNumber = "truckNumber"
        val dispatchId = "dispatchId"
        coEvery { dispatchFirestoreRepo.getStopCountOfDispatch(cid, truckNumber, dispatchId) } returns 0

        // Act
        val result = tripCompletionUseCase.areAllStopsDeleted(cid, truckNumber, dispatchId)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `areAllStopsDeleted returns false when stop count is null`() = runTest {
        val cid = "cid"
        val truckNumber = "truckNumber"
        val dispatchId = "dispatchId"
        coEvery { dispatchFirestoreRepo.getStopCountOfDispatch(cid, truckNumber, dispatchId) } returns null

        val result = tripCompletionUseCase.areAllStopsDeleted(cid, truckNumber, dispatchId)

        assertFalse(result)
    }


    @Test
    fun `verify processDeletedDispatchAndSendTripCompletionEventsToPFM() does not call runOnTripEnd() if there is no active dispatch`() = runTest {
        val fcmData = FcmData(
            cid = "TestCustomerId",
            vid = "TestVehicleId",
            dispatchId = "TestDispatchId",
            dispatchDeletedTime = "TestDispatchDeletedTime"
        )

        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns ""
        coEvery { tripCompletionUseCase.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(any(), any(), any(), any()) } just runs
        coEvery { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any()) } just runs

        tripCompletionUseCase.processDeletedDispatchAndSendTripCompletionEventsToPFM(fcmData)

        coVerify(exactly = 0) { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any())  }
        coVerify(exactly = 1) { tripCompletionUseCase.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(any(), any(), any(), any())  }
    }

    @Test
    fun `verify processDeletedDispatchAndSendTripCompletionEventsToPFM() does calls runOnTripEnd() if there is an active dispatch`() = runTest {
        val fcmData = FcmData(
            cid = "TestCustomerId",
            vid = "TestVehicleId",
            dispatchId = "TestDispatchId",
            dispatchDeletedTime = "TestDispatchDeletedTime"
        )

        coEvery { WorkManager.getInstance(ApplicationContextProvider.getApplicationContext()) }returns workManager
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "TestDispatchId"
        coEvery { tripCompletionUseCase.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(any(), any(), any(), any()) } just runs
        coEvery { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any()) } just runs

        tripCompletionUseCase.processDeletedDispatchAndSendTripCompletionEventsToPFM(fcmData)

        coVerify(exactly = 1) { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any())  }
        coVerify(exactly = 1) { tripCompletionUseCase.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(any(), any(), any(), any())  }
    }

    @Test
    fun `verify processDeletedDispatchAndSendTripCompletionEventsToPFM() does calls not runOnTripEnd() when the dispatchId from FCM does not match active dispatchId`() = runTest {
        val fcmData = FcmData(
            cid = "TestCustomerId",
            vid = "TestVehicleId",
            dispatchId = "FCMDispatchId",
            dispatchDeletedTime = "TestDispatchDeletedTime"
        )

        coEvery { WorkManager.getInstance(ApplicationContextProvider.getApplicationContext()) }returns workManager
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "TestDispatchId"
        coEvery { tripCompletionUseCase.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(any(), any(), any(), any()) } just runs
        coEvery { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any()) } just runs

        tripCompletionUseCase.processDeletedDispatchAndSendTripCompletionEventsToPFM(fcmData)

        coVerify(exactly = 0) { tripCompletionUseCase.runOnTripEnd(any(), any(), any(), any())  }
        coVerify(exactly = 1) { tripCompletionUseCase.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(any(), any(), any(), any())  }
    }

    @Test
    fun `verify setIsDispatchDeleteAndDispatchDeletedTimeInFireStore calls required methods`() = runTest {
        val cid = "testCid"
        val vid = "testVehicleId"
        val dispatchId = "dispatchId"
        val dispatchDeletedTIme = "dispatchDeletedTIme"
        coEvery {  tripMobileOriginatedEventsRepo.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(any(), any(), any(), any()) } just runs

        tripCompletionUseCase.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(cid, vid, dispatchId, dispatchDeletedTIme)

        coVerify(exactly = 1) { tripMobileOriginatedEventsRepo.setIsDispatchDeleteAndDispatchDeletedTimeInFireStore(any(), any(), any(), any()) }
    }

    @After
    fun clear() {
        unmockkObject(FormUtils)
        unmockkAll()
    }
}