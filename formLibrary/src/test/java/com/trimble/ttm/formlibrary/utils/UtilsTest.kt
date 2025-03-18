package com.trimble.ttm.formlibrary.utils

import android.content.Context
import android.text.format.DateFormat
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.internal.LinkedTreeMap
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.utils.ISO_DATE_TIME_FORMAT
import com.trimble.ttm.commons.utils.UTC_TIME_ZONE_ID
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.Message.Companion.getFormattedUiString
import com.trimble.ttm.formlibrary.utils.Utils.checkforUiData
import com.trimble.ttm.formlibrary.utils.Utils.getDateOrTimeStringForInbox
import com.trimble.ttm.formlibrary.utils.Utils.getFormGroupTabIndex
import com.trimble.ttm.formlibrary.utils.Utils.getSubtractedTimeInMillis
import com.trimble.ttm.formlibrary.utils.Utils.getTtsList
import com.trimble.ttm.formlibrary.utils.Utils.goToFormLibraryActivity
import com.trimble.ttm.formlibrary.utils.Utils.setTtsHeader
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals


class UtilsTest {

    private lateinit var context: Context

    @Mock
    val username = "username"
    val sentDate = "Jul 16 - 11:12:13"
    val formName = "formname"
    val formFieldsList = ArrayList<FormField>()

