package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import android.content.SharedPreferences
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.repo.FireStoreCacheRepository
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
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
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripCacheUseCaseTest {
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var formDataStoreManager: FormDataStoreManager
    @MockK
    private lateinit var context: Context
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var workflowAppNotificationUseCase:WorkflowAppNotificationUseCase
    @MockK
    private lateinit var fireStoreCacheRepository:FireStoreCacheRepository
    private lateinit var tripCacheUseCase: TripCacheUseCase
    @MockK
    private lateinit var dispatchListUseCase: DispatchListUseCase
    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase
    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @RelaxedMockK
    private lateinit var application: WorkflowApplication

    @RelaxedMockK
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        ApplicationContextProvider.init(application)
        mockkObject(WorkflowApplication)
        mockkObject(ApplicationContextProvider)
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        every { context.getSharedPreferences(any(), 0) } returns sharedPreferences
        every { ApplicationContextProvider.getApplicationContext() } returns application.applicationContext
        dataStoreManager = spyk(DataStoreManager(context))
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        tripCacheUseCase = spyk(TripCacheUseCase(
            fireStoreCacheRepository,
            dataStoreManager,
            workflowAppNotificationUseCase,
            fetchDispatchStopsAndActionsUseCase,
            dispatchListUseCase,
            appModuleCommunicator,
            dispatchFirestoreRepo
        ), recordPrivateCalls = true)
    }

    @Test
    fun `validate if dispatch exists runs dispatch scheduling`() = runTest {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.RECEIVED_DISPATCH_SET_KEY,
                mutableSetOf()
            )
        } returns mutableSetOf()
        coEvery {
            dataStoreManager.setValue(DataStoreManager.RECEIVED_DISPATCH_SET_KEY, any())
        } returns mockk()
        Assert.assertEquals(tripCacheUseCase.addDispatchIdIfDispatchIsNewElseReturn("1"), false)
    }

    @Test
    fun `validate if dispatch exists returns without dispatch scheduling`() = runTest {
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.RECEIVED_DISPATCH_SET_KEY,
                mutableSetOf()
            )
        } returns mutableSetOf("1", "2", "3", "-1")
        coEvery {
            dataStoreManager.setValue(DataStoreManager.RECEIVED_DISPATCH_SET_KEY, any())
        } returns mockk()
        Assert.assertEquals(tripCacheUseCase.addDispatchIdIfDispatchIsNewElseReturn("1"), true)
    }

    @Test
    fun `schedule one minute delay called when remove one minute delay feature flag off`() = runTest{
        val dispatchId="1000"
        val scheduler = testScheduler // the scheduler used for this test
        val coroutineDispatcher = StandardTestDispatcher(scheduler, name = "IO dispatcher")
        val key1 = FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE
        val value1 = FeatureFlagDocument(FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE.id, listOf(), shouldEnableCIDFilter = true, shouldEnableFeature = false, "")
        coEvery { appModuleCommunicator.getFeatureFlags() } returns mapOf(key1 to value1)
        coEvery {
            dataStoreManager.getValue(
                DataStoreManager.RECEIVED_DISPATCH_SET_KEY,
                mutableSetOf()
            )
        } returns mutableSetOf()
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.RECEIVED_DISPATCH_SET_KEY,
                any()
            )
        } returns mockk()
        coEvery { dataStoreManager.containsKey(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns false
        coEvery { dispatchListUseCase.scheduleDispatch(any(), any(), any(),any()) } just runs
        coEvery { workflowAppNotificationUseCase.sendNewDispatchAppNotification(any()) } just runs
        val dispatch = Dispatch(name = "test", cid = 10119, vehicleNumber = "cgvus", dispid = "1000")

        //When
        tripCacheUseCase.scheduleOneMinuteDelayBasedOnFeatureFlag(
            dispatchId, isTripCompleted = false, isTripReady = false, dispatch, coroutineDispatcher)

        //Then
        testScheduler.advanceTimeBy(2*60*1000)

        //Verify
        coVerify(exactly = 1) { dispatchListUseCase.scheduleDispatch("1000","10119","cgvus",any()) }

        coVerify(exactly = 1) {
            tripCacheUseCase.sendNewDispatchNotificationIfThereIsNoActiveDispatch(
                any()
            )
        }
    }


    @Test
    fun `schedule one minute delay received dispatches data store cleared when it exceeds 50 limit also schedule 1 minute delay called if feature flag off`() =
        runTest {
            val dispatchId="1000"
            val scheduler = testScheduler // the scheduler used for this test
            val coroutineDispatcher = StandardTestDispatcher(scheduler, name = "IO dispatcher")
            val key1 = FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE
            val value1 = FeatureFlagDocument(FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE.id, listOf(), shouldEnableCIDFilter = true, shouldEnableFeature = false, "")
            coEvery { appModuleCommunicator.getFeatureFlags() } returns mapOf(key1 to value1)
            val receivedDispatches= mutableSetOf<String>()
            for (i in 1..50) {
                receivedDispatches.add(i.toString())
            }
            coEvery {
                dataStoreManager.getValue(
                    DataStoreManager.RECEIVED_DISPATCH_SET_KEY,
                    mutableSetOf()
                )
            } returns receivedDispatches.toSet()
            coEvery {
                dataStoreManager.setValue(
                    DataStoreManager.RECEIVED_DISPATCH_SET_KEY,
                    any()
                )
            } returns mockk()
            coEvery { dataStoreManager.containsKey(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns false
            coEvery { dispatchListUseCase.scheduleDispatch(any(), any(), any(),any()) } just runs
            coEvery { workflowAppNotificationUseCase.sendNewDispatchAppNotification(any()) } just runs
            val dispatch = Dispatch(name = "test", cid = 10119, vehicleNumber = "cgvus", dispid = "1000")

            //When
            tripCacheUseCase.scheduleOneMinuteDelayBasedOnFeatureFlag(
                dispatchId, isTripCompleted = false, isTripReady = false, dispatch,coroutineDispatcher)

            //Then
            testScheduler.advanceTimeBy(2*60*1000)

            //Verify
            coVerify(exactly = 1) { dispatchListUseCase.scheduleDispatch("1000","10119","cgvus","") }

            coVerify(exactly = 1) { dataStoreManager.setValue(eq(DataStoreManager.RECEIVED_DISPATCH_SET_KEY),any()) }

            coVerify(exactly = 1) {
                tripCacheUseCase.sendNewDispatchNotificationIfThereIsNoActiveDispatch(
                    any()
                )
            }
        }

    @Test
    fun `schedule one minute delay not called when remove one minute delay feature flag on`() = runTest {
        //When
        val key1 = FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE
        val value1 = FeatureFlagDocument(FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE.id, listOf(), shouldEnableCIDFilter = true, shouldEnableFeature = true, "")
        coEvery { appModuleCommunicator.getFeatureFlags() } returns mapOf(key1 to value1)
        coEvery { dataStoreManager.containsKey(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns false
        coEvery { workflowAppNotificationUseCase.sendNewDispatchAppNotification(any()) } just runs

        //Then
        tripCacheUseCase.scheduleOneMinuteDelayBasedOnFeatureFlag(
            "100",
            isTripCompleted = false,
            isTripReady = false,
            Dispatch(),
             UnconfinedTestDispatcher()
        )

        //Verify
        coVerify(exactly = 1) {
            tripCacheUseCase.sendNewDispatchNotificationIfThereIsNoActiveDispatch(
                any()
            )
        }
        coVerify(exactly = 0) { dispatchListUseCase.scheduleDispatch(any(), any(), any(),any()) }
    }

    @Test
    fun testFormSyncHappenedIfDriverFormIdAndFormClassAreValid() = runTest {
        val cid = "5688"
        val stopDetail = StopDetail().apply {
            Actions.add(
                Action(
                    actionType = 1,
                    stopid = 123,
                    driverFormid = 1234,
                    driverFormClass = 1
                )
            )
        }
        coEvery { fireStoreCacheRepository.syncFormData(any(), any(), any()) } just runs
        tripCacheUseCase.getForms(cid, stopDetail.Actions)
        verify(exactly = 1) {
            tripCacheUseCase["syncFormDataBasedOnTheFormClass"](
                cid,
                stopDetail.Actions[0].driverFormid.toString(),
                stopDetail.Actions[0].driverFormClass
            )
        }
    }

    @Test
    fun testFormSyncNotHappenedIfDriverFormIdIsEmpty() = runTest {
        val cid = "5688"
        val stopDetail = StopDetail().apply {
            Actions.add(
                Action(
                    actionType = 1,
                    stopid = 123,
                    driverFormid = 0,
                    driverFormClass = 1
                )
            )
        }
        coEvery { fireStoreCacheRepository.syncFormData(any(), any(), any()) } just runs
        tripCacheUseCase.getForms(cid, stopDetail.Actions)
        verify(exactly = 0) {
            tripCacheUseCase["syncFormDataBasedOnTheFormClass"](
                cid,
                stopDetail.Actions[0].driverFormid.toString(),
                stopDetail.Actions[0].driverFormClass
            )
        }
    }

    @Test
    fun testFormSyncHappenedIfReplyFormIdAndFormClassAreValid() = runTest {
        val cid = "5688"
        val stopDetail = StopDetail().apply {
            Actions.add(
                Action(
                    actionType = 1,
                    stopid = 123,
                    forcedFormId = "1234",
                    forcedFormClass = 1
                )
            )
        }
        coEvery { fireStoreCacheRepository.syncFormData(any(), any(), any()) } just runs
        tripCacheUseCase.getForms(cid, stopDetail.Actions)
        verify(exactly = 1) {
            tripCacheUseCase["syncFormDataBasedOnTheFormClass"](
                cid,
                stopDetail.Actions[0].forcedFormId,
                stopDetail.Actions[0].forcedFormClass
            )
        }
    }

    @Test
    fun testFormSyncNotHappenedIfReplyFormIdIsEmpty() = runTest {
        val cid = "5688"
        val stopDetail = StopDetail().apply {
            Actions.add(
                Action(
                    actionType = 1,
                    stopid = 123,
                    forcedFormId = EMPTY_STRING,
                    driverFormClass = 1
                )
            )
        }
        coEvery { fireStoreCacheRepository.syncFormData(any(), any(), any()) } just runs
        tripCacheUseCase.getForms(cid, stopDetail.Actions)
        verify(exactly = 0) {
            tripCacheUseCase["syncFormDataBasedOnTheFormClass"](
                cid,
                stopDetail.Actions[0].forcedFormId,
                stopDetail.Actions[0].forcedFormClass
            )
        }
    }

    @Test
    fun `verify sendNewDispatchNotificationIfThereIsNoActiveDispatch() returns true if there is a active dispatch`() = runTest {
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns true

        val result = tripCacheUseCase.sendNewDispatchNotificationIfThereIsNoActiveDispatch(Dispatch())

        coVerify(exactly = 0) { workflowAppNotificationUseCase.sendNewDispatchAppNotification(any()) }
        assertTrue { result }
    }

    @Test
    fun `verify sendNewDispatchNotificationIfThereIsNoActiveDispatch() returns false and calls required methods if there is no active dispatch`() = runTest {
        coEvery { dataStoreManager.containsKey(ACTIVE_DISPATCH_KEY) } returns false
        coEvery { workflowAppNotificationUseCase.sendNewDispatchAppNotification(any()) } just runs

        val result = tripCacheUseCase.sendNewDispatchNotificationIfThereIsNoActiveDispatch(Dispatch())

        coVerify(exactly = 1) { workflowAppNotificationUseCase.sendNewDispatchAppNotification(any()) }
        assertFalse { result }
    }

    @Test
    fun `verify cacheDispatchData() returns required stopId to StopName Map`() = runTest {
        val dispatchId = "testDispatchId"
        val customerId = "testCustomerId"
        val vehicleId = "testTruckId"
        val expectedResult = hashMapOf("0" to "stop 1")
        coEvery { appModuleCommunicator.doGetCid() } returns customerId
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns vehicleId
        coEvery { tripCacheUseCase["getDispatch"](customerId, vehicleId, dispatchId) } returns Unit
        coEvery {
            tripCacheUseCase["getStopsAndActions"](customerId, vehicleId, dispatchId)
        } returns expectedResult

        val actualResult = tripCacheUseCase.cacheDispatchData(customerId, vehicleId, dispatchId)

        coVerify(exactly = 1) {
            tripCacheUseCase["getDispatch"](customerId, vehicleId, dispatchId)
            tripCacheUseCase["getStopsAndActions"](customerId, vehicleId, dispatchId)
        }
        assertEquals(actualResult, expectedResult)
    }

    @Test
    fun `verify cacheDispatchData() returns emptyMap if customerId from FCM is empty`() = runTest {
        val dispatchId = "testDispatchId"
        val customerId = EMPTY_STRING
        val vehicleId = "testTruckId"
        val expectedResult = hashMapOf<String, String>()
        coEvery { appModuleCommunicator.doGetCid() } returns "testCustomerId"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns vehicleId

        val actualResult = tripCacheUseCase.cacheDispatchData(customerId, vehicleId, dispatchId)

        assertEquals(actualResult, expectedResult)
    }

    @Test
    fun `verify cacheDispatchData() returns emptyMap if customerId from FCM and from App differs`() =
        runTest {
            val dispatchId = "testDispatchId"
            val customerId = "testCustomerIdFromFCM"
            val vehicleId = "testTruckId"
            val expectedResult = hashMapOf<String, String>()
            coEvery { appModuleCommunicator.doGetCid() } returns "testCustomerId"
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns vehicleId

            val actualResult = tripCacheUseCase.cacheDispatchData(customerId, vehicleId, dispatchId)

            assertEquals(actualResult, expectedResult)
        }

    @Test
    fun `verify getStopsAndActions() returns stopData`() = runTest {
        val stops = listOf(
            StopDetail(stopid = 0, name = "stop1"),
            StopDetail(stopid = 1, name = "stop2")
        )
        val expectedResult = hashMapOf<String, String>().apply {
            this["0"] = "stop1"
            this["1"] = "stop2"
        }
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getStopsAndActions(
                any(),
                any(),
                any(),
                any()
            )
        } returns stops
        coEvery { tripCacheUseCase.getForms(any(), any()) } just runs

        val result = tripCacheUseCase.getStopsAndActions(
            "testCustomerId",
            "testVehicleNumber",
            "testDispatchId"
        )

        assertEquals(expectedResult, result)
    }

    private suspend fun callGetDispatchPrivateFunction() {
        val getDispatchMethod =
            tripCacheUseCase::class.declaredFunctions.find { it.name == "getDispatch" }
        getDispatchMethod?.isAccessible = true
        getDispatchMethod?.callSuspend(
            tripCacheUseCase,
            "testCustomerId",
            "testVehicleNumber",
            "testDispatchId"
        )
    }

    @Test
    fun `verify getDispatch in tripCacheUseCase calls required methods when dispatchId is not Empty`() =
        runTest {
            val testDispatch = Dispatch(dispid = "TestDispatchId")
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getDispatch(
                    any(),
                    any(),
                    any()
                )
            } returns testDispatch
            coEvery {
                tripCacheUseCase.scheduleOneMinuteDelayBasedOnFeatureFlag(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns false

            callGetDispatchPrivateFunction()

            coVerify(exactly = 1) {
                tripCacheUseCase.scheduleOneMinuteDelayBasedOnFeatureFlag(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `verify getDispatch in tripCacheUseCase does not call required methods when dispatchId is Empty`() =
        runTest {
            val testDispatch = Dispatch()
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getDispatch(
                    any(),
                    any(),
                    any()
                )
            } returns testDispatch
            coEvery {
                tripCacheUseCase.scheduleOneMinuteDelayBasedOnFeatureFlag(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns false

            callGetDispatchPrivateFunction()

            coVerify(exactly = 0) {
                tripCacheUseCase.scheduleOneMinuteDelayBasedOnFeatureFlag(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `verify getDispatchBlob() calls required methods`() = runTest {
        coEvery {
            dispatchFirestoreRepo.getDispatchBlobDataByBlobId(
                any(),
                any(),
                any()
            )
        } returns DispatchBlob()

        tripCacheUseCase.getDispatchBlob("testCustomerId", "testVehicleId", "testBlobId")

        coVerify { dispatchFirestoreRepo.getDispatchBlobDataByBlobId(any(), any(), any()) }
    }

    @After
    fun clear() {
        unmockkAll()
    }
}