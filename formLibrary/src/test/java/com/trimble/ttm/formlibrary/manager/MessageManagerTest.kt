package com.trimble.ttm.formlibrary.manager

import android.content.Context
import android.view.View
import com.trimble.ttm.commons.logger.INBOX
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.WIDGET
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.repo.isNewMessageNotificationReceived
import com.trimble.ttm.formlibrary.usecases.InboxUseCase
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.TEST_DELAY_OR_TIMEOUT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MessageManagerTest {

    private lateinit var messagesManagerImpl: MessagesManagerImpl
    val formFieldList = ArrayList<MessageFormField>()

    @RelaxedMockK
    private lateinit var inboxUseCase: InboxUseCase

    @RelaxedMockK
    private lateinit var messageFormUseCase: MessageFormUseCase

    @RelaxedMockK
    private lateinit var context: Context

    @RelaxedMockK
    private lateinit var callback: IMessageManagerCallback

    @RelaxedMockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator

    private var lastInboxMessageReadTimeInMillis: Long = 0

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        messagesManagerImpl = spyk( MessagesManagerImpl(
            appModuleCommunicator,
            messageFormUseCase,
            inboxUseCase,
            DefaultDispatcherProvider(),
            context
        ))
        messagesManagerImpl.setCallback(callback)
        coEvery {
            appModuleCommunicator.getAppModuleApplicationScope()
        } returns TestScope()
        coEvery {
            messageFormUseCase.markMessageAsRead(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Unit
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns CoroutineScope(Job())
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetTruckNumber() } returns "truck100"
        coEvery { appModuleCommunicator.doGetObcId() } returns "11000751"
        coEvery { appModuleCommunicator.isFirebaseAuthenticated() } returns true

    }

    private fun getMessageMutableList(): MutableSet<Message> {
        formFieldList.add(MessageFormField("1","text","question","1","answer","1","1"))
        return mutableSetOf<Message>().also {
            it.add(
                Message(
                    userName = "user1",
                    summary = "summ1",
                    isRead = false,
                    formId = "0",
                    formClass = "1"
                )
            )
            it.add(
                Message(
                    userName = "user2",
                    summary = "summ2",
                    isRead = false,
                    formId = "0",
                    formClass = "1"
                )
            )
            it.add(
                Message(
                    userName = "user3",
                    summary = "summ3",
                    isRead = false,
                    date="date",
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
                    isRead = false,
                    formId = "1",
                    formClass = "0",
                    date="date",
                    formFieldList = formFieldList
                )
            )
        }
    }

    private fun getReadedMessageMutableList(): MutableSet<Message> {
        formFieldList.add(MessageFormField("1","text","question","1","answer","1","1"))
        return mutableSetOf<Message>().also {
            it.add(
                Message(
                    userName = "user1",
                    summary = "summ1",
                    isRead = true,
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
                    isRead = true,
                    formId = "1",
                    formClass = "0",
                    date="date",
                    formFieldList = formFieldList
                )
            )
        }
    }

    @Test
    fun `when getMessages is called, it updates totalMessageSet with a list from a mutableSet`() =
        runTest {
            //Arrange
            val messages = getMessageMutableList()

            coEvery {
                inboxUseCase.getMessageWithConfirmationOfVehicleAtOnce(
                    any(), any()
                )
            } returns flow {
                messages.map {
                    emit(it)
                }
            }
            every {
                inboxUseCase.processReceivedMessages(any(), any())
            } returns messages

            //Act
            messagesManagerImpl.getLatestInboxMessages()

            //Assert
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(TEST_DELAY_OR_TIMEOUT) {
                    delay(TEST_DELAY_OR_TIMEOUT)
                    Assert.assertEquals(messages, messagesManagerImpl.totalMessageSet)
                }
            }
        }


    @Test
    fun `when getMessages gets an readed list, onMessagesUpdated should not be called`() = runTest {
        //Arrange
        val messages = getReadedMessageMutableList()

        coEvery {
            inboxUseCase.getMessageWithConfirmationOfVehicleAtOnce(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetTruckNumber()
            )
        } returns flow {
            messages.map {
                emit(it)
            }
        }
        every {
            callback.onMessagesUpdated()
        } returns Unit

        //Act
        messagesManagerImpl.getLatestInboxMessages()

        //Assert
        verify(exactly = 0) {
            callback.onMessagesUpdated()
        }

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(TEST_DELAY_OR_TIMEOUT) {
                delay(TEST_DELAY_OR_TIMEOUT)
                assertTrue { messagesManagerImpl.totalMessageSet.isEmpty() }
            }
        }
    }

    @Test
    fun `verify getTTSFormHeader for TTS string`(){
        val messages = getMessageMutableList()
        messagesManagerImpl.index = 1
        messagesManagerImpl.totalMessageSet.addAll(messages)
        Assert.assertEquals("From user2 Date  Form Name ",messagesManagerImpl.getTTSFormHeader())
    }

    @Test
    fun `when index is the last from messages and navigateNext is called, index should remain the same`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.index = messages.toList().lastIndex
        messagesManagerImpl.totalMessageSet.addAll(messages)

        //Act
        messagesManagerImpl.navigateNext()

        //Assert
        assertEquals(messages.toList().lastIndex, messagesManagerImpl.index)
    }

    @Test
    fun `when totalMessageSet is empty, index has to be 0 when navigateNext is called`() {
        //Arrange
        //Act
        messagesManagerImpl.navigateNext()

        //Assert
        assertEquals(0, messagesManagerImpl.index)
    }

    @Test
    fun `when totalMessageSet is empty, index has to be 0 when navigatePrevious is called`() {
        //Arrange
        //Act
        messagesManagerImpl.navigatePrevious()

        //Assert
        assertEquals(0, messagesManagerImpl.index)
    }

    @Test
    fun `when index is not the last from messages and navigateNext is called, index should increase in one`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.index = 1
        messagesManagerImpl.totalMessageSet.addAll(messages)

        //Act
        messagesManagerImpl.navigateNext()

        //Assert
        assertEquals(2, messagesManagerImpl.index)
    }

    @Test
    fun `when index is first from messages and navigatePrevious is called, index should remain the same`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.index = 0
        messagesManagerImpl.totalMessageSet.addAll(messages)

        //Act
        messagesManagerImpl.navigatePrevious()

        //Assert
        assertEquals(0, messagesManagerImpl.index)
    }

    @Test
    fun `when index is not first from messages and navigatePrevious is called, index should decrease in one`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.index = 2
        messagesManagerImpl.totalMessageSet.addAll(messages)

        //Act
        messagesManagerImpl.navigatePrevious()

        //Assert
        assertEquals(1, messagesManagerImpl.index)
    }

    @Test
    fun `when getCurrent is called, should return the summary of the current index on the messages list`() {

        //Arrange
        messagesManagerImpl.index = 2
        messagesManagerImpl.totalMessageSet.addAll(getMessageMutableList())

        //Act
        val result = messagesManagerImpl.getCurrentMessage()
        var messageList=getMessageMutableList()
        var expected="From ${messageList.elementAt(2).userName}"+" Date ${messageList.elementAt(2).date}" +" Form Name ${messageList.elementAt(2).formName}" + "${messageList.elementAt(2).summary}"
        //Act
        assertEquals(expected, result)
    }

    @Test
    fun `when getCurrent is called, an totalMessage is empty should return an empty string`() {

        //Act
        val result = messagesManagerImpl.getCurrentMessage()

        //Act
        assert(result.isEmpty())
    }

    @Test
    fun `when getCurrent is called, should return the form details of the current index on the messages list`() {
        //Arrange
        messagesManagerImpl.index = 4
        messagesManagerImpl.totalMessageSet.addAll(getMessageMutableList())

        //Act
        val result = messagesManagerImpl.getCurrentMessage()
        var messageList=getMessageMutableList()

        var expected="From ${messageList.elementAt(4).userName}"+" Date ${messageList.elementAt(4).date}" +" Form Name ${messageList.elementAt(4).formName}"
        expected=expected+"${messageList.elementAt(4).formFieldList[0].fqText}"+"${messageList.elementAt(4).formFieldList[0].text}"+" "

        //Act
        assertEquals(expected, result)
    }

    @Test
    fun `when totalMessageSet is empty and getMessage is called, should return the no new message text`() {
        //Arrange
        val expected = "No new messages"
        every {
            context.getString(any())
        } returns expected

        //Act
        val result = messagesManagerImpl.getMessageCountTitle()

        //Assert
        assertEquals(expected, result)
    }

    @Test
    fun `when totalMessageSet is not empty and getMessage is called, should return the message text on the current index`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.index = 2
        messagesManagerImpl.totalMessageSet.addAll(messages)
        val expected =
            "${messagesManagerImpl.index + 1} of ${messagesManagerImpl.totalMessageSet.size} messages"
        every {
            context.getString(any(), any(), any())
        } returns expected

        //Act
        val result = messagesManagerImpl.getMessageCountTitle()

        //Assert
        assertEquals(expected, result)
    }

    @Test
    fun `when is in last index, hideOrShowNext should return view visible`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.totalMessageSet.addAll(messages)
        messagesManagerImpl.index = messagesManagerImpl.totalMessageSet.indexOfLast { true }

        //Act
        val result = messagesManagerImpl.hideOrShowNext()

        //Assert
        assertEquals(View.VISIBLE, result)
    }

    @Test
    fun `when is not in last index, hideOrShowNext should return view gone`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.totalMessageSet.addAll(messages)
        messagesManagerImpl.index = messagesManagerImpl.totalMessageSet.indexOfLast { true } - 1

        //Act
        val result = messagesManagerImpl.hideOrShowNext()

        //Assert
        assertEquals(View.GONE, result)
    }

    @Test
    fun `when is in first index, hideOrShowPrevious should return view visible`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.totalMessageSet.addAll(messages)
        messagesManagerImpl.index = 0

        //Act
        val result = messagesManagerImpl.hideOrShowPrevious()

        //Assert
        assertEquals(View.VISIBLE, result)
    }

    @Test
    fun `when is not in first index, hideOrShowPrevious should return view gone`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.totalMessageSet.addAll(messages)
        messagesManagerImpl.index = 1

        //Act
        val result = messagesManagerImpl.hideOrShowPrevious()

        //Assert
        assertEquals(View.GONE, result)
    }

    @Test
    fun `when totalMessageSet is empty, hideOrShowPrevious should return view Visible`() {
        //Act
        val result = messagesManagerImpl.hideOrShowPrevious()

        //Assert
        assertEquals(View.VISIBLE, result)
    }

    @Test
    fun `when totalMessageSet is empty, hideOrShowNext should return view Visible`() {
        //Act
        val result = messagesManagerImpl.hideOrShowNext()

        //Assert
        assertEquals(View.VISIBLE, result)
    }

    @Test
    fun `when totalMessageSet is not empty, hideOrShowPlay should return view visible`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.totalMessageSet.addAll(messages)

        //Act
        val result = messagesManagerImpl.hideOrShowPlay()

        //Assert
        assertEquals(View.GONE, result)
    }

    @Test
    fun `when totalMessageSet is not empty, delete its content on clear Messages call`() {
        //Arrange
        val messages = getMessageMutableList()
        messagesManagerImpl.totalMessageSet.addAll(messages)

        //Act
        messagesManagerImpl.clearMessages()

        //Assert
        assert(messagesManagerImpl.totalMessageSet.isEmpty())
        assert(messagesManagerImpl.index == 0)
    }

    @Test
    fun `when totalMessageSet is empty, hideOrShowPlay should return view Visible`() {
        //Act
        val result = messagesManagerImpl.hideOrShowPlay()

        //Assert
        assertEquals(View.VISIBLE, result)
    }

    @Test
    fun `get 0 messages when all the messages are mark as read`() {
        //Arrange
        val readedMessages = getReadedMessageMutableList()
        //Act
        val result = messagesManagerImpl.getFilteredMsgs(readedMessages)
        //Assert
        assert(result.isEmpty())
    }

    @Test
    fun `get 3 messages when some messages are mark as not read yet`() {
        //Arrange
        val readedMessages = getMessageMutableList()
        //Act
        val result = messagesManagerImpl.getFilteredMsgs(readedMessages)
        //Assert
        assertEquals(3, result.size)
    }

    @Test
    fun `get true in isFreeForm when formClass is equals to 1 and formId is not empty`() {
        //Arrange
        val formClass = "1"
        val formId = "0"
        //Act
        val result = messagesManagerImpl.isFreeForm(
            formClass = formClass,
            formId = formId
        )
        //Assert
        assert(result)
    }

    @Test
    fun `get false in isFreeForm when formClass is different from 1 and formId is not empty`() {
        //Arrange
        val formClass = "0"
        val formId = "0"
        //Act
        val result = messagesManagerImpl.isFreeForm(
            formClass = formClass,
            formId = formId
        )
        //Assert
        assert(result.not())
    }

    @Test
    fun `get false in isFreeForm when formClass is equals to 1 and formId is empty`() {
        //Arrange
        val formClass = "1"
        val formId = ""
        //Act
        val result = messagesManagerImpl.isFreeForm(
            formClass = formClass,
            formId = formId
        )
        //Assert
        assert(result.not())
    }

    @Test
    fun `get false in isFreeForm when formClass is diferent from 1 and formId is empty`() {
        //Arrange
        val formClass = "0"
        val formId = ""
        //Act
        val result = messagesManagerImpl.isFreeForm(
            formClass = formClass,
            formId = formId
        )
        //Assert
        assert(result.not())
    }

    @Test
    fun `get false in isFreeForm when formClass is empty and formId is empty`() {
        //Arrange
        val formClass = ""
        val formId = ""
        //Act
        val result = messagesManagerImpl.isFreeForm(
            formClass = formClass,
            formId = formId
        )
        //Assert
        assert(result.not())
    }

    @Test
    fun `when markAsRead is called markMessageAsRead is called once`() = runTest {
        //Arrange
        val msg = getMessageMutableList().elementAt(0)
        coEvery {
            messageFormUseCase.markMessageAsRead(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns Unit
        //Act
        messagesManagerImpl.markAsRead(
            msg
        )
        //Assert
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            messageFormUseCase.markMessageAsRead(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `when getCurrent is called markMessageAsRead with a not read msg markMessageAsRead is called once`() {
        //Arrange
        messagesManagerImpl.index = 1
        messagesManagerImpl.totalMessageSet.addAll(getMessageMutableList())
        //Act
        messagesManagerImpl.getCurrentMessage()
        //Assert
        coVerify(exactly = 1, timeout = TEST_DELAY_OR_TIMEOUT) {
            messageFormUseCase.markMessageAsRead(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `when getCurrent is called markMessageAsRead with a read msg markMessageAsRead is not called`() {
        //Arrange
        //Arrange
        messagesManagerImpl.index = 3
        messagesManagerImpl.totalMessageSet.addAll(getMessageMutableList())
        //Act
        messagesManagerImpl.getCurrentMessage()
        //Assert
        coVerify(exactly = 0) {
            messageFormUseCase.markMessageAsRead(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `when getMessages is called, it checks for truck number data empty state and device authenticate state and returns if they are empty`() =
        runTest {
            //Arrange
            val messages = mutableSetOf<Message>()
            coEvery { appModuleCommunicator.doGetCid() } returns ""
            coEvery { appModuleCommunicator.doGetTruckNumber() } returns ""

            coEvery {
                inboxUseCase.getMessageWithConfirmationOfVehicleAtOnce(any(), any())
            } returns flow { Message() }
            every {
                inboxUseCase.processReceivedMessages(any(), any())
            } returns messages

            //Assert
            Assert.assertEquals(0, messagesManagerImpl.totalMessageSet.size)

        }

    @Test
    fun `when isNewMessageNotificationReceived is true, getLatestInboxMessages is called`() {
        isNewMessageNotificationReceived = true
        lastInboxMessageReadTimeInMillis = System.currentTimeMillis()
        every {
            messagesManagerImpl.getCurrentTimeMillis()
        } returns 2000L
        every {
            messagesManagerImpl.getLastInboxMessageReadTimeInMillis()
        } returns 1000L
        messagesManagerImpl.totalMessageSet.add(Message())
        messagesManagerImpl.getMessages()
        verify(exactly = 1) { messagesManagerImpl.getLatestInboxMessages() }
    }

    @Test
    fun `when lastInboxMessageReadTimeInMillis is lesser than 2 mins, getLatestInboxMessages is not called`() {
        isNewMessageNotificationReceived = false
        every {
            messagesManagerImpl.getCurrentTimeMillis()
        } returns 2000L
        every {
            messagesManagerImpl.getLastInboxMessageReadTimeInMillis()
        } returns 1000L
        messagesManagerImpl.totalMessageSet.add(Message())
        messagesManagerImpl.getMessages()
        verify(exactly = 0) { messagesManagerImpl.getLatestInboxMessages() }
    }

    @Test
    fun `when lastInboxMessageReadTimeInMillis is greater than 2 mins, getLatestInboxMessages is called`() {
        isNewMessageNotificationReceived = false
        lastInboxMessageReadTimeInMillis = System.currentTimeMillis()
        every {
            messagesManagerImpl.getCurrentTimeMillis()
        } returns 130001
        every {
            messagesManagerImpl.getLastInboxMessageReadTimeInMillis()
        } returns 10000
        messagesManagerImpl.totalMessageSet.add(Message())
        messagesManagerImpl.getMessages()
        verify(exactly = 1) { messagesManagerImpl.getLatestInboxMessages() }
    }

    @Test
    fun `when totalMessageSet is empty, getLatestInboxMessages is called`() {
        isNewMessageNotificationReceived = false
        lastInboxMessageReadTimeInMillis = System.currentTimeMillis()
        every {
            messagesManagerImpl.getCurrentTimeMillis()
        } returns 2000L
        every {
            messagesManagerImpl.getLastInboxMessageReadTimeInMillis()
        } returns 1000L
        messagesManagerImpl.getMessages()
        verify(exactly = 1) { messagesManagerImpl.getLatestInboxMessages() }
    }

    @Test
    fun `when totalMessageSet is empty and lastInboxMessageReadTimeInMillis is lesser than 2 mins, getLatestInboxMessages is not called`() {
        isNewMessageNotificationReceived = false
        every {
            messagesManagerImpl.getCurrentTimeMillis()
        } returns 2000L
        every {
            messagesManagerImpl.getLastInboxMessageReadTimeInMillis()
        } returns 1000L
        messagesManagerImpl.totalMessageSet.add(Message())
        messagesManagerImpl.getMessages()
        verify(exactly = 0) { messagesManagerImpl.getLatestInboxMessages() }
    }

    @Test
    fun `verify getCurrentTimeMillis is called`()
    {
        Assert.assertEquals(System.currentTimeMillis()/1000,messagesManagerImpl.getCurrentTimeMillis()/1000)
    }

    @Test
    fun `verify getLastInboxMessageReadTimeInMillis is called`()
    {
        Assert.assertEquals(lastInboxMessageReadTimeInMillis,messagesManagerImpl.getLastInboxMessageReadTimeInMillis())
    }

    @Test
    fun `verify getLatestInboxMessages, when  Cid is empty, Obcid is empty or no firebaseauthentication`(): Unit = runTest{
        coEvery { appModuleCommunicator.doGetCid()} returns ""
        coEvery { appModuleCommunicator.doGetObcId()} returns ""
        coEvery { appModuleCommunicator.isFirebaseAuthenticated()} returns false
        messagesManagerImpl.getLatestInboxMessages()
        coVerify{
            Log.d("$INBOX$WIDGET",
                "Truck information is not yet available CID:${appModuleCommunicator.doGetCid()} " +
                        "Vehicle ${appModuleCommunicator.doGetObcId()} " +
                        "ValidateAuthentication ${appModuleCommunicator.isFirebaseAuthenticated()}"
            )
        }
    }

    @Test
    fun `getContext returns the correct context`() {
        assertEquals(context, messagesManagerImpl.getContext())
    }
}