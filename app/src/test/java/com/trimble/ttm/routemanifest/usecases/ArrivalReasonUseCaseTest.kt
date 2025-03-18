package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.CIRCULAR
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.commons.model.SiteCoordinate
import com.trimble.ttm.commons.repo.*
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.ArrivalReasonEventRepo
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.utils.*
import com.trimble.ttm.routemanifest.utils.Utils.containsLocation
import com.trimble.ttm.routemanifest.utils.Utils.getDistanceBetweenLatLongs
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.*
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import kotlin.test.assertEquals

class ArrivalReasonUseCaseTest {

    @MockK
    private lateinit var arrivalReasonUseCase: ArrivalReasonUsecase
    @MockK
    private lateinit var application: WorkflowApplication
    @MockK
    private lateinit var context: Context
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase
    @MockK
    private lateinit var arrivalReasonEventRepo: ArrivalReasonEventRepo
    @MockK
    private lateinit var managedConfigurationRepo: ManagedConfigurationRepo

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val testDispatcher = TestCoroutineScheduler()

    private lateinit var localDataSourceRepo: LocalDataSourceRepo

    private var modulesRequiredForTest = module {
        single<DispatcherProvider> { DefaultDispatcherProvider() }
        single { appModuleCommunicator }
        single { arrivalReasonEventRepo }
        single<LocalDataSourceRepo> { LocalDataSourceRepoImpl(get(), get(), appModuleCommunicator) }
    }

    val stopId = 1
    val cid = "123"
    val truckNum = "456"
    val dispatchId = "789"
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
    val stopDetail = StopDetail(stopid = 1, siteCoordinates = mutableListOf(SiteCoordinate(11.11,12.11))).apply {
        this.Actions.addAll(actions)
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkObject(Utils)
        mockkObject(DateUtil)
        startKoin {
            androidContext(application)
            loadKoinModules(modulesRequiredForTest)
        }
        localDataSourceRepo = mockk()
        arrivalReasonUseCase = ArrivalReasonUsecase(backboneUseCase, localDataSourceRepo, arrivalReasonEventRepo, appModuleCommunicator, dispatchFirestoreRepo, managedConfigurationRepo)
    }

