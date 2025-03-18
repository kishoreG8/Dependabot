package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Context
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithMainUnconfinedDispatcher
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.repo.DraftRepo
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.SentUseCase
import com.trimble.ttm.formlibrary.utils.DRAFT_SCREEN_TIME
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.formlibrary.utils.callOnCleared
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class DraftViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @RelaxedMockK
    private lateinit var application: Application

    private lateinit var formDataStoreManager: FormDataStoreManager

    @RelaxedMockK
    private lateinit var context: Context

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var draftViewModel: DraftViewModel

    @RelaxedMockK
    private lateinit var draftUseCase: DraftUseCase

    @RelaxedMockK
    private lateinit var draftRepo: DraftRepo

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator


    @Before
    fun setup() {
        MockKAnnotations.init(this)
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetObcId() } returns "123442"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "INST751"
        mockkObject(Log)
        draftUseCase = spyk(DraftUseCase(draftRepo, appModuleCommunicator, mockk(), mockk()))
        every { draftUseCase.didLastMessageReached() } returns flow { emit(false) }
        coEvery { draftUseCase.isComposeFormFeatureFlagEnabled()} returns false
        draftViewModel = DraftViewModel(application, draftUseCase, mockk(), appModuleCommunicator, coroutinesTestRule.testDispatcherProvider)
    }

    @Test
    fun `verify get messages`() = runTest { //NOSONAR
        draftViewModel.getMessages(true)
    }

    @Test
    fun `verify onClear call`() =    //NOSONAR
        draftViewModel.callOnCleared()

    @ExperimentalCoroutinesApi
    @Test
    fun `verify message delete calls`(): Unit = runTest {    //NOSONAR
        val sentUseCase: SentUseCase = mockk()
        every { sentUseCase.didLastMessageReached() } returns flow { emit(true) }
        val messageFormResponseViewModel = SentViewModel(
            application, sentUseCase, mockk(),
            mockk(), coroutinesTestRule.testDispatcherProvider
        )
        coVerify(exactly = 0) { messageFormResponseViewModel.deleteMessage("", "", 0L) }
        coVerify(exactly = 0) {
            messageFormResponseViewModel.deleteAllMessage(
                "",
                "",
                UnconfinedTestDispatcher()
            )
        }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `verify isDraftedFormOfTheActiveDispatch for drafted form of active trip`(): Unit =
        runTest {
            val dispatchFormSavePath = "PfmFormResponses/10119/INST751/12345/2/0"
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "12345"
            assertTrue(
                draftViewModel.isDraftedFormOfTheActiveDispatch(dispatchFormSavePath).await()
            )
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `verify isDraftedFormOfTheActiveDispatch for drafted form of inactive trip`(): Unit =
        runTest {
            val dispatchFormSavePath = "FormResponses/10119/INST751/12342/2"
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "12345"
            assertFalse(
                draftViewModel.isDraftedFormOfTheActiveDispatch(dispatchFormSavePath).await()
            )
        }

    @ExperimentalCoroutinesApi
    @Test

    fun `verify isDraftedFormOfTheActiveDispatch for drafted form which has invalid dispatchFormSavePath`(): Unit =
        runTest {
            val dispatchFormSavePath = "FormResponses/10119/INST751/12342"
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns "12345"
            every { Log.w(any(), any()) } returns Unit
            assertFalse(
                draftViewModel.isDraftedFormOfTheActiveDispatch(dispatchFormSavePath).await()
            )
            coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
                Log.w(any(), any())
            }
        }

    @ExperimentalCoroutinesApi
    @Test

    fun `verify isDraftedFormOfTheActiveDispatch for drafted form if there is no active dispatch`(): Unit =
        runTest {
            val dispatchFormSavePath = "FormResponses/10119/INST751/12342/2"
            coEvery { appModuleCommunicator.getCurrentWorkFlowId(any()) } returns EMPTY_STRING
            assertFalse(
                draftViewModel.isDraftedFormOfTheActiveDispatch(dispatchFormSavePath).await()
            )
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `verify shouldSendDraftDataBackToDispatchStopForm when stopsid is greater than 0`() =
        runTest {
            val messageFormResponse =
                MessageFormResponse(uncompletedDispatchFormPath = DispatchFormPath(stopId = 2))
            assertTrue {
                draftViewModel.shouldSendDraftDataBackToDispatchStopForm(
                    messageFormResponse = messageFormResponse
                )
            }
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `verify shouldSendDraftDataBackToDispatchStopForm when stopsid is equal to 0`() = runTest {
        val messageFormResponse =
            MessageFormResponse(uncompletedDispatchFormPath = DispatchFormPath(stopId = 0))
        assertTrue { draftViewModel.shouldSendDraftDataBackToDispatchStopForm(messageFormResponse = messageFormResponse) }
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `verify shouldSendDraftDataBackToDispatchStopForm when stopsid is less than 0`() = runTest {
        val messageFormResponse =
            MessageFormResponse(uncompletedDispatchFormPath = DispatchFormPath(stopId = -1))
        assertFalse { draftViewModel.shouldSendDraftDataBackToDispatchStopForm(messageFormResponse = messageFormResponse) }
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is not empty`() = runTest {
        every { draftUseCase.logScreenViewEvent(any()) } just runs
        draftViewModel.logScreenViewEvent(DRAFT_SCREEN_TIME)
        verify(exactly = 1) {
            draftUseCase.logScreenViewEvent(any())
        }
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is empty`() = runTest {
        every { draftUseCase.logScreenViewEvent(any()) } just runs
        draftViewModel.logScreenViewEvent(EMPTY_STRING)
        verify(exactly = 0) {
            draftUseCase.logScreenViewEvent(any())
        }
    }

    @After
    fun after() {
        unmockkAll()
    }

}
