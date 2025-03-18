package com.trimble.ttm.formlibrary.repo

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.gson.Gson
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FreeText
import com.trimble.ttm.commons.utils.FREETEXT_KEY
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_ID
import com.trimble.ttm.formlibrary.utils.FORM_NAME
import com.trimble.ttm.formlibrary.utils.FULL_DATE_TIME_FORMAT
import com.trimble.ttm.formlibrary.utils.MESSAGE_CONTENT
import com.trimble.ttm.formlibrary.utils.NEWLINE
import com.trimble.ttm.formlibrary.utils.READABLE_DATE_FORMAT
import com.trimble.ttm.formlibrary.utils.READABLE_DATE_TIME_FORMAT
import com.trimble.ttm.formlibrary.utils.REPLY_FORM_ID
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals

class BaseAbstractMessagingRepoTest: BaseAbstractMessagingRepoImpl("test") {

    private lateinit var mockDocumentSnapshot: DocumentSnapshot

    @Before
    fun setup() {
        mockDocumentSnapshot = mockk()
    }


    @Test
    fun `parseFireStoreMessageDocument returns correct message and rowDate`() {
        val currentTime = Date()
        val currentTimeInString = SimpleDateFormat(FULL_DATE_TIME_FORMAT, Locale.getDefault()).format(
            currentTime
        )
        val expectedDateString = SimpleDateFormat(READABLE_DATE_FORMAT, Locale.getDefault()).format(
            currentTime
        )

        val expectedDateTimeString = SimpleDateFormat(READABLE_DATE_TIME_FORMAT, Locale.getDefault()).format(
            currentTime
        )
        val expectedMessage = Message(formId = "101", formName = "Test Form", asn = "12313", userName = "testUserName", subject = "testSubject", uid = 1, isDelivered = true, isRead = true, date = expectedDateString, dateTime = expectedDateTimeString, replyFormId = "201").apply {
            summary = "Hi How are you?"
        }
        val expectedRowDate = currentTime.time
        val messagePayloadMap = mapOf("Asn" to 12313, "EmailAddr" to "testEmailAddr", "Message" to mapOf(Pair(MESSAGE_CONTENT, "Hi How are you?"), Pair(FORM_ID,"101"), Pair(FORM_NAME, "Test Form"), Pair(REPLY_FORM_ID, "201")), "MessageType" to "testMessageType", "Subject" to "testSubject", "TimeCreated" to currentTimeInString, "UserName" to "testUserName", "UID" to 1, "IsDelivered" to true, "IsRead" to true)

        every { mockDocumentSnapshot.get("Payload") } returns messagePayloadMap
        every { mockDocumentSnapshot.reference.path } returns "testPath"
        every { mockDocumentSnapshot.getTimestamp("RowDate") } returns Timestamp(Date(expectedRowDate))

        val result = parseFireStoreMessageDocument(mockDocumentSnapshot)

        assertEquals(expectedMessage, result.first)
        assertEquals(expectedRowDate, result.second)
    }

    @Test
    fun `parseFireStoreMessageDocument returns default values on exception`() {
        every { mockDocumentSnapshot.get("Payload") } throws Exception()
        every { mockDocumentSnapshot.reference.path } returns "testPath"

        val result = parseFireStoreMessageDocument(mockDocumentSnapshot)

        assertEquals(Message(), result.first)
        assertEquals(0L, result.second)
    }

    @Test
    fun testGetFreeFormTextForEmptyResponse() =
        assertEquals(EMPTY_STRING, getFreeFormText(FormResponse(), 0))

    @Test
    fun testGetFreeFormTestForFreeText() {
        val fieldList = arrayListOf<Any>()
        val field = HashMap<String, String>()
        field[FREETEXT_KEY] = Gson().toJson(FreeText(1, true, "hello"))
        fieldList.add(
            field
        )
        val response = FormResponse(fieldData = fieldList)
        assertEquals("hello", getFreeFormText(response, 1))
    }

    @Test
    fun testGetMessageBodyWithTimestamp() {
        val messageText = "02/01 16:41 summary for the message"
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        SimpleDateFormat(FULL_DATE_TIME_FORMAT).parse("2022-02-01T21:41:31.577Z")?.let { date ->
            assertEquals("02/01 21:41 UTC".plus(NEWLINE).plus("summary for the message"), getMessageBody(messageText, date))
        }
    }

    @Test
    fun testGetMessageBodyWithOnlyTimestamp() {
        val messageText = "02/01 16:41"
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        SimpleDateFormat(FULL_DATE_TIME_FORMAT).parse("2022-02-01T21:41:31.577Z")?.let { date ->
            assertEquals("02/01 21:41 UTC".plus(NEWLINE).plus(""), getMessageBody(messageText, date))
        }
    }

    @Test
    fun testGetMessageBodyWithOnlyTimestampAndSpace() {
        val messageText = "02/01 16:41 "
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        SimpleDateFormat(FULL_DATE_TIME_FORMAT).parse("2022-02-01T21:41:31.577Z")?.let { date ->
            assertEquals("02/01 21:41 UTC".plus(NEWLINE).plus(""), getMessageBody(messageText, date))
        }
    }


    @Test
    fun testGetMessageBodyWithoutTimestamp() {
        val messageText = "summary for the message"
        SimpleDateFormat(FULL_DATE_TIME_FORMAT, Locale.getDefault()).parse("2022-02-01T21:41:31.577Z")?.let { date ->
            assertEquals("summary for the message", getMessageBody(messageText, date))
        }
    }

    @Test
    fun testGetMessageBodyWithOutTimeStampAndHaveLessCharacters() {
        val messageText = "summary"
        SimpleDateFormat(FULL_DATE_TIME_FORMAT, Locale.getDefault()).parse("2022-02-01T21:41:31.577Z")?.let { date ->
            assertEquals("summary", getMessageBody(messageText, date))
        }
    }
}