    @Test
    fun `verify GetArrivalReasonHashMap when its NOT arrival` () = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns truckNum
        coEvery { localDataSourceRepo.getActiveDispatchId("getArrivalReasonMap") } returns dispatchId
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0,0.0)
        coEvery { backboneUseCase.getCurrentUser() } returns "DRIVERID"
        coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetail
        coEvery { dispatchFirestoreRepo.getActionsOfStop(any(),any(),any()) } returns actions
        coEvery { managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration(any()) } returns true
        coEvery { Utils.getGeofenceType(stopDetail.siteCoordinates ?: mutableListOf(), isPolygonalOptOut = true) } returns CIRCULAR
        every { DateUtil.getUTCFormattedDate(any())} returns "2025-01-07T10:09:14.979Z"
        coEvery { containsLocation(any(), any(), any(), any(), any(), any()) } returns true
        coEvery { getDistanceBetweenLatLongs(any(),any()) } returns 0.0

        val result = arrivalReasonUseCase.getArrivalReasonMap("DRIVER_CLICKED_NO", stopId, false)
        val expected = HashMap<String,Any>().also {
            it[GEOFENCE_TYPE]=CIRCULAR
            it[ETA] = "null"
            it[DRIVERID] = "DRIVERID"
            it[DISTANCE_TO_ARRIVAL_ACTION_STATUS_LOCATION] = 0.0
            it[STOP_LOCATION] = SiteCoordinate(latitude = 0.0, longitude = 0.0)
            it[ARRIVAL_ACTION_STATUS] = "DRIVER_CLICKED_NO"
            it[ARRIVAL_ACTION_STATUS_TIME] = "2025-01-07T10:09:14.979Z"
            it[ARRIVAL_ACTION_STATUS_LOCATION] = SiteCoordinate(latitude = 0.0, longitude = 0.0)
            it[SEQUENCED] = 0
            it[INSIDE_GEOFENCE_AT_ARRIVAL_ACTION_STATUS] = true
        }
        assertEquals(expected, result)

    }

    @Test
    fun `verify GetArrivalReasonHashMap when its arrival` () = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns truckNum
        coEvery { localDataSourceRepo.getActiveDispatchId("getArrivalReasonMap") } returns dispatchId
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0,0.0)
        coEvery { backboneUseCase.getCurrentUser() } returns "DRIVERID"
        coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(),any()) } returns stopDetail
        coEvery { dispatchFirestoreRepo.getActionsOfStop(any(),any(),any()) } returns actions
        coEvery { managedConfigurationRepo.getPolygonalOptOutFromManageConfiguration(any()) } returns true
        coEvery { Utils.getGeofenceType(stopDetail.siteCoordinates ?: mutableListOf(), isPolygonalOptOut = true) } returns CIRCULAR
        every { DateUtil.getUTCFormattedDate(any())} returns "2025-01-07T10:09:14.979Z"
        coEvery { containsLocation(any(), any(), any(), any(), any(), any()) } returns true
        coEvery { getDistanceBetweenLatLongs(any(),any()) } returns 0.0


        val result = arrivalReasonUseCase.getArrivalReasonMap("TIMER_EXPIRED", stopId, true)
        val expected = HashMap<String,Any>().also {
            it[GEOFENCE_TYPE]=CIRCULAR
            it[INSIDE_GEOFENCE_AT_ARRIVAL] = true
            it[ETA] = "null"
            it[ARRIVAL_TYPE] = "TIMER_EXPIRED"
            it[DRIVERID] = "DRIVERID"
            it[ARRIVAL_TIME] = "2025-01-07T10:09:14.979Z"
            it[STOP_LOCATION] = SiteCoordinate(latitude = 0.0, longitude = 0.0)
            it[DISTANCE_TO_ARRIVAL_LOCATION] = 0.0
            it[SEQUENCED] = 0
            it[ARRIVAL_LOCATION] = SiteCoordinate(latitude = 0.0, longitude = 0.0)
        }
        assertEquals(expected, result)

    }

    @Test
    fun `updateArrivalReason updates its arrivalReason`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "gowtham"
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0,0.0)
        coEvery { backboneUseCase.getCurrentUser() } returns EMPTY_STRING
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "1698"
        coEvery { arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any()) } just runs
        coEvery { arrivalReasonEventRepo.getArrivalReasonCollectionPath(123) } returns "path"
        coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(), any()) } returns stop

        arrivalReasonUseCase.updateArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify {
            arrivalReasonEventRepo.getArrivalReasonCollectionPath(123)
            arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any())
        }
    }


    @Test
    fun `updateArrivalReason does not update its arrivalReason when CID is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any()) } just runs

        arrivalReasonUseCase.updateArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify(exactly = 0) { arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any()) }
    }

    @Test
    fun `updateArrivalReason does not update its arrivalReason when its arrival`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any()) } just runs

        arrivalReasonUseCase.updateArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify(exactly = 0) { arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any()) }
    }

    @Test
    fun `updateArrivalReason does not update when truck number is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""
        coEvery { arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any()) } just runs

        arrivalReasonUseCase.updateArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify(exactly = 0) { arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any()) }
    }

    @Test
    fun `updateArrivalReason does not update when workflow ID is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "gowtham"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId("updateArrivalReasonforCurrentStop") } returns ""

        arrivalReasonUseCase.updateArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify(exactly = 0) { arrivalReasonEventRepo.updateArrivalReasonforStop(any(), any()) }
    }

    @Test
    fun `setArrivalReason does not update when CID ID is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns EMPTY_STRING
        coEvery { arrivalReasonEventRepo.setArrivalReasonforStop(any(), any()) } just runs

        arrivalReasonUseCase.setArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify(exactly = 0) { arrivalReasonEventRepo.setArrivalReasonforStop(any(), any()) }
    }

    @Test
    fun `setArrivalReason does not update when Truck ID is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns EMPTY_STRING
        coEvery { arrivalReasonEventRepo.setArrivalReasonforStop(any(), any()) } just runs

        arrivalReasonUseCase.setArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify(exactly = 0) { arrivalReasonEventRepo.setArrivalReasonforStop(any(), any()) }
    }

    @Test
    fun `setArrivalReason does not update when workflow ID is not available`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "gowtham"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId("setArrivalReasonforCurrentStop") } returns EMPTY_STRING
        coEvery { arrivalReasonEventRepo.setArrivalReasonforStop(any(), any()) } just runs

        arrivalReasonUseCase.setArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify(exactly = 0) { arrivalReasonEventRepo.setArrivalReasonforStop(any(), any()) }
    }


    @Test
    fun `setArrivalReason updates its arrivalReason`() = runTest(testDispatcher) {
        val stop = StopDetail(stopid = 123)
        val valueMap = hashMapOf<String, Any>("key" to "value")

        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "gowtham"
        coEvery { backboneUseCase.getCurrentLocation() } returns Pair(0.0,0.0)
        coEvery { backboneUseCase.getCurrentUser() } returns EMPTY_STRING
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "1698"
        coEvery { arrivalReasonEventRepo.setArrivalReasonforStop(any(), any()) } just runs
        coEvery { arrivalReasonEventRepo.getArrivalReasonCollectionPath(123) } returns "path"
        coEvery { dispatchFirestoreRepo.getStop(any(),any(),any(), any()) } returns stop

        arrivalReasonUseCase.setArrivalReasonForCurrentStop(stop.stopid, valueMap)

        coVerify {
            arrivalReasonEventRepo.getArrivalReasonCollectionPath(123)
            arrivalReasonEventRepo.setArrivalReasonforStop(any(), any())
        }
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