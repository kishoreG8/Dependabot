package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.utils.DRAFT_KEY
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.model.MessageFormResponseFromDB
import com.trimble.ttm.formlibrary.utils.*
import com.trimble.ttm.commons.utils.ext.safeLaunch
import io.mockk.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class DraftRepoTest {

    private lateinit var draftRepoImpl: DraftRepoImpl

    private val recipientUserNames = ArrayList<HashMap<String, Any>>().also {
        it.add(
            hashMapOf(
                ACTIVE to 1,
                EMAIL to "123@trimble.com",
                UID_FIELD_KEY to 12345,
                USERNAME to "user1"
            )
        )
        it.add(hashMapOf(
            ACTIVE to 1,
            EMAIL to "1232@trimble.com",
            UID_FIELD_KEY to 12349,
            USERNAME to "user2"
        ))
    }

    private val fieldData = ArrayList<Any>().also {
        it.add(
            hashMapOf("freeText" to "{\"initiallyEmpty\":true,\"text\":\"1666\",\"uniqueTag\":68098}")
        )
        it.add("multipleChoice" to "{\"choice\":4,\"initiallyEmpty\":false,\"uniqueTag\":68100}")
    }
    
    private val formResponse = FormResponse(fieldData = fieldData)

    private val messagePayload = MessageFormResponseFromDB("formName", "formLibrary",
        recipientUserNames, 5355535363, "0", formResponse, "11")

    private lateinit var message: MessageFormResponse

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any()) } returns 0
        draftRepoImpl = DraftRepoImpl(mockk())
        message = draftRepoImpl.parseMessageResponsePayload(messagePayload, DRAFT_KEY)
    }

    @Test
    fun `verify message payload generic content parsing`() {    //NOSONAR
        assertEquals("formName", messagePayload.formName)
        assertEquals("formLibrary", messagePayload.formResponseType)
        assertEquals("11", messagePayload.formId)
        assertEquals("0", messagePayload.formClass)
    }

    @Test
    fun `verify message payload recipient usernames content parsing`() {    //NOSONAR
        assertEquals("123@trimble.com", recipientUserNames.elementAt(0)[EMAIL])
        assertEquals(12345, recipientUserNames.elementAt(0)[UID_FIELD_KEY])
    }

    @Test
    fun `verify message payload form response content parsing`() {    //NOSONAR
        assertEquals("{\"initiallyEmpty\":true,\"text\":\"1666\",\"uniqueTag\":68098}",
            (formResponse.fieldData[0] as HashMap<String, Any>)["freeText"])
        assertEquals("multipleChoice",
            (formResponse.fieldData[1] as Pair<String, Any>).first)
    }

    @Test
    fun `verify getMessageListFlow returns the correct flow`() = runTest {    //NOSONAR
        // Assign
        val blankResponse = MessageFormResponse()
        val blankContent = hashSetOf(blankResponse)

        val expectedContent = hashSetOf(message)

        // Use reflection to get the private field for the flow pair, so we can use notify()
        val privateField = getFieldFromObject(
            "draftMessageResponseFlowPair", draftRepoImpl
        )
        val flowPair = privateField.get(draftRepoImpl) as
                Pair<ExternalNotifier<MutableSet<MessageFormResponse>>,
                        Flow<MutableSet<MessageFormResponse>>>

        val result = mutableListOf<MessageFormResponse>()

        // Act
        val collectJob = safeLaunch(start = CoroutineStart.LAZY) {
            val flow = draftRepoImpl.getMessageListFlow()
            flow.take(2).collect { result.add(it.first()) }
        }

        collectJob.start()
        advanceTimeBy(100)
        flowPair.first.notify(blankContent)
        advanceTimeBy(100)
        flowPair.first.notify(expectedContent)
        advanceTimeBy(100)
        collectJob.cancel()

        // Assert
        assertEquals("Received all messages", 2, result.size)
        assertEquals("First message has blank message", blankResponse, result[0])
        assertEquals("Second message has expected value", message, result[1])
    }

    @Test
    fun `verify draft path from form path`() {
        val formPath = "FormResponses/myCID/myTruck/352558/1/1"
        val draftPath = "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/myCID/myTruck"

        val actualPath = draftRepoImpl.getDraftCollectionFromFormPath(formPath)
        assertEquals(draftPath, actualPath)
    }

    @After
    fun after() {
        unmockkAll()
    }
}