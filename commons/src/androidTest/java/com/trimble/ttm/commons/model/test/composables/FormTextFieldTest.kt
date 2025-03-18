package com.trimble.ttm.commons.model.test.composables

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.ImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trimble.ttm.commons.composable.customViews.CustomTextField
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.utils.CUSTOM_TEXT_FIELD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormTextFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun testFormTextFieldWithRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.TEXT.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Text Field Test"
        ).also {
            it.uiData=""
        }

        setTextFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_TEXT_FIELD).assertExists()
        composeTestRule.onNodeWithText("Text Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertDoesNotExist()

    }

    @Test
    fun testFormTextFieldWithRequiredAndInputValidation() {
        val formField = FormField(
            qtype = FormFieldType.TEXT.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Text Field Test"
        ).also {
            it.uiData="Type something"
        }

        setTextFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_TEXT_FIELD).assertExists()
        composeTestRule.onNodeWithText("Text Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Type something").assertIsDisplayed()
    }

    @Test
    fun testFormTextFieldWithRequiredAndSendButtonClicked() {
        val formField = FormField(
            qtype = FormFieldType.TEXT.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Text Field Test"
        ).also {
            it.uiData=""
        }

        setTextFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = true,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_TEXT_FIELD).assertExists()
        composeTestRule.onNodeWithText("Text Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertIsDisplayed()

    }

    @Test
    fun testFormTextFieldWithRequiredAndFormSavedValidation() {
        val formField = FormField(
            qtype = FormFieldType.TEXT.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Text Field Test"
        ).also {
            it.uiData="Type something"
        }

        setTextFieldCompose(formField,
            isFormSaved = true,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_TEXT_FIELD).assertExists()
        composeTestRule.onNodeWithText("Text Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Type something").assertIsDisplayed()

    }

    @Test
    fun testFormTextFieldWithNotRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.TEXT.ordinal,
            required = 0,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Text Field Test"
        ).also {
            it.uiData=""
        }

        setTextFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_TEXT_FIELD).assertExists()
        composeTestRule.onNodeWithText("Text Field Test").assertIsDisplayed()

    }






    private fun setTextFieldCompose(formField: FormField,isFormSaved:Boolean, sendButtonClick :Boolean,isFormInReadOnlyMode :Boolean){

        // Set up your Composable here
        composeTestRule.setContent {
            CustomTextField(
                formField = formField, ImeAction.Next,isFormSaved,
                remember {
                    mutableStateOf(sendButtonClick)
                },
                LocalFocusManager.current,
                isFormInReadOnlyMode = isFormInReadOnlyMode
            )

        }
    }


}