package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.model.FormField

@SuppressLint("ViewConstructor")
class FormPassword(
    context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    formSaved: Boolean
) : FormEditText(context, textInputLayout, formField, formSaved) {

    init {
        assignDefaultParams()
        formFieldManager.isFormFieldNotEditable(isFormSaved,formField.driverEditable).let {
            if(it){
                formFieldManager.assignValueForEditableProperties(!it)
                disableEditText()
            }
        }

    }

    private fun assignDefaultParams() {
        this.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
}