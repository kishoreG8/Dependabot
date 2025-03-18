package com.trimble.ttm.commons.model.test.composables

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trimble.ttm.commons.composable.customViews.CustomDateTimeField
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.utils.DATE_TIME_FIELD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormDateTimePickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun testFormDateTimeFieldWithRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "DateTime Field Test"
        ).also {
            it.uiData=""
        }

        setDateTimeFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("DateTime Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertDoesNotExist()

    }

    @Test
    fun testFormDateTimeFieldWithRequiredAndInputValidation() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "DateTime Field Test"
        ).also {
            it.uiData="05/05/2023 10:20:00"
        }

        setDateTimeFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("DateTime Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("05/05/23 10:20 am").assertIsDisplayed()
    }

    @Test
    fun testFormDateTimeFieldWithRequiredAndSendButtonClicked() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "DateTime Field Test"
        ).also {
            it.uiData=""
        }

        setDateTimeFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = true,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("DateTime Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertIsDisplayed()

    }

    @Test
    fun testFormDateTimeFieldWithRequiredAndFormSavedValidation() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "DateTime Field Test"
        ).also {
            it.uiData="04/12/2023 11:40:00"
        }

        setDateTimeFieldCompose(formField,
            isFormSaved = true,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("DateTime Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("12/04/23 11:40 am").assertIsDisplayed()

    }

    @Test
    fun testFormDateTimeFieldWithNotRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 0,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "DateTime Field Test"
        ).also {
            it.uiData=""
        }

        setDateTimeFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_TIME_FIELD).assertExists()
        composeTestRule.onNodeWithText("DateTime Field Test").assertIsDisplayed()

    }

    private fun setDateTimeFieldCompose(formField: FormField,isFormSaved:Boolean, sendButtonClick :Boolean,isFormInReadOnlyMode :Boolean){

        // Set up your Composable here
        composeTestRule.setContent {
            CustomDateTimeField(
                formField = formField, isFormSaved,
                remember {
                    mutableStateOf(sendButtonClick)
                },
                isFormInReadOnlyMode = isFormInReadOnlyMode
            )

        }
    }

}