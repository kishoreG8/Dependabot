package com.trimble.ttm.formlibrary.repo

import com.google.gson.internal.LinkedTreeMap
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.model.MessagePayload
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test


class InboxRepoTest {
    private lateinit var inboxRepoImpl: InboxRepoImpl
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
    private val rawMessageWithTimestamp = LinkedHashMap<String, Any>().apply {
        put(MESSAGE_CONTENT, "01/09 09:10 summary for the message")
        put(FORM_ID, 1092)
        put(FORM_CLASS, 0)
        put(FORM_NAME, "formName")
        put(REPLY_FORM_ID, 5501)
        put(REPLY_FORM_CLASS, 1)
        put(REPLY_FORM_NAME, "replyFormName")
        put(FORM_FIELD, formFieldMap)
    }

    private val rawMessageWithoutTimestamp = LinkedHashMap<String, Any>().apply {
        put(MESSAGE_CONTENT, "summary for the message without timestamp")
        put(FORM_ID, 1092)
        put(FORM_CLASS, 0)
        put(FORM_NAME, "formName")
        put(REPLY_FORM_ID, 5501)
        put(REPLY_FORM_CLASS, 1)
        put(REPLY_FORM_NAME, "replyFormName")
        put(FORM_FIELD, formFieldMap)
    }

    private val messagePayload = MessagePayload(
        asn = "1092",
        emailAddr = "sender@trimble.com",
        subject = "Message Parse Test",
        isRead = true,
        message = rawMessageWithTimestamp,
        timeCreated = "2022-02-01T21:41:31.577Z"
    )
    private val messagePayloadNoTimestamp = MessagePayload(
        asn = "1093",
        emailAddr = "sender@trimble.com",
        subject = "Message Parse Test",
        isRead = true,
        message = rawMessageWithoutTimestamp,
        timeCreated = "2022-02-01T21:41:31.577Z"
    )
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
    private lateinit var messageNoTimestamp: Message


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        inboxRepoImpl = InboxRepoImpl(mockk(), mockk())
        message = inboxRepoImpl.parseMessagePayload(messagePayload, EMPTY_STRING)
        messageNoTimestamp =
            inboxRepoImpl.parseMessagePayload(messagePayloadNoTimestamp, EMPTY_STRING)
    }

    /*
    This may fail in local system, dont worry
    */
    @Test
    fun `verify message payload generic content parsing`() { //NOSONAR
        assertEquals("Message Parse Test", message.subject)
        assertEquals("1092", message.asn)
        assertEquals("sender@trimble.com", message.userName)
        assertEquals(true, message.isRead)
        assertEquals(
            "02/01 21:41 UTC".plus(NEWLINE).plus("summary for the message"),
            message.summary
        )
    }

    @Test
    fun `verify message payload with PFM timestamp setting OFF generic content parsing`() {    //NOSONAR
        assertEquals("Message Parse Test", messageNoTimestamp.subject)
        assertEquals("summary for the message without timestamp", messageNoTimestamp.summary)
        assertEquals("1093", messageNoTimestamp.asn)
        assertEquals("sender@trimble.com", messageNoTimestamp.userName)
        assertEquals(true, messageNoTimestamp.isRead)
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

    @After
    fun after() {
        unmockkAll()
    }
}