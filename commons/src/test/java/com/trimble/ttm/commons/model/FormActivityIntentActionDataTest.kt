package com.trimble.ttm.commons.model

import android.content.Intent
import android.os.Parcelable
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.DRIVER_FORM_ID
import com.trimble.ttm.commons.utils.FORM_DATA_KEY
import com.trimble.ttm.commons.utils.IMESSAGE_REPLY_FORM_DEF
import com.trimble.ttm.commons.utils.IS_SECOND_FORM_KEY
import com.trimble.ttm.commons.utils.composeFormActivityIntentAction
import com.trimble.ttm.commons.utils.formActivityIntentAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class FormActivityIntentActionDataTest {

    private lateinit var resultIntent: Intent

    private lateinit var expectedAttributes : HashMap<String, Any>

    @Before
    fun setUp() {
        expectedAttributes = HashMap()
        mockkObject(Log)
        every {
            Log.i(any(),any())
        } returns Unit
        resultIntent = mockk()
        every {
            resultIntent.setAction(any())
        } returns resultIntent
        every {
            resultIntent.putExtra(any(), any<Parcelable>())
        } returns resultIntent
        every {
            resultIntent.putExtra(any(), any<String>())
        } returns resultIntent
        every {
            resultIntent.putExtra(any(), any<Boolean>())
        } returns resultIntent
        every {
            resultIntent.addFlags(any())
        } returns resultIntent

    }
    @Test
    fun `validate we get the correct action based on compose flag on`() {
        val result = buildFormActivityIntentActionData(
            isComposeEnabled = true,
            containsStopData = true
        ).buildIntent(intent = resultIntent)

        validateIntent(result, expectedAttributes)
    }

    @Test
    fun `validate we get the correct action based on compose flag off`() {
        val result = buildFormActivityIntentActionData(
            isComposeEnabled = false,
            containsStopData = true,
        ).buildIntent(intent = resultIntent)

        validateIntent(result, expectedAttributes)
    }

    @Test
    fun `verify when customerId and formId are provided then imessage_reply is populated in intent`() {
        val customerId = 123
        val formId = 456
        val result = buildFormActivityIntentActionData(
            customerId = customerId,
            formId = formId,
        ).buildIntent(intent = resultIntent)

        validateIntent(result, expectedAttributes)
        verify { resultIntent.putExtra(IMESSAGE_REPLY_FORM_DEF, FormDef(cid = customerId, formid = formId)) }

    }

    @Test
    fun `verify when request contains stop data then extra values are added to intent`() {
        val formResponse = FormResponse()
        val driverFormPath = DispatchFormPath()
        val result = buildFormActivityIntentActionData(
            containsStopData = true,
            formResponse = formResponse,
            driverFormPath = driverFormPath,
            isSecondForm = true
        ).buildIntent(intent = resultIntent)

        validateIntent(result, expectedAttributes)
        verify { resultIntent.putExtra(FORM_DATA_KEY, formResponse) }
        verify { resultIntent.putExtra(DRIVER_FORM_ID, driverFormPath) }
        verify { resultIntent.putExtra(IS_SECOND_FORM_KEY, true) }
        verify { resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }
    }


    fun buildFormActivityIntentActionData(
        isComposeEnabled: Boolean = false,
        containsStopData: Boolean = true,
        customerId: Int? = null,
        formId: Int? = null,
        formResponse: FormResponse = FormResponse(),
        driverFormPath: DispatchFormPath = DispatchFormPath(),
        isSecondForm: Boolean = true,
        dispatchFormSavePath: String = "save path",
        isFormFromTripPanel: Boolean = true,
        isFormResponseSentToServer: Boolean = false) : FormActivityIntentActionData {

        expectedAttributes.put("isComposeEnabled", isComposeEnabled)
        expectedAttributes.put("containsStopData", containsStopData)
        if(customerId != null) {
            expectedAttributes.put("customerId", customerId)
        }
        if(formId != null) {
            expectedAttributes.put("formId", formId)
        }
        expectedAttributes.put("formResponse", formResponse)
        expectedAttributes.put("driverFormPath", driverFormPath)
        expectedAttributes.put("isSecondForm", isSecondForm)
        expectedAttributes.put("dispatchFormSavePath", dispatchFormSavePath)
        expectedAttributes.put("isFormFromTripPanel", isFormFromTripPanel)
        expectedAttributes.put("isFormResponseSentToServer", isFormResponseSentToServer)
        return FormActivityIntentActionData(
            isComposeEnabled = isComposeEnabled,
            containsStopData = containsStopData,
            customerId = customerId,
            formId = formId,
            formResponse = formResponse,
            driverFormPath = driverFormPath,
            isSecondForm = isSecondForm,
            dispatchFormSavePath = dispatchFormSavePath,
            isFormFromTripPanel = isFormFromTripPanel,
            isFormResponseSentToServer = isFormResponseSentToServer
        )

    }

    fun validateIntent(intent: Intent, expectedAttributes: HashMap<String, Any>) {
        val isComposeEnabled = expectedAttributes["isComposeEnabled"] as Boolean
        if (!isComposeEnabled) {
            verify { intent.action = formActivityIntentAction }
        } else {
            verify { intent.action = composeFormActivityIntentAction }
        }

    }
}