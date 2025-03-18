package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithMainUnconfinedDispatcher
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.repo.TrashRepo
import com.trimble.ttm.formlibrary.repo.TrashRepoImpl
import com.trimble.ttm.formlibrary.usecases.FirebaseCurrentUserTokenFetchUseCase
import com.trimble.ttm.formlibrary.usecases.TrashUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.TRASH_SCREEN_TIME
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import org.koin.test.inject

class TrashViewModelTest: KoinTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private var application: Application = mockk()

    @RelaxedMockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @RelaxedMockK
    private lateinit var firebaseCurrentUserTokenFetchUseCase: FirebaseCurrentUserTokenFetchUseCase

    private lateinit var formDataStoreManager: FormDataStoreManager
    private lateinit var context: Context
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val trashViewModel: TrashViewModel by inject()
    private lateinit var firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder
    private val trashUseCase : TrashUseCase by inject()

    private var koinModulesRequiredForTest = module {
        single<TrashRepo> { TrashRepoImpl(mockk()) }
        factory { TrashUseCase(get(), firebaseAnalyticEventRecorder) }
        single { formDataStoreManager }
        single<DispatcherProvider> { DefaultDispatcherProvider() }
        single { TrashViewModel(application = application, trashUseCase, appModuleCommunicator, get(), firebaseCurrentUserTokenFetchUseCase) }
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        context = mockk(relaxed = true)
        appModuleCommunicator = mockk()
        firebaseAnalyticEventRecorder = mockk()
        firebaseCurrentUserTokenFetchUseCase = mockk()
        stopKoin()
        startKoin {
            androidContext(application)
            loadKoinModules(koinModulesRequiredForTest)
        }
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123442"
        coEvery { firebaseCurrentUserTokenFetchUseCase.getIDTokenOfCurrentUser() } returns "idToken"
        coEvery { firebaseCurrentUserTokenFetchUseCase.getAppCheckToken() } returns "appCheckToken"
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is not empty`(){
        every { trashUseCase.logScreenViewEvent(any()) } just runs
        trashViewModel.logScreenViewEvent(TRASH_SCREEN_TIME)
        verify(exactly = 1) {
            trashUseCase.logScreenViewEvent(any())
        }
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is empty`(){
        every { trashUseCase.logScreenViewEvent(any()) } just runs
        trashViewModel.logScreenViewEvent(EMPTY_STRING)
        verify(exactly = 0) {
            trashUseCase.logScreenViewEvent(any())
        }
    }

    @Test
    fun `verify deleteAllMessages() in trashViewModel calls the TrashUseCase deleteAllMessage()`()  {
        val response = CollectionDeleteResponse(true, "Test")
        coEvery { trashUseCase.deleteAllMessages(any(), any(), any(), any()) } returns response

        trashViewModel.deleteAllMessages()

        coVerify(exactly = 1) {
            trashUseCase.deleteAllMessages(any(), any(), any(), any())
        }
    }

    @Test
    fun `verify deleteSelectedMessage() in trashViewModel`() = runTest {
        val deletedMessages = mutableSetOf(Message(asn = "123"))
        trashViewModel.deletedMessages = deletedMessages
        coEvery { trashUseCase.deleteMessage(any(), any(), any(), any()) } returns Unit
        trashViewModel.deleteSelectedMessages()
        coVerify(exactly = 1) {
            trashUseCase.deleteMessage(any(), any(), any(), any())
        }
    }

    @Test
    fun `test removeDeletedItems with clearAllMessages true`() {
        val totalMessages = mutableSetOf(Message(asn = "123"), Message(asn = "124"))
        val selectedItems = mutableSetOf(Message(asn = "123"))
        trashViewModel.removeDeletedItems(selectedItems, totalMessages, true)
        assertTrue(totalMessages.isEmpty())
    }

    @Test
    fun `test removeDeletedItems with clearAllMessages false`() {
        val totalMessages = mutableSetOf(Message(asn = "123"), Message(asn = "124"))
        val selectedItems = mutableSetOf(Message(asn = "123"))

        trashViewModel.removeDeletedItems(selectedItems, totalMessages, false)

        assertEquals(1, totalMessages.size)
        assertFalse(totalMessages.contains(Message(asn = "123")))
        assertTrue(totalMessages.contains(Message(asn = "124")))
    }

    @Test
    fun `test processReceivedMessage() in viewModel with Messages`() {
        //Non empty message Set processing with selectAllCheckedTimeStamp
        var messageSet = mutableSetOf(Message(asn = "123"), Message(asn = "124"))
        trashViewModel.selectAllCheckedTimeStamp = 1730110711
        trashViewModel.processReceivedMessage(messageSet, false)
        assertEquals(messageSet, trashViewModel.messages.value)
        assertEquals(trashViewModel.messages.value?.size, trashViewModel.getTotalMessages().size)
        assertEquals(false, trashViewModel.isNewTrashMessageReceived.value)
        assertEquals(true, trashViewModel.isPaginationMessageReceived.value)

        //Non Empty messageSet with selectAllCheckedTimeStamp as 0L
        trashViewModel.selectAllCheckedTimeStamp = 0L
        trashViewModel.processReceivedMessage(messageSet, true)
        assertEquals(false, trashViewModel.isNewTrashMessageReceived.value)
        assertEquals(true, trashViewModel.isPaginationMessageReceived.value)
    }

    @Test
    fun `verify getSelectedItems`() {
        trashViewModel.setSelectedItems(mutableSetOf(Message(asn = "123")))
        assertEquals(1, trashViewModel.getSelectedItems().size)
    }

    @Test
    fun `test processReceivedMessage with empty messageSet and non-empty messages`() {
        every { application.getString(any()) } returns "No messages available"
        val nonEmptyMessages = mutableSetOf(Message(asn = "123"))
        trashViewModel.processReceivedMessage(nonEmptyMessages, true) // To set initial non-empty messages
        trashViewModel.processReceivedMessage(mutableSetOf(), true)

        assertEquals("No messages available", trashViewModel.errorData.value)
    }

    @Test
    fun `verify isNewTrashMessageReceived is set to true when messageSet contains message with rowDate greater than selectAllCheckedTimeStamp`() {
        val messageSet = mutableSetOf(Message(asn = "123"))
        messageSet.first().rowDate = 1730110888
        trashViewModel.selectAllCheckedTimeStamp = 1730110710
        trashViewModel.processReceivedMessage(messageSet, false)
        assertEquals(true, trashViewModel.isNewTrashMessageReceived.value)
    }

    @Test
    fun `verify with messageSet and messages as Empty`() {
        every { application.getString(any()) } returns "No Messages Available"
        trashViewModel.processReceivedMessage(mutableSetOf(), false)
        assertEquals("No Messages Available", trashViewModel.errorData.value)
    }

    @Test
    fun `verify with messageSet is non emtpy but old messages are empty`() {
        val messageSet = mutableSetOf(Message(asn = "123"))
        trashViewModel.processReceivedMessage(messageSet, true)
        trashViewModel.processReceivedMessage(messageSet, false)
        assertEquals(messageSet, trashViewModel.messages.value)
    }

    @Test
    fun `test sortMessagesBasedOnDeletionTime`() {
        val message1 = Message(asn = "123").apply { rowDate = 1000 }
        val message2 = Message(asn = "124").apply { rowDate = 2000 }
        val messageSet = mutableSetOf(message1, message2)

        val sortedMessages = trashViewModel.sortMessagesBasedOnDeletionTime(messageSet)

        assertEquals(2, sortedMessages.size)
        assertEquals(message2, sortedMessages.first())
        assertEquals(message1, sortedMessages.last())
    }

    @Test
    fun `verify isNewTrashMessageReceived is set correctly based on selectAllCheckedTimeStamp`() {
        val messageSet = mutableSetOf(
            Message(asn = "123").apply { rowDate = 1730110888 },
            Message(asn = "124").apply { rowDate = 1730110700 }
        )
        trashViewModel.selectAllCheckedTimeStamp = 1730110710

        trashViewModel.checkIfNewMessageReceived(messageSet)

        assertEquals(true, trashViewModel.isNewTrashMessageReceived.value)
    }

    @After
    fun after() {
        unloadKoinModules(koinModulesRequiredForTest)
        stopKoin()
        unmockkAll()
    }

}