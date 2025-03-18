package com.trimble.ttm.routemanifest.usecases

import android.app.Application
import android.content.Context
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.usecase.SendWorkflowEventsToAppUseCase
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.usecases.MessageConfirmationUseCase
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.managers.ServiceManager
import com.trimble.ttm.routemanifest.model.FcmData
import io.mockk.MockKAnnotations
import io.mockk.Runs
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
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.stopKoin

class WorkFlowApplicationUseCaseTest {
    @RelaxedMockK
    private lateinit var backboneUseCase: BackboneUseCase

    @RelaxedMockK
    private lateinit var serviceManager: ServiceManager

    @RelaxedMockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @RelaxedMockK
    private lateinit var edvirSettingsCacheUseCase: EDVIRSettingsCacheUseCase

    @RelaxedMockK
    private lateinit var notificationQueueUseCase: NotificationQueueUseCase

    @RelaxedMockK
    private lateinit var tripCacheUseCase: TripCacheUseCase

    @RelaxedMockK
    private lateinit var workflowAppNotificationUseCase: WorkflowAppNotificationUseCase

    @RelaxedMockK
    private lateinit var application: Application

    @RelaxedMockK
    private lateinit var dataStoreManager: DataStoreManager

    @RelaxedMockK
    private lateinit var messageConfirmationUseCase: MessageConfirmationUseCase

    @RelaxedMockK
    private lateinit var sendWorkflowEventsToAppUseCase: SendWorkflowEventsToAppUseCase

    @RelaxedMockK
    private lateinit var dispatchStopsUseCase: DispatchStopsUseCase

    @RelaxedMockK
    private lateinit var dispatchListUseCase: DispatchListUseCase

    @MockK
    private lateinit var tripCompletionUseCase : TripCompletionUseCase

    private val testDispatcher = TestDispatcherProvider()
    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var SUT: WorkFlowApplicationUseCase
    private lateinit var context: Context