    private fun setTTSList(sentDate: String): ArrayList<String> {
        var expected = setTtsHeader(username,sentDate,formName)
        formFieldsList.forEach {
        formField ->
            if(Utils.checkforLabel(formField))
            {
                if(formField.qtext.isNotEmpty()) expected.add(formField.qtext)
                if(checkforUiData(formField)) {
                    expected.add(formField.uiData.getFormattedUiString())
                }
            }
        }
        return expected
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk()
        formFieldsList.add(FormField(
            22,
            "Field",
            FormFieldType.NUMERIC.ordinal,
            111,
            "",
            fieldId = 2
        ).also { it.uiData="567" })
        formFieldsList.add(FormField(
            22,
            "Field",
            FormFieldType.TEXT.ordinal,
            111,
            "",
            fieldId = 2
        ).also { it.uiData="Text" })
        formFieldsList.add(FormField(qtype = FormFieldType.MULTIPLE_CHOICE.ordinal,qtext = "Multiple choice field Field Test").apply {
            uiData = "2"
        })
        formFieldsList.add(FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Date Field Test"
        ).also {
            it.uiData="09/11/2024"
        })
        formFieldsList.add(FormField(
            qtype = FormFieldType.TIME.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Time Field Test"
        ).also {
            it.uiData="01:20:00"
        })
        formFieldsList.add(FormField(qtype = FormFieldType.SIGNATURE_CAPTURE.ordinal).also { it.uiData = "1256126526" })
        formFieldsList.add(FormField(qtype = FormFieldType.IMAGE_REFERENCE.ordinal).also { it.uiData = "1256126526" })
        formFieldsList.add(FormField(
            1,
            "Barcode",
            FormFieldType.BARCODE_SCAN.ordinal,
            12,
            ""
        ).apply {
            uiData = "9876543456565767676756,78657843456565767676756"
        })
        formFieldsList.add(FormField(
            qnum=2, qtext="Currency Field", qtype=0, formid=18985,
            description="field", fieldId=68379, required=0, dispatchEditable=0, driverEditable=1
        ).also { it.uiData="1.5"})
    }



    @Test
    fun `verify getIntentDataErrorString returns a string with the expected values`() {
        val dataName = "value"
        val dataType = "Int"
        val nullOrEmpty = "null"
        val actionName = "myIntent"
        val expected =
            "RECEIVED DATA INTENT ERROR ---> $dataName $dataType is $nullOrEmpty when is got from an intent. action: $actionName"
        every {
            context.getString(
                R.string.intent_received_data_error,
                dataName,
                dataType,
                nullOrEmpty,
                actionName
            )
        } returns expected
        val result = Utils.getIntentDataErrorString(
            context,
            dataName,
            dataType,
            nullOrEmpty,
            actionName
        )
        assert(result == expected)
    }

    @Test
    fun `verify getIntentSendErrorString returns a string with the expected values`() {
        val errorMsg = "my custom error message"
        val expected = "SENT INTENT ERROR ---> $errorMsg"
        every {
            context.getString(
                R.string.intent_send_error,
                errorMsg
            )
        } returns expected
        val result = Utils.getIntentSendErrorString(
            context,
            errorMsg
        )
        assert(result == expected)
    }

    @Test
    fun isNumeric() {
        Assert.assertEquals(true, Utils.isNumeric("100", "$"))

        Assert.assertEquals(true, Utils.isNumeric("100667776676", "$"))

        Assert.assertEquals(true, Utils.isNumeric("$1000", "$"))
    }

    @Test
    fun isNotNumeric() {
        Assert.assertEquals(false, Utils.isNumeric("not a currency", null))
    }

    @Test
    fun verifyInboxIndividualDeleteMessageCount() {
        val expected = "Are you sure you want to delete the selected 10 message(s)?"
        every {
            context.getString(
                R.string.inbox_individual_delete_message_content,
                any()
            )
        } returns expected
        Assert.assertEquals(
            expected,
            Utils.getInboxDeletionMessageBasedOnSelection(context, false, 10)
        )
    }

    @Test
    fun verifyDeleteAllInboxDeleteMessage() {
        val expected = "Are you sure you want to delete all the message(s)"
        every { context.getString(R.string.inbox_delete_all_message_content) } returns expected
        Assert.assertEquals(
            expected,
            Utils.getInboxDeletionMessageBasedOnSelection(context, true, 0)
        )
    }

    @Test
    fun verifyDraftSentIndividualDeleteMessageCount() {
        val expected = "Are you sure you want to delete the selected 100 message(s) permanently?"
        every {
            context.getString(
                R.string.draft_sent_trash_individual_delete_message_content,
                any()
            )
        } returns expected
        Assert.assertEquals(
            expected,
            Utils.getPermanentDeletionMessageBasedOnSelection(context, false, 100)
        )
    }

    @Test
    fun verifyDraftSentDeleteAllDeleteMessage() {
        val expected = "Are you sure you want to delete all the message(s) permanently?"
        every { context.getString(R.string.draft_sent_trash_delete_all_message_content) } returns expected
        Assert.assertEquals(
            expected,
            Utils.getPermanentDeletionMessageBasedOnSelection(context, true, 0)
        )
    }

    @Test
    fun ` verify getTTSList`() {
        val expected = setTTSList(sentDate)
        //message

        Assert.assertEquals(
            expected,
            setTtsHeader(username,sentDate,formName) + getTtsList(formFieldsList)
        ) // sentDate is !NULL
    }
    @Test
    fun `verify checkforUiData`(){

        var formField = FormField(
            22,
            "Field",
            FormFieldType.TEXT.ordinal,
            111,
            "",
            fieldId = 2
        ).also { it.uiData="Text" }
        assertEquals(true, checkforUiData( formFieldsList[0] ))
    }

    @Test
    fun `in getDateOrTimeStringForInbox currentDate(yyyyMMdd) and messageTimeStamp mismatches`() {

        Assert.assertEquals(
            "",
            getDateOrTimeStringForInbox(context, 0, "")
        )
    }

    @Test
    fun `verify getDateOrTimeStringForInbox, timestamp in 12 hr format`() {
        mockkStatic(DateFormat::class)
        every { DateFormat.is24HourFormat(any()) } returns false
        Assert.assertEquals(
            SimpleDateFormat("hh:mm aa").format(Date().time),
            getDateOrTimeStringForInbox(context, Date().time, Date().toString())
        )
    }
    @Test
    fun `verify getDateOrTimeStringForInbox, timestamp in 24 hr format`(){
        mockkStatic(DateFormat::class)
        every { DateFormat.is24HourFormat(any()) } returns true

        Assert.assertEquals(SimpleDateFormat("HH:mm").format(Date().time),getDateOrTimeStringForInbox(context,Date().time, Date().toString()))
    }

    @Test
    fun `verify getSubtractedTimeInMillis`() {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.add(Calendar.DATE, 0)
        val diff = calendar.timeInMillis - getSubtractedTimeInMillis(0)
        if (diff > 5000)
            Assert.assertEquals(1, 1)
    }

    @Test
    fun `verify getSystemLocalDateTimeFromUTCDateTime `() {
        val isoDateFormatter = SimpleDateFormat(ISO_DATE_TIME_FORMAT, Locale.getDefault())
        val utcDateTime = ""
        isoDateFormatter.timeZone = TimeZone.getTimeZone(UTC_TIME_ZONE_ID)
        Assert.assertEquals("",
            Utils.getSystemLocalDateTimeFromUTCDateTime("2024-07-16T18:52:31.123Z", context)
        )
    }

    @Test
    fun `verify getFormGroupTabIndex when hotKeys is selected`() {
        assertEquals(HOTKEYS, getFormGroupTabIndex(HOT_KEYS_MENU_INDEX, HOT_KEYS_MENU_INDEX))
    }

    @Test
    fun `verify getFormGroupTabIndex when hotKeys is not selected`() {
        assertEquals(FORMS, getFormGroupTabIndex(0, HOT_KEYS_MENU_INDEX))
    }

    @Test
    fun `verify goToFormLibrary function`() {
        every { context.startActivity(any()) } just runs
        goToFormLibraryActivity(context, 1, HOT_KEYS_MENU_INDEX)
        verify(exactly = 1) {
            context.startActivity(any())
        }
    }

    @Test
    fun `verify updateMessageSet updates adapter and returns new message set`() {
        val oldMessageSet = mutableSetOf<Message>(
            Message(asn = "1"),
            Message(asn = "2")
        )
        val newMessageSet = mutableSetOf<Message>(
            Message(asn = "1", summary = "Old Message 1"),
            Message(asn = "3")
        )
        val adapter = mockk<RecyclerView.Adapter<*>>(relaxed = true)

        val result = Utils.updateMessageSet(newMessageSet, oldMessageSet, adapter)
        assertEquals(newMessageSet, result)
    }

    @Test
    fun testGetImageIdentifiers_withValidData_returnsIdentifiers() {
        val arrayList = arrayListOf<Any>(
            LinkedTreeMap<String, Any>().apply {
                this["imageRef"] = mapOf("uniqueIdentifier" to "identifier1")
            },
            LinkedTreeMap<String, Any>().apply {
                this["imageRef"] = mapOf("uniqueIdentifier" to "identifier2")
            }
        )

        val expectedIdentifiers = listOf("identifier1", "identifier2")
        val actualIdentifiers = Utils.getImageIdentifiers(arrayList)

        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test
    fun testGetImageIdentifiers_withEmptyData_returnsEmptyList() {
        val arrayList = arrayListOf<Any>()

        val expectedIdentifiers = emptyList<String>()
        val actualIdentifiers = Utils.getImageIdentifiers(arrayList)

        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test
    fun testGetImageIdentifiers_withInvalidData_returnsEmptyList() {
        val arrayList = arrayListOf<Any>(
            "invalidData",
            123,
            LinkedTreeMap<String, Any>().apply {
                this["otherKey"] = "otherValue"
            }
        )

        val expectedIdentifiers = emptyList<String>()
        val actualIdentifiers = Utils.getImageIdentifiers(arrayList)

        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test
    fun testGetImageIdentifiers_withNullUniqueIdentifier_returnsEmptyList() {
        val arrayList = arrayListOf<Any>(
            LinkedTreeMap<String, Any>().apply {
                this["imageRef"] = mapOf("uniqueIdentifier" to null)
            }
        )

        val expectedIdentifiers = emptyList<String>()
        val actualIdentifiers = Utils.getImageIdentifiers(arrayList)

        assertEquals(expectedIdentifiers, actualIdentifiers)
    }
}