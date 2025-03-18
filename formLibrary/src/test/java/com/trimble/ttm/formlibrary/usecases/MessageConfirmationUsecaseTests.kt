package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageConfirmation
import com.trimble.ttm.formlibrary.repo.MessageConfirmationRepoImpl
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test


class MessageConfirmationUsecaseTests {

    private lateinit var messageConfirmationUseCase: MessageConfirmationUseCase

    @MockK
    private lateinit var messageConfirmationRepo: MessageConfirmationRepoImpl

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        messageConfirmationUseCase = MessageConfirmationUseCase(
            messageConfirmationRepo,
            TestScope(),
            DefaultDispatcherProvider()
        )
    }

    private fun getReadedMessageMutableList(): MutableSet<Message> {
        return mutableSetOf<Message>().also {
            it.add(
                Message(
                    userName = "user1",
                    summary = "summ1",
                    isDelivered = false,
                    isRead = false,
                    formId = "0",
                    formClass = "1"
                )
            )
            it.add(
                Message(
                    userName = "user2",
                    summary = "summ2",
                    isRead = true,
                    formId = "0",
                    formClass = "1"
                )
            )
            it.add(
                Message(
                    userName = "user3",
                    summary = "summ3",
                    isRead = true,
                    formId = "0",
                    formClass = "1"
                )
            )
            it.add(
                Message(
                    userName = "user4",
                    summary = "summ4",
                    isRead = true,
                    formId = "0",
                    formClass = "1"
                )
            )
            it.add(
                Message(
                    userName = "user5",
                    summary = "summ5",
                    isDelivered = false,
                    isRead = false,
                    formId = "1",
                    formClass = "11"
                )
            )
        }
    }

    private fun getFlowOfMutableSetOfMessages(): MutableSet<Message> {
        val messages = getReadedMessageMutableList()
        return messages.toMutableSet()
    }

    @Test
    fun `send Edvir Message Viewed Confirmation`() {
        every { messageConfirmationRepo.sendEdvirMessageViewedConfirmation(any(), any(), any()) } just runs

        messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(
            "path",
            MessageConfirmation(),
            "type"
        )
        verify(exactly = 1) { messageConfirmationRepo.sendEdvirMessageViewedConfirmation(any(), any(), any()) }
    }

    @Test
    fun `verify sendInboxMessageDeliveryConfirmation() is not called from verifyDeliveredAndReadAndSendMessageConfirmation() when message isDelivered and isRead are true`() = runTest {
        val messageFetchedFrom = INBOX_COLLECTION
        val message = Message(isDelivered = true, isRead = true)

        messageConfirmationUseCase.verifyDeliveredAndReadAndSendMessageConfirmation(messageFetchedFrom, message)

        coVerify(exactly = 0) { messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any()) }
    }

    @Test
    fun `verify sendInboxMessageDeliveryConfirmation() is not called from verifyDeliveredAndReadAndSendMessageConfirmation() when message isDelivered is true and isRead is false`() = runTest {
        val messageFetchedFrom = INBOX_COLLECTION
        val message = Message(isDelivered = true, isRead = false)

        messageConfirmationUseCase.verifyDeliveredAndReadAndSendMessageConfirmation(messageFetchedFrom, message)

        coVerify(exactly = 0) { messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any()) }
    }

    @Test
    fun `verify sendInboxMessageDeliveryConfirmation() is not called from verifyDeliveredAndReadAndSendMessageConfirmation() when message isDelivered is false and isRead is true`() = runTest {
        val messageFetchedFrom = INBOX_COLLECTION
        val message = Message(isDelivered = false, isRead = true)

        messageConfirmationUseCase.verifyDeliveredAndReadAndSendMessageConfirmation(messageFetchedFrom, message)

        coVerify(exactly = 0) { messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any()) }
    }

    @Test
    fun `verify sendInboxMessageDeliveryConfirmation() is called from verifyDeliveredAndReadAndSendMessageConfirmation() when message isDelivered is false and isRead is false`() = runTest {
        val messageFetchedFrom = INBOX_COLLECTION
        val message = Message(isDelivered = false, isRead = false)
        coEvery { messageConfirmationRepo.markMessageAsDelivered(any(), any(), any()) } just runs

        messageConfirmationUseCase.verifyDeliveredAndReadAndSendMessageConfirmation(messageFetchedFrom, message)

        coVerify(exactly = 1) { messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any()) }
        coVerify(exactly = 1) { messageConfirmationRepo.markMessageAsDelivered(any(), any(), any()) }
    }

    @Test
    fun `Send Inbox Message Delivery Confirmation Via Inbox`() {
        coEvery {
            messageConfirmationRepo.markMessageAsDelivered(any(), any() ,any())
        } just runs
        print(getFlowOfMutableSetOfMessages())

        messageConfirmationUseCase.sendUnDeliveredMessageConfirmationForMessagesFetchedViaInboxScreen(
            getFlowOfMutableSetOfMessages()
        )
        coVerify(exactly = 2 , timeout = TEST_DELAY_OR_TIMEOUT) {
            messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any())
        }
    }

    @Test
    fun `Send Inbox Message Delivery Confirmation Fetched Via TTS Widget`() = runTest {
        val message = Message(
            userName = "user4",
            summary = "summ4",
            isRead = false,
            formId = "0",
            formClass = "1",
            isDelivered = false,
        )

        coEvery {
            messageConfirmationRepo.markMessageAsDelivered(any(),any(), any())
        } just runs

        messageConfirmationUseCase.sendUnDeliveredMessageConfirmationForMessageFetchedViaTtsWidget(
            flow { emit(message) }
        )

        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any())
        }
    }

    @Test
    fun `Verify Inbox Message Delivery Confirmation Fetched Via TTS Widget is not sent if the message is already delivered`() = runTest {
        val message = Message(
            userName = "testUser",
            summary = "testSummary",
            isDelivered = true,
            isRead = false,
            formId = "0",
            formClass = "1"
        )

        coEvery {
            messageConfirmationRepo.markMessageAsDelivered(any(),any(), any())
        } just runs

        messageConfirmationUseCase.sendUnDeliveredMessageConfirmationForMessageFetchedViaTtsWidget(
            flow { emit(message) }
        )

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any())
        }
    }

    @Test
    fun `Verify Inbox Message Delivery Confirmation Fetched Via TTS Widget is not sent if the message is already read and delivered`() = runTest {
        val message = Message(
            userName = "testUser",
            summary = "testSummary",
            isDelivered = true,
            isRead = true,
            formId = "0",
            formClass = "1"
        )

        coEvery {
            messageConfirmationRepo.markMessageAsDelivered(any(),any(), any())
        } just runs

        messageConfirmationUseCase.sendUnDeliveredMessageConfirmationForMessageFetchedViaTtsWidget(
            flow { emit(message) }
        )

        coVerify(exactly = 0, timeout = TEST_DELAY_OR_TIMEOUT) {
            messageConfirmationUseCase.sendInboxMessageDeliveryConfirmation(any(), any(), any())
        }
    }

    @After
    fun tearDown(){
        unmockkAll()
    }
}