package com.trimble.ttm.commons.model.test.composables

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trimble.ttm.commons.composable.customViews.CustomTimeField
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.utils.SIGNATURE_WIDTH_DP
import com.trimble.ttm.commons.utils.TIME_FIELD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormTimePickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun testFormTimeFieldWithRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.TIME.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Time Field Test"
        ).also {
            it.uiData=""
        }

        setTimeFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("Time Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertDoesNotExist()

    }

    @Test
    fun testFormTimeFieldWithRequiredAndInputValidation() {
        val formField = FormField(
            qtype = FormFieldType.TIME.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Time Field Test"
        ).also {
            it.uiData="08:34:00"
        }

        setTimeFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("Time Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("8:34 am").assertIsDisplayed()
    }

    @Test
    fun testFormTimeFieldWithRequiredAndSendButtonClicked() {
        val formField = FormField(
            qtype = FormFieldType.TIME.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Time Field Test"
        ).also {
            it.uiData=""
        }

        setTimeFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = true,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("Time Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertIsDisplayed()

    }

    @Test
    fun testFormTimeFieldWithRequiredAndFormSavedValidation() {
        val formField = FormField(
            qtype = FormFieldType.TIME.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Time Field Test"
        ).also {
            it.uiData="01:20:00"
        }

        setTimeFieldCompose(formField,
            isFormSaved = true,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("Time Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("1:20 am").assertIsDisplayed()

    }

    @Test
    fun testFormTimeFieldWithNotRequiredValidation() {
        SIGNATURE_WIDTH_DP
        val formField = FormField(
            qtype = FormFieldType.TIME.ordinal,
            required = 0,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Time Field Test"
        ).also {
            it.uiData=""
        }

        setTimeFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("Time Field Test").assertIsDisplayed()

    }

    private fun setTimeFieldCompose(formField: FormField,isFormSaved:Boolean, sendButtonClick :Boolean,isFormInReadOnlyMode :Boolean){

        // Set up your Composable here
        composeTestRule.setContent {
            CustomTimeField(
                formField = formField, isFormSaved,
                remember {
                    mutableStateOf(sendButtonClick)
                },
                isFormInReadOnlyMode = isFormInReadOnlyMode
            )

        }
    }

}