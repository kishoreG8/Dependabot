package com.trimble.ttm.routemanifest.usecases

import androidx.datastore.preferences.core.intPreferencesKey
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.ADDED
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import com.trimble.ttm.routemanifest.utils.INVALID_STOP_INDEX
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_CONFIRMED
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_TIMEOUT
import com.trimble.ttm.routemanifest.utils.REMOVED
import com.trimble.ttm.routemanifest.utils.REQUIRED_GUF_CONFIRMED
import com.trimble.ttm.routemanifest.utils.STOP_DETAIL_SCREEN_TIME
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.TIMELINE_VIEW_COUNT
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
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DispatchBaseUseCaseTest {

    @RelaxedMockK
    private lateinit var formDataStoreManager: FormDataStoreManager

    @RelaxedMockK
    private lateinit var dataStoreManager: DataStoreManager

    private lateinit var appModuleCommunicator: AppModuleCommunicator

    private lateinit var dispatchBaseUseCase: DispatchBaseUseCase

    private lateinit var dispatchFirestoreRepo: DispatchFirestoreRepo

    private lateinit var localDataSourceRepo: LocalDataSourceRepo

    private lateinit var managedConfigurationRepo: ManagedConfigurationRepo

    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase

    @RelaxedMockK
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder

    private val completedTime = "10:00:00"

    private val selectedDispatchId="3333"

    @MockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase

    private fun getControlDispatchFormPath(): DispatchFormPath =
        DispatchFormPath("Control", 123, 123, 123, 123)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        appModuleCommunicator= mockk()
        dispatchFirestoreRepo = mockk()
        dispatchStopsUseCase = mockk()
        managedConfigurationRepo = mockk()
        localDataSourceRepo = mockk()
        dispatchBaseUseCase = DispatchBaseUseCase(
            appModuleCommunicator,
            fetchDispatchStopsAndActionsUseCase,
            dispatchFirestoreRepo,
            firebaseAnalyticEventRecorder,
            localDataSourceRepo
        )
        formDataStoreManager = spyk(FormDataStoreManager(mockk(relaxed = true)))
        dataStoreManager = spyk(DataStoreManager(mockk(relaxed = true)))
        mockkObject(FormUtils)
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "Swift"
        mockkObject(UncompletedFormsUseCase)
    }


    @Test
    fun testStopAddition() {
        val stop1 = StopDetail(stopid = 0)
        val stop2 = StopDetail(stopid = 1)
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(stop1)
        with(CopyOnWriteArrayList<StopDetail>()) {
            add(stop1)
            add(stop2)
            assertEquals(this, dispatchBaseUseCase.processStopAdditionOrUpdate(stop2, stopList))
        }
    }

    @Test
    fun testStopUpdate() {
        val stop1 = StopDetail(stopid = 0)
        val stop2 = StopDetail(stopid = 1)
        val updatedStop1 = StopDetail(stopid = 0, sequenced = 1, name = "anonymous stop")
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(stop1)
        stopList.add(stop2)
        with(CopyOnWriteArrayList<StopDetail>()) {
            add(updatedStop1)
            add(stop2)
            assertEquals(
                this,
                dispatchBaseUseCase.processStopAdditionOrUpdate(updatedStop1, stopList)
            )
        }
    }

    @Test
    fun `verify handleStopAdditionOrUpdate - existing stop update - Action data empty check `() = runTest {
        val dispatchId="111"
        val stop1 = StopDetail(stopid = 0)
        val stop2 = StopDetail(stopid = 1, dispid = dispatchId).also {
            with(it.Actions) {
                addAll(mutableListOf(Action(0, actionid = 1, stopid = 1, dispid = dispatchId)))
            }
        }
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(stop1)
        stopList.add(stop2)

        val stopToAddOrUpdate = StopDetail(stopid = 1, dispid = dispatchId)
        dispatchBaseUseCase.processStopAdditionOrUpdate(stopToAddOrUpdate,stopList)
        assertTrue(stopList[1].Actions.isEmpty().not())
        assertTrue(stopList[0].Actions.isEmpty())
    }

    @Test
    fun `verify handleStopAdditionOrUpdate - new stop  - Action data empty check `() = runTest {
        val dispatchId="111"
        val stop1 = StopDetail(stopid = 0)
        val stop2 = StopDetail(stopid = 1, dispid = dispatchId).also {
            with(it.Actions) {
                addAll(mutableListOf(Action(0, actionid = 1, stopid = 1, dispid = dispatchId)))
            }
        }
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(stop1)
        stopList.add(stop2)

        val stopToAddOrUpdate = StopDetail(stopid = 2, dispid = dispatchId)
        dispatchBaseUseCase.processStopAdditionOrUpdate(stopToAddOrUpdate,stopList)
        assertTrue(stopList[2].Actions.isEmpty())
        assertEquals(3,stopList.size)
        assertEquals(2,stopList[2].stopid)
    }

    @Test
    fun testStopRemoval() {
        val stop1 = StopDetail(stopid = 0)
        val stop2 = StopDetail(stopid = 1)
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(stop1)
        stopList.add(stop2)
        with(CopyOnWriteArrayList<StopDetail>()) {
            add(stop2)
            assertEquals(this, dispatchBaseUseCase.processStopRemoval(stop1, stopList))
        }
    }

    @Test
    fun testStopCompletionForCompletedForms() = runTest {
        val stop1 = StopDetail(stopid = 0, completedTime = completedTime)
        val updatedStop1 = StopDetail(stopid = 0, completedTime = completedTime)
        updatedStop1.StopCompleted = true
        coEvery {
            formDataStoreManager.getValue(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stop1.stopid}"),
                ZERO
            )
        } returns ZERO
        assertEquals(
            updatedStop1,
            dispatchBaseUseCase.checkAndMarkStopCompletion(formDataStoreManager, stop1)
        )
    }

    @Test
    fun testStopCompletionForInCompleteForms() = runTest {
        val stop1 = StopDetail(stopid = 0, completedTime = completedTime)
        val updatedStop1 = StopDetail(stopid = 0, completedTime = completedTime)
        updatedStop1.StopCompleted = false
        coEvery {
            formDataStoreManager.getValue(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stop1.stopid}"),
                ZERO
            )
        } returns 1
        assertEquals(
            updatedStop1,
            dispatchBaseUseCase.checkAndMarkStopCompletion(formDataStoreManager, stop1)
        )
    }

    @Test
    fun testFormCompletionAndStopInCompleteness() = runTest {
        val stop1 = StopDetail(stopid = 0)
        val updatedStop1 = StopDetail(stopid = 0)
        updatedStop1.StopCompleted = false
        coEvery {
            formDataStoreManager.getValue(
                intPreferencesKey(name = "$FORM_COUNT_FOR_STOP${stop1.stopid}"),
                ZERO
            )
        } returns ZERO
        assertEquals(
            updatedStop1,
            dispatchBaseUseCase.checkAndMarkStopCompletion(formDataStoreManager, stop1)
        )
    }

    @Test
    fun testVIDFetchForInvalidCID() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns ""
        assertFalse(dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData("103939", dataStoreManager, ""))
        assertFalse(
            dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(
                "103939",
                dataStoreManager,
                ""
            )
        )
    }

    @Test
    fun testVIDFetchForInvalidVehicleID() = runTest {
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""
        assertFalse(dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData("103939",  dataStoreManager, ""))
    }

    @Test
    fun testVIDFetchForInvalidVID() = runTest {
        coEvery {
            dispatchFirestoreRepo.getDispatchPayload(any(), any(), any(),any())
        } returns Dispatch(vid = 0)
        assertFalse(
            dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(
                "103939",
                dataStoreManager,
                ""
            )
        )
    }

    @Test
    fun testVIDFetchForValidVID() = runTest {

        coEvery {
            dispatchFirestoreRepo.getDispatchPayload(any(), any(), any(),any())
        } returns Dispatch(vid = 12838)
        coEvery {
            dataStoreManager.setValue(DataStoreManager.VID_KEY, any())
        } returns mockk()
        assertTrue(
            dispatchBaseUseCase.fetchAndStoreVIDFromDispatchData(
                "103939",
                dataStoreManager,
                ""
            )
        )
    }

    @Test
    fun testValidCIdVehicleIdDispatchId() = runTest {
        assertTrue(dispatchBaseUseCase.getCidAndTruckNumber("").third)
    }

    @Test
    fun testInValidCIdVehicleIdDispatchId() = runTest {
        coEvery {  appModuleCommunicator.doGetCid()} returns ""
        coEvery {  appModuleCommunicator.doGetTruckNumber()} returns ""
        assertFalse(dispatchBaseUseCase.getCidAndTruckNumber("").third)
        coEvery {  appModuleCommunicator.doGetTruckNumber()} returns ""
        assertFalse(dispatchBaseUseCase.getCidAndTruckNumber("").third)
    }

    @Test
    fun testActionAddition() {
        val stop1 = StopDetail(stopid = 0)
        val action1 = Action(actionid = 0, stopid = 0)
        val action2 = Action(actionid = 1, stopid = 0)
        val action3 = Action(actionid = 2, stopid = 0)
        val actions = mutableListOf<Action>()
        actions.add(action1)
        actions.add(action2)
        stop1.Actions.addAll(actions)
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(stop1)
        assertEquals(
            stop1.Actions,
            dispatchBaseUseCase.handleActionAddAndUpdate(
                action3,
                stopList,
                EMPTY_STRING
            ).Actions
        )
    }


    @Test
    fun testActionUpdation() {
        val stop1 = StopDetail(stopid = 0)
        val action1 = Action(actionid = 0, stopid = 0)
        val action2 = Action(actionid = 1, stopid = 0)
        val updatedAction1 = Action(actionid = 0, stopid = 0, driverFormid = 32)
        val actions = mutableListOf<Action>()
        actions.add(action1)
        actions.add(action2)
        stop1.Actions.addAll(actions)
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(stop1)
        assertEquals(
            updatedAction1,
            dispatchBaseUseCase.handleActionAddAndUpdate(
                updatedAction1,
                stopList,
                EMPTY_STRING
            ).Actions[1]
        )
    }

    @Test
    fun testDriverFormAdditionInAction() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = 1,
            driverFormClass = 0,
            forcedFormId = "3",
            forcedFormClass = -1
        )
        assertEquals(
            1,
            dispatchBaseUseCase.fetchAndAddFormsOfActionForFormSync(action, mutableSetOf()).size
        )
    }

    @Test
    fun testForcedFormAdditionInAction() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = 3,
            driverFormClass = -1,
            forcedFormId = "3",
            forcedFormClass = 0
        )
        assertEquals(
            1,
            dispatchBaseUseCase.fetchAndAddFormsOfActionForFormSync(action, mutableSetOf()).size
        )
    }

    @Test
    fun testDriverAndForcedFormAdditionInAction() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = 1,
            driverFormClass = 0,
            forcedFormId = "2",
            forcedFormClass = 0
        )
        assertEquals(
            2,
            dispatchBaseUseCase.fetchAndAddFormsOfActionForFormSync(action, mutableSetOf()).size
        )
    }

    @Test
    fun testInvalidDriverAndForcedFormInAction() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = 1,
            driverFormClass = -1,
            forcedFormId = "2",
            forcedFormClass = -1
        )
        assertEquals(
            0,
            dispatchBaseUseCase.fetchAndAddFormsOfActionForFormSync(action, mutableSetOf()).size
        )
    }

    @Test
    fun testValidDriverFormAdditionForPersistence() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = 1,
            driverFormClass = 0,
            forcedFormId = "",
            forcedFormClass = -1
        )
        assertEquals(
            1,
            dispatchBaseUseCase.checkAndAddFormIfInCompleteForLocalPersistence(
                action,
                false,
                hashSetOf()
            ).size
        )
    }

    @Test
    fun testValidForcedFormAdditionForPersistence() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = -1,
            driverFormClass = -1,
            forcedFormId = "3",
            forcedFormClass = 0
        )
        assertEquals(
            1,
            dispatchBaseUseCase.checkAndAddFormIfInCompleteForLocalPersistence(
                action,
                false,
                hashSetOf()
            ).size
        )
    }

    @Test
    fun testValidDriverAndForcedFormAdditionForPersistence() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = 21,
            driverFormClass = 0,
            forcedFormId = "3",
            forcedFormClass = 0
        )
        assertEquals(
            2,
            dispatchBaseUseCase.checkAndAddFormIfInCompleteForLocalPersistence(
                action,
                false,
                hashSetOf()
            ).size
        )
    }

    @Test
    fun testSavedDriverAndForcedFormAdditionForPersistence() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = 21,
            driverFormClass = 0,
            forcedFormId = "3",
            forcedFormClass = 0
        )
        assertEquals(
            0,
            dispatchBaseUseCase.checkAndAddFormIfInCompleteForLocalPersistence(
                action,
                true,
                hashSetOf()
            ).size
        )
    }

    @Test
    fun testInvalidDriverAndForcedFormAdditionForPersistence() {
        val action = Action(
            actionid = 0,
            stopid = 0,
            driverFormid = -1,
            driverFormClass = -1,
            forcedFormId = "-1",
            forcedFormClass = -1
        )
        assertEquals(
            0,
            dispatchBaseUseCase.checkAndAddFormIfInCompleteForLocalPersistence(
                action,
                false,
                hashSetOf()
            ).size
        )
    }

    @Test
    fun testDeepCopyOfStopDetailList() {
        val stops = mutableListOf<StopDetail>()
        stops.add(StopDetail(stopid = 0))
        stops.add(StopDetail(stopid = 1))
        assertEquals(stops, dispatchBaseUseCase.getDeepCopyOfStopDetailList(stops))
    }

    @Test
    fun testAnyStopAlreadyCompletedAtStartOfTheTrip() {
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 0))
        stopList.add(StopDetail(stopid = 1))
        assertFalse(dispatchBaseUseCase.anyStopAlreadyCompleted(stopList))
    }

    @Test
    fun testAnyStopAlreadyCompletedInMiddleOfTrip() {
        val stopList = CopyOnWriteArrayList<StopDetail>()
        stopList.add(StopDetail(stopid = 0, completedTime = completedTime))
        stopList.add(StopDetail(stopid = 1))
        assertTrue(dispatchBaseUseCase.anyStopAlreadyCompleted(stopList))
    }

    @Test
    fun testGetCurrentStop() = runTest {
        coEvery {
            dataStoreManager.getValue(DataStoreManager.CURRENT_STOP_KEY, EMPTY_STRING)
        } returns EMPTY_STRING
        assertEquals(Stop(), dispatchBaseUseCase.getCurrentStop(dataStoreManager))
    }

    @Test
    fun `test addDispatchFormPathToFormStack when addFormToPreference returns an empty string then it should not interact with setValue`() =
        runTest {
            //Arrange
            coEvery {
                formDataStoreManager.getValue(any(), any<String>())
            } returns EMPTY_STRING

            every {
                UncompletedFormsUseCase.addFormToPreference(any(), any())
            } returns EMPTY_STRING

            coEvery {
                formDataStoreManager.setValue(any(), any<String>())
            } returns Unit

            coEvery {
                dispatchStopsUseCase.addDispatchFormPathToFormStack(
                    any(),
                    any()
                )
            } just runs

            //Act
            dispatchStopsUseCase.addDispatchFormPathToFormStack(
                getControlDispatchFormPath(),
                dataStoreManager
            )

            //Assert
            coVerify(exactly = 0) { formDataStoreManager.setValue(any(), any<String>()) }
        }

    @Test
    fun `test getActiveStopCount`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 0))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns "3333"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "3333"
        assertEquals(3, dispatchBaseUseCase.getActiveStopCount("3333"))
    }

    @Test
    fun `test getActiveStopCount for invalid assertion`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 0))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns selectedDispatchId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns selectedDispatchId
        assertNotEquals(2, dispatchBaseUseCase.getActiveStopCount(selectedDispatchId), "The actual result will be 3")
    }

    @Test
    fun `test getInActiveStopCount`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 0))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns selectedDispatchId
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns selectedDispatchId
        assertEquals(2, dispatchBaseUseCase.getInActiveStopCount(selectedDispatchId))
    }

    @Test
    fun `test getInActiveStopCount for invalid assertion`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 0))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "3333"
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns selectedDispatchId
        assertNotEquals(3, dispatchBaseUseCase.getInActiveStopCount(selectedDispatchId), "The actual result will be 2")
    }

    @Test
    fun `test getManipulatedStopCount for stop addition`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 0))
            it.add(StopDetail(stopid = 5, deleted = 0))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns selectedDispatchId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns selectedDispatchId
        assertEquals(Triple(4, 1, true), dispatchBaseUseCase.getManipulatedStopCount(selectedDispatchId,ADDED, 3, 2))
    }

    @Test
    fun `test getManipulatedStopCount for stop addition with no stop update`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 0))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns selectedDispatchId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns selectedDispatchId
        assertEquals(Triple(3, 0, false), dispatchBaseUseCase.getManipulatedStopCount(selectedDispatchId,ADDED, 3, 3))
    }

    @Test
    fun `test getManipulatedStopCount stop removal`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 1))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns selectedDispatchId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns selectedDispatchId
        assertEquals(Triple(3, 1, true), dispatchBaseUseCase.getManipulatedStopCount(selectedDispatchId,REMOVED, 2, 2))
    }

    @Test
    fun `test getManipulatedStopCount stop removal with no stop update`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 1))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns selectedDispatchId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns selectedDispatchId
        assertEquals(Triple(3, 0, false), dispatchBaseUseCase.getManipulatedStopCount(selectedDispatchId,REMOVED, 2, 3))
    }

    @Test
    fun `test getManipulatedStopCount for invalid manipulation`() = runTest {
        val stops = ArrayList<StopDetail>().also {
            it.add(StopDetail(stopid = 0, deleted = 0))
            it.add(StopDetail(stopid = 1, deleted = 1))
            it.add(StopDetail(stopid = 2, deleted = 0))
            it.add(StopDetail(stopid = 3, deleted = 1))
            it.add(StopDetail(stopid = 4, deleted = 1))
        }
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopsForDispatch(any(), any(), any()) } returns stops
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST555"
        coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns selectedDispatchId
        coEvery { dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)  } returns selectedDispatchId
        assertEquals(Triple(0, 0, false), dispatchBaseUseCase.getManipulatedStopCount(selectedDispatchId,INVALID_STOP_INDEX, 2, 3))
    }

    @Test
    fun `verify startTime is set`() = runTest(UnconfinedTestDispatcher()) {
        val startTime = 10L
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.TRIP_START_TIME_IN_MILLIS_KEY,
                startTime
            )
        } returns Unit

        dispatchBaseUseCase.setStartTime(startTime, dataStoreManager)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            dataStoreManager.setValue(
                DataStoreManager.TRIP_START_TIME_IN_MILLIS_KEY,
                startTime
            )
        }
    }

    @Test
    fun `verify storeActiveDispatchIdToDataStore`() = runTest(UnconfinedTestDispatcher()) {
        val dispatchKey = "dispatchKey"
        coEvery {
            dataStoreManager.getValue(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)
        } returns dispatchKey
        coEvery {
            dataStoreManager.setValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY,
                dispatchKey
            )
        } returns Unit
        val returnedDispatchKey = dispatchBaseUseCase.storeActiveDispatchIdToDataStore(dataStoreManager, EMPTY_STRING)
        assertEquals(returnedDispatchKey, dispatchKey)
    }

    @Test
    fun `check logNewEventWithDefaultParameters gets called`(){
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any()) } just runs
        dispatchBaseUseCase.logNewEventWithDefaultParameters(TIMELINE_VIEW_COUNT)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any())
        }
    }

    @Test
    fun `check logScreenViewEvent gets called`(){
        every { firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any()) } just runs
        dispatchBaseUseCase.logScreenViewEvent(STOP_DETAIL_SCREEN_TIME)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any())
        }
    }

    @Test
    fun `verify getTripEventReasonTypeAndGuf for correctness`() {
        assertEquals(Pair(StopActionReasonTypes.TIMEOUT.name, true), dispatchBaseUseCase.getTripEventReasonTypeAndGuf(NEGATIVE_GUF_TIMEOUT))
        assertEquals(Pair(StopActionReasonTypes.NORMAL.name, true), dispatchBaseUseCase.getTripEventReasonTypeAndGuf(NEGATIVE_GUF_CONFIRMED))
        assertEquals(Pair(StopActionReasonTypes.NORMAL.name, false), dispatchBaseUseCase.getTripEventReasonTypeAndGuf(REQUIRED_GUF_CONFIRMED))
        assertEquals(Pair(StopActionReasonTypes.AUTO.name, false), dispatchBaseUseCase.getTripEventReasonTypeAndGuf(EMPTY_STRING))
    }

    @Test
    fun `verify dispatch active state when the trip is active and selected`() = testDispatchActiveState(
        activeDispatchId = "1000",
        selectedDispatchId = "1000",
        expected = DispatchActiveState.ACTIVE
    )

    @Test
    fun `verify dispatch active state when a trip is active but not selected`() = testDispatchActiveState(
        activeDispatchId = "1000",
        selectedDispatchId = "1337",
        expected = DispatchActiveState.PREVIEWING
    )

    @Test
    fun `verify dispatch active state when no trip is active`() = testDispatchActiveState(
        activeDispatchId = EMPTY_STRING,
        selectedDispatchId = "1000",
        expected = DispatchActiveState.NO_TRIP_ACTIVE
    )

    @Test
    fun `verify dispatch active state when no trip is active (null)`() = testDispatchActiveState(
        activeDispatchId = null,
        selectedDispatchId = "1000",
        expected = DispatchActiveState.NO_TRIP_ACTIVE
    )

    @Test
    fun `verify dispatch active state in the vacuous case`() = testDispatchActiveState(
        activeDispatchId = EMPTY_STRING,
        selectedDispatchId = EMPTY_STRING,
        expected = DispatchActiveState.NO_TRIP_ACTIVE
    )

    @Test
    fun `verify dispatch active state in the vacuous case (null)`() = testDispatchActiveState(
        activeDispatchId = null,
        selectedDispatchId = EMPTY_STRING,
        expected = DispatchActiveState.NO_TRIP_ACTIVE
    )

    private fun testDispatchActiveState(
        activeDispatchId: String?,
        selectedDispatchId: String,
        expected: DispatchActiveState
    ) = runTest {
        val actual = dispatchBaseUseCase.getDispatchActiveState(activeDispatchId, selectedDispatchId)
        assertEquals(expected, actual)
    }

    @Test
    fun `verify getDispatchAndCheckIsCompleted() returns dispatch completion state`() = runTest {
        val testDispatchId = "testActiveDispatch"
        val testDispatch = Dispatch(isCompleted = true)
        coEvery {
            fetchDispatchStopsAndActionsUseCase.getDispatch(
                any(),
                any(),
                any(),
                any()
            )
        } returns testDispatch
        coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns "dispId"
        coEvery { localDataSourceRepo.removeAllKeysOfAppModuleDataStore() } just runs

        val result = dispatchBaseUseCase.getDispatchAndCheckIsCompleted(testDispatchId)

        coVerify(exactly = 0) {
            localDataSourceRepo.removeAllKeysOfAppModuleDataStore()
        }
        coVerify(exactly = 1) {
            localDataSourceRepo.getActiveDispatchId(any())
            fetchDispatchStopsAndActionsUseCase.getDispatch(
                any(),
                any(),
                any(),
                any()
            )
        }
        assertTrue { result }
    }

    @Test
    fun `verify getDispatchAndCheckIsCompleted() returns dispatch completion state and removes all keys if the completed dispatch is active`() =
        runTest {
            val testDispatchId = "testActiveDispatch"
            val testDispatch = Dispatch(isCompleted = true)
            coEvery {
                fetchDispatchStopsAndActionsUseCase.getDispatch(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns testDispatch
            coEvery { localDataSourceRepo.getActiveDispatchId(any()) } returns testDispatchId
            coEvery { localDataSourceRepo.removeAllKeysOfAppModuleDataStore() } just runs

            val result = dispatchBaseUseCase.getDispatchAndCheckIsCompleted(testDispatchId)

            coVerify(exactly = 1) {
                localDataSourceRepo.getActiveDispatchId(any())
                localDataSourceRepo.removeAllKeysOfAppModuleDataStore()
                fetchDispatchStopsAndActionsUseCase.getDispatch(any(), any(), any(), any())
            }
            assertTrue { result }
        }

    @After
    fun clear() {
        unmockkObject(FormUtils)
        unmockkAll()
    }
}