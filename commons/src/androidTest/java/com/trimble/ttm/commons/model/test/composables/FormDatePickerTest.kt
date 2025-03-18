package com.trimble.ttm.commons.model.test.composables

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trimble.ttm.commons.composable.customViews.CustomDateField
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.utils.DATE_FIELD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormDatePickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun testFormDateFieldWithRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Date Field Test"
        ).also {
            it.uiData=""
        }

        setDateFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_FIELD).assertExists()
        composeTestRule.onNodeWithText("Date Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertDoesNotExist()

    }

    @Test
    fun testFormDateFieldWithRequiredAndInputValidation() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Date Field Test"
        ).also {
            it.uiData="05/05/2023"
        }

        setDateFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_FIELD).assertExists()
        composeTestRule.onNodeWithText("Date Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("05/05/23").assertIsDisplayed()
    }

    @Test
    fun testFormDateFieldWithRequiredAndSendButtonClicked() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Date Field Test"
        ).also {
            it.uiData=""
        }

        setDateFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = true,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_FIELD).assertExists()
        composeTestRule.onNodeWithText("Date Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertIsDisplayed()

    }

    @Test
    fun testFormDateFieldWithRequiredAndFormSavedValidation() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Date Field Test"
        ).also {
            it.uiData="04/12/2023"
        }

        setDateFieldCompose(formField,
            isFormSaved = true,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_FIELD).assertExists()
        composeTestRule.onNodeWithText("Date Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("12/04/23").assertIsDisplayed()

    }

    @Test
    fun testFormDateFieldWithNotRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.DATE.ordinal,
            required = 0,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Date Field Test"
        ).also {
            it.uiData=""
        }

        setDateFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(DATE_FIELD).assertExists()
        composeTestRule.onNodeWithText("Date Field Test").assertIsDisplayed()

    }

    private fun setDateFieldCompose(formField: FormField,isFormSaved:Boolean, sendButtonClick :Boolean,isFormInReadOnlyMode :Boolean){

        // Set up your Composable here
        composeTestRule.setContent {
            CustomDateField(
                formField = formField, isFormSaved,
                remember {
                    mutableStateOf(sendButtonClick)
                },
                isFormInReadOnlyMode = isFormInReadOnlyMode
            )

        }
    }

}