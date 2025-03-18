package com.trimble.ttm.formlibrary.viewmodel

import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.model.HotKeys
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.FormLibraryUseCase
import com.trimble.ttm.formlibrary.usecases.InboxUseCase
import com.trimble.ttm.formlibrary.usecases.SentUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORMS_SHORTCUT_USE_COUNT
import com.trimble.ttm.formlibrary.utils.TestDelayProvider
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MessagingViewModelTest {

    private lateinit var messagingViewModel: MessagingViewModel

    @RelaxedMockK
    private lateinit var inboxUseCase: InboxUseCase
    @RelaxedMockK
    private lateinit var sentUseCase: SentUseCase
    @RelaxedMockK
    private lateinit var authenticateUseCase: AuthenticateUseCase
    @RelaxedMockK
    private lateinit var edvirFormUseCase: EDVIRFormUseCase
    @RelaxedMockK
    private lateinit var formLibraryUseCase: FormLibraryUseCase
    @RelaxedMockK
    private lateinit var hotKeysObserver : Observer<Boolean>

    @RelaxedMockK
    private lateinit var intent: Intent

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    @RelaxedMockK
    private lateinit var testCoroutineDispatcherProvider: TestDispatcherProvider

    @RelaxedMockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
        coEvery { authenticateUseCase.getAppModuleCommunicator() } returns appModuleCommunicator
        coEvery { edvirFormUseCase.setCrashReportIdentifier() } returns Unit
        messagingViewModel = MessagingViewModel(mockk(), sentUseCase = sentUseCase, draftUseCase = mockk(), inboxUseCase = inboxUseCase, trashUseCase = mockk(), authenticateUseCase = authenticateUseCase, edvirFormUseCase = edvirFormUseCase, formLibraryUseCase = formLibraryUseCase, formDataStoreManager = mockk(), appModuleCommunicator = appModuleCommunicator)
    }

    @Test
    fun `verify livedata of changeNetworkAvailabilityStatus for active internet connectivity`() =
        // NOSONAR
        messagingViewModel.changeNetworkAvailabilityStatus(true).also {
            assertTrue(messagingViewModel.isNetworkAvailable.value ?: false)
        }

    @Test
    fun `verify livedata of changeNetworkAvailabilityStatus for inactive internet connectivity`() =
        // NOSONAR
        messagingViewModel.changeNetworkAvailabilityStatus(false).also {
            assertFalse(messagingViewModel.isNetworkAvailable.value ?: false)
        }

    @Test
    fun `verify livetada of setShouldFinishFragment for new arrived HPN message`() =
        messagingViewModel.setShouldFinishFragment(true).also {
            assertTrue(messagingViewModel.shouldFinishFragment.value ?: false)
        }

    @Test
    fun `verify livetada of setShouldFinishFragment for inbox opening outside HPN`() =
        messagingViewModel.setShouldFinishFragment(false).also {
            assertFalse(messagingViewModel.shouldFinishFragment.value ?: false)
        }

    @Test
    fun `verify livetada of setCurrentTabPosition for inbox opening outside HPN`() =
        messagingViewModel.setCurrentTabPosition(4).also {
            assertTrue(messagingViewModel.tabPosition.value == 4)
        }

    @Test
    fun `verify livetada of setShouldGoToListStart for inbox opening outside HPN`() =
        messagingViewModel.setShouldGoToListStart(false).also {
            assertFalse(messagingViewModel.shouldGoToListStart.value ?: false)
        }

    @Test
    fun `verify has active dispatch`() = runTest {
        coEvery { appModuleCommunicator.hasActiveDispatch(any(), any()) } returns true
        assertTrue(messagingViewModel.hasActiveDispatch())

        coVerify {
            appModuleCommunicator.hasActiveDispatch(any(), any())
        }
    }

    @Test
    fun `check when hasOnlyOneTripOnList is called hasOnlyOneDispatchOnList is called also`() = runTest {
        coEvery {
            appModuleCommunicator.hasOnlyOneDispatchOnList()
        } returns true
        messagingViewModel.hasOnlyOneTripOnList().collect()
        coVerify {
            appModuleCommunicator.hasOnlyOneDispatchOnList()
        }
    }

    @Test
    fun `check when restoreSelectedDispatch is called restoreSelectedDispatch is called also`() {
        val callback: () -> Unit = mockk()
        val callbackFailedRestore: () -> Unit = mockk()
        coEvery {
            appModuleCommunicator.getCurrentWorkFlowId(any())
        } returns "1"
        coEvery {
            appModuleCommunicator.restoreSelectedDispatch()
        } returns Unit
        coEvery {
            callback()
        } returns Unit
        messagingViewModel.restoreSelectedDispatch(
            TestDelayProvider(),
            callback,
            callbackFailedRestore
        )
        coVerify(Ordering.ORDERED) {
            appModuleCommunicator.restoreSelectedDispatch()
            callback()
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is not null and event name is not empty`(){
        every { inboxUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        messagingViewModel.recordShortCutIconClickEvent(FORMS_SHORTCUT_USE_COUNT,intent)
        verify(exactly = 1) {
            inboxUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is null and event name is not empty`(){
        every { inboxUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        messagingViewModel.recordShortCutIconClickEvent(FORMS_SHORTCUT_USE_COUNT,null)
        verify(exactly = 0) {
            inboxUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is not null and event name empty`(){
        every { inboxUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        messagingViewModel.recordShortCutIconClickEvent(EMPTY_STRING,intent)
        verify(exactly = 0) {
            inboxUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }

    @Test
    fun `check recordShortCutIconClickEvent gets called when intent is null and event name empty`(){
        every { inboxUseCase.recordShortCutIconClickEvent(any(),any()) } just runs
        messagingViewModel.recordShortCutIconClickEvent(EMPTY_STRING,null)
        verify(exactly = 0) {
            inboxUseCase.recordShortCutIconClickEvent(any(),any())
        }
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is not empty and the hotkeys count is greater than 0`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { formLibraryUseCase.getHotKeysWithoutDescription(any(), any()) } returns flow { emit(
            mutableSetOf(HotKeys(hkId = 1), HotKeys(hkId = 2))
        ) }
        messagingViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        messagingViewModel.canShowHotKeysMenu()
        kotlin.test.assertTrue(messagingViewModel.isHotKeysAvailable.value!!)
        messagingViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is not empty and the hotkeys count is 0`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns "1234"
        coEvery { formLibraryUseCase.getHotKeysWithoutDescription(any(), any()) } returns flow { emit(
            mutableSetOf()
        ) }
        messagingViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        messagingViewModel.canShowHotKeysMenu()
        kotlin.test.assertFalse(messagingViewModel.isHotKeysAvailable.value!!)
        messagingViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
    }

    @Test
    fun `verify canShowHotKeysMenu when obcId is empty`() = runTest {
        coEvery { appModuleCommunicator.doGetObcId() } returns EMPTY_STRING
        messagingViewModel.isHotKeysAvailable.observeForever(hotKeysObserver)
        messagingViewModel.canShowHotKeysMenu()
        assertFalse(messagingViewModel.isHotKeysAvailable.value!!)
        messagingViewModel.isHotKeysAvailable.removeObserver(hotKeysObserver)
        coVerify(exactly = 0) {
            formLibraryUseCase.getHotKeysWithoutDescription(any(), any())
        }
    }

    @Test
    fun `changeAuthenticationCompletedStatus updates LiveData value`() {
        val observer = mockk<Observer<Boolean>>(relaxed = true)
        messagingViewModel.isAuthenticationCompleted.observeForever(observer)

        messagingViewModel.changeAuthenticationCompletedStatus(true)

        verify { observer.onChanged(true) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

}
