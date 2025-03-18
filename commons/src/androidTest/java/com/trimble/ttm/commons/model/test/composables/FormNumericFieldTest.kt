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
import com.trimble.ttm.commons.composable.customViews.CustomNumericField
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.utils.CUSTOM_NUMERIC_FIELD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormNumericFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun testFormNumericFieldWithRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.NUMERIC_ENHANCED.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Numeric Field Test"
        ).also {
            it.uiData=""
        }

        setNumericFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_NUMERIC_FIELD).assertExists()
        composeTestRule.onNodeWithText("Numeric Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertDoesNotExist()

    }

    @Test
    fun testFormNumericFieldWithRequiredAndInputValidation() {
        val formField = FormField(
            qtype = FormFieldType.NUMERIC_ENHANCED.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Numeric Field Test"
        ).also {
            it.uiData="Type something"
        }

        setNumericFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_NUMERIC_FIELD).assertExists()
        composeTestRule.onNodeWithText("Numeric Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Type something").assertIsDisplayed()
    }

    @Test
    fun testFormNumericFieldWithRequiredAndSendButtonClicked() {
        val formField = FormField(
            qtype = FormFieldType.NUMERIC_ENHANCED.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Numeric Field Test"
        ).also {
            it.uiData=""
        }

        setNumericFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = true,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_NUMERIC_FIELD).assertExists()
        composeTestRule.onNodeWithText("Numeric Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("*Cannot be empty").assertIsDisplayed()

    }

    @Test
    fun testFormNumericFieldWithRequiredAndFormSavedValidation() {
        val formField = FormField(
            qtype = FormFieldType.NUMERIC_ENHANCED.ordinal,
            required = 1,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Numeric Field Test"
        ).also {
            it.uiData="Type something"
        }

        setNumericFieldCompose(formField,
            isFormSaved = true,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_NUMERIC_FIELD).assertExists()
        composeTestRule.onNodeWithText("Numeric Field Test (Required) *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Type something").assertIsDisplayed()

    }

    @Test
    fun testFormNumericFieldWithNotRequiredValidation() {
        val formField = FormField(
            qtype = FormFieldType.NUMERIC_ENHANCED.ordinal,
            required = 0,
            numspTsep = 1,
            numspDec = 0,
            bcMinLength = -1,
            bcMaxLength = 500,
            qtext = "Numeric Field Test"
        ).also {
            it.uiData=""
        }

        setNumericFieldCompose(formField,
            isFormSaved = false,
            sendButtonClick = false,
            isFormInReadOnlyMode = false
        )

        composeTestRule.onNodeWithContentDescription(CUSTOM_NUMERIC_FIELD).assertExists()
        composeTestRule.onNodeWithText("Numeric Field Test").assertIsDisplayed()

    }






    private fun setNumericFieldCompose(formField: FormField,isFormSaved:Boolean, sendButtonClick :Boolean,isFormInReadOnlyMode :Boolean){

        // Set up your Composable here
        composeTestRule.setContent {
            CustomNumericField(
                formField = formField, isFormSaved,
                remember {
                    mutableStateOf(sendButtonClick)
                },
                ImeAction.Next,
                LocalFocusManager.current,
                isFormInReadOnlyMode = isFormInReadOnlyMode
            )

        }
    }


}