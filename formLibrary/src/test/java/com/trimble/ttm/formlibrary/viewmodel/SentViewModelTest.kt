package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.TestDispatcherProvider
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithMainUnconfinedDispatcher
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.usecases.FirebaseCurrentUserTokenFetchUseCase
import com.trimble.ttm.formlibrary.usecases.SentUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.SENT_SCREEN_TIME
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.test.KoinTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentViewModelTest : KoinTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var application: Application

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    private lateinit var formDataStoreManager: FormDataStoreManager
    @RelaxedMockK
    private lateinit var context: Context

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var sentViewModel: SentViewModel
    @MockK
    private lateinit var sentUseCase: SentUseCase

    @RelaxedMockK
    private lateinit var errorDataObserver: Observer<String>

    @RelaxedMockK
    private lateinit var messagesObserver: Observer<MutableSet<MessageFormResponse>>
    @MockK
    private lateinit var firebaseCurrentUserTokenFetchUseCase: FirebaseCurrentUserTokenFetchUseCase

    private lateinit var dispatcherProvider: TestDispatcherProvider

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        dispatcherProvider = TestDispatcherProvider()
        every { sentUseCase.didLastMessageReached() } returns flow { emit(true) }
        coEvery { sentUseCase.getMessageOfVehicle(any(), any(), any()) } just runs
        sentViewModel = SentViewModel(
            application,
            sentUseCase,
            firebaseCurrentUserTokenFetchUseCase,
            appModuleCommunicator,
            dispatcherProvider
        )
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123442"
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        mockkStatic(android.util.Log::class)
    }

    @Test
    fun `verify message delete calls`(): Unit = runTest {    //NOSONAR
        val sentUseCase: SentUseCase = mockk()
        every { sentUseCase.didLastMessageReached() } returns flow { emit(true) }
        coEvery {
            sentUseCase.deleteAllMessage(
                String(),
                String(),
                String(),
                String()
            )
        } returns CollectionDeleteResponse(true, "")
        coEvery {
            sentUseCase.deleteMessage(
                String(),
                String(),
                0L
            )
        } returns Unit
        val messageFormResponseViewModel = SentViewModel(
            application, sentUseCase, mockk(),
            mockk(), dispatcherProvider
        )
        coVerify(exactly = 0) {
            messageFormResponseViewModel.deleteMessage(
                "",
                "",
                0L
            )
        }
        coVerify(exactly = 0) {
            messageFormResponseViewModel.deleteAllMessage(
                "",
                "",
                UnconfinedTestDispatcher()
            )
        }
    }

    @Test
    fun `verify get messages - cid empty`() = runTest {
        verifyGetMessagesTestHelper("", "test")
    }

    @Test
    fun `verify get messages - vehicle number empty`() = runTest {
        verifyGetMessagesTestHelper("123", "")
    }

    private fun verifyGetMessagesTestHelper(cid: String, vehicleNumber: String) = runTest {
        coEvery { appModuleCommunicator.doGetCid() } returns cid
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns vehicleNumber
        every { application.getString(R.string.err_loading_messages) } returns "Failed to load Message"
        sentViewModel.errorData.observeForever(errorDataObserver)

        sentViewModel.getMessages(true)
        advanceTimeBy(TEST_DELAY_OR_TIMEOUT)
        assertTrue(sentViewModel.errorData.value == "Failed to load Message")

        verify(exactly = 1) {
            application.getString(R.string.err_loading_messages)
            errorDataObserver.onChanged("Failed to load Message")
        }
    }

    @Test
    fun `verify get messages`() = runTest {
        val formResponseList = mutableSetOf<MessageFormResponse>()
        formResponseList.add(MessageFormResponse("Test"))
        formResponseList.add(MessageFormResponse("Test1"))

        coEvery { appModuleCommunicator.doGetCid() } returns "1223"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "12323"
        sentViewModel.messages.observeForever(messagesObserver)
        coEvery { sentUseCase.getMessageListFlow() } returns flow { emit(formResponseList) }
        coEvery { sentUseCase.getMessageOfVehicle(any(), any(), any()) } just runs
        sentViewModel.getMessages(true)
        assertTrue(sentViewModel.messages.value?.count() == 2)
        assertEquals(sentViewModel.messages.value, formResponseList)

    }

    @Test
    fun `verify get messages - empty set`() = runTest {
        val formResposeList = mutableSetOf<MessageFormResponse>()

        coEvery { sentUseCase.getMessageOfVehicle(any(), any(), any()) } just runs
        coEvery { appModuleCommunicator.doGetCid() } returns "1223"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "12323"
        sentViewModel.messages.observeForever(messagesObserver)
        coEvery { sentUseCase.getMessageListFlow() } returns flow { emit(formResposeList) }
        sentViewModel.getMessages(true)
        assertTrue(sentViewModel.messages.value!!.isEmpty())
        assertEquals(sentViewModel.messages.value, formResposeList)

        coVerify {
            sentUseCase.getMessageOfVehicle(any(), any(), any())
        }
    }

    @Test
    fun `verify get messages - throws exception`() = runTest {
        every { application.getString(R.string.unable_to_fetch_messages) } returns "Failed to fetch messages"
        coEvery { appModuleCommunicator.doGetCid() } returns "1223"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "12323"
        sentViewModel.errorData.observeForever(errorDataObserver)
        sentViewModel.messages.observeForever(messagesObserver)
        coEvery { sentUseCase.getMessageListFlow() } returns flow { throw Exception() }
        sentViewModel.getMessages(true)
        verify(timeout = TEST_DELAY_OR_TIMEOUT) {
            errorDataObserver.onChanged("Failed to fetch messages")
        }
        verify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            messagesObserver.onChanged(any())
        }
    }

    @Test
    fun `verify get messages - last set of data`() = runTest {
        val formResponseList = mutableSetOf<MessageFormResponse>()
        formResponseList.add(MessageFormResponse("Test"))
        formResponseList.add(MessageFormResponse("Test1"))

        coEvery { appModuleCommunicator.doGetCid() } returns "1223"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "12323"
        sentViewModel.messages.observeForever(messagesObserver)
        sentViewModel.errorData.observeForever(errorDataObserver)
        every { sentUseCase.getMessageListFlow() } returns flow { emit(mutableSetOf()) }
        coEvery { sentUseCase.getMessageOfVehicle(any(), any(), any()) } just runs
        sentViewModel.postMessagesForOnlyForTest(formResponseList)
        sentViewModel.getMessages(true)
        verify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            errorDataObserver.onChanged("EndReached")
        }
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is not empty`(){
        every { sentUseCase.logScreenViewEvent(any()) } just runs
        sentViewModel.logScreenViewEvent(SENT_SCREEN_TIME)
        verify(exactly = 1) {
            sentUseCase.logScreenViewEvent(any())
        }
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is empty`(){
        every { sentUseCase.logScreenViewEvent(any()) } just runs
        sentViewModel.logScreenViewEvent(EMPTY_STRING)
        verify(exactly = 0) {
            sentUseCase.logScreenViewEvent(any())
        }
    }

    @After
    fun after() {
        unmockkAll()
    }

}