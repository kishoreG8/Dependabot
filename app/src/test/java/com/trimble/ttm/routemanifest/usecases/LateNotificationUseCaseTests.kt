package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.gson.Gson
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.managers.workmanager.StopActionLateNotificationWorker
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.ArrivalReason
import com.trimble.ttm.routemanifest.model.JsonData
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.ArrivalReasonEventRepo
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.repo.TripMobileOriginatedEventsRepo
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.CoroutineTestRule
import com.trimble.ttm.routemanifest.utils.JsonDataConstructionUtils
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

const val DEPART_ACTION_RESPONSE_JSONDATA =
    "JsonData(key=data, value={\"actionId\":0,\"cid\":5097,\"createdDate\":\"2022-01-12T00:00:00.000Z\",\"dispId\":123434,\"dsn\":\"12345\",\"fuel\":34,\"lat\":12.5,\"lon\":30.0,\"mileType\":\"gps\",\"negGuf\":false,\"odom\":21404,\"odomType\":\"j1708\",\"quality\":\"good\",\"reason\":\"timeout\",\"stopId\":0,\"vid\":\"INST751\"})"

class LateNotificationUseCaseTests {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var lateNotificationUseCase: LateNotificationUseCase

    private lateinit var context: Context

    @RelaxedMockK
    private lateinit var tripMobileOriginatedEventsRepo: TripMobileOriginatedEventsRepo

    @RelaxedMockK
    private lateinit var arrivalReasonEventRepo: ArrivalReasonEventRepo

    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo
    @RelaxedMockK
    private lateinit var backboneUseCase: BackboneUseCase

    @RelaxedMockK
    private lateinit var application: WorkflowApplication

    private lateinit var appModuleCommunicator: AppModuleCommunicator

    private val testScope = TestScope()
    private val testDispatcherProvider = TestDispatcherProvider()

    private val stopList = mutableListOf<StopDetail>()

    private val dispatchId = "123434"

