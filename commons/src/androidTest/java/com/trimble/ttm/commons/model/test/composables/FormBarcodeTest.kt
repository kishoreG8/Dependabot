package com.trimble.ttm.commons.model.test.composables

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trimble.ttm.commons.composable.customViews.CustomBarcodeField
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.BARCODE_FIELD
import com.trimble.ttm.commons.utils.BARCODE_FIELD_ICON
import com.trimble.ttm.commons.utils.BARCODE_LIST_ITEM
import com.trimble.ttm.commons.utils.BARCODE_SCAN_LIST_ITEM
import com.trimble.ttm.commons.utils.CANCEL_BUTTON
import com.trimble.ttm.commons.utils.CLOSE_ICON_LIST_ITEM
import com.trimble.ttm.commons.utils.SAVE_BUTTON
import com.trimble.ttm.commons.utils.TAP_TO_SCAN_CLICK
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormBarcodeTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun testFormBarcodeIsClickableAndNavigatedToScanBarcode() {
        val formField = FormField(qtext = "Barcode Field", required = 1, driverEditable = 1,bcLimitMultiples = 3).also {
            it.uiData="1234223,232323,898998080"
        }

        setBarcodeScanCompose(formField,false,false)

        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD_ICON).assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertHasClickAction()
        composeTestRule.onNodeWithText("1234223,232323,898998080").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).performClick()
        composeTestRule.onNode(hasText("Tap here to scan"))

        composeTestRule.onNodeWithContentDescription(SAVE_BUTTON).assertExists()
        composeTestRule.onNodeWithContentDescription(CANCEL_BUTTON).assertExists()

    }

    @Test
    fun testFormBarcodeNavigatedToScanBarcodeAndClickableInDialog() {
        val formField = FormField(qtext = "Barcode Field", required = 1, driverEditable = 1,bcLimitMultiples = 3).also {
            it.uiData="1234223,232323,898998080"
        }

        setBarcodeScanCompose(formField,false,false)

        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertExists()
        composeTestRule.onNodeWithText("1234223,232323,898998080").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).performClick()

        composeTestRule.onNode(hasText("Tap here to scan"))
        composeTestRule.onNodeWithContentDescription(TAP_TO_SCAN_CLICK).assertExists()
        composeTestRule.onNodeWithContentDescription(TAP_TO_SCAN_CLICK).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(TAP_TO_SCAN_CLICK).performClick()
    }

    @Test
    fun testFormBarcodeNavigatedToScanBarcodeAndCheckListDataIsShown() {
        val formField = FormField(qtext = "Barcode Field", required = 1, driverEditable = 1,bcLimitMultiples = 3).also {
            it.uiData="1234223,232323,898998080"
        }

        setBarcodeScanCompose(formField,false,false)

        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertExists()
        composeTestRule.onNodeWithText("1234223,232323,898998080").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).performClick()

        composeTestRule.onNode(hasText("Tap here to scan"))

        composeTestRule.onAllNodesWithContentDescription(BARCODE_LIST_ITEM).assertCountEquals(3)
        composeTestRule.onAllNodesWithContentDescription(BARCODE_SCAN_LIST_ITEM).assertCountEquals(3)
        composeTestRule.onAllNodesWithContentDescription(CLOSE_ICON_LIST_ITEM).assertCountEquals(3)
    }

    @Test
    fun testFormBarcodeNavigatedToScanBarcodeAndCheckListIsNotShown() {
        val formField = FormField(qtext = "Barcode Field", driverEditable = 1,bcLimitMultiples = 3).also {
            it.uiData=""
        }

        setBarcodeScanCompose(formField,false,false)

        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).performClick()

        composeTestRule.onNode(hasText("Tap here to scan"))

        composeTestRule.onNodeWithContentDescription(BARCODE_LIST_ITEM).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(BARCODE_SCAN_LIST_ITEM).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(CLOSE_ICON_LIST_ITEM).assertDoesNotExist()
    }

    @Test
    fun testBarcodeDialogSaveButtonClick() {
        val formField = FormField(qtext = "Barcode Field", required = 1, driverEditable = 1,bcLimitMultiples = 3).also {
            it.uiData="1234223"
        }

        setBarcodeScanCompose(formField,false,false)

        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD_ICON).assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertHasClickAction()
        composeTestRule.onNodeWithText("1234223").assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).performClick()
        composeTestRule.onNode(hasText("Tap here to scan"))

        composeTestRule.onNodeWithContentDescription(SAVE_BUTTON).assertExists()
        composeTestRule.onNodeWithContentDescription(CANCEL_BUTTON).assertExists()

        composeTestRule.onNodeWithContentDescription(CLOSE_ICON_LIST_ITEM).assertExists()
        composeTestRule.onNodeWithContentDescription(CLOSE_ICON_LIST_ITEM).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(CLOSE_ICON_LIST_ITEM).performClick()

        composeTestRule.onNodeWithContentDescription(SAVE_BUTTON).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(SAVE_BUTTON).performClick()
        composeTestRule.onNodeWithText("1234223").assertDoesNotExist()
    }

    @Test
    fun testBarcodeDialogCancelButtonClick() {
        val formField = FormField(qtext = "Barcode Field", required = 1, driverEditable = 1,bcLimitMultiples = 3).also {
            it.uiData="1234223"
        }

        setBarcodeScanCompose(formField,false,false)

        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD_ICON).assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertHasClickAction()
        composeTestRule.onNodeWithText("1234223").assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).performClick()
        composeTestRule.onNode(hasText("Tap here to scan"))

        composeTestRule.onNodeWithContentDescription(SAVE_BUTTON).assertExists()
        composeTestRule.onNodeWithContentDescription(CANCEL_BUTTON).assertExists()

        composeTestRule.onNodeWithContentDescription(CLOSE_ICON_LIST_ITEM).assertExists()
        composeTestRule.onNodeWithContentDescription(CLOSE_ICON_LIST_ITEM).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(CLOSE_ICON_LIST_ITEM).performClick()

        composeTestRule.onNodeWithContentDescription(CANCEL_BUTTON).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(CANCEL_BUTTON).performClick()
        composeTestRule.onNodeWithText("1234223").assertExists()
    }

    @Test
    fun testFormBarcodeIsClickableAndNotNavigatedToScanBarcode() {
        val formField = FormField(qtext = "Barcode Field", required = 1, driverEditable = 0,bcLimitMultiples = 3).also {
            it.uiData="1234223,232323,898998080"
        }

        setBarcodeScanCompose(formField,true,true)


        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertExists()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD_ICON).assertExists()
        composeTestRule.onNodeWithText("1234223").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(BARCODE_FIELD).performClick()
        composeTestRule.onNode(hasText("Tap here to scan")).assertDoesNotExist()
    }

    private fun setBarcodeScanCompose(formField : FormField, isFormSaved : Boolean, isReadOnlyMode : Boolean){

        val sendButtonClickEventListener = MutableSharedFlow<Boolean>()

        // Set up your Composable here
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            scope.launch{
                sendButtonClickEventListener.emit(false)
            }
            CustomBarcodeField(
                formField = formField,
                isFormSaved = isFormSaved,
                sendButtonState = sendButtonClickEventListener.collectAsState(initial = false),
                isFormInReadOnlyMode = isReadOnlyMode
            )
        }
    }


}