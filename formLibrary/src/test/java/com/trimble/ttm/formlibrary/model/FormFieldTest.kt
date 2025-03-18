package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.commons.model.*
import com.trimble.ttm.formlibrary.utils.FormUtils
import org.junit.Assert
import org.junit.Test
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormFieldTest {

    @Test
    fun checkIfFieldIsDriverEditableOrNot(){
        assertTrue(FormField(driverEditable = 1).isDriverEditable())
        assertFalse(FormField(driverEditable = 0).isDriverEditable())
    }

    @Test
    fun checkIfFieldIsDispatcherEditableOrNot(){
        assertTrue(FormField(dispatchEditable = 1).isDispatcherEditable())
        assertFalse(FormField(dispatchEditable = 0).isDispatcherEditable())
    }

    @Test
    fun `verify driver non editable field in dispatcher form` (){
        val formField = FormField(driverEditable = 0)
        formField.isInDriverForm = false
        assertFalse(formField.checkForDriverNonEditableFieldInDriverForm())

    }

    @Test
    fun `verify driver non editable field in driver form` (){
        val formField = FormField(driverEditable = 0)
        formField.isInDriverForm = true
        assertTrue(formField.checkForDriverNonEditableFieldInDriverForm())

    }

    @Test
    fun `verify driver editable field in driver form` (){
        val formField = FormField(driverEditable = 1)
        formField.isInDriverForm = true
        assertFalse(formField.checkForDriverNonEditableFieldInDriverForm())

    }

    @Test
    fun `verify driver editable field in dispatcher form` (){
        val formField = FormField(driverEditable = 1)
        formField.isInDriverForm = false
        assertFalse(formField.checkForDriverNonEditableFieldInDriverForm())

    }

    @Test
    fun `verify processing of form loop fields not required if next form field is not of loop end type`(){
        val currentFormField=FormField(qnum = FormFieldType.TEXT.ordinal)
        val emptyFormFieldStack = Stack<FormField>()
        Assert.assertEquals(
            false,
            FormUtils.isProcessLoopFieldsRequired(
                getFormTemplateWithLoopStartField(),
                currentFormField,
                emptyFormFieldStack
            )
        )
    }

    @Test
    fun `verify processing of form loop fields not required if form fields stack is empty`(){
        val currentFormField=FormField(qnum = FormFieldType.TEXT.ordinal)
        val emptyFormFieldStack = Stack<FormField>()
        Assert.assertEquals(
            false,
            FormUtils.isProcessLoopFieldsRequired(
                getFormTemplateWithLoopEndField(),
                currentFormField,
                emptyFormFieldStack
            )
        )
    }

    @Test
    fun `verify processing of form loop fields required if multiple choice with no targets`(){
        val currentFormField=FormField(qnum = FormFieldType.TEXT.ordinal)
        val formFieldStack = Stack<FormField>()
        formFieldStack.push(
            getFormFieldOfMultipleChoiceButChoicesHaveNoTarget()
        )
        Assert.assertEquals(
            true,
            FormUtils.isProcessLoopFieldsRequired(
                getFormTemplateWithLoopEndField(),
                currentFormField,
                formFieldStack
            )
        )
    }

    @Test
    fun `verify processing of form loop fields required if multiple choice with targets`(){
        val currentFormField=getFormFieldOfMultipleChoiceAndHaveTargetId()
        val formFieldStack = Stack<FormField>()
        formFieldStack.push(
            getFormFieldOfMultipleChoiceButChoicesHaveNoTarget()
        )
        Assert.assertEquals(
            false,
            FormUtils.isProcessLoopFieldsRequired(
                getFormTemplateWithLoopEndField(),
                currentFormField,
                formFieldStack
            )
        )
    }


    private fun getFormTemplateWithLoopStartField(): FormTemplate {
        val formFieldArray = arrayListOf(
            FormField(
                qnum = FormFieldType.LOOP_START.ordinal,
                qtext = "Loop_start"
            )
        )
        return FormTemplate(
            formFieldsList = formFieldArray
        )
    }

    private fun getFormTemplateWithLoopEndField(): FormTemplate {
        val formFieldArray = arrayListOf(
            FormField(
                qnum = FormFieldType.LOOP_END.ordinal,
                qtext = "Loop_End",
                qtype = FormFieldType.LOOP_END.ordinal
            )
        )
        return FormTemplate(
            formFieldsList = formFieldArray
        )
    }

    private fun getFormFieldOfMultipleChoiceButChoicesHaveNoTarget():FormField{
        val formChoice1=FormChoice(qnum = 1, choicenum = 1, value = "Yes", formid = 100)
        val formChoice2=FormChoice(qnum = 2, choicenum = 2, value = "No", formid = 100)
        val formField=FormField(qtype = 2, formid = 100, displayText = "FormField1")
        formField.formChoiceList= arrayListOf(formChoice1,formChoice2)
        return formField
    }


    @Test
    fun `verify form field is of multiple choice but choices have no target`(){
        Assert.assertEquals(
            false,
            getFormFieldOfMultipleChoiceButChoicesHaveNoTarget().multipleChoiceDriverInputNeeded()
        )
    }

    @Test
    fun `verify form field is of multiple choice choices have target`(){
        Assert.assertEquals(
            true,
            getFormFieldOfMultipleChoiceAndHaveTargetId().multipleChoiceDriverInputNeeded()
        )
    }

    private fun getFormFieldOfMultipleChoiceAndHaveTargetId(): FormField {
        val formChoice1 = FormChoice(qnum = 1, choicenum = 1, value = "Yes", formid = 100, 12)
        val formChoice2 = FormChoice(qnum = 2, choicenum = 2, value = "No", formid = 100)
        val formField = FormField(qtype = 2, formid = 100, displayText = "FormField1")
        formField.formChoiceList = arrayListOf(formChoice1, formChoice2)
        return formField
    }

    @Test
    fun `verify form field is of multiple choice but choices have no target id`(){
        val formChoice1=FormChoice(qnum = 1, choicenum = 1, value = "Yes", formid = 100)
        val formChoice2=FormChoice(qnum = 2, choicenum = 2, value = "No", formid = 100)
        val formField=FormField(qtype = 2, formid = 100, displayText = "FormField1")
        formField.formChoiceList= arrayListOf(formChoice1,formChoice2)

        Assert.assertEquals(true, formField.isNotMultipleChoiceOrMultipleChoiceWithNoTarget())
    }
}