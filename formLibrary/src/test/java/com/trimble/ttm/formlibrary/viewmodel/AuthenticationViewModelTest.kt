package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Observer
import com.trimble.ttm.commons.model.AuthenticationState
import com.trimble.ttm.commons.model.DeviceAuthResult
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.AUTH_SERVER_ERROR
import com.trimble.ttm.commons.utils.AUTH_SUCCESS
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.WORKFLOW_SHORTCUT_USE_COUNT
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.manager.workmanager.FormLibraryCacheWorker
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AuthenticationViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var eDVIRFormUseCase: EDVIRFormUseCase
    @MockK
    private lateinit var authenticateUseCase: AuthenticateUseCase
    @MockK
    private lateinit var formDataStoreManager: FormDataStoreManager
    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    @RelaxedMockK
    private lateinit var intent : Intent
    @RelaxedMockK
    private lateinit var application: Application

    private lateinit var authenticationViewModel: AuthenticationViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { authenticateUseCase.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { eDVIRFormUseCase.setCrashReportIdentifier() } just runs
        appModuleCommunicator = authenticateUseCase.getAppModuleCommunicator()
        mockkStatic(Log::class)
        mockkObject(DateUtil::class)
        mockkObject(FormLibraryCacheWorker.Companion)
        authenticationViewModel = spyk(
            AuthenticationViewModel(eDVIRFormUseCase, authenticateUseCase, formDataStoreManager, application) ,
            recordPrivateCalls = true
        )
        coEvery { formDataStoreManager.setValue(any<Preferences.Key<Any>>(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCid() } returns "rewqr"
        coEvery { appModuleCommunicator.doGetObcId() } returns "gfsdgfd"
    }

    @Test
    fun `verify cache backbone data method`() = runTest {

        authenticationViewModel.cacheBackboneData()

        coVerify {
            eDVIRFormUseCase.setCrashReportIdentifier()
        }
    }

    @Test
    fun `verify fetchAndRegisterFcmDeviceSpecificToken`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "rewqr"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "3423"
        coEvery {
            authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(
                any(),
                any(),
                any()
            )
        } just runs

        authenticationViewModel.fetchAndRegisterFcmDeviceSpecificToken()

        coVerify(exactly = 1) {
            authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `verify getEDVIRSettingsAvailabilityStatus when cid is empty`() = runTest {

        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { appModuleCommunicator.doGetObcId() } returns "123"
        authenticationViewModel.checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility()

        coVerify(exactly = 0) {
            eDVIRFormUseCase.isEDVIREnabled(any(), any())
        }
    }

    @Test
    fun `verify getEDVIRSettingsAvailabilityStatus when OBC ID is empty`() =
        runTest {
            coEvery { appModuleCommunicator.doGetCid() } returns "rewqr"
            coEvery { appModuleCommunicator.doGetObcId() } returns ""
            authenticationViewModel.checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility()

            coVerify(exactly = 0) {
                eDVIRFormUseCase.isEDVIREnabled(any(), any())
            }
        }

    @Test
    fun `verify checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility`() = runTest {
        coEvery { eDVIRFormUseCase.isEDVIREnabled(any(), any()) } returns true
        coEvery { formDataStoreManager.setValue(FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, any()) } just runs
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, any()) } returns false

        authenticationViewModel.checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility()

        coVerify(exactly = 1) {
            eDVIRFormUseCase.isEDVIREnabled(any(), any())
            formDataStoreManager.setValue(FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, any())
        }
        coVerify(exactly = 2) {
            appModuleCommunicator.doGetCid()
            appModuleCommunicator.doGetObcId()
        }
    }

    @Test
    fun `check if the event gets recorded when the intent is not null and event name is not empty`() {
        every { authenticateUseCase.recordShortcutClickEvent(any(), any(), any()) } just runs
        authenticationViewModel.recordShortcutClickEvent(
            WORKFLOW_SHORTCUT_USE_COUNT,
            "ShortcutIcon",
            intent
        )
        verify(exactly = 1) {
            authenticateUseCase.recordShortcutClickEvent(any(), any(), any())
        }
    }

    @Test
    fun `check if the event gets recorded when the intent is null and event name is not empty`() {
        every { authenticateUseCase.recordShortcutClickEvent(any(), any(), any()) } just runs
        authenticationViewModel.recordShortcutClickEvent(
            WORKFLOW_SHORTCUT_USE_COUNT,
            "ShortcutIcon",
            null
        )
        verify(exactly = 0) {
            authenticateUseCase.recordShortcutClickEvent(any(), any(), any())
        }
    }

    @Test
    fun `check if the event gets recorded when the intent is not null and event name is empty`() {
        every { authenticateUseCase.recordShortcutClickEvent(any(), any(), any()) } just runs
        authenticationViewModel.recordShortcutClickEvent(EMPTY_STRING, "ShortcutIcon", intent)
        verify(exactly = 0) {
            authenticateUseCase.recordShortcutClickEvent(any(), any(), any())
        }
    }

    @Test
    fun `check if the event gets recorded when the intent is null and event name is empty`() {
        every { authenticateUseCase.recordShortcutClickEvent(any(), any(), any()) } just runs
        authenticationViewModel.recordShortcutClickEvent(EMPTY_STRING, "ShortcutIcon", null)
        verify(exactly = 0) {
            authenticateUseCase.recordShortcutClickEvent(any(), any(), any())
        }
    }

    @Test
    fun `verify getEDVIRSettingsAvailabilityStatus execution if inspection is visible in the hamburger menu`() = runTest {
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false) } returns true

        authenticationViewModel.checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility()

        coVerify(exactly = 0) {
            eDVIRFormUseCase.isEDVIREnabled(any(), any())
        }
    }

    @Test
    fun `verify getEDVIRSettingsAvailabilityStatus execution if inspection is invisible in the hamburger menu`() = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns "rewqr"
        coEvery { appModuleCommunicator.doGetObcId() } returns "gfsdgfd"
        coEvery { eDVIRFormUseCase.isEDVIREnabled(any(), any()) } returns true
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false) } returns false

        authenticationViewModel.checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility()

        coVerify(exactly = 1) {
            eDVIRFormUseCase.isEDVIREnabled(any(), any())
        }
    }

    @Test
    fun `verify fetchAuthenticationPreRequisites execution`() = runTest {
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false) } returns true
        coEvery { appModuleCommunicator.doGetCid() } returns "rewqr"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "3423"
        coEvery { appModuleCommunicator.doGetObcId() } returns "123"
        coEvery { eDVIRFormUseCase.isEDVIREnabled(any(), any()) } returns true
        coEvery { authenticateUseCase.fetchAndRegisterFcmDeviceSpecificToken(any(), any()) } just runs
        coEvery { formDataStoreManager.setValue(any<Preferences.Key<Any>>(), any()) } just runs
        coEvery { authenticateUseCase.updateFeatureFlagCache(any()) }returns Unit
        assertEquals(Unit, authenticationViewModel.fetchAuthenticationPreRequisites().await())
    }

    @Test
    fun `verify handleAuthenticationProcess execution`() {
        coEvery {
            authenticateUseCase.handleAuthenticationProcess(any(), any(), any(), any(), any(), any())
        } just runs

        authenticationViewModel.handleAuthenticationProcess("test", {}, {}, {}, {})

        coVerifyAll {
            repeat(3) {
                authenticateUseCase.getAppModuleCommunicator()
            }
            authenticateUseCase.handleAuthenticationProcess(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `verify doAuthentication for success`() = runTest {
        val observer = mockk<Observer<AuthenticationState>>(relaxed = true)
        authenticationViewModel.authenticationState.observeForever(observer)
        coEvery { authenticateUseCase.doAuthentication(any()) } returns DeviceAuthResult(
            AUTH_SUCCESS, true)
        coEvery { appModuleCommunicator.getConsumerKey() } returns "consumerKey"
        coEvery { appModuleCommunicator.doGetCid() } returns "5688"
        coEvery { appModuleCommunicator.doGetObcId() } returns "12234"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST123"
        every { authenticationViewModel["cacheFormLibraryData"]() } returns Unit
        authenticationViewModel.doAuthentication("test")
        coVerify { observer.onChanged(AuthenticationState.FirestoreAuthenticationSuccess) }
        coVerify(exactly = 1) {
            authenticationViewModel["cacheFormLibraryData"]()
        }
        authenticationViewModel.authenticationState.removeObserver(observer)
    }

    @Test
    fun `verify doAuthentication for failure`() = runTest {
        val observer = mockk<Observer<AuthenticationState>>(relaxed = true)
        authenticationViewModel.authenticationState.observeForever(observer)
        coEvery { authenticateUseCase.doAuthentication(any()) } returns DeviceAuthResult(
            AUTH_SERVER_ERROR, false)
        coEvery { appModuleCommunicator.getConsumerKey() } returns "consumerKey"
        authenticationViewModel.doAuthentication("test")
        coVerify { observer.onChanged(AuthenticationState.Error(AUTH_SERVER_ERROR)) }
        authenticationViewModel.authenticationState.removeObserver(observer)
    }

    @Test
    fun `handleAuthenticationProcessForComposable - onAuthenticationComplete`() = runTest {
        val observer = mockk<Observer<AuthenticationState>>(relaxed = true)
        authenticationViewModel.composeAuthenticationState.observeForever(observer)
        coEvery { authenticateUseCase.handleAuthenticationProcess(any(), any(), any(), any(), any(), any()) } answers {
            thirdArg<() -> Unit>().invoke()
        }
        every { application.getString(any()) } returns "Authentication Complete"
        authenticationViewModel.handleAuthenticationProcessForComposable("testCaller")
        coVerify { observer.onChanged(AuthenticationState.FirestoreAuthenticationSuccess) }
        authenticationViewModel.composeAuthenticationState.removeObserver(observer)
    }

    @Test
    fun `handleAuthenticationProcessForComposable - doAuthentication`() = runTest {
        val observer = mockk<Observer<AuthenticationState>>(relaxed = true)
        authenticationViewModel.composeAuthenticationState.observeForever(observer)
        coEvery { authenticateUseCase.handleAuthenticationProcess(any(), any(), any(), any(), any(), any()) } answers {
            (args[3] as? () -> Unit)?.invoke()
        }
        every { application.getString(any()) } returns "Authentication is not completed. Calling doAuthentication."
        coEvery { authenticationViewModel["doAuthentication"](any<String>()) } returns Unit
        authenticationViewModel.handleAuthenticationProcessForComposable("testCaller")
        coVerify { authenticationViewModel["doAuthentication"]("testCaller") }
        authenticationViewModel.composeAuthenticationState.removeObserver(observer)
    }

    @Test
    fun `handleAuthenticationProcessForComposable - onAuthenticationFailed`() = runTest {
        val observer = mockk<Observer<AuthenticationState>>(relaxed = true)
        authenticationViewModel.composeAuthenticationState.observeForever(observer)
        coEvery { authenticateUseCase.handleAuthenticationProcess(any(), any(), any(), any(), any(), any()) } answers {
            (args[4] as? () -> Unit)?.invoke()
        }
        every { application.getString(any()) } returns "Authentication Failed"
        authenticationViewModel.handleAuthenticationProcessForComposable("testCaller")
        coVerify { observer.onChanged(AuthenticationState.Error("Authentication Failed")) }
        authenticationViewModel.composeAuthenticationState.removeObserver(observer)
    }

    @Test
    fun `handleAuthenticationProcessForComposable - onNoInternet`() = runTest {
        val observer = mockk<Observer<AuthenticationState>>(relaxed = true)
        authenticationViewModel.composeAuthenticationState.observeForever(observer)
        coEvery { authenticateUseCase.handleAuthenticationProcess(any(), any(), any(), any(), any(), any()) } answers {
            (args[5] as? () -> Unit)?.invoke()
        }
        every { application.getString(any()) } returns "No Internet"
        authenticationViewModel.handleAuthenticationProcessForComposable("testCaller")
        coVerify { observer.onChanged(AuthenticationState.Error("No Internet")) }
        authenticationViewModel.composeAuthenticationState.removeObserver(observer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

}
