package com.trimble.ttm.formlibrary.customViews

import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.utils.UiUtil.getDisplayWidth
import com.trimble.ttm.formlibrary.utils.UiUtil.isTablet

abstract class FormDateTimeBaseDialog(
    private val context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    formSaved: Boolean
) : FormEditText(context, textInputLayout, formField, formSaved) {

    abstract val dateTimeDrawableId: Int

    abstract fun showDateTimePickerDialog()

    init {
        apply {
            inputType = InputType.TYPE_NULL
            keyListener = null
            isFocusable = false
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                ResourcesCompat.getDrawable(resources, dateTimeDrawableId, context.theme),
                null
            )
            setOnClickListener {
                showDateTimePickerDialog()
            }
        }
        formFieldManager.isFormFieldNotEditable(isFormSaved,formField.driverEditable).let {
            if(it){
                formFieldManager.assignValueForEditableProperties(!it)
                disableEditText()
            }
        }
    }

    fun getDateTimeDialog(): Dialog =
        Dialog(context, R.style.dialogTheme).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setContentView(R.layout.dialog_date_time)
        }

    fun handleDialogActionsAndShowDialog(dialog: Dialog) {
        dialog.findViewById<Button>(R.id.cancelButton).let {
            it.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.findViewById<Button>(R.id.clearButton).let {
            it.setOnClickListener {
                this.setText("")
                dialog.dismiss()
            }
        }

        var dialogWidth = getDisplayWidth()

        dialogWidth -= if (isTablet(context.applicationContext)) {
            dialogWidth / 4
        } else {
            dialogWidth / 8
        }

        dialog.apply {
            window?.setLayout(
                dialogWidth,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }.also {
            it.show()
        }
    }
}