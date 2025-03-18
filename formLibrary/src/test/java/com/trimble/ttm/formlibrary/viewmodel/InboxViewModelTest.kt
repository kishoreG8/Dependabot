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
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.usecases.InboxUseCase
import com.trimble.ttm.formlibrary.usecases.MessageConfirmationUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.INBOX_SCREEN_TIME
import com.trimble.ttm.formlibrary.utils.NEW_FORM_VIEW_COUNT
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import com.trimble.ttm.formlibrary.utils.callOnCleared
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboxViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @RelaxedMockK
    private lateinit var application: Application

    private lateinit var formDataStoreManager: FormDataStoreManager
    @RelaxedMockK
    private lateinit var context: Context
    @MockK
    private lateinit var inboxUseCase: InboxUseCase

    @MockK
    private lateinit var messageConfirmationUseCase: MessageConfirmationUseCase

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var inboxViewModel: InboxViewModel

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    @RelaxedMockK
    lateinit var isNewMessageObserver: Observer<Boolean>

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        coEvery { inboxUseCase.getAppModuleCommunicator() } returns appModuleCommunicator
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        every { inboxUseCase.didLastMessageReached() } returns flow { emit(false) }
        every { inboxUseCase.processReceivedMessages(any(), any()) } returns mutableSetOf()
        inboxViewModel = InboxViewModel(application, inboxUseCase, messageConfirmationUseCase, TestDispatcherProvider())
    }

    @Test
    fun `verify getMessages returns error message when customerId or truckNumber are blank`() {
        // Assign
        val expectedMessage = "Failed to load Messages"
        every { application.getString(R.string.err_loading_messages) } returns expectedMessage
        coEvery { appModuleCommunicator.doGetCid() } returns ""
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""
        coEvery { inboxUseCase.didLastMessageReached() } returns flow { emit(true) }
        // Act
        inboxViewModel.getMessages(true)

        // Assert
        assertEquals(expectedMessage, inboxViewModel.errorData.value, "Got my expected error data")
    }

    @Test
    fun `verify getMessages calls getMessageOfVehicle when not first time fetch`() {
        // Assign
        val messages = mutableSetOf<Message>().also {
            it.add(Message(userName = "user1"))
            it.add(Message(userName = "user2"))
        }
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "123442"
        coEvery {
            inboxUseCase.getMessageOfVehicle("10119", "123442", false)
        } returns flow { emit(messages) }
        coEvery { inboxUseCase.didLastMessageReached() } returns flow { emit(true) }
        coEvery { messageConfirmationUseCase.sendUnDeliveredMessageConfirmationForMessagesFetchedViaInboxScreen(messages) } just runs

        // Act
        inboxViewModel.getMessages(false)

        // Assert
        assertTrue(inboxViewModel.messages.value.isNullOrEmpty(), "Messages did not change")
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            inboxUseCase.getMessageOfVehicle("10119", "123442", false)
            inboxUseCase.processReceivedMessages(any(), any())
        }
    }


    @Test
    fun `verify onClear call`() {//NOSONAR
        inboxViewModel.callOnCleared()
    }

    @Test
    fun `check true when replyFormClass is equals to -1`() {
        assert(
            inboxViewModel.showReply("-1", "8","5")
        )
    }

    @Test
    fun `check false when replyFormClass is different to -1`() {
        assert(
            !inboxViewModel.showReply("1","8","7")
        )
    }

    @Test
    fun `check true when replyFormClass is different to -1 but formId and replyFormId are matching`() {
        assert(
            !inboxViewModel.showReply("1","8","6")
        )
    }

    @Test
    fun `verify removing only DeletedItems if clear all message is false`(){
        //Given
        val messageDeleted = mutableSetOf(Message(userName = "user1",     asn = "1"))

        val totalMessage = mutableSetOf(
            Message(userName = "user1",     asn = "1"),
            Message(userName = "user2",     asn = "2")
        )

        //Call
        inboxViewModel.removeDeletedItems(messageDeleted, totalMessage,false)

        //Assert
        assertEquals(1, totalMessage.size)
    }

    @Test
    fun `verify removing all Items if clear all message is true`(){
        //Given
        val messageDeleted = mutableSetOf(Message(userName = "user1",     asn = "1"))

        val totalMessage = mutableSetOf(
            Message(userName = "user1",     asn = "1"),
            Message(userName = "user2",     asn = "2")
        )

        //Call
        inboxViewModel.removeDeletedItems(messageDeleted, totalMessage,true)

        //Assert
        assertEquals(0, totalMessage.size)
    }
    
    @Test
    fun `verify removal of asn from total message set`(){
        //Given
        val deletedAsn="1"
        val totalMessage = mutableSetOf(
            Message(userName = "user1",     asn = "1"),
            Message(userName = "user2",     asn = "2")
        )
        coEvery { inboxUseCase.didLastMessageReached() } returns flow { emit(true) }
        coEvery { inboxUseCase.sortMessagesByAsn(any()) } returns totalMessage

        //Call
        inboxViewModel.removeDeletedMessageFromTotalMessages(deletedAsn,totalMessage)

        //Assert
        assertEquals(1, totalMessage.size)
    }


    @Test
    fun `check logScreenViewEvent gets called when screenName is not empty`(){
        every { inboxUseCase.logScreenViewEvent(any()) } just runs
        inboxViewModel.logScreenViewEvent(INBOX_SCREEN_TIME)
        verify(exactly = 1) {
            inboxUseCase.logScreenViewEvent(any())
        }
    }

    @Test
    fun `check logScreenViewEvent gets called when screenName is empty`(){
        every { inboxUseCase.logScreenViewEvent(any()) } just runs
        inboxViewModel.logScreenViewEvent(EMPTY_STRING)
        verify(exactly = 0) {
            inboxUseCase.logScreenViewEvent(any())
        }
    }


    @Test
    fun `check logNewEventWithDefaultParameters gets called when eventName is not empty`(){
        every { inboxUseCase.logNewEventWithDefaultParameters(any()) } just runs
        inboxViewModel.logNewEventWithDefaultParameters(NEW_FORM_VIEW_COUNT)
        verify(exactly = 1) {
            inboxUseCase.logNewEventWithDefaultParameters(any())
        }
    }

    @Test
    fun `check logEventWithDefaultParameters gets called when eventName is empty`(){
        every { inboxUseCase.logNewEventWithDefaultParameters(any()) } just runs
        inboxViewModel.logNewEventWithDefaultParameters(EMPTY_STRING)
        verify(exactly = 0) {
            inboxUseCase.logNewEventWithDefaultParameters(any())
        }
    }

    @Test
    fun `check if new message received during select all`() {
        val messageSet = mutableSetOf<Message>()
        for (i in 1..50) {
            val message = Message(summary = "Message $i").apply {
                timestamp = i * 1000L
            }
            messageSet.add(message)
        }
        inboxViewModel.selectAllCheckedTimeStamp = 1500L
        inboxViewModel.checkIfNewMessageReceived(messageSet)
        val isNewInboxMessageReceived = inboxViewModel.isNewInboxMessageReceived.value
        assertTrue(isNewInboxMessageReceived!!)
    }

    @Test
    fun `check if no new message received during select all`() {
        val messageSet = mutableSetOf<Message>()
        for (i in 1..50) {
            val message = Message(summary = "Message $i").apply {
                timestamp = i * 1000L
            }
            messageSet.add(message)
        }
        inboxViewModel.selectAllCheckedTimeStamp = 600000L
        inboxViewModel.checkIfNewMessageReceived(messageSet)
        val isNewInboxMessageReceived = inboxViewModel.isNewInboxMessageReceived.value
        assertFalse(isNewInboxMessageReceived!!)
    }

    @Test
    fun `check if new message received not during select all`() {
        val messageSet = mutableSetOf<Message>()
        for (i in 1..50) {
            val message = Message(summary = "Message $i").apply {
                timestamp = i * 1000L
            }
            messageSet.add(message)
        }
        inboxViewModel.isNewInboxMessageReceived.observeForever(isNewMessageObserver)
        inboxViewModel.checkIfNewMessageReceived(messageSet)
        coVerify(exactly = 0) {
            isNewMessageObserver.onChanged(any())
        }
        inboxViewModel.isNewInboxMessageReceived.removeObserver(isNewMessageObserver)
    }

    @Test
    fun `getSelectedItems returns correct set`() {
        // Arrange
        val expectedSet = mutableSetOf(Message(/* initialize Message object */))
        inboxViewModel.setSelectedItems(expectedSet)

        // Act
        val result = inboxViewModel.getSelectedItems()

        // Assert
        assertEquals(expectedSet, result)
    }


    @After
    fun after() {
        unmockkAll()
    }

}