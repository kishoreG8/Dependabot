package com.trimble.ttm.routemanifest.viewmodels

import android.content.pm.PackageManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Observer
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_LIST
import com.trimble.ttm.commons.logger.VIEW_MODEL
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.model.HotKeys
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.utils.TestDelayProvider
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.service.RouteManifestForegroundService
import com.trimble.ttm.routemanifest.usecases.DispatchListUseCase
import com.trimble.ttm.routemanifest.usecases.DispatchValidationUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartCaller
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously
import com.trimble.ttm.routemanifest.viewmodel.DispatchListViewModel
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.coVerifyOrder
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class DispatchListViewModelTests {


    private lateinit var dispatchListViewModel: DispatchListViewModel

    @MockK
    private lateinit var dispatchListUseCase: DispatchListUseCase
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @MockK
    private lateinit var backboneUseCase: BackboneUseCase
    @MockK
    private lateinit var dispatchValidationUseCase: DispatchValidationUseCase
    @MockK
    private lateinit var tripStartUseCase: TripStartUseCase
    @RelaxedMockK
    private lateinit var application: WorkflowApplication
    @MockK
    private lateinit var localDataSourceRepo: LocalDataSourceRepo
    @RelaxedMockK
    private lateinit var packageManager: PackageManager
    @RelaxedMockK
    private lateinit var formLibraryUseCase: FormLibraryUseCase
    @RelaxedMockK
    private lateinit var hotKeysObserver : Observer<Boolean>

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
        every { application.packageManager } returns packageManager
        dispatchListViewModel =
            spyk(
                DispatchListViewModel(
                    application, dispatchListUseCase, backboneUseCase,
                    appModuleCommunicator, dispatchValidationUseCase, tripStartUseCase, formLibraryUseCase, TestDispatcherProvider()
                )
            )
        mockkObject(Log)
        mockkObject(Utils)
    }


    @Test
    fun `verify dismissTripPanelMessageIfThereIsNoActiveTrip`() = runTest {

        coEvery { dispatchListUseCase.dismissTripPanelMessageIfThereIsNoActiveTrip() } returns true
        dispatchListViewModel.dismissTripPanelMessageIfThereIsNoActiveTrip()

        coVerify(exactly = 1) { dispatchListUseCase.dismissTripPanelMessageIfThereIsNoActiveTrip() }
    }

    @Test
    fun `verify hasBackboneDataChanged - cid is empty`() = runTest {

        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { appModuleCommunicator.doGetObcId() } returns "34214"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "rawefs"
        assertTrue(dispatchListViewModel.hasBackboneDataChanged())
    }

    @Test
    fun `verify hasBackboneDataChanged - OBC ID is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "gfsdgsdf"
        coEvery { appModuleCommunicator.doGetObcId() } returns ""
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "rawefs"
        assertTrue(dispatchListViewModel.hasBackboneDataChanged())
    }

    @Test
    fun `verify hasBackboneDataChanged - vehicle number is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "fsdfsd"
        coEvery { appModuleCommunicator.doGetObcId() } returns "34214"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""
        assertTrue(dispatchListViewModel.hasBackboneDataChanged())
    }

    @Test
    fun `verify hasBackboneDataChanged - CID different from backbone`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "fsdfsd"
        coEvery { appModuleCommunicator.doGetObcId() } returns "34214"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "dghdf"

        coEvery { backboneUseCase.getCustomerId() } returns "3243"
        coEvery { backboneUseCase.getOBCId() } returns "34214"
        coEvery { backboneUseCase.getVehicleId() } returns "dghdf"

        assertTrue(dispatchListViewModel.hasBackboneDataChanged())
    }

    @Test
    fun `verify hasBackboneDataChanged - OBC ID different from backbone`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "fsdfsd"
        coEvery { appModuleCommunicator.doGetObcId() } returns "34214"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "dghdf"

        coEvery { backboneUseCase.getCustomerId() } returns "fsdfsd"
        coEvery { backboneUseCase.getOBCId() } returns "3421sdfgd4"
        coEvery { backboneUseCase.getVehicleId() } returns "dghdf"

        assertTrue(dispatchListViewModel.hasBackboneDataChanged())
    }

    @Test
    fun `verify hasBackboneDataChanged - Vehicle Number different from backbone`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "fsdfsd"
        coEvery { appModuleCommunicator.doGetObcId() } returns "34214"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "dghdf"

        coEvery { backboneUseCase.getCustomerId() } returns "fsdfsd"
        coEvery { backboneUseCase.getOBCId() } returns "34214"
        coEvery { backboneUseCase.getVehicleId() } returns "dghvsdfsddf"

        assertTrue(dispatchListViewModel.hasBackboneDataChanged())
    }

    @Test
    fun `verify hasBackboneDataChanged - all match backbone`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "fsdfsd"
        coEvery { appModuleCommunicator.doGetObcId() } returns "34214"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "dghdf"

        coEvery { backboneUseCase.getCustomerId() } returns "fsdfsd"
        coEvery { backboneUseCase.getOBCId() } returns "34214"
        coEvery { backboneUseCase.getVehicleId() } returns "dghdf"

        assertFalse(dispatchListViewModel.hasBackboneDataChanged())
    }

    @Test
    fun `verify hasBackboneDataChanged for exception`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "fsdfsd"
        coEvery { appModuleCommunicator.doGetObcId() } returns "34214"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "dghdf"

        coEvery { backboneUseCase.getCustomerId() } throws Exception("Exception")

        assertTrue(dispatchListViewModel.hasBackboneDataChanged())
    }

    @Test
    fun `verify observeForLastDispatchReachPagination`() = runTest {
        coEvery { dispatchListUseCase.getDispatches() } returns mutableListOf()
        coEvery { dispatchListUseCase.getLastDispatchReachedFlow() } returns flow { emit(true) }
        dispatchListViewModel.observeForLastDispatchReachPagination()

        coVerify(exactly = 1) { dispatchListUseCase.getLastDispatchReachedFlow() }
    }

    @Test
    fun `verify observeForRemovedDispatchId`() = runTest {
        coEvery { dispatchListUseCase.getRemovedDispatchIDFlow() } returns flow { emit("test") }
        dispatchListViewModel.observeForRemovedDispatchId()

        coVerify(exactly = 1) { dispatchListUseCase.getRemovedDispatchIDFlow() }
    }

    @Test
    fun `verify getDispatchList when cid is empty`() = runTest {

        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "fasdf"
        coEvery { application.getString(any()) } returns "asdf"
        dispatchListViewModel.getDispatchList(EMPTY_STRING)

        coVerify(exactly = 0) {
            dispatchListUseCase.listenDispatchesForTruck(
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify getDispatchList when vehicle number is empty`() = runTest {

        coEvery { appModuleCommunicator.doGetCid() } returns "23"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""
        coEvery { application.getString(any()) } returns "asdf"
        dispatchListViewModel.getDispatchList(EMPTY_STRING)

        coVerify(exactly = 0) {
            dispatchListUseCase.listenDispatchesForTruck(
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify getDispatchList`() = runTest {

        coEvery { appModuleCommunicator.doGetCid() } returns "23"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "324"
        coEvery { application.getString(any()) } returns "asdf"
        coEvery { dispatchListUseCase.listenDispatchesForTruck(any(), any()) } just runs
        coEvery { dispatchListUseCase.listenDispatchesList() } returns  flow { emit(listOf<Dispatch>()) }
        coEvery { dispatchListUseCase.getRemovedDispatchIDFlow() } returns flow { emit("test") }
        coEvery { dispatchListUseCase.getLastDispatchReachedFlow() } returns flow { emit(true) }
        dispatchListViewModel.getDispatchList(EMPTY_STRING)

        coVerify(exactly = 1) {
            dispatchListUseCase.listenDispatchesForTruck(
                any(),
                any()
            )
        }
    }

    @Test
    fun `check when updateDispatchesQuantity is called updateDispatchesQuantity is called also`() = runTest {
        val quantity = 1
        coEvery {
            dispatchValidationUseCase.updateQuantity(quantity)
        } returns Unit
        dispatchListViewModel.updateDispatchesQuantity(quantity)
        coVerify {
            dispatchValidationUseCase.updateQuantity(quantity)
        }
    }

    @Test
    fun `check when hasAnActiveDispatch is called hasAnActiveDispatch is called also`() = runTest {
        coEvery {
            dispatchValidationUseCase.hasAnActiveDispatch()
        } returns true
        dispatchListViewModel.hasAnActiveDispatch().collect()
        coVerify {
            dispatchValidationUseCase.hasAnActiveDispatch()
        }
    }

    @Test
    fun `check when hasOnlyOneDispatchOnList is called hasOnlyOneDispatchOnList is called also`() = runTest {
        coEvery {
            dispatchValidationUseCase.hasOnlyOne()
        } returns true
        dispatchListViewModel.hasOnlyOneDispatchOnList().collect()
        coVerify {
            dispatchValidationUseCase.hasOnlyOne()
        }
    }

    @Test
    fun `check when restoreSelectedDispatch is called restoreSelectedDispatch is called also`() {
        val callback : () -> Unit = mockk()
        coEvery {
            appModuleCommunicator.restoreSelectedDispatch()
        } returns Unit
        coEvery {
            callback()
        } returns Unit
        dispatchListViewModel.restoreSelectedDispatch(
            TestDelayProvider(),
            callback
        )
        coVerify(Ordering.ORDERED) {
            appModuleCommunicator.restoreSelectedDispatch()
            callback()
        }
    }

    @Test
    fun `verify cacheBackboneData`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "cid"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "truckNumber"
        coEvery { appModuleCommunicator.doGetObcId() } returns "obcId"

        dispatchListViewModel.cacheBackboneData()

        coVerify { appModuleCommunicator.doGetCid() }
        coVerify { appModuleCommunicator.doGetTruckNumber() }
        coVerify { appModuleCommunicator.doGetObcId() }
    }

    @Test
    fun `test cacheBackboneData with exception`() = runTest {
        val exceptionMessage = "Exception test"
        coEvery { appModuleCommunicator.doGetCid() } throws Exception(exceptionMessage)
        every { Log.e(any(), any()) } returns Unit

        dispatchListViewModel.cacheBackboneData()

        verify {
            Log.e("$TRIP_LIST$VIEW_MODEL", "ExceptionSetCrashIdentifier$exceptionMessage")
        }
    }

    @Test
    fun `verify getDispatchList code flow`() = runTest {
        val dispatchList = arrayListOf(Dispatch(dispid = "1", stopsCountOfDispatch = 5))
        coEvery { appModuleCommunicator.doGetCid() } returns "32323"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "23232"
        every { dispatchListUseCase.listenDispatchesList() } returns flow { emit(dispatchList) }
        coEvery { dispatchListUseCase.checkAndUpdateStopCount(any()) } returns dispatchList
        coEvery { dispatchListUseCase.sortDispatchListByTripStartTime(any()) } returns dispatchList
        coEvery { dispatchValidationUseCase.updateQuantity(any()) } just runs
        coEvery { dispatchListUseCase.updateActiveDispatchDatastoreKeys(any()) } just runs
        every {  dispatchListUseCase.getDispatchToBeStarted(any(), any()) } returns dispatchList[0]
        every { dispatchListUseCase.canShowDispatchStartPopup(any(), any()) } returns true
        coEvery { dispatchListUseCase.listenDispatchesForTruck(any(), any()) } just runs
        coEvery { dispatchListUseCase.getRemovedDispatchIDFlow() } returns flow { emit("test") }
        coEvery { dispatchListUseCase.getLastDispatchReachedFlow() } returns flow { emit(true) }

        dispatchListViewModel.getDispatchList(EMPTY_STRING)

        coVerifyAll {
            dispatchListUseCase.listenDispatchesForTruck(any(), any())
            dispatchListUseCase.listenDispatchesList()
            dispatchListUseCase.getRemovedDispatchIDFlow()
            dispatchListUseCase.getLastDispatchReachedFlow()
            dispatchListUseCase.checkAndUpdateStopCount(dispatchList)
            dispatchListUseCase.sortDispatchListByTripStartTime(dispatchList)
            dispatchValidationUseCase.updateQuantity(any())
            dispatchListUseCase.updateActiveDispatchDatastoreKeys(dispatchList)
            dispatchListUseCase.getDispatchToBeStarted(dispatchList, TripStartCaller.DISPATCH_DETAIL_SCREEN)
        }

    }

    @Test
    fun `verify processDispatchNotificationData for null dispatch data`() = runTest {
        val dispatchData: Dispatch? = null

        dispatchListViewModel.processDispatchNotificationData(dispatchData, "")

        coVerify(exactly = 0) { dispatchListViewModel.addNotifiedDispatchToTheDispatchList(any()) }
        coVerify { Log.w(any(), any()) }
    }

    @Test
    fun `verify processDispatchNotificationData for invalid dispatch data`() = runTest {
        val dispatchData = Dispatch()

        dispatchListViewModel.processDispatchNotificationData(dispatchData, "")

        coVerify(exactly = 0) { dispatchListViewModel.addNotifiedDispatchToTheDispatchList(any()) }
        coVerify { Log.w(any(), any()) }
    }

    @Test
    fun `verify processDispatchNotificationData for valid dispatch`() {
        coEvery { dispatchListUseCase.getDispatches() } returns mutableListOf()
        coEvery { dispatchListUseCase.addNotifiedDispatchToTheDispatchList(any(),any(), any(), any()) } returns  mutableListOf()
        val dispatchData = Dispatch(dispid = "123")

        dispatchListViewModel.processDispatchNotificationData(dispatchData, "wwer")

        coVerify {
            dispatchListUseCase.addNotifiedDispatchToTheDispatchList(any(),any(), any(), any())
        }
    }

    @Test
    fun `verify performStateUpdateUponStartedLifecycle code flow`() = runTest {
        val hasActiveDispatchSharedFlow = MutableSharedFlow<Boolean>()
        val dispatchListSharedFlow = MutableSharedFlow<List<Dispatch>>()

        every { dispatchListViewModel.getAppLauncherVersion(any()) } returns 1090L
        coEvery { localDataSourceRepo.setToAppModuleDataStore(any<Preferences.Key<Any>>(), any()) } just runs
        every { dispatchListUseCase.getLocalDataSourceRepo() } returns localDataSourceRepo
        coEvery { localDataSourceRepo.setToFormLibModuleDataStore(any<Preferences.Key<Any>>(), any()) } just runs
        coEvery { dispatchListUseCase.getRemoveOneMinuteDelayFeatureFlag() } just runs
        coEvery { appModuleCommunicator.getFeatureFlags() } returns mapOf()
        coEvery { dispatchListUseCase.getAppLauncherMapsPerformanceFixVersionFromFireStore(any()) } returns 0L
        coEvery { dispatchValidationUseCase.hasAnActiveDispatchListener } returns hasActiveDispatchSharedFlow
        every { dispatchListViewModel.dispatchListFlow } returns dispatchListSharedFlow

        dispatchListViewModel.performStateAndUiUpdateUponCreatedLifecycle({}, {})

        // emit the value for the 1st flow
        coEvery { dispatchListViewModel.getAppLauncherMapsPerformanceFixVersionFromFireStore() } returns flow { emit(Unit) }
        // emit the value for the 2nd flow
        hasActiveDispatchSharedFlow.emit(true)
        // emit the value for the 3rd flow
        dispatchListSharedFlow.emit(listOf(Dispatch()))

        coVerifyAll {
            dispatchListUseCase.getLocalDataSourceRepo()
            localDataSourceRepo.setToFormLibModuleDataStore(any<Preferences.Key<Any>>(), any())
            dispatchListUseCase.getRemoveOneMinuteDelayFeatureFlag()
            repeat(2) {
                appModuleCommunicator.getFeatureFlags()
            }
            dispatchListUseCase.getAppLauncherMapsPerformanceFixVersionFromFireStore(any())
            dispatchListUseCase.getLocalDataSourceRepo()
            localDataSourceRepo.setToAppModuleDataStore(any<Preferences.Key<Any>>(), any())
        }

    }

    @Test
    fun `verify doAfterAllPermissionsGranted execution flow`() = runTest {
        every { application.startForegroundServiceIfNotStartedPreviously(RouteManifestForegroundService::class.java) } just runs
        coEvery { appModuleCommunicator.doGetCid() } returns "cid"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "truckNumber"
        coEvery { appModuleCommunicator.doGetObcId() } returns "obcId"
        every { dispatchListUseCase.clear() } just runs

        dispatchListViewModel.doAfterAllPermissionsGranted()

        coVerifyOrder {
            dispatchListViewModel.doAfterAllPermissionsGranted()
            dispatchListViewModel.cacheBackboneData()
            dispatchListViewModel.hasBackboneDataChanged()
            dispatchListUseCase.clear()
        }

    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is not empty and the hotkeys count is greater than 0`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { formLibraryUseCase.getHotKeysWithoutDescription(any(), any()) } returns flow { emit(
            mutableSetOf(HotKeys(hkId = 1), HotKeys(hkId = 2))
        ) }
        dispatchListViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        dispatchListViewModel.canShowHotKeysMenu()
        assertTrue(dispatchListViewModel.isHotKeysAvailable.value!!)
        dispatchListViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is not empty and the hotkeys count is 0`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { formLibraryUseCase.getHotKeysWithoutDescription(any(), any()) } returns flow { emit(
            mutableSetOf()
        ) }
        dispatchListViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        dispatchListViewModel.canShowHotKeysMenu()
        assertFalse(dispatchListViewModel.isHotKeysAvailable.value!!)
        dispatchListViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns EMPTY_STRING
        dispatchListViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        dispatchListViewModel.canShowHotKeysMenu()
        assertFalse(dispatchListViewModel.isHotKeysAvailable.value!!)
        dispatchListViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
        coVerify(exactly = 0) {
            formLibraryUseCase.getHotKeysWithoutDescription(any(), any())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

}