    @MockK
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = mockk()
        MockKAnnotations.init(this)
        appModuleCommunicator = mockk()
        dispatchFirestoreRepo = mockk(relaxed = true)
        ApplicationContextProvider.init(application)
        coEvery { appModuleCommunicator.doGetCid() } returns "5097"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123456"
        coEvery { appModuleCommunicator.doGetObcId() } returns "12345"
        coEvery { appModuleCommunicator.doGetVid() } returns 123456L
        mockkObject(FormUtils)
        mockkObject(com.trimble.ttm.commons.utils.FormUtils)
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0, 0.0)
        every { dispatchFirestoreRepo.getAppModuleCommunicator() } returns appModuleCommunicator
        lateNotificationUseCase = LateNotificationUseCase(testScope, backboneUseCase, dispatchFirestoreRepo, tripMobileOriginatedEventsRepo, testDispatcherProvider, arrivalReasonEventRepo)

        stopList.add(
            StopDetail(
                deleted = 0,
                stopid = 0,
                dispid = dispatchId
            ).also {
                it.Actions.addAll(
                    mutableListOf(
                        Action(eta = "2022-02-08T14:30:00.000Z", responseSent = true, actionid = 0, stopid = it.stopid),
                        Action(eta = "2022-02-08T14:30:00.000Z", responseSent = false, actionid = 1, stopid = it.stopid)
                    )
                )
            }
        )
        stopList.add(
            StopDetail(
                deleted = 1,
                stopid = 1,
                dispid = dispatchId
            ).also {
                it.Actions.addAll(
                    mutableListOf(
                        Action(eta = "2022-02-08T14:30:00.000Z", actionid = 0, stopid = it.stopid),
                        Action(eta = "2022-02-08T14:30:00.000Z", actionid = 1, stopid = it.stopid)
                    )
                )
            }
        )
        val flagName = FeatureGatekeeper.KnownFeatureFlags.SHOULD_USE_CONFIGURABLE_ODOMETER
        val featureMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> =
            mapOf(flagName to FeatureFlagDocument(flagName.id, shouldEnableFeature = true))
        coEvery { appModuleCommunicator.getFeatureFlags() } returns featureMap
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        mockkObject(StopActionLateNotificationWorker.Companion)
        mockkObject(JsonDataConstructionUtils.Companion)
        every { StopActionLateNotificationWorker.Companion.enqueueStopActionLateNotificationCheckWork(any(), any(), any(), any()) } just runs
    }

    @Test
    fun getActionPathForArrive() = runTest {
        val action = Action(
            1, 0, stopid = 1, dispid = "123434"
        )

        assertEquals(
            lateNotificationUseCase.getLateNotificationActionPath(action, "timeout"),
            "5097/vehicles/123456/stopEvents/123434_1_0_timeout"
        )
    }

    @Test
    fun getTimeoutActionResponse() = runTest {
        val action = Action(
            2, 0, stopid = 0, dispid = "123434"
        )
        val time = DateUtil.getUTCFormattedDate(Date(1641945600000))
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(12.5, 30.0)

        coEvery {
            backboneUseCase.getFuelLevel()
        } returns 34

        coEvery {
            backboneUseCase.getOdometerReading(true)
        } returns 3444.8

        val jsonData = JsonDataConstructionUtils.getStopActionJson(
            action = action,
            createDate = time,
            pfmEventsInfo = PFMEventsInfo.StopActionEvents("timeout"),
            fuelLevel = 34,
            odometerReading = 3444.8,
            customerIdObcIdVehicleId = Triple(5097, "12345", "INST751"),
            currentLocationLatLong = Pair(12.5, 30.0)
        ).toString()
        assertEquals(jsonData, DEPART_ACTION_RESPONSE_JSONDATA)
    }

    @Test
    fun `check if processForLateActionNotification is writing data to firestore if there is no timeout already in firestore`() = runTest {
        val action = Action(eta = "2022-02-08T14:30:00.000Z")
        coEvery {
            tripMobileOriginatedEventsRepo.saveStopActionResponse(
                any(), any(), any(), any()
            )
        } just runs
        coEvery {
            arrivalReasonEventRepo.getCurrentStopArrivalReason(
                any()
            )
        } returns ArrivalReason()
        coEvery { JsonDataConstructionUtils.getStopActionJson(any(), any(), any(), any(), any(), any(), any()) } returns JsonData("data", "{\"actionId\":0,\"cid\":5097,\"createdDate\":\"2022-01-12T00:00:00.000Z\",\"dispId\":123434,\"dsn\":\"12345\",\"fuel\":34,\"lat\":12.5,\"lon\":30.0,\"mileType\":\"ecm\",\"negGuf\":\"false\",\"odom\":21404,\"odomType\":\"j1708\",\"quality\":\"good\",\"reason\":\"timeout\",\"stopId\":0,\"vid\":123456}")
        coEvery { tripMobileOriginatedEventsRepo.isLateNotificationExists(any(), any()) } returns false

        lateNotificationUseCase.processForLateActionNotification(coroutineDispatcherProvider = testDispatcherProvider, action)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripMobileOriginatedEventsRepo.saveStopActionResponse(
                any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `check if processForLateActionNotification is not writing data to firestore if there is a timeout already in firestore`() = runTest {
        val action = Action(eta = "2022-02-08T14:30:00.000Z")
        coEvery { tripMobileOriginatedEventsRepo.isLateNotificationExists(any(), any()) } returns true

        lateNotificationUseCase.processForLateActionNotification(coroutineDispatcherProvider = DefaultDispatcherProvider(), action)

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            tripMobileOriginatedEventsRepo.saveStopActionResponse(any(), any(), any(), any())
            arrivalReasonEventRepo.getCurrentStopArrivalReason(any())
        }
    }

    @Test
    fun `check if fetchInCompleteStopActions is giving proper inCompleteActionsOfActiveStops`() = runTest {
        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(
                any(),
                any(),
                any(),
                any()
            )
        } returns stopList
        val inCompleteActionsOfActiveStops = lateNotificationUseCase.fetchInCompleteStopActions(
            caller = "doOnTripStart",
            dispatchId = "123434"
        )
        assertEquals(inCompleteActionsOfActiveStops.size, 1)
        assertEquals(inCompleteActionsOfActiveStops[0].actionid, 1)
        assertEquals(inCompleteActionsOfActiveStops[0].stopid, 0)
        assertNotEquals(inCompleteActionsOfActiveStops[0].stopid, 1)
    }

    @Test
    fun `check if scheduleLateNotificationCheckWorker is returning without scheduling work manager for actions without eta`() = runTest {
        val validInCompleteAction = stopList[0].Actions[1]
        val dispatchId = stopList[0].dispid
        validInCompleteAction.eta = EMPTY_STRING
        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
        } returns stopList

        lateNotificationUseCase.scheduleLateNotificationCheckWorker(testScope, "test", dispatchId, workManager, false)

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) wasNot Called
        }
    }

    @Test
    fun `check if scheduleLateNotificationCheckWorker is proceeding without scheduling work manager for actions with past expired eta`() = runTest {
        val dispatchId = stopList[0].dispid
        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
        } returns stopList

        lateNotificationUseCase.scheduleLateNotificationCheckWorker(testScope, "test", dispatchId, workManager, false)

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) wasNot Called
        }
    }

    @Test
    fun `check if scheduleLateNotificationCheckWorker is proceeding with work manager schedule for actions with future eta`() = runTest {
        val dispatchId = stopList[0].dispid
        val validInCompleteAction = stopList[0].Actions[1]
        validInCompleteAction.eta = "2050-02-08T14:30:00.000Z"

        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
        } returns stopList

        lateNotificationUseCase.scheduleLateNotificationCheckWorker(testScope, "test", dispatchId, workManager, false)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            StopActionLateNotificationWorker.Companion.enqueueStopActionLateNotificationCheckWork(any(), any(), any(), any())
        }
    }

    @Test
    fun `check if scheduleLateNotificationCheckWorker is proceeding with work manager schedule for actions with future eta for all incomplete actions`() = runTest {
        val dispatchId = stopList[0].dispid
        stopList[0].Actions[0].responseSent = false
        stopList[0].Actions[0].eta = "2050-02-08T14:30:00.000Z"
        stopList[0].Actions[1].eta = "2050-02-08T14:30:00.000Z"
        stopList.add(
            StopDetail(
                deleted = 0,
                stopid = 2,
                dispid = dispatchId
            ).also {
                it.Actions.addAll(
                    mutableListOf(
                        Action(eta = "2050-02-08T14:30:00.000Z", actionid = 0, stopid = it.stopid),
                        Action(eta = "2050-02-08T14:30:00.000Z", actionid = 1, stopid = it.stopid),
                        Action(eta = "2050-02-08T14:30:00.000Z", actionid = 2, stopid = it.stopid),
                    )
                )
            }
        )

        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
        } returns stopList

        lateNotificationUseCase.scheduleLateNotificationCheckWorker(testScope, "test", dispatchId, workManager, false)

        verify(exactly = 5, timeout = TEST_DELAY_OR_TIMEOUT * 2) {
            StopActionLateNotificationWorker.Companion.enqueueStopActionLateNotificationCheckWork(any(), any(), any(), any())
        }
    }

    @Test
    fun `check if scheduleLateNotificationCheckWorker is returning without scheduling work manager for actions without eta for trip start`() = runTest {
        val validInCompleteAction = stopList[0].Actions[1]
        val dispatchId = stopList[0].dispid
        validInCompleteAction.eta = EMPTY_STRING
        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
        } returns stopList

        lateNotificationUseCase.scheduleLateNotificationCheckWorker(testScope, "test", dispatchId, workManager, true)

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) wasNot Called
        }
    }

    @Test
    fun `check if scheduleLateNotificationCheckWorker is proceeding with scheduling work manager for actions with past expired eta for trip start`() = runTest {
        val dispatchId = stopList[0].dispid
        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
        } returns stopList

        lateNotificationUseCase.scheduleLateNotificationCheckWorker(testScope, "test", dispatchId, workManager, true)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            StopActionLateNotificationWorker.Companion.enqueueStopActionLateNotificationCheckWork(any(), any(), any(), 0L)
        }
    }

    @Test
    fun `check if scheduleLateNotificationCheckWorker is proceeding with work manager schedule for actions with future eta for trip start`() = runTest {
        val dispatchId = stopList[0].dispid
        val validInCompleteAction = stopList[0].Actions[1]
        validInCompleteAction.eta = "2050-02-08T14:30:00.000Z"

        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
        } returns stopList

        lateNotificationUseCase.scheduleLateNotificationCheckWorker(testScope, "test", dispatchId, workManager, true)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            StopActionLateNotificationWorker.Companion.enqueueStopActionLateNotificationCheckWork(any(), any(), any(), any())
        }
    }

    @Test
    fun `check if scheduleLateNotificationCheckWorker is proceeding with work manager schedule for actions with future eta for all incomplete actions for trip start`() = runTest {
        val dispatchId = stopList[0].dispid
        stopList[0].Actions[0].responseSent = false
        stopList[0].Actions[0].eta = "2050-02-08T14:30:00.000Z"
        stopList[0].Actions[1].eta = "2050-02-08T14:30:00.000Z"
        stopList.add(
            StopDetail(
                deleted = 0,
                stopid = 2,
                dispid = dispatchId
            ).also {
                it.Actions.addAll(
                    mutableListOf(
                        Action(eta = "2050-02-08T14:30:00.000Z", actionid = 0, stopid = it.stopid),
                        Action(eta = "2050-02-08T14:30:00.000Z", actionid = 1, stopid = it.stopid),
                        Action(eta = "2050-02-08T14:30:00.000Z", actionid = 2, stopid = it.stopid),
                    )
                )
            }
        )

        coEvery {
            dispatchFirestoreRepo.getStopsFromFirestore(any(), any(), any(), any())
        } returns stopList

        lateNotificationUseCase.scheduleLateNotificationCheckWorker(testScope, "test", dispatchId, workManager, true)

        verify(exactly = 5, timeout = TEST_DELAY_OR_TIMEOUT) {
            StopActionLateNotificationWorker.Companion.enqueueStopActionLateNotificationCheckWork(any(), any(), any(), any())
        }
    }

    @Test
    fun `validate action id 0 and reason is timeout`() = runTest {
        val gson = Gson()
        val jsonData = getJsonData("timeout")
        val actionIdWithReason = lateNotificationUseCase.getActionIdWithReason(jsonData, gson)

        assertEquals(actionIdWithReason.first, "0")
        assertEquals(actionIdWithReason.second, "\"timeout\"")
    }

    @Test
    fun `validate action id 0 and reason is not timeout`() = runTest {
        val gson = Gson()
        val jsonData = getJsonData("manual")
        val actionIdWithReason =
            lateNotificationUseCase.getActionIdWithReason(jsonData, gson)

        coEvery { appModuleCommunicator.doGetCid() } returns "5097"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123456"
        coEvery { appModuleCommunicator.doGetObcId() } returns "12345"
        coEvery { appModuleCommunicator.doGetVid() } returns 123456L

        assertEquals(actionIdWithReason.first, "0")
        assertNotEquals(actionIdWithReason.second, "\"timeout\"")
    }

    @Test
    fun `validate exception in get ActionId With Reason`() {
        val gson = Gson()
        val jsonData = JsonData("data", "")
        val actionIdWithReason =
            lateNotificationUseCase.getActionIdWithReason(jsonData, gson)

        assertTrue(actionIdWithReason.first.isEmpty())
        assertTrue(actionIdWithReason.second.isEmpty())
    }

    private suspend fun getJsonData(reason: String): JsonData {
        val action = Action(
            2, 0, stopid = 0, dispid = "123434"
        )
        val time = DateUtil.getUTCFormattedDate(Date(1641945600000))
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(12.5, 30.0)

        coEvery {
            backboneUseCase.getFuelLevel()
        } returns 34

        coEvery {
            backboneUseCase.getOdometerReading(false)
        } returns 3444.8
        return JsonDataConstructionUtils.getStopActionJson(
            action = action,
            createDate = time,
            pfmEventsInfo = PFMEventsInfo.StopActionEvents(reason),
            fuelLevel = 34,
            odometerReading = 3444.8,
            customerIdObcIdVehicleId = Triple(5097, "12345", "INST751"),
            currentLocationLatLong = Pair(12.5, 30.0)
        )
    }

    @After
    fun tearDown() {
        unmockkObject(FormUtils)
        unmockkObject(com.trimble.ttm.commons.utils.FormUtils)
        unmockkAll()
    }
}