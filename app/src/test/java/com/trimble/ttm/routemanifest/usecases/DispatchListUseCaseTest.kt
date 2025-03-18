package com.trimble.ttm.routemanifest.usecases

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.trimble.launchercommunicationlib.commons.EMPTY_STRING
import com.trimble.ttm.commons.repo.FeatureFlagCacheRepo
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_FIRST_TIME_OPEN
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.isValid
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_CONFIRMED
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_TIMEOUT
import com.trimble.ttm.routemanifest.utils.REQUIRED_GUF_CONFIRMED
import com.trimble.ttm.routemanifest.utils.Utils
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DispatchListUseCaseTest {

    private val dateAndTime = "2020-06-01T10:05:00.000Z"

    private lateinit var SUT: DispatchListUseCase

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var dispatchFirestoreRepo : DispatchFirestoreRepo

    @RelaxedMockK
    private lateinit var featureFlagCacheRepo: FeatureFlagCacheRepo

    @RelaxedMockK
    private lateinit var dataStoreManager: DataStoreManager
    @MockK
    private lateinit var tripPanelUseCase: TripPanelUseCase
    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo
    @RelaxedMockK
    private lateinit var formDataStoreManager: FormDataStoreManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this,relaxUnitFun = true)
        dataStoreManager = spyk(DataStoreManager(mockk(relaxed = true)))
        formDataStoreManager = spyk(FormDataStoreManager(mockk(relaxed = true)))
        SUT = spyk( DispatchListUseCase(
            dispatchFirestoreRepo,
            featureFlagCacheRepo,
            localDataSourceRepo,
            tripPanelUseCase,
            formDataStoreManager
            ), recordPrivateCalls = true
        )
    }

    @Test
    fun `verify sortDispatchListByTripStartTime when dispatch list has only past trip start time`() {    //NOSONAR
        val dispatchList = mutableListOf<Dispatch>()
        val dispatch1 = Dispatch(
            tripStartTimed = 0,
            tripStarttime = "2020-06-01T09:00:00.000Z"
        )
        val dispatch2 = Dispatch(
            tripStartTimed = 1,
            tripStarttime = "2020-06-02T10:00:00.000Z"
        )
        val dispatch3 = Dispatch(
            tripStartTimed = 0,
            tripStarttime = "2020-06-03T11:00:00.000Z"
        )
        dispatchList.add(dispatch1)
        dispatchList.add(dispatch2)
        dispatchList.add(dispatch3)

        assertEquals(
            dispatch1.tripStarttime,
            SUT.sortDispatchListByTripStartTime(dispatchList)[0].tripStarttime
        )
    }

    @Test
    fun `verify sortDispatchListByTripStartTime when dispatch has trip start and created time in past are same`() {    //NOSONAR
        val dispatchList = mutableListOf<Dispatch>()
        val dispatch1 = Dispatch(
            tripStartTimed = 1,
            created = "2020-06-01T10:00:00.000Z",
            tripStarttime = "2020-06-01T10:00:00.000Z"
        )
        val dispatch2 = Dispatch(
            tripStartTimed = 0,
            created = dateAndTime,
            tripStarttime = dateAndTime
        )
        val dispatch3 = Dispatch(
            tripStartTimed = 1,
            created = "2020-06-01T10:10:00.000Z",
            tripStarttime = "2020-06-01T10:10:00.000Z"
        )
        dispatchList.add(dispatch1)
        dispatchList.add(dispatch2)
        dispatchList.add(dispatch3)

        assertEquals(
            dispatch1.tripStarttime,
            SUT.sortDispatchListByTripStartTime(dispatchList)[0].tripStarttime
        )
    }

    @Test
    fun `verify sortDispatchListByTripStartTime when all dispatches have same trip start time`() {    //NOSONAR
        val dispatchList = mutableListOf<Dispatch>()
        val dispatch1 = Dispatch(
            dispid = "7550",
            tripStartTimed = 1,
            tripStarttime = dateAndTime
        )
        val dispatch2 = Dispatch(
            dispid = "7551",
            tripStartTimed = 0,
            tripStarttime = dateAndTime
        )
        val dispatch3 = Dispatch(
            dispid = "7552",
            tripStartTimed = 1,
            tripStarttime = dateAndTime
        )
        dispatchList.add(dispatch1)
        dispatchList.add(dispatch2)
        dispatchList.add(dispatch3)

        assertEquals(
            dispatch1.tripStarttime,
            SUT.sortDispatchListByTripStartTime(dispatchList)[0].tripStarttime
        )
    }

    @Test
    fun `verify sortDispatchListByTripStartTime when dispatch list has past and future trip start time`() {    //NOSONAR
        val dispatchList = mutableListOf<Dispatch>()
        val dispatch1 = Dispatch(
            tripStartTimed = 0,
            tripStarttime = "2020-06-23T09:00:00.000Z"
        )
        val dispatch2 = Dispatch(
            tripStartTimed = 0,
            tripStarttime = "2020-06-24T10:00:00.000Z"
        )
        val dispatch3 = Dispatch(
            tripStartTimed = 0,
            tripStarttime = "2020-06-25T11:00:00.000Z"
        )
        val dispatch4 = Dispatch(
            tripStartTimed = 1,
            tripStarttime = "2020-06-30T10:05:00.000Z"
        )
        val dispatch5 = Dispatch(
            tripStartTimed = 0,
            tripStarttime = "2020-07-01T10:06:00.000Z"
        )
        val dispatch6 = Dispatch(
            tripStartTimed = 0,
            tripStarttime = "2020-07-02T10:10:00.000Z"
        )
        dispatchList.add(dispatch1)
        dispatchList.add(dispatch2)
        dispatchList.add(dispatch3)
        dispatchList.add(dispatch4)
        dispatchList.add(dispatch5)
        dispatchList.add(dispatch6)

        assertEquals(
            dispatch1.tripStarttime,
            SUT.sortDispatchListByTripStartTime(dispatchList)[0].tripStarttime
        )
    }

    @Test
    fun `verify getDispatchEligibleToStart when dispatch list has only past trip start time`() {    //NOSONAR
        val dispatchList = mutableListOf<Dispatch>()
        val dispatch1 = Dispatch(
            dispid = "7550",
            tripStartTimed = 0,
            tripStarttime = "2020-06-23T09:00:00.000Z"
        )
        val dispatch2 = Dispatch(
            dispid = "7551",
            tripStartTimed = 0,
            tripStarttime = "2020-06-24T10:00:00.000Z"
        )
        val dispatch3 = Dispatch(
            dispid = "7552",
            tripStartTimed = 0,
            tripStarttime = "2020-06-25T11:00:00.000Z"
        )
        dispatchList.add(dispatch1)
        dispatchList.add(dispatch2)
        dispatchList.add(dispatch3)

        val sortedDispatchList = SUT.sortDispatchListByTripStartTime(dispatchList)
        val dispatchEligibleToStart =
            SUT.getDispatchToBeStarted(sortedDispatchList, TripStartCaller.DISPATCH_DETAIL_SCREEN)

        assertEquals(
            dispatch1.dispid,
            dispatchEligibleToStart.dispid
        )
    }

    @Test
    fun `verify getDispatchEligibleToStart when dispatch list has same past trip start, created time`() {    //NOSONAR
        val dispatchList = mutableListOf<Dispatch>()
        val dispatch1 = Dispatch(
            dispid = "7550",
            tripStartTimed = 1,
            created = "2020-06-29T10:05:00.000Z",
            tripStarttime = "2020-06-29T10:05:00.000Z"
        )
        val dispatch2 = Dispatch(
            dispid = "7551",
            tripStartTimed = 1,
            created = "2020-06-29T10:06:00.000Z",
            tripStarttime = "2020-06-29T10:06:00.000Z"
        )
        val dispatch3 = Dispatch(
            dispid = "7552",
            tripStartTimed = 1,
            created = "2020-06-29T10:10:00.000Z",
            tripStarttime = "2020-06-29T10:10:00.000Z"
        )
        dispatchList.add(dispatch1)
        dispatchList.add(dispatch2)
        dispatchList.add(dispatch3)

        val sortedDispatchList = SUT.sortDispatchListByTripStartTime(dispatchList)
        val dispatchEligibleToStart =
            SUT.getDispatchToBeStarted(sortedDispatchList, TripStartCaller.DISPATCH_DETAIL_SCREEN)

        assertEquals(
            dispatch1.dispid,
            dispatchEligibleToStart.dispid
        )
    }

    @Test
    fun `verify getDispatchEligibleToStart when dispatch list has only future trip start time`() {    //NOSONAR
        val dispatchList = mutableListOf<Dispatch>()
        val dispatch1 = Dispatch(
            dispid = "7550",
            tripStartTimed = 1,
            tripStarttime = "2020-06-05T10:05:00.000Z"
        )
        val dispatch2 = Dispatch(
            dispid = "7551",
            tripStartTimed = 0,
            tripStarttime = "2020-06-06T10:06:00.000Z"
        )
        val dispatch3 = Dispatch(
            dispid = "7552",
            tripStartTimed = 0,
            tripStarttime = "2020-06-07T10:07:00.000Z"
        )
        dispatchList.add(dispatch1)
        dispatchList.add(dispatch2)
        dispatchList.add(dispatch3)

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.apply {
            set(Calendar.YEAR, 2020)
            set(Calendar.MONTH, Calendar.JUNE)
            set(Calendar.DAY_OF_MONTH, 4)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        mockkObject(Utils)
        every { Utils.calendarInUTC() } returns cal

        val sortedDispatchList = SUT.sortDispatchListByTripStartTime(dispatchList)
        val dispatchEligibleToStart =
            SUT.getDispatchToBeStarted(sortedDispatchList, TripStartCaller.DISPATCH_DETAIL_SCREEN)

        verify(exactly = 1) { Utils.calendarInUTC() }

        assertEquals(
            false,
            dispatchEligibleToStart.isValid()
        )
    }

    @Test
    fun `verify getDispatchEligibleToStart when dispatch list has past and future trip start time`() {    //NOSONAR
        val dispatchList = mutableListOf<Dispatch>()
        val dispatch1 = Dispatch(
            dispid = "7550",
            tripStartTimed = 0,
            tripStarttime = "2020-06-01T09:00:00.000Z"
        )
        val dispatch2 = Dispatch(
            dispid = "7551",
            tripStartTimed = 0,
            tripStarttime = "2020-06-02T10:00:00.000Z"
        )
        val dispatch3 = Dispatch(
            dispid = "7552",
            tripStartTimed = 0,
            tripStarttime = "2020-06-03T11:00:00.000Z"
        )
        val dispatch4 = Dispatch(
            dispid = "7553",
            tripStartTimed = 1,
            tripStarttime = "2020-06-05T10:05:00.000Z"
        )
        val dispatch5 = Dispatch(
            dispid = "7554",
            tripStartTimed = 0,
            tripStarttime = "2020-07-06T10:06:00.000Z"
        )
        val dispatch6 = Dispatch(
            dispid = "7555",
            tripStartTimed = 0,
            tripStarttime = "2020-07-07T10:10:00.000Z"
        )
        dispatchList.add(dispatch1)
        dispatchList.add(dispatch2)
        dispatchList.add(dispatch3)
        dispatchList.add(dispatch4)
        dispatchList.add(dispatch5)
        dispatchList.add(dispatch6)

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.apply {
            set(Calendar.YEAR, 2020)
            set(Calendar.MONTH, Calendar.JUNE)
            set(Calendar.DAY_OF_MONTH, 4)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        mockkObject(Utils)
        every { Utils.calendarInUTC() } returns cal

        val sortedDispatchList = SUT.sortDispatchListByTripStartTime(dispatchList)
        val dispatchEligibleToStart =
            SUT.getDispatchToBeStarted(sortedDispatchList, TripStartCaller.DISPATCH_DETAIL_SCREEN)

        verify(exactly = 1) { Utils.calendarInUTC() }

        assertEquals(
            true,
            dispatchEligibleToStart.isValid()
        )
        assertEquals(
            dispatch1.dispid,
            dispatchEligibleToStart.dispid
        )
    }

    @Test
    fun `isTripCreatedTimeNotDuplicated should return true if tripStartTime and TripCreatedTime matches ignoring seconds`() {    //NOSONAR
        val tripStartTime = "2001-09-18T06:02:13.000Z"
        val tripCreatedTime = "2001-09-18T06:02:13.141Z"
        assertEquals(
            false,
            SUT.isTripCreatedTimeNotDuplicated(tripCreatedTime, tripStartTime)
        )
    }

    @Test
    fun `isTripCreatedTimeNotDuplicated should return false if tripStartTime and TripCreatedTime doesnt match`() {    //NOSONAR
        val tripStartTime = "2011-09-18T06:04:13.000Z"
        val tripCreatedTime = "2001-09-18T06:02:13.141Z"
        assertEquals(
            true,
            SUT.isTripCreatedTimeNotDuplicated(tripCreatedTime, tripStartTime)
        )
    }

    @Test
    fun testDispatchAdditionForNewDispatch() {
        val dispatch1 = Dispatch(dispid = "0")
        val dispatch2 = Dispatch(dispid = "1")
        val oldDispatches = mutableListOf<Dispatch>()
        oldDispatches.add(dispatch1)
        with(mutableListOf<Dispatch>()) {
            addAll(oldDispatches)
            add(dispatch2)
            assertEquals(this, SUT.handleDispatchAdditionAndRemoval(mutableListOf(dispatch2), oldDispatches))
        }
    }

    @Test
    fun testDispatchUpdate() {
        val dispatch1 = Dispatch(dispid = "0", isCompleted = false, stopsCountOfDispatch = 10)
        val dispatch2 = Dispatch(dispid = "1", isCompleted = false, stopsCountOfDispatch = 1)
        val updatedDispatch1 = Dispatch(dispid = "0", isCompleted = false, stopsCountOfDispatch = 15)
        val oldDispatches = mutableListOf<Dispatch>()
        oldDispatches.add(dispatch1)
        oldDispatches.add(dispatch2)
        with(mutableListOf<Dispatch>()) {
            add(dispatch2)
            add(updatedDispatch1)
            assertEquals(this, SUT.handleDispatchAdditionAndRemoval(mutableListOf( updatedDispatch1), oldDispatches))
        }
    }

    @Test
    fun testDispatchRemovalForExistingCompletedDispatch() {
        val dispatch1 = Dispatch(dispid = "0")
        val dispatch2 = Dispatch(dispid = "1")
        val oldDispatches = mutableListOf<Dispatch>()
        oldDispatches.add(dispatch1)
        oldDispatches.add(dispatch2)
        assertEquals(2,SUT.handleDispatchAdditionAndRemoval(listOf(Dispatch(dispid = "0")), oldDispatches).size )
    }

    @Test
    fun `verify that the trip panel message is removed if there is no active trip`() = runTest {
        coEvery { localDataSourceRepo.isKeyAvailableInAppModuleDataStore(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns false
        every { tripPanelUseCase.dismissTripPanelOnLaunch() } just runs
        assertTrue(SUT.dismissTripPanelMessageIfThereIsNoActiveTrip())
    }

    @Test
    fun `verify that the trip panel message is not removed if there is an active trip`() = runTest {
        coEvery { localDataSourceRepo.isKeyAvailableInAppModuleDataStore(DataStoreManager.ACTIVE_DISPATCH_KEY) } returns true
        every { tripPanelUseCase.dismissTripPanelOnLaunch() } just runs
        assertFalse(SUT.dismissTripPanelMessageIfThereIsNoActiveTrip())
    }

    @Test
    fun `verify CanShowDispatchStartPopup With different dispatch id`() {  //NOSONAR
        val existingDispatch = Dispatch(dispid = "75589", name = "Trip 1")
        val newDispatch = Dispatch(dispid = "75590", name = "Trip 2")
        assertEquals(
            true,
            SUT.canShowDispatchStartPopup(existingDispatch, newDispatch)
        )
    }

    @Test
    fun `verify CanShowDispatchStartPopup With same dispatch id and different name`() { //NOSONAR
        val existingDispatch = Dispatch(dispid = "75590", name = "Trip 1")
        val newDispatch = Dispatch(dispid = "75590", name = "Trip 2")
        assertEquals(
            true,
            SUT.canShowDispatchStartPopup(existingDispatch, newDispatch)
        )
    }

    @Test
    fun `verify CanShowDispatchStartPopup With same dispatch id and same name`() { //NOSONAR
        val existingDispatch = Dispatch(dispid = "75590", name = "Trip 2")
        val newDispatch = Dispatch(dispid = "75590", name = "Trip 2")
        assertEquals(
            false,
            SUT.canShowDispatchStartPopup(existingDispatch, newDispatch)
        )
    }

    @Test
    fun `verify new dispatch add and removing old dispatches from existing list`()=runTest {
        val cid = "100"
        val truck = "cgvus"
        val dispatchId="1000"
        val incomingDispatch= mutableListOf( Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = dispatchId ))
        val existingDispatches= mutableListOf(Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = "1001" ),Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = "1002" ))
        SUT.removeDispatchIfNotAvailableInNewDispatchList(incomingDispatch,existingDispatches)

        assertEquals(1,existingDispatches.size)
    }

    @Test
    fun `verify new dispatch is not added when it is available in existing list`()=runTest {
        val cid = "100"
        val truck = "cgvus"
        val dispatchId="1000"
        val newDispatchId="1000"

        //When
        val incomingDispatch= mutableListOf( Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = dispatchId ))
        val existingDispatches= mutableListOf(Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = newDispatchId ))

        //Then
        SUT.removeDispatchIfNotAvailableInNewDispatchList(incomingDispatch,existingDispatches)

        //Verify
        assertEquals(1,existingDispatches.size)
    }

    @Test
    fun `verify new dispatch stop count is 0 when firestore fetch returns null`()=runTest {
        val cid = "100"
        val truck = "cgvus"
        val dispatchId="1000"

        //When
        coEvery { SUT.getStopCountOfDispatch(cid, truck, dispatchId) } returns null
        coEvery {
            dispatchFirestoreRepo.getStopCountOfDispatch(
                cid,
                truck,
                dispatchId
            )
        } returns null

        //Then
        val updatedStopCountDispatchList=SUT.checkAndUpdateStopCount(mutableListOf(Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = dispatchId)))

        //Verify
        assertEquals(0,updatedStopCountDispatchList.first().stopsCountOfDispatch)
    }

    @Test
    fun `verify new dispatch stop count is as expected with firestore returned count`()=runTest {
        val cid = "100"
        val truck = "cgvus"
        val dispatchId="1000"

        //When
        coEvery { SUT.getStopCountOfDispatch(cid, truck, dispatchId) } returns 1
        coEvery {
            dispatchFirestoreRepo.getStopCountOfDispatch(
                cid,
                truck,
                dispatchId
            )
        } returns 1

        //Then
        val updatedStopCountDispatchList=SUT.checkAndUpdateStopCount(mutableListOf(Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = dispatchId)))

        //Verify
        assertEquals(1,updatedStopCountDispatchList.first().stopsCountOfDispatch)
    }

    @Test
    fun `verify new dispatch stop count is 0 when truck number is empty`()=runTest {
        val cid = "100"
        val truck = ""
        val dispatchId="1000"

        //Then
        val updatedStopCountDispatchList=SUT.checkAndUpdateStopCount(mutableListOf(Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = dispatchId)))

        //Verify
        assertEquals(0,updatedStopCountDispatchList.first().stopsCountOfDispatch)
    }

    @Test
    fun `verify can ShowDispatch Start Popup true when dispatch id not matches`()=runTest {
        val cid = "100"
        val truck = ""
        val existingDispatchId="1000"
        val newDispatchId="1001"
        val existingDispatch=Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = existingDispatchId, name = "Trip1")
        val newDispatch=Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = newDispatchId, name = "Trip1")

        //Then
        val canShowStartTripDialog=SUT.canShowDispatchStartPopup(existingDispatch,newDispatch)

        //Verify
        assertEquals(true, canShowStartTripDialog)
    }

    @Test
    fun `verify can ShowDispatch Start Popup true when dispatch id  matches`()=runTest {
        val cid = "100"
        val truck = ""
        val existingDispatchId="1000"
        val newDispatchId="1000"
        val existingDispatch=Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = existingDispatchId, name = "Trip1")
        val newDispatch=Dispatch(cid =cid.toInt(), vehicleNumber = truck, dispid = newDispatchId, name = "Trip1")

        //Then
        val canShowStartTripDialog=SUT.canShowDispatchStartPopup(existingDispatch,newDispatch)

        //Verify
        assertEquals(false, canShowStartTripDialog)
    }

    @Test
    fun `updateActiveDispatchId updates active dispatch id when current workflow id is empty`() = runTest {
        val dispatchId = "1234"
        val dispatchName = "Trip1"
        val dispatch = Dispatch(dispid = dispatchId, name = dispatchName)
        dispatch.isActive = true
        val dispatchList = mutableListOf(dispatch)
        coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns ""
        coEvery { dispatchFirestoreRepo.setCurrentWorkFlowId(dispatchId) } just runs
        coEvery { dispatchFirestoreRepo.setCurrentWorkFlowDispatchName(dispatchName) } just runs
        coEvery { formDataStoreManager.getValue(IS_FIRST_TIME_OPEN,false) } returns true
        coEvery { formDataStoreManager.setValue(IS_FIRST_TIME_OPEN,false) } just runs

        SUT.updateActiveDispatchDatastoreKeys(dispatchList)
        coVerify { dispatchFirestoreRepo.setCurrentWorkFlowId(any()) }
        coVerify { dispatchFirestoreRepo.setCurrentWorkFlowDispatchName(any()) }
    }

    @Test
    fun `updateActiveDispatchId does not update active dispatch id when current workflow id is not empty`() = runTest {
        val dispatchId = "1234"
        val dispatch = Dispatch(dispid = dispatchId)
        dispatch.isActive = true
        val dispatchList = mutableListOf(dispatch)
        coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns "1234"

        SUT.updateActiveDispatchDatastoreKeys(dispatchList)
        coVerify(exactly = 0) { dispatchFirestoreRepo.setCurrentWorkFlowId(any()) }
        coVerify(exactly = 0) { dispatchFirestoreRepo.setCurrentWorkFlowDispatchName(any()) }
    }

    @Test
    fun `updateActiveDispatchId does not update active dispatch id when no active dispatch in list`() = runTest {
        val dispatchId = "1234"
        val dispatch = Dispatch(dispid = dispatchId)
        dispatch.isActive = false
        val dispatchList = mutableListOf(dispatch)
        coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns ""
        coEvery { formDataStoreManager.getValue(any(),false) } returns false


        SUT.updateActiveDispatchDatastoreKeys(dispatchList)
        coVerify(exactly = 0) { dispatchFirestoreRepo.setCurrentWorkFlowId(any()) }
        coVerify(exactly = 0) { dispatchFirestoreRepo.setCurrentWorkFlowDispatchName(any()) }
    }

    @Test
    fun `verify if isActive flag is updated during update scenario - Backward Compatibility`() = runTest {
        val dispatchId = "1234"
        val dispatch = Dispatch(dispid = dispatchId)
        dispatch.isActive = false
        val dispatchList = mutableListOf(dispatch)
        coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns "1234"

        SUT.updateActiveDispatchDatastoreKeys(dispatchList)
        coVerify(exactly = 0) { dispatchFirestoreRepo.setCurrentWorkFlowId(any()) }
        coVerify(exactly = 0) { dispatchFirestoreRepo.setCurrentWorkFlowDispatchName(any()) }
        assertEquals(true, dispatch.isActive)
    }

    @Test
    fun `verify firestore feature flag fetch called if key not available` ()= runTest {
        //When
        coEvery { featureFlagCacheRepo.listenAndUpdateFeatureFlagCacheMap(captureLambda()) } returns emptyMap()
        coEvery { dispatchFirestoreRepo.getAppModuleCommunicator().getFeatureFlags() } returns mapOf()

        //Then
        SUT.getRemoveOneMinuteDelayFeatureFlag()

        //Verify
        coVerify { featureFlagCacheRepo.listenAndUpdateFeatureFlagCacheMap(captureLambda()) }
    }

    @Test
    fun `verify firestore feature flag fetch not called if key is available` ()= runTest {
        //When
        val key1 = FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE
        val value1 = FeatureFlagDocument(FeatureGatekeeper.KnownFeatureFlags.ONE_MINUTE_DELAY_REMOVE.id, listOf(), true, true, "")

        coEvery { dispatchFirestoreRepo.getAppModuleCommunicator().getFeatureFlags() } returns mapOf(
           key1 to value1)

        //Then
        SUT.getRemoveOneMinuteDelayFeatureFlag()

        //Verify
        coVerify(exactly = 0) { featureFlagCacheRepo.listenAndUpdateFeatureFlagCacheMap { any() } }
    }

    @Test
    fun `addNotifiedDispatchToTheDispatchList adds dispatch to list with updated stop count`() =
        runTest {
            val dispatches= mutableListOf<Dispatch>().apply {
                add(Dispatch(dispid = "1234"))
                add(Dispatch(dispid = "1235"))
            }
            val mockStopCount = 5
            coEvery { dispatchFirestoreRepo.getStopCountOfDispatch(any(),any(),any()) } returns mockStopCount

            // Act
           val updatedDispatches= SUT.addNotifiedDispatchToTheDispatchList(dispatches,"100", "cgvus", "1234")

            // Assert
            assertEquals(
                mockStopCount,
                updatedDispatches.find { it.dispid == "1234" }?.stopsCountOfDispatch
            )
        }

    @Test
    fun `getTripStartEventReasons returns NEGATIVE_GUF_CONFIRMED when tripStartNegGuf is 1`() {
        val dispatch = Dispatch(tripStartNegGuf = 1)


        val result = SUT.getTripStartEventReasons(dispatch)

        assertEquals(NEGATIVE_GUF_CONFIRMED, result)
    }

    @Test
    fun `getTripStartEventReasons returns REQUIRED_GUF_CONFIRMED when tripStartNegGuf is not 1`() {
        val dispatch = Dispatch(tripStartNegGuf = 0)

        val result = SUT.getTripStartEventReasons(dispatch)

        assertEquals(REQUIRED_GUF_CONFIRMED, result)
    }

    @Test
    fun `verify getTripStartEventReasonsFromWorker() returns NEGATIVE_GUF_TIMEOUT when tripStartNegGuf is 1`() {
        val dispatch = Dispatch(tripStartNegGuf = 1)

        val result = SUT.getTripStartEventReasonsFromWorker(dispatch)

        assertEquals(NEGATIVE_GUF_TIMEOUT, result)
    }

    @Test
    fun `verify getTripStartEventReasonsFromWorker() returns EMPTY_STRING when tripStartNegGuf is not 1`() {
        val dispatch = Dispatch(tripStartNegGuf = 0)

        val result = SUT.getTripStartEventReasonsFromWorker(dispatch)

        assertEquals(EMPTY_STRING, result)
    }

    @Test
    fun `verify activeDispatchKey for active trip is set using setDispatchAsActive() when getDispatchesForTheTruckAndScheduleAutoStartTrip() is called`() =
        runTest {
            val dispatchSet = mutableSetOf<Dispatch>()
            val dispatch1 = Dispatch(dispid = "123456", name = "dispatch1")
            dispatch1.isActive = true
            val dispatch2 = Dispatch(name = "dispatch2")
            dispatch2.isActive = false
            val dispatch3 = Dispatch(name = "dispatch3")
            dispatch3.isActive = false
            dispatchSet.add(dispatch1)
            dispatchSet.add(dispatch2)
            dispatchSet.add(dispatch3)
            val nonActiveDispatches = dispatchSet.filter { it.isActive.not() }.toMutableSet()
            coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns ""
            coEvery {
                dispatchFirestoreRepo.getDispatchesList(
                    any(),
                    any(),
                    any()
                )
            } returns dispatchSet
            coEvery { formDataStoreManager.getValue(IS_FIRST_TIME_OPEN, any()) } returns true
            coEvery { SUT["setDispatchAsActive"]("123456", "dispatch1") } returns Unit

            SUT.getDispatchesForTheTruckAndScheduleAutoStartTrip("testCid", "testVid", "testCaller")

            coVerify(exactly = 1) { SUT["setDispatchAsActive"]("123456", "dispatch1") }
            coVerify(exactly = 1) {
                SUT["filterAndSortDispatchesToGetAutoStart"](
                    nonActiveDispatches,
                    "testCaller"
                )
            }
        }

    @Test
    fun `verify activeDispatchKey is not set using setDispatchAsActive(), if there is no active trip when getDispatchesForTheTruckAndScheduleAutoStartTrip() is called`() =
        runTest {
            val dispatchSet = mutableSetOf<Dispatch>()
            val dispatch1 = Dispatch(dispid = "123456", name = "dispatch1")
            dispatch1.isActive = false
            val dispatch2 = Dispatch(name = "dispatch2")
            dispatch2.isActive = false
            val dispatch3 = Dispatch(name = "dispatch3")
            dispatch3.isActive = false
            dispatchSet.add(dispatch1)
            dispatchSet.add(dispatch2)
            dispatchSet.add(dispatch3)
            val nonActiveDispatches = dispatchSet.filter { it.isActive.not() }.toMutableSet()
            coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns ""
            coEvery {
                dispatchFirestoreRepo.getDispatchesList(
                    any(),
                    any(),
                    any()
                )
            } returns dispatchSet
            coEvery { formDataStoreManager.getValue(IS_FIRST_TIME_OPEN, any()) } returns true
            coEvery { SUT["setDispatchAsActive"]("123456", "dispatch1") } returns Unit

            SUT.getDispatchesForTheTruckAndScheduleAutoStartTrip("testCid", "testVid", "testCaller")

            coVerify(exactly = 0) { SUT["setDispatchAsActive"]("123456", "dispatch1") }
            coVerify(exactly = 1) {
                SUT["filterAndSortDispatchesToGetAutoStart"](
                    nonActiveDispatches,
                    "testCaller"
                )
            }
        }

    @Test
    fun `verify activeDispatchKey is not set using setDispatchAsActive(), if there is a active trip, but FIRST_TIME_OPEN is false when getDispatchesForTheTruckAndScheduleAutoStartTrip() is called`() =
        runTest {
            val dispatchSet = mutableSetOf<Dispatch>()
            val dispatch1 = Dispatch(dispid = "123456", name = "dispatch1")
            dispatch1.isActive = true
            val dispatch2 = Dispatch(name = "dispatch2")
            dispatch2.isActive = false
            val dispatch3 = Dispatch(name = "dispatch3")
            dispatch3.isActive = false
            dispatchSet.add(dispatch1)
            dispatchSet.add(dispatch2)
            dispatchSet.add(dispatch3)
            val nonActiveDispatches = dispatchSet.filter { it.isActive.not() }.toMutableSet()
            coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns ""
            coEvery {
                dispatchFirestoreRepo.getDispatchesList(
                    any(),
                    any(),
                    any()
                )
            } returns dispatchSet
            coEvery { formDataStoreManager.getValue(IS_FIRST_TIME_OPEN, any()) } returns false
            coEvery { SUT["setDispatchAsActive"]("123456", "dispatch1") } returns Unit

            SUT.getDispatchesForTheTruckAndScheduleAutoStartTrip("testCid", "testVid", "testCaller")

            coVerify(exactly = 0) { SUT["setDispatchAsActive"]("123456", "dispatch1") }
            coVerify(exactly = 1) {
                SUT["filterAndSortDispatchesToGetAutoStart"](
                    nonActiveDispatches,
                    "testCaller"
                )
            }
        }

    @Test
    fun `verify activeDispatchKey is not set using setDispatchAsActive(), if there is a active trip, but getCurrentWorkFlowId() returns 'nonEmptyString' is false when getDispatchesForTheTruckAndScheduleAutoStartTrip() is called`() =
        runTest {
                val dispatchSet = mutableSetOf<Dispatch>()
                val dispatch1 = Dispatch(dispid = "123456", name = "dispatch1")
                dispatch1.isActive = true
                val dispatch2 = Dispatch(name = "dispatch2")
                dispatch2.isActive = false
                val dispatch3 = Dispatch(name = "dispatch3")
                dispatch3.isActive = false
                dispatchSet.add(dispatch1)
                dispatchSet.add(dispatch2)
                dispatchSet.add(dispatch3)
                val nonActiveDispatches = dispatchSet.filter { it.isActive.not() }.toMutableSet()
                coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns "nonEmptyString"
                coEvery {
                    dispatchFirestoreRepo.getDispatchesList(
                        any(),
                        any(),
                        any()
                    )
                } returns dispatchSet
                coEvery { formDataStoreManager.getValue(IS_FIRST_TIME_OPEN, any()) } returns true
                coEvery { SUT["setDispatchAsActive"]("123456", "dispatch1") } returns Unit

                SUT.getDispatchesForTheTruckAndScheduleAutoStartTrip("testCid", "testVid", "testCaller")

                coVerify(exactly = 0) { SUT["setDispatchAsActive"]("123456", "dispatch1") }
                coVerify(exactly = 1) {
                    SUT["filterAndSortDispatchesToGetAutoStart"](
                        nonActiveDispatches,
                        "testCaller"
                    )
                }
        }

    @Test
    fun `verify nothing is called when getDispatchesList() is empty `() = runTest {
        val nonActiveDispatches = mutableSetOf<Dispatch>()
        val dispatch1 = Dispatch(dispid = "123456", name = "dispatch1")
        nonActiveDispatches.add(dispatch1)
        coEvery { dispatchFirestoreRepo.getCurrentWorkFlowId(any()) } returns "nonEmptyString"
        coEvery { dispatchFirestoreRepo.getDispatchesList(any(), any(), any()) } returns emptySet()
        coEvery { formDataStoreManager.getValue(IS_FIRST_TIME_OPEN, any()) } returns true
        coEvery { SUT["setDispatchAsActive"]("123456", "dispatch1") } returns Unit

        SUT.getDispatchesForTheTruckAndScheduleAutoStartTrip("testCid", "testVid", "testCaller")

        coVerify(exactly = 0) { SUT["setDispatchAsActive"]("123456", "dispatch1") }
        coVerify(exactly = 0) {
            SUT["filterAndSortDispatchesToGetAutoStart"](
                nonActiveDispatches,
                "testCaller"
            )
        }
    }

    @After
    fun after() {
        unmockkAll()
        Dispatchers.resetMain()
    }
}