package com.trimble.ttm.routemanifest.usecases

import android.content.Context
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.BackboneUseCase
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.RouteData
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.routemanifest.utils.Utils
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
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
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StopDetentionWarningUseCaseTest: KoinTest {

    @RelaxedMockK
    private lateinit var stopDetentionWarningUseCase: StopDetentionWarningUseCase

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    @RelaxedMockK
    private lateinit var application: WorkflowApplication

    private lateinit var dataStoreManager: DataStoreManager
    @RelaxedMockK
    private lateinit var context: Context
    @RelaxedMockK
    private lateinit var backboneUseCase: BackboneUseCase

    @RelaxedMockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @RelaxedMockK
    private lateinit var fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase

    private lateinit var thisStopDetail: StopDetail

    private val testScope = TestScope()

    private var modulesRequiredForTest = module {
        single { appModuleCommunicator }
        single { stopDetentionWarningUseCase }
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkObject(FormUtils)
        mockkObject(Utils)
        every { application.applicationContext } returns application
        every { context.packageName } returns "com.trimble.ttm.formsandworkflow"
        dataStoreManager = spyk(DataStoreManager(context))
        startKoin {
            androidContext(application)
            loadKoinModules(modulesRequiredForTest)
        }
        stopDetentionWarningUseCase = spyk(
            StopDetentionWarningUseCase(
                applicationInstance = application,
                appModuleCommunicator = appModuleCommunicator,
                dataStoreManager = dataStoreManager,
                DefaultDispatcherProvider(),
                fetchDispatchStopsAndActionsUseCase = fetchDispatchStopsAndActionsUseCase,
                backboneUseCase = backboneUseCase
            )
        )
        every { context.filesDir } returns temporaryFolder.newFolder()
        mockkObject(ApplicationContextProvider)
        every { ApplicationContextProvider.getApplicationContext() } returns application.applicationContext
        coEvery {
            backboneUseCase.getCurrentLocation()
        } returns Pair(12.5, 30.0)
        coEvery {
            dataStoreManager.setValue(any(), any<String>())
        } returns Unit
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
    }

    @Test
    fun `calculateDetentionTime() returns the same detention time when method is 1`() = runTest {
        val expectedDetentionTime = 60000L
        val routeData = RouteData(Date(100000L))
        coEvery { Utils.getRouteData(any(), any()) } returns routeData
        val dtwMinutes = 1
        val dtwMethod = 1
        val minutesMultiplier = 60 * 1000

        val result = stopDetentionWarningUseCase.calculateDetentionTime(dtwMinutes, dtwMethod, -1, minutesMultiplier)

        assertEquals(expectedDetentionTime, result)
    }

    @Test
    fun `calculateDetentionTime() returns the new detention time when the driver arrives earlier and method is 2`() = runTest {
        val expectedDetentionTime = 110000L
        val routeData = RouteData(Date(100000L))
        val comparingDate = Date(50000L)
        coEvery { Utils.getRouteData(any(), any()) } returns routeData
        every { stopDetentionWarningUseCase.getCurrentDate() } returns comparingDate
        val dtwMinutes = 1
        val dtwMethod = 2
        val minutesMultiplier = 60 * 1000

        val result = stopDetentionWarningUseCase.calculateDetentionTime(dtwMinutes, dtwMethod, -1, minutesMultiplier)

        assertEquals(expectedDetentionTime, result)
    }

    @Test
    fun `calculateDetentionTime() returns the same detention time when the driver arrives late and method is 2`() = runTest {
        val expectedDetentionTime = 60000L
        val routeData = RouteData(Date(50000L))
        val comparingDate = Date(100000L)
        coEvery { Utils.getRouteData(any(), any()) } returns routeData
        every { stopDetentionWarningUseCase.getCurrentDate() } returns comparingDate
        val dtwMinutes = 1
        val dtwMethod = 2
        val minutesMultiplier = 60 * 1000

        val result = stopDetentionWarningUseCase.calculateDetentionTime(dtwMinutes, dtwMethod, -1, minutesMultiplier)

        assertEquals(expectedDetentionTime, result)
    }


    @Test
    fun `startDetentionWarningTimer() doesn't call startDetentionWarningActivity() before timer runs out when inside the radius`() {
        coEvery { stopDetentionWarningUseCase.startDetentionWarningActivity(any(), any()) } returns Unit
        val stop = Stop(departRadius = 200, latitude = 40.555, longitude = 32.344)
        coEvery { stopDetentionWarningUseCase.getDistanceInFeet(stop) } returns 100.3
        val currentStop = StopDetail(dtwMethod = 1, dtwMins = 1)
        coEvery { stopDetentionWarningUseCase.calculateDetentionTime(any(), any(), any(), any()) } returns 1000L
        stopDetentionWarningUseCase.startDetentionWarningTimer(currentStop, EMPTY_STRING)

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT, exactly = 0) { stopDetentionWarningUseCase.startDetentionWarningActivity(any(), any()) }
    }

    @Test
    fun `startDetentionWarningTimer() call startDetentionWarningActivity() after timer runs out when inside the radius`() = runTest {
        coEvery { stopDetentionWarningUseCase.startDetentionWarningActivity(any(), any()) } returns Unit
        coEvery { stopDetentionWarningUseCase.getDistanceInFeet(any()) } returns 100.0
        val currentStop = StopDetail(dtwMethod = 1, dtwMins = 1)
        coEvery { stopDetentionWarningUseCase.calculateDetentionTime(any(), any(), any(), any()) } returns 500L
        stopDetentionWarningUseCase.startDetentionWarningTimer(currentStop, EMPTY_STRING)

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT, atLeast = 1) {
            stopDetentionWarningUseCase.startDetentionWarningActivity(any(), any())
        }
    }

    @Test
    fun `startDetentionWarningTimer() doesn't call startDetentionWarningActivity() after timer runs out when outside the radius`() {
        coEvery { stopDetentionWarningUseCase.startDetentionWarningActivity(any(), any()) } returns Unit
        coEvery { stopDetentionWarningUseCase.getDistanceInFeet(any()) } returns 1000.0
        val currentStop = StopDetail(dtwMethod = 1, dtwMins = 1)
        coEvery { stopDetentionWarningUseCase.calculateDetentionTime(any(), any(), any(), any()) } returns 500L
        stopDetentionWarningUseCase.startDetentionWarningTimer(currentStop, EMPTY_STRING)

        coVerify(timeout = TEST_DELAY_OR_TIMEOUT, exactly = 0) {
            stopDetentionWarningUseCase.startDetentionWarningActivity(any(), any())
        }
    }

    @Test
    fun `canDisplayDetentionWarning() should be false if dtwMethod is not set`() = runTest { //NOSONAR
        thisStopDetail =
            StopDetail(name = "Test")
        assertEquals(
            false,
            stopDetentionWarningUseCase.canDisplayDetentionWarning(thisStopDetail)
        )
    }

    @Test
    fun `canDisplayDetentionWarning() should be true if dtwMethod is set to 1`() = runTest { //NOSONAR
        thisStopDetail =
            StopDetail(name = "Test", dtwMethod = 1)
        assertEquals(
            true,
            stopDetentionWarningUseCase.canDisplayDetentionWarning(thisStopDetail)
        )
    }

    @Test
    fun `canDisplayDetentionWarning() should be true if dtwMethod is set to 2`() { //NOSONAR
        thisStopDetail =
            StopDetail(name = "Test", dtwMethod = 2)
        assertEquals(
            true,
            stopDetentionWarningUseCase.canDisplayDetentionWarning(thisStopDetail)
        )
    }

    @Test
    fun `verify canDisplayDetentionWarning() returns false when currentStop is null`() {
        assertFalse {  stopDetentionWarningUseCase.canDisplayDetentionWarning(null) }
    }

    @Test
    fun `verify checkForDisplayingDetentionWarningAndStartDetentionWarningTimer() does not start detention warning timer when canDisplayDetentionWarning() returns false`() = runTest {
        val messageId = 1
        val currentStop = StopDetail()
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopData(any()) } returns currentStop
        every { stopDetentionWarningUseCase.canDisplayDetentionWarning(currentStop) } returns false

        stopDetentionWarningUseCase.checkForDisplayingDetentionWarningAndStartDetentionWarningTimer(messageId)
        verify(exactly = 0) { stopDetentionWarningUseCase.startDetentionWarningTimer(currentStop, any()) }
    }

    @Test
    fun `verify checkForDisplayingDetentionWarningAndStartDetentionWarningTimer() is not called with invalid messageId`() = runTest {
        val messageId = -1
        val currentStop = StopDetail()
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopData(messageId) } returns currentStop

        stopDetentionWarningUseCase.checkForDisplayingDetentionWarningAndStartDetentionWarningTimer(messageId)

        verify(exactly = 0){stopDetentionWarningUseCase.startDetentionWarningTimer(any(), any())}
    }

    @Test
    fun `verify checkForDisplayingDetentionWarningAndStartDetentionWarningTimer() starts detention warning timer when canDisplayDetentionWarning() returns true`() = runTest {
        val messageId = 1
        val currentStop = StopDetail()
        coEvery { fetchDispatchStopsAndActionsUseCase.getStopData(messageId) } returns currentStop
        every { stopDetentionWarningUseCase.canDisplayDetentionWarning(any()) } returns true

        stopDetentionWarningUseCase.checkForDisplayingDetentionWarningAndStartDetentionWarningTimer(messageId)

        verify(exactly = 1) { stopDetentionWarningUseCase.startDetentionWarningTimer(any(), any()) }
    }

    @After
    fun after() {
        unloadKoinModules(modulesRequiredForTest)
        stopKoin()
        unmockkObject(FormUtils)
        unmockkObject(Utils)
        unmockkAll()
    }

}