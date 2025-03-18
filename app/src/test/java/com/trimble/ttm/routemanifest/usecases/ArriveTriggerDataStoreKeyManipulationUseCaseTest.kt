package com.trimble.ttm.routemanifest.usecases

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.model.ArrivedGeoFenceTriggerData
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.FILL_FORMS_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ArriveTriggerDataStoreKeyManipulationUseCaseTest {

    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo
    @MockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase
    private lateinit var arriveTriggerDataStoreKeyManipulationUseCase: ArriveTriggerDataStoreKeyManipulationUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        arriveTriggerDataStoreKeyManipulationUseCase = spyk(ArriveTriggerDataStoreKeyManipulationUseCase(localDataSourceRepo))
        mockkStatic(Log::class)
    }

    @Test
    fun `verify putArrivedTriggerDataIntoPreference when there is a trigger data doesnt exist`() = runTest {

        val triggerList = arrayListOf(
            ArrivedGeoFenceTriggerData(1),
            ArrivedGeoFenceTriggerData(2)
        )
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData() } returns triggerList

        coEvery {
            arriveTriggerDataStoreKeyManipulationUseCase.checkArrivedGeofenceTriggerDataExistInThePreference(
                any(),
                any()
            )
        } returns false

        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.setArrivedTriggerDataInPreference(any()) } just runs

        arriveTriggerDataStoreKeyManipulationUseCase.putArrivedTriggerDataIntoPreference(1)

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
            arriveTriggerDataStoreKeyManipulationUseCase.setArrivedTriggerDataInPreference(any())
        }
    }

    @Test
    fun `verify getArrivedTriggerData for empty arrive data string of datastore`() = runTest {
        coEvery {
            localDataSourceRepo.getFromAppModuleDataStore(
                DataStoreManager.ARRIVED_TRIGGERS_KEY,
                DataStoreManager.emptyArrivedGeoFenceTriggerList
            )
        } returns EMPTY_STRING

        assertEquals(listOf(), arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData())
    }

    @Test
    fun `verify getArrivedTriggerData for invalid arrive data string of datastore`() = runTest {
        coEvery {
            localDataSourceRepo.getFromAppModuleDataStore(DataStoreManager.ARRIVED_TRIGGERS_KEY, DataStoreManager.emptyArrivedGeoFenceTriggerList)
        } returns Gson().toJson(arrayListOf<ArrivedGeoFenceTriggerData>())

        assertEquals(listOf(), arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData())
    }

    @Test
    fun `verify getArrivedTriggerData for correct arrive data string of datastore`() = runTest {
        coEvery {
            localDataSourceRepo.getFromAppModuleDataStore(DataStoreManager.ARRIVED_TRIGGERS_KEY, DataStoreManager.emptyArrivedGeoFenceTriggerList)
        } returns Gson().toJson(arrayListOf<ArrivedGeoFenceTriggerData>().also {
            it.add(ArrivedGeoFenceTriggerData(1))
            it.add(ArrivedGeoFenceTriggerData(2))
        })

        assertEquals(2, arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData().size)
    }

    @Test
    fun `verify putArrivedTriggerDataIntoPreference when there is a trigger data does exist`() = runTest {
        val triggerList = arrayListOf(
            ArrivedGeoFenceTriggerData(1),
            ArrivedGeoFenceTriggerData(2)
        )
        coEvery {
            arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerData()
        } returns triggerList
        coEvery {
            arriveTriggerDataStoreKeyManipulationUseCase.checkArrivedGeofenceTriggerDataExistInThePreference(
                any(),
                any()
            )
        } returns true

        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.setArrivedTriggerDataInPreference(any()) } just runs

        arriveTriggerDataStoreKeyManipulationUseCase.putArrivedTriggerDataIntoPreference(1)

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            arriveTriggerDataStoreKeyManipulationUseCase.setArrivedTriggerDataInPreference(any())
        }
    }

    @Test
    fun `verify trigger data not exist in the arrived geofence trigger list`() {    //NOSONAR
        val preferenceList = ArrayList<ArrivedGeoFenceTriggerData>()
        preferenceList.add(
            ArrivedGeoFenceTriggerData(
                1
            )
        )
        //Assert data if trigger already exist in the queue, if trigger comes again
        Assert.assertFalse(
            arriveTriggerDataStoreKeyManipulationUseCase.checkArrivedGeofenceTriggerDataExistInThePreference(
                preferenceList,
                3
            )
        )
    }

    @Test
    fun `verify trigger data already exist in the arrived geofence trigger list`() {    //NOSONAR
        val preferenceList = ArrayList<ArrivedGeoFenceTriggerData>()
        preferenceList.add(
            ArrivedGeoFenceTriggerData(
                1
            )
        )
        preferenceList.add(
            ArrivedGeoFenceTriggerData(
                2
            )
        )

        //Assert data if trigger already exist in the queue, if trigger comes again
        Assert.assertTrue(
            arriveTriggerDataStoreKeyManipulationUseCase.checkArrivedGeofenceTriggerDataExistInThePreference(
                preferenceList,
                1
            )
        )
    }

    @Test
    fun `check If ArrivedGeoFenceTrigger AvailableForCurrentStop`() = runTest {    //NOSONAR
        val preferenceList = ArrayList<ArrivedGeoFenceTriggerData>()
        preferenceList.add(
            ArrivedGeoFenceTriggerData(
                1
            )
        )
        preferenceList.add(
            ArrivedGeoFenceTriggerData(
                2
            )
        )
        preferenceList.add(
            ArrivedGeoFenceTriggerData(
                3
            )
        )

        coEvery {
            localDataSourceRepo.getFromAppModuleDataStore(
                DataStoreManager.ARRIVED_TRIGGERS_KEY,
                DataStoreManager.emptyArrivedGeoFenceTriggerList
            )
        } returns
                GsonBuilder().setPrettyPrinting().create().toJson(preferenceList)

        //Assert data if trigger available for user selected stop
        Assert.assertTrue(
            arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(
                1
            )
        )
        //Assert data if trigger not available for user selected stop or incorrect stop
        Assert.assertFalse(
            arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(
                -1
            )
        )

        coEvery {
            localDataSourceRepo.getFromAppModuleDataStore(
                DataStoreManager.ARRIVED_TRIGGERS_KEY,
                DataStoreManager.emptyArrivedGeoFenceTriggerList
            )
        } returns
                GsonBuilder().setPrettyPrinting().create().toJson(arrayListOf<ArrivedGeoFenceTriggerData>())
        Assert.assertFalse(
            arriveTriggerDataStoreKeyManipulationUseCase.checkIfArrivedGeoFenceTriggerAvailableForCurrentStop(
                1
            )
        )
    }

    @Test
    fun `verify if arrival trigger is getting removed from preference` () = runTest {

        val removeMessageFromPriorityQueueAndUpdateTripPanelFlags = mockk<(Int) -> Unit>(relaxed = true)

        val triggerList = arrayListOf(
            ArrivedGeoFenceTriggerData(1),
            ArrivedGeoFenceTriggerData(2)
        )
        coEvery { dispatchStopsUseCase.getArrivedTriggerDataFromPreferenceString() } returns triggerList
        coEvery {
            arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerDataFromPreference()
        } returns Gson().toJson(triggerList)
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.setArrivedTriggerDataInPreference(any()) } just runs

        assertEquals(
            arrayListOf(
                ArrivedGeoFenceTriggerData(
                    2
                )
            ),
            arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(1, removeMessageFromPriorityQueueAndUpdateTripPanelFlags)
        )

    }

    @Test
    fun `verify if arrival trigger data is set in preference` () = runTest {
        val triggerList = arrayListOf(
            ArrivedGeoFenceTriggerData(1),
            ArrivedGeoFenceTriggerData
                (2)
        )
        coEvery { localDataSourceRepo.getFromAppModuleDataStore(
            DataStoreManager.ARRIVED_TRIGGERS_KEY,
            DataStoreManager.emptyArrivedGeoFenceTriggerList) } returns Gson().toJson(triggerList)
        coEvery { localDataSourceRepo.setToAppModuleDataStore(DataStoreManager.ARRIVED_TRIGGERS_KEY, any()) } just runs
        arriveTriggerDataStoreKeyManipulationUseCase.setArrivedTriggerDataInPreference(triggerList)
        assertEquals<String>(
            Gson().toJson(triggerList),
            arriveTriggerDataStoreKeyManipulationUseCase.getArrivedTriggerDataFromPreference()
        )
    }

    @Test
    fun `verify removeArrivedTriggersFromPreferenceIfRespondedByUser with SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID` () = runTest {
        val removeMessageFromPriorityQueueAndUpdateTripPanelFlags = mockk<(Int) -> Unit>(relaxed = true)
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any()) } returns arrayListOf()
        arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID, removeMessageFromPriorityQueueAndUpdateTripPanelFlags)

        coVerify(exactly = 0) {
            arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any())
        }
    }

    @Test
    fun `verify removeArrivedTriggersFromPreferenceIfRespondedByUser with FILL_FORMS_MESSAGE_ID` () = runTest {
        val removeMessageFromPriorityQueueAndUpdateTripPanelFlags = mockk<(Int) -> Unit>(relaxed = true)
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any()) } returns arrayListOf()
        arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(FILL_FORMS_MESSAGE_ID, removeMessageFromPriorityQueueAndUpdateTripPanelFlags)

        coVerify(exactly = 0) {
            arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any())
        }
    }

    @Test
    fun `verify removeArrivedTriggersFromPreferenceIfRespondedByUser with did you arrive message id` () = runTest {
        val removeMessageFromPriorityQueueAndUpdateTripPanelFlags = mockk<(Int) -> Unit>(relaxed = true)
        coEvery { arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any()) } returns arrayListOf()
        arriveTriggerDataStoreKeyManipulationUseCase.removeArrivedTriggersFromPreferenceIfRespondedByUser(1, removeMessageFromPriorityQueueAndUpdateTripPanelFlags)

        coVerify(exactly = 1) {
            arriveTriggerDataStoreKeyManipulationUseCase.removeTriggerFromPreference(any(), any())
        }
    }

    @Test
    fun `verify logStopListBeforeAndAfterRemove when stopList is empty`() {
        arriveTriggerDataStoreKeyManipulationUseCase.logStopListBeforeAndAfterRemove(arrayListOf(),"before")
        verify(exactly = 0) {
            Log.d(any(), any())
        }
    }

    @Test
    fun `verify logStopListBeforeAndAfterRemove when stopList is not empty`() {
        val arrivedGeoFenceTriggerData = ArrivedGeoFenceTriggerData(messageId = 2)
        arriveTriggerDataStoreKeyManipulationUseCase.logStopListBeforeAndAfterRemove(arrayListOf(arrivedGeoFenceTriggerData), "before")
    }

    @After
    fun clear() {
        unmockkAll()
        unmockkStatic(Log::class)
    }

}