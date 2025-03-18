package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.repo.InboxRepo
import com.trimble.ttm.formlibrary.utils.INBOX_SCREEN_TIME
import com.trimble.ttm.formlibrary.utils.NEW_FORM_VIEW_COUNT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class InboxUseCaseTest {
    private lateinit var inboxUseCase: InboxUseCase
    private lateinit var inboxRepo: InboxRepo
    private lateinit var messageConfirmationUseCase: MessageConfirmationUseCase
    private lateinit var firebaseAnalyticEventRecorder : FirebaseAnalyticEventRecorder

    @Before
    fun setup() {
        MockKAnnotations.init()
        inboxRepo = mockk()
        messageConfirmationUseCase = mockk()
        firebaseAnalyticEventRecorder = mockk()
        inboxUseCase = InboxUseCase(inboxRepo, messageConfirmationUseCase, firebaseAnalyticEventRecorder)
    }

    @Test
    fun `verify getMessageOfVehicleFlow call`() = runTest {    //NOSONAR
        val messages = mutableSetOf<Message>().also {
            it.add(Message(userName = "user1"))
            it.add(Message(userName = "user2"))
        }
        coEvery {
            inboxUseCase.getMessageOfVehicle("1","2",true)
        } returns flow { emit(messages) }
        inboxUseCase.getMessageOfVehicle("1","2",true).safeCollect(this.javaClass.name) {
            assertEquals(messages, it)
        }
    }

    /*@Test
    fun `verify didLastMessageReached call`() = runTest {    //NOSONAR
        coEvery {
            inboxUseCase.didLastMessageReached()
        } returns true
        assertTrue(inboxUseCase.didLastMessageReached())
        coEvery {
            inboxUseCase.didLastMessageReached()
        } returns false
        assertFalse(inboxUseCase.didLastMessageReached())
    }*/

    @Test
    fun `verify updateMessage method for new message replacement`() {
        val uiStateMsgs = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "1", subject = "hi1")
            )
            it.add(
                Message(asn = "2", subject = "hi2")
            )
        }
        val receivedMsgChunk = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "2", subject = "hi2.")
            )
            it.add(
                Message(asn = "3", subject = "hi3")
            )
        }
        val expectedResult = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "1", subject = "hi1")
            )
            it.add(
                Message(asn = "2", subject = "hi2.")
            )
            it.add(
                Message(asn = "3", subject = "hi3")
            )
        }
        assertEquals(expectedResult, inboxUseCase.updateMessagesIfAny(uiStateMsgs, receivedMsgChunk))
    }

    @Test
    fun `verify updateMessage method for no new message replacement`() {
        val uiStateMsgs = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "1", subject = "hi1")
            )
            it.add(
                Message(asn = "2", subject = "hi2")
            )
        }
        val receivedMsgChunk = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "2", subject = "hi2")
            )
        }
        val expectedResult = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "1", subject = "hi1")
            )
            it.add(
                Message(asn = "2", subject = "hi2")
            )
        }
        assertEquals(expectedResult, inboxUseCase.updateMessagesIfAny(uiStateMsgs, receivedMsgChunk))
    }

    @Test
    fun `verify updateMessage method for only new message replacement`() {
        // Given
        val totalMessageSet = mutableSetOf(
            Message(asn = "1", subject = "Message 1"),
            Message(asn = "2", subject = "Message 2"),
        )
        val receivedMessageChunk = mutableSetOf(
            Message(asn = "3", subject = "Message 3"),
            Message(asn = "4", subject = "Message 4"),
            Message(asn = "5", subject = "Message 5")
        )

        // When
        val updatedMessageSet = inboxUseCase.updateMessagesIfAny(totalMessageSet, receivedMessageChunk)

        // Then
        val expectedUpdatedMessageSet = mutableSetOf(
            Message(asn = "1", subject = "Message 1"),
            Message(asn = "2", subject = "Message 2"),
            Message(asn = "3", subject = "Message 3"),
            Message(asn = "4", subject = "Message 4"),
            Message(asn = "5", subject = "Message 5"),
        )
        assertEquals(expectedUpdatedMessageSet, updatedMessageSet)
    }

    @Test
    fun `verify sortMessagesByAsn method for proper sorting`() {
        val uiStateMsgs = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "1", subject = "hi1")
            )
            it.add(
                Message(asn = "2", subject = "hi2")
            )
            it.add(
                Message(asn = "3", subject = "hi3")
            )
            it.add(
                Message(asn = "110", subject = "hi110")
            )
        }
        val expectedResult = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "110", subject = "hi110")
            )
            it.add(
                Message(asn = "3", subject = "hi3")
            )
            it.add(
                Message(asn = "2", subject = "hi2")
            )
            it.add(
                Message(asn = "1", subject = "hi1")
            )
        }
        assertEquals(expectedResult, inboxUseCase.sortMessagesByAsn(uiStateMsgs))
    }

    @Test
    fun `verify processReceivedMessages method for new message chunk`() {
        val uiStateMsgs = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "1", subject = "hi1")
            )
            it.add(
                Message(asn = "2", subject = "hi2")
            )
        }
        val receivedMsgChunk = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "2", subject = "hi2.")
            )
            it.add(
                Message(asn = "3", subject = "hi3")
            )
            it.add(
                Message(asn = "0", subject = "hi0")
            )
        }
        val expectedResult = mutableSetOf<Message>().also {
            it.add(
                Message(asn = "0", subject = "hi0")
            )
            it.add(
                Message(asn = "1", subject = "hi1")
            )
            it.add(
                Message(asn = "2", subject = "hi2.")
            )
            it.add(
                Message(asn = "3", subject = "hi3")
            )
        }
        assertEquals(expectedResult, inboxUseCase.processReceivedMessages(uiStateMsgs, receivedMsgChunk))
    }

    @Test
    fun `verify resetPagination call`() {    //NOSONAR
        verify(exactly = 0) {
            inboxUseCase.resetPagination()
        }
    }

    @Test
    fun `check logScreenViewEvent gets called`(){
        every { firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any()) } just runs
        inboxUseCase.logScreenViewEvent(INBOX_SCREEN_TIME)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logScreenViewEventWithDefaultAndCustomParameters(any())
        }
    }

    @Test
    fun `check logNewEventWithDefaultParameters gets called`(){
        every { firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any()) } just runs
        inboxUseCase.logNewEventWithDefaultParameters(NEW_FORM_VIEW_COUNT)
        verify(exactly = 1) {
            firebaseAnalyticEventRecorder.logNewCustomEventWithDefaultCustomParameters(any())
        }
    }

    @After
    fun after() {
        unmockkAll()
    }
}