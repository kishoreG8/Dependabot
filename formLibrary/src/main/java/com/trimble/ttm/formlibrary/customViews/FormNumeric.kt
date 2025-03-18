package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils.isEmpty
import android.text.TextWatcher
import android.view.Gravity
import android.view.View.OnFocusChangeListener
import androidx.compose.ui.semantics.text
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.utils.COMMA
import com.trimble.ttm.formlibrary.utils.DecimalDigitsInputFilter
import com.trimble.ttm.formlibrary.utils.FormUtils.checkNumericFieldIsNotValid
import com.trimble.ttm.formlibrary.utils.FormUtils.checkValidityOfTheField
import com.trimble.ttm.formlibrary.utils.FormUtils.isNumericFieldNotValidWithinMaxMinRange
import com.trimble.ttm.formlibrary.utils.NUMERIC_THOUSAND_SEPARATOR_PATTERN
import com.trimble.ttm.formlibrary.utils.UiUtil
import com.trimble.ttm.formlibrary.utils.Utils.isNumeric
import com.trimble.ttm.formlibrary.utils.ext.afterTextChanged
import com.trimble.ttm.formlibrary.utils.ext.textChanged
import com.trimble.ttm.formlibrary.utils.toSafeDouble
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

@SuppressLint("ViewConstructor")
class FormNumeric(
    context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    formSaved: Boolean
) : FormEditText(context, textInputLayout, formField, formSaved) {

    init {
        this.inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        formField.numspDec?.let {
            if (it == 0) {  //Decimals are not allowed.So removing decimal input capability
                this.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
        }
        if (formField.driverEditable == NOT_EDITABLE && formField.isInDriverForm) {
            disableEditText()

        } else {
            this.isClickable = !formSaved
            isEnabled = !formSaved
        }
        formField.numspTsep?.let {
            initFieldThousandsSeparateListener(formField)
        }

        initFieldOnFocusChangeListener(formField)

        // Add TextWatcher for prepending "0" before "."
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.startsWith(".") == true) {
                    setText("0$s")
                    text?.let { setSelection(it.length) } // Set cursor to the end
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun initFieldThousandsSeparateListener(
        formField: FormField
    ) {
        afterTextChanged { textWatcher, s ->
            removeTextChangedListener(textWatcher)

            var formattedString = s
            try {
                if (formattedString.contains(COMMA)) {
                    formattedString = formattedString.replace(COMMA, "")
                }

                formField.numspPre?.let { numspPre ->
                    if (formattedString.contains(numspPre))
                        formattedString = formattedString.replace(numspPre, "")
                }

                if(formField.numspTsep == 1){
                    formattedString =
                        (NumberFormat.getNumberInstance(Locale.getDefault()) as DecimalFormat).apply {
                            applyPattern(NUMERIC_THOUSAND_SEPARATOR_PATTERN)
                        }.format(formattedString.toSafeDouble())

                }
                //set formatted text to EditText
                formField.numspPre?.let { formattedString = it.trim() + formattedString }
                setText(formattedString)
                setSelection(length())
            } catch (nfe: NumberFormatException) {
                //Do nothing now.log it
                Log.i("number format error", "input number format error$nfe")
            }

            addTextChangedListener(textWatcher)
        }
    }

    //On Focus change listener
    private fun initFieldOnFocusChangeListener(
        formField: FormField
    ) {
        initOnTextChangeListener(formField)
        onFocusChangeListener =
            OnFocusChangeListener { view, hasFocus ->
                if (!hasFocus && !isEmpty(text) && !checkValidityOfTheField(text.toString(),formField)) {
                    textInputLayout?.error =
                        context.getString(R.string.form_not_a_valid_number)
                } else {
                    formField.errorMessage = ""
                }

                textInputLayout?.editText!!.gravity =
                    Gravity.END //By default the text should be aligned to the end side of text input layout.

                //Left justify will align the text to start of the text input layout.
                formField.numspLeftjust?.let {
                    if (it == 0) {
                        textInputLayout?.editText!!.gravity = Gravity.START
                    }
                }

                if(hasFocus  && (isFormSaved || formField.driverEditable == NOT_EDITABLE)){
                    //Code to hide keypad if user clicks on non editable field while currently on editable field with keypad open
                    UiUtil.hideKeyboard(context,view)
                }
            }
    }

    private fun initOnTextChangeListener(formField: FormField) {
        //Decimal limit filter
        formField.numspDec?.let {
            filters = arrayOf<InputFilter>(DecimalDigitsInputFilter(it))
        }
        textChanged {
            when {
                !isEmpty(text.toString()) && !isNumeric(text.toString(), formField.numspPre) -> {
                    textInputLayout?.error =
                        context.getString(R.string.form_not_a_valid_number)
                }
                isNumericFieldNotValidWithinMaxMinRange(text.toString(),formField)-> {
                    textInputLayout?.error =
                        context.getString(R.string.form_number_not_in_range) + formField.numspMin + ", " + formField.numspMax
                }
                checkNumericFieldIsNotValid(text.toString(),formField) -> {
                    textInputLayout?.error = null
                    formField.errorMessage = ""
                }
            }

            textInputLayout?.error?.let { formField.errorMessage = it.toString() }
        }
    }
}