    private val customerIdFlow = MutableSharedFlow<String>()
    private val vehicleIdFlow = MutableSharedFlow<String>()
    private val obcIdFlow = MutableSharedFlow<String>()
    private val cid = "cid123"
    private val vid = "vid123"
    private val obcid = "obcId123"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkObject(AppLauncherCommunicator)
        context = mockk()
        workflowAppNotificationUseCase = spyk(
            WorkflowAppNotificationUseCase(
                application = application,
                dataStoreManager = dataStoreManager,
                messageConfirmationUseCase = messageConfirmationUseCase,
                appModuleCommunicator = appModuleCommunicator,
                sendWorkflowEventsToAppUseCase = sendWorkflowEventsToAppUseCase,
                dispatchStopsUseCase = dispatchStopsUseCase,
                dispatchListUseCase = dispatchListUseCase,
                backboneUseCase = backboneUseCase,
                tripCompletionUseCase = tripCompletionUseCase
            ), recordPrivateCalls = true
        )
        SUT =
            spyk(
                WorkFlowApplicationUseCase(
                    applicationScope = testScope,
                    serviceManager = serviceManager,
                    coroutineDispatcherProvider = testDispatcher,
                    backboneUseCase = backboneUseCase,
                    mockk(),
                    appModuleCommunicator = appModuleCommunicator,
                    edvirSettingsCacheUseCase = edvirSettingsCacheUseCase,
                    notificationQueueUseCase = notificationQueueUseCase,
                    tripCacheUseCase = tripCacheUseCase,
                    workflowAppNotificationUseCase
                )
            )
    }

    @Test
    fun `test cacheEDVIRSettings when not authenticated`() = runTest {
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns false

        SUT.listenEdvirSettings()
        coVerify(exactly = 0) { edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates() }
    }

    @Test
    fun `test cacheEDVIRSettings when authenticated`() = runTest {
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true
        coEvery { edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates() } just Runs

        SUT.listenEdvirSettings()
        coVerify { edvirSettingsCacheUseCase.listenToEDVIRSettingsLiveUpdates() }
    }

    @Test
    fun `verify handleCidChange() has been called on customer id change`() = runTest {
        every { backboneUseCase.monitorCustomerId() } returns customerIdFlow

        SUT.monitorDeviceChanges()
        customerIdFlow.emit(cid)

        coVerify(exactly = 1) { serviceManager.handleCidChange(cid) }
    }

    @Test
    fun `verify handleVehicleNumberChange() has been called on vehicle id change`() = runTest {
        every { backboneUseCase.monitorVehicleId() } returns vehicleIdFlow

        SUT.monitorDeviceChanges()
        vehicleIdFlow.emit(vid)

        coVerify(exactly = 1) { serviceManager.handleVehicleNumberChange(vid) }
    }

    @Test
    fun `verify handleDsnChange() has been called on  obcId change when obcId emitted from flow and obcId from appModuleCommunicator are same`() =
        runTest {
            every { backboneUseCase.monitorOBCId() } returns obcIdFlow
            coEvery { serviceManager.handleDsnChange(any(), any(), any()) } returns true

            SUT.monitorDeviceChanges()
            obcIdFlow.emit(obcid)

            coVerify(exactly = 1) { serviceManager.handleDsnChange(any(), any(), obcid) }
            coVerify(exactly = 1) { SUT.listenEdvirSettings() }

        }

    @Test
    fun `verify handleDsnChange() has been called on  obcId change when obcId emitted from flow and obcId from appModuleCommunicator are not same`() =
        runTest {
            coEvery { appModuleCommunicator.doGetObcId() } returns "000"
            every { backboneUseCase.monitorOBCId() } returns obcIdFlow

            SUT.monitorDeviceChanges()
            obcIdFlow.emit(obcid)

            coVerify(exactly = 1) { serviceManager.handleDsnChange(any(), any(), obcid) }
            coVerify(exactly = 0) { SUT.listenEdvirSettings() }
        }

    @Test
    fun `verify the Active monitorCidJob is getting cancelled`() {
        SUT.monitorCidJob = Job()
        SUT.monitorDeviceChanges()
        verify(exactly = 1) { SUT.cancelCidJob() }
    }

    @Test
    fun `verify the inActive monitorCidJob is not getting cancelled`() {
        SUT.monitorDeviceChanges()
        verify(exactly = 0) { SUT.cancelCidJob() }
    }

    @Test
    fun `verify the Active monitorVehicleIdJob is getting cancelled`() {
        SUT.monitorVehicleIdJob = Job()
        SUT.monitorDeviceChanges()
        verify(exactly = 1) { SUT.cancelVehicleIdJob() }
    }

    @Test
    fun `verify the inActive monitorVehicleIdJob is not getting cancelled`() {
        SUT.monitorDeviceChanges()
        verify(exactly = 0) { SUT.cancelVehicleIdJob() }
    }

    @Test
    fun `verify the Active monitorObcIdJob is getting cancelled`() {
        SUT.monitorObcIdJob = Job()
        SUT.monitorDeviceChanges()
        verify(exactly = 1) { SUT.cancelObcIdJob() }
    }

    @Test
    fun `verify the inActive monitorObcIdJob is not getting cancelled`() {
        SUT.monitorDeviceChanges()
        verify(exactly = 0) { SUT.cancelObcIdJob() }
    }

    @Test
    fun `test listenToCopilotEvents`() = runTest {
        every { AppLauncherCommunicator.sendMessage(any(), any(), any()) } just runs
        SUT.sendGeofenceServiceIntentToLauncher()
        coVerify { AppLauncherCommunicator.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `test cancelCidJob`() {
        SUT.monitorCidJob = Job()
        SUT.cancelCidJob()
        assertTrue(SUT.monitorCidJob?.isCancelled == true)
    }

    @Test
    fun `test cancelVehicleIdJob`() {
        SUT.monitorVehicleIdJob = Job()
        SUT.cancelVehicleIdJob()
        assertTrue(SUT.monitorVehicleIdJob?.isCancelled == true)
    }

    @Test
    fun `test cancelObcIdJob`() {
        SUT.monitorObcIdJob = Job()
        SUT.cancelObcIdJob()
        assertTrue(SUT.monitorObcIdJob?.isCancelled == true)
    }

    @Test
    fun `verify if each of the enqueued notifications inside of notification list is getting processed`() =
        runTest {
            coEvery { notificationQueueUseCase.getEnqueuedNotificationsList(DataStoreManager.NOTIFICATION_LIST) } returns listOf(
                FcmData(), FcmData(), FcmData()
            )

            SUT.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection()

            coVerify(exactly = 3) {
                workflowAppNotificationUseCase.processReceivedFCMMessage(
                    receivedFcmData = any(),
                    coroutineScope = any(),
                    dispatcher = any(),
                    cacheStopAndActionData = any()
                )
            }
            coVerify(exactly = 3) { workflowAppNotificationUseCase.buildFCMDataState(any()) }
        }

    @Test
    fun `verify processReceivedFCMMessage is called if the notification list is empty`() =
        runTest {
            coEvery { notificationQueueUseCase.getEnqueuedNotificationsList(DataStoreManager.NOTIFICATION_LIST) } returns listOf()

            SUT.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection()

            coVerify(exactly = 0) {
                workflowAppNotificationUseCase.processReceivedFCMMessage(
                    receivedFcmData = any(),
                    coroutineScope = any(),
                    dispatcher = any(),
                    cacheStopAndActionData = any()
                )
            }
            coVerify(exactly = 0) {
                workflowAppNotificationUseCase.sendWorkflowEventToThirdPartyApps(
                    receivedFcmData = any(),
                    cacheNewDispatchData = any()
                )
            }
        }

    @Test
    fun `verify handleTripPanelConnectionStatus() triggers handleLibraryConnectionState() with HostAppState SERVICE_CONNECTED`() = runTest{
        coEvery { serviceManager.handleLibraryConnectionState(any()) } just runs

        SUT.handleTripPanelConnectionStatus(HostAppState.SERVICE_CONNECTED,testScope)

        coVerify(exactly = 1) { serviceManager.handleLibraryConnectionState(HostAppState.SERVICE_CONNECTED) }
    }

    @Test
    fun `verify handleTripPanelConnectionStatus() triggers handleLibraryConnectionState() with HostAppState SERVICE_DISCONNECTED`() = runTest{
        coEvery { serviceManager.handleLibraryConnectionState(any()) } just runs

        SUT.handleTripPanelConnectionStatus(HostAppState.SERVICE_DISCONNECTED,testScope)

        coVerify(exactly = 1) { serviceManager.handleLibraryConnectionState(HostAppState.SERVICE_DISCONNECTED) }
    }

    @Test
    fun `verify handleTripPanelConnectionStatus() triggers handleLibraryConnectionState() with newState from launcher`() = runTest{
        coEvery { serviceManager.handleLibraryConnectionState(any()) } just runs

        SUT.handleTripPanelConnectionStatus(HostAppState.SERVICE_DISCONNECTED,testScope)
        SUT.handleTripPanelConnectionStatus(HostAppState.READY_TO_PROCESS,testScope)
        SUT.handleTripPanelConnectionStatus(HostAppState.SERVICE_CONNECTED,testScope)
        SUT.handleTripPanelConnectionStatus(HostAppState.NOT_READY_TO_PROCESS,testScope)
        SUT.handleTripPanelConnectionStatus(HostAppState.SERVICE_BINDING_DEAD,testScope)

        coVerify(exactly = 5) { serviceManager.handleLibraryConnectionState(any()) }
    }

    @After
    fun after() {
        unmockkAll()
        stopKoin()
    }
}
