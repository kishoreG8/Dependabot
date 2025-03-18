package com.trimble.ttm.commons.model.test

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.ext.getParcelableData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class FormResponseSerializationTest {

    @Test
    fun serializeFormResponseAsParcel() {
        val testRecipientsList : MutableList<Recipients> = mutableListOf(Recipients())
        val testFieldData: ArrayList<Any> = arrayListOf("String of stuff", "String of more stuff")

        val formResponseOriginal = FormResponse(
        dsn = 1L,
        baseSerialNumber = 2L,
        baseSerialNumberType = "notASN",
        creationDateTime= getUTCFormattedDate(Date()),
        driverCanSendUrgent = true,
        driverMustSend = true,
        driverSentUrgent = true,
        msgSerialNumber = 3L,
        msgSerialNumberType = "notDAMN",
        mailbox = "mailbox",
        noDelete = true,
        priority = true,
        readFromPda = true,
        sendToPda = true,
        timeOffset = 100,
        uniqueTemplateHash = 4L,
        uniqueTemplateTag = 5L,
        recipients = testRecipientsList,
        fieldData = testFieldData)

        val intent = Intent()
        intent.putExtra("myObject", formResponseOriginal )
        val result = intent.getParcelableData("myObject", FormResponse::class.java)

        assertEquals(formResponseOriginal, result)

    }
}