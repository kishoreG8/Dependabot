package com.trimble.ttm.formlibrary.manager

import com.trimble.ttm.formlibrary.utils.IS_FORM_FIELD_DRIVER_EDITABLE


class FormFieldManager {

    var isEnabled : Boolean = true
        private set
    var isFocusable : Boolean = true
        private set
    var isClickable : Boolean = true
        private set

    fun isFormFieldNotEditable(isFormSaved : Boolean, isFieldEditable: Int) : Boolean =
         isFormSaved || (isFieldEditable != IS_FORM_FIELD_DRIVER_EDITABLE)

    fun assignValueForEditableProperties(isEditable : Boolean){
        isEnabled = isEditable
        isClickable = isEditable
        isFocusable = isEditable
    }
}