package com.trimble.ttm.formlibrary.repo

import com.google.gson.internal.LinkedTreeMap
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.model.MessagePayload
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_CLASS
import com.trimble.ttm.formlibrary.utils.FORM_FIELD
import com.trimble.ttm.formlibrary.utils.FORM_ID
import com.trimble.ttm.formlibrary.utils.FORM_NAME
import com.trimble.ttm.formlibrary.utils.FQUESTION
import com.trimble.ttm.formlibrary.utils.MESSAGE_CONTENT
import com.trimble.ttm.formlibrary.utils.NEWLINE
import com.trimble.ttm.formlibrary.utils.REPLY_FORM_CLASS
import com.trimble.ttm.formlibrary.utils.REPLY_FORM_ID
import com.trimble.ttm.formlibrary.utils.REPLY_FORM_NAME
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrashRepoTest {
    private lateinit var trashRepoImpl: TrashRepoImpl

    private val firstField = LinkedTreeMap<String, String>().apply {
        put("FieldID", "68089")
        put("FieldType", "1")
        put("FqText", "Number Field")
        put("QNum", "1")
        put("Text", "123")
    }
    private val secondField = LinkedTreeMap<String, String>().apply {
        put("FieldID", "68088")
        put("FieldType", "2")
        put("FqText", "Text Field")
        put("QNum", "2")
        put("Text", "")
    }
    private val messageFormFields = ArrayList<LinkedTreeMap<String, String>>().apply {
        add(firstField)
        add(secondField)
    }
    private val formFieldMap = LinkedHashMap<String, Any>().apply {
        put(FQUESTION, messageFormFields)
    }
    private val rawMessage = LinkedHashMap<String, Any>().apply {
        put(MESSAGE_CONTENT, "01/07 09:10 summary for the message")
        put(FORM_ID, 1092)
        put(FORM_CLASS, 0)
        put(FORM_NAME, "formName")
        put(REPLY_FORM_ID, 5501)
        put(REPLY_FORM_CLASS, 1)
        put(REPLY_FORM_NAME, "replyFormName")
        put(FORM_FIELD, formFieldMap)
    }
    private val messagePayload = MessagePayload(asn = "1092", emailAddr = "sender@trimble.com",
        subject = "Message Parse Test", isRead = true, message = rawMessage, timeCreated = "2022-02-01T21:41:31.577Z")
    private val formFieldList = ArrayList<MessageFormField>().apply {
        add(
            MessageFormField(
                firstField["FieldID"] ?: "",
                fieldType = firstField["FieldType"] ?: "",
                fqText = firstField["FqText"] ?: "",
                qNum = firstField["QNum"] ?: "",
                text = firstField["Text"] ?: ""
            )
        )
        add(
            MessageFormField(
                secondField["FieldID"] ?: "",
                fieldType = secondField["FieldType"] ?: "",
                fqText = secondField["FqText"] ?: "",
                qNum = secondField["QNum"] ?: "",
                text = secondField["Text"] ?: ""
            )
        )
    }
    private lateinit var message: Message
    private var messageFormUseCase = mockk<MessageFormUseCase>()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        trashRepoImpl = TrashRepoImpl(messageFormUseCase)
        message = trashRepoImpl.parseMessagePayload(messagePayload, EMPTY_STRING)
    }

    /*
This may fail in local system, dont worry
*/
    @Test
    fun `verify message payload generic content parsing`() {    //NOSONAR
        assertEquals("Message Parse Test", message.subject)
        assertEquals("02/01 21:41 UTC".plus(NEWLINE).plus("summary for the message"), message.summary)
        assertEquals("1092", message.asn)
        assertEquals("sender@trimble.com", message.userName)
        assertEquals(true, message.isRead)
    }

    @Test
    fun `verify message payload form related content parsing`() {    //NOSONAR
        assertEquals("1092", message.formId)
        assertEquals("0", message.formClass)
        assertEquals("formName", message.formName)
        assertEquals("5501", message.replyFormId)
        assertEquals("1", message.replyFormClass)
        assertEquals("replyFormName", message.replyFormName)
    }

    /*
This may fail in local system, dont worry
*/
    @Test
    fun `verify message payload date parsing`() =    //NOSONAR
        assertEquals("Feb 01", message.date)

    /*
This may fail in local system, dont worry
*/
    @Test
    fun `verify message payload date and time parsing`() =    //NOSONAR
        assertEquals("Feb 01 - 21:41:31", message.dateTime)

    @Test
    fun `verify message payload form fields parsing`() =    //NOSONAR
        assertEquals(formFieldList, message.formFieldList)

    @Test
    fun `verify whether the trash messages are sorted`(){ //NOSONAR
        with(trashRepoImpl.trashMessageMap){
            put(2L, Message(uid = 2L))
            put(4L, Message(uid = 4L))
            put(0L, Message(uid = 0L))
            put(1L, Message(uid = 1L))
            put(3L, Message(uid = 3L))
        }
        assertEquals(2L,trashRepoImpl.trashMessageMap.keys.elementAt(2))
    }

    @Test
    fun `verify deleteAllMessages calls the messageFormUse delete method`() = runTest {
        val mockedResponse = CollectionDeleteResponse(true, "success")
        coEvery { messageFormUseCase.deleteAllTrashMessages(any(), any(), any(), any()) } returns mockedResponse
        val response = trashRepoImpl.deleteAllMessages("customerId", "vehicleId", "token", "appCheck")
        assertEquals(mockedResponse, response)
    }

    @Test
    fun `verify deleteMessage calls the messageFormUse delete method`() = runTest {
        coEvery { messageFormUseCase.deleteSelectedTrashMessage(any(), any(), any(), any()) } returns Unit
        trashRepoImpl.deleteMessage("customerId", "vehicleId", "asn", "caller")
        coVerify {
           messageFormUseCase.deleteSelectedTrashMessage("customerId", "vehicleId", "asn", "caller")
        }
    }

    @After
    fun after() {
        unmockkAll()
    }
}