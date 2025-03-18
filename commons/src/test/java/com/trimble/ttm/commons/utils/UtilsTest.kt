package com.trimble.ttm.commons.utils

import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkIfTheFieldIsEditable
import com.trimble.ttm.commons.composable.utils.formfieldutils.isTrailingIconVisible
import com.trimble.ttm.commons.composable.utils.formfieldutils.restoreFormFieldUiDataAfterConfigChange
import com.trimble.ttm.commons.composable.utils.formfieldutils.shouldClearSignatureCanvas
import com.trimble.ttm.commons.composable.utils.formfieldutils.valueShouldContainNegativeSymbolAtFront
import com.trimble.ttm.commons.composable.utils.formfieldutils.valueShouldContainUtmostOneDecimalPoint
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilsTest {

    @RelaxedMockK
    private lateinit var bundle: Bundle

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }


    @Test
    fun `check if the field is editable or not when driver editable is set to 0 and form is saved`() {
        assertFalse(
            checkIfTheFieldIsEditable(
                formField = FormField(driverEditable = 0),
                isFormSaved = true
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns true when isEditable is true, isFormSaved is false and isReadyOnlyView is false`() {
        assertTrue(
            isTrailingIconVisible(
                fieldValue = "TextField",
                isFormSaved = false,
                isEditable = true,
                isReadOnlyView = false
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns false when field value is empty, isEditable is true, isFormSaved is false and isReadyOnlyView is false`() {
        assertFalse(
            isTrailingIconVisible(
                fieldValue = EMPTY_STRING,
                isFormSaved = false,
                isEditable = true,
                isReadOnlyView = false
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns false when isEditable is true, isFormSaved is true and isReadyOnlyView is false`() {
        assertFalse(
            isTrailingIconVisible(
                fieldValue = "TextField",
                isFormSaved = true,
                isEditable = true,
                isReadOnlyView = false
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns false when  isEditable is false, isFormSaved is false and isReadyOnlyView is false`() {
        assertFalse(
            isTrailingIconVisible(
                fieldValue = "TextField",
                isFormSaved = false,
                isEditable = false,
                isReadOnlyView = false
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns false when  isEditable is false, isFormSaved is false and isReadyOnlyView is true`() {
        assertFalse(
            isTrailingIconVisible(
                fieldValue = "TextField",
                isFormSaved = false,
                isEditable = false,
                isReadOnlyView = true
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns false when field value is empty, isEditable is false, isFormSaved is true and isReadyOnlyView is true`() {
        assertFalse(
            isTrailingIconVisible(
                fieldValue = EMPTY_STRING,
                isFormSaved = true,
                isEditable = false,
                isReadOnlyView = true
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns false when field value is empty, isEditable is true, isFormSaved is true and isReadyOnlyView is true`() {
        assertFalse(
            isTrailingIconVisible(
                fieldValue = EMPTY_STRING,
                isFormSaved = true,
                isEditable = true,
                isReadOnlyView = true
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns false when isEditable is false, isFormSaved is true and isReadyOnlyView is true`() {
        assertFalse(
            isTrailingIconVisible(
                fieldValue = "TextField",
                isFormSaved = true,
                isEditable = false,
                isReadOnlyView = true
            )
        )
    }

    @Test
    fun `check if the trailingIconVisible returns false when isEditable is true, isFormSaved is true and isReadyOnlyView is true`() {
        assertFalse(
            isTrailingIconVisible(
                fieldValue = "TextField",
                isFormSaved = true,
                isEditable = true,
                isReadOnlyView = true
            )
        )
    }

    @Test
    fun `check the formField data gets restored after config change`(){
        val textValue = mutableStateOf("TextField")
        val formField = FormField()
        restoreFormFieldUiDataAfterConfigChange(textState = textValue, formField = formField)
        assertEquals(textValue.value,formField.uiData)
    }

    @Test
    fun `check valueShouldContainUtmostOneDecimalPoint returns true when value contains one decimal point`(){
        assertTrue(valueShouldContainUtmostOneDecimalPoint(value = "123.43"))
    }

    @Test
    fun `check valueShouldContainUtmostOneDecimalPoint returns true when value doesn't have any decimal points`(){
        assertTrue(valueShouldContainUtmostOneDecimalPoint(value = "12343"))
    }

    @Test
    fun `check valueShouldContainUtmostOneDecimalPoint returns false when value have more than one decimal points`(){
        assertFalse(valueShouldContainUtmostOneDecimalPoint(value = "12.343."))
    }

    @Test
    fun `check valueShouldContainNegativeSymbolAtFront returns true when value contain negative symbol at front`(){
        assertTrue(valueShouldContainNegativeSymbolAtFront(value = "-123.23"))
    }

    @Test
    fun `check valueShouldContainNegativeSymbolAtFront returns true when value doesn't have any negative symbol`(){
        assertTrue(valueShouldContainNegativeSymbolAtFront(value = "123.23"))
    }

    @Test
    fun `check valueShouldContainNegativeSymbolAtFront returns false when value contain negative symbol at random positions`(){
        assertFalse(valueShouldContainNegativeSymbolAtFront(value = "12-3.23"))
    }

    @Test
    fun `check valueShouldContainNegativeSymbolAtFront returns false when value contain more than one negative symbol`(){
        assertFalse(valueShouldContainNegativeSymbolAtFront(value = "-13.2-3"))
    }

    @Test
    fun `check valueShouldContainNegativeSymbolAtFront returns false when value contain more than one negative symbol at random positions`(){
        assertFalse(valueShouldContainNegativeSymbolAtFront(value = "-12-3.2-3"))
    }

    @Test
    fun `check whether the entire signature canvas returns true when dialog state is true and ui data is empty`(){
        val formField = FormField(qtype = FormFieldType.SIGNATURE_CAPTURE.ordinal)
        formField.uiData = EMPTY_STRING
        assertTrue(shouldClearSignatureCanvas(signatureDialogState = true, formField = formField))
    }

    @Test
    fun `check whether the entire signature canvas returns true when dialog state is false and ui data is empty`(){
        val formField = FormField(qtype = FormFieldType.SIGNATURE_CAPTURE.ordinal)
        formField.uiData = EMPTY_STRING
        assertFalse(shouldClearSignatureCanvas(signatureDialogState = false, formField = formField))
    }

    @Test
    fun `check whether should clear last drawn signature returns true when dialog state is true and uidata is not empty`(){
        val formField = FormField(qtype = FormFieldType.SIGNATURE_CAPTURE.ordinal)
        formField.uiData = "1256126526"
        assertFalse(shouldClearSignatureCanvas(signatureDialogState = true, formField = formField))
    }

    @Test
    fun `check whether should clear last drawn signature returns when dialog state is false and uidata is not empty`(){
        val formField = FormField(qtype = FormFieldType.SIGNATURE_CAPTURE.ordinal)
        formField.uiData = "1256126526"
        assertFalse(shouldClearSignatureCanvas(signatureDialogState = false, formField = formField))
    }

    @Test
    fun `check getSafeString when key is present in bundle`() {
        every { bundle.getString(any()) } returns "Vector"
        assertEquals("Vector",bundle.getStringOrDefaultValue(APP_NAME_KEY))
    }

    @Test
    fun `check getSafeString when key is not present in bundle`() {
        every { bundle.getString(any()) } returns null
        assertEquals(EMPTY_STRING, bundle.getStringOrDefaultValue(APP_NAME_KEY))
    }

    @Test
    fun `check getSafeString when bundle is null`(){
        val bundle1 : Bundle? = null
        assertEquals(EMPTY_STRING,bundle1.getStringOrDefaultValue(APP_NAME_KEY))
    }


}