package com.trimble.ttm.formlibrary.manager

import com.trimble.ttm.formlibrary.utils.IS_FORM_FIELD_DRIVER_EDITABLE
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormFieldManagerTest {


    @Test
    fun `isFormFieldNotEditable returns true when form is saved`() {
        val formFieldManager = FormFieldManager()
        val result = formFieldManager.isFormFieldNotEditable(isFormSaved = true, isFieldEditable = IS_FORM_FIELD_DRIVER_EDITABLE)
        assertTrue(result)
    }

    @Test
    fun `isFormFieldNotEditable returns true when field is not editable`() {
        val formFieldManager = FormFieldManager()
        val result = formFieldManager.isFormFieldNotEditable(isFormSaved = false, isFieldEditable = 0)
        assertTrue(result)
    }

    @Test
    fun `isFormFieldNotEditable returns false when form is not saved and field is editable`() {
        val formFieldManager = FormFieldManager()
        val result = formFieldManager.isFormFieldNotEditable(isFormSaved = false, isFieldEditable = IS_FORM_FIELD_DRIVER_EDITABLE)
        assertFalse(result)
    }

    @Test
    fun `verify if form field not editable saved form editable field`(){
        val formFieldManager = FormFieldManager()
        formFieldManager.assignValueForEditableProperties(!formFieldManager.isFormFieldNotEditable(isFormSaved = true, isFieldEditable = 1))
        assertFalse(formFieldManager.isEnabled)
        assertFalse(formFieldManager.isFocusable)
        assertFalse(formFieldManager.isClickable)
    }

    @Test
    fun `verify if form field not editable saved form not editable field`(){
        val formFieldManager = FormFieldManager()
        formFieldManager.assignValueForEditableProperties(!formFieldManager.isFormFieldNotEditable(isFormSaved = true, isFieldEditable = 0))
        assertFalse(formFieldManager.isEnabled)
        assertFalse(formFieldManager.isFocusable)
        assertFalse(formFieldManager.isClickable)
    }

    @Test
    fun `verify if form field editable unsaved form editable field`(){
        val formFieldManager = FormFieldManager()
        formFieldManager.assignValueForEditableProperties(!formFieldManager.isFormFieldNotEditable(isFormSaved = false, isFieldEditable = 1))
        assertTrue(formFieldManager.isEnabled)
        assertTrue(formFieldManager.isFocusable)
        assertTrue(formFieldManager.isClickable)
    }

    @Test
    fun `check if form field not editable unsaved form not editable field`(){
        val formFieldManager = FormFieldManager()
        formFieldManager.assignValueForEditableProperties(!formFieldManager.isFormFieldNotEditable(isFormSaved = false, isFieldEditable = 0))
        assertFalse(formFieldManager.isEnabled)
        assertFalse(formFieldManager.isFocusable)
        assertFalse(formFieldManager.isClickable)
    }


}