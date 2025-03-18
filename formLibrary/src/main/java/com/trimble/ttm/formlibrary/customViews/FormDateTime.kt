package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.checkForDriverNonEditableFieldInDriverForm
import com.trimble.ttm.commons.utils.DateUtil.convertToSystemDateTimeFormat
import com.trimble.ttm.commons.utils.DateUtil.getDate
import com.trimble.ttm.commons.utils.DateUtil.getSystemDateFormat
import com.trimble.ttm.commons.utils.DateUtil.getSystemDateTimeFormatString
import com.trimble.ttm.commons.utils.DateUtil.getSystemTimeFormat
import com.trimble.ttm.commons.utils.SPACE
import com.trimble.ttm.formlibrary.R
import java.util.*


@SuppressLint("ViewConstructor")
open class FormDateTime(
    private val context: Context,

    textInputLayout: TextInputLayout,
    formField: FormField,
    formSaved: Boolean
) : FormEditText(context, textInputLayout, formField, formSaved) {

    private val amPmIdentifier = "a"
    private val DATE_TIME_WITH_SECONDS_FORMAT = getSystemDateTimeFormatString(context)
    private val TAG = "FormDateTime"

    init {
        this.id = formField.qnum
        assignDefaultParam()
        if (formField.uiData.isNotEmpty()) {
            formField.isSystemFormattable = true
            setText(convertToSystemDateTimeFormat(formField.uiData, context))
            formField.isSystemFormattable = false
        }
        formFieldManager.isFormFieldNotEditable(isFormSaved,formField.driverEditable).let {
            if(it){
                formFieldManager.assignValueForEditableProperties(!it)
                disableEditText()
            }
        }
    }

    private fun assignDefaultParam() {
        this.apply {
            inputType = InputType.TYPE_NULL
            keyListener = null
            isFocusable = false
            if (formField?.checkForDriverNonEditableFieldInDriverForm() == true)
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null,
                    null,
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_date_time_dark_grey,
                        context.theme
                    ),
                    null
                )
            else
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null,
                    null,
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_date_range_white,
                        context.theme
                    ),
                    null
                )
            setOnClickListener {
                this.showDateTimePickerDialog()
            }
        }
    }

    private fun showDateTimePickerDialog() {
        val simpleSystemDateFormat =
            getSystemDateFormat(context)

        val simpleSystemTimeFormat =
            getSystemTimeFormat(context)

        val dialog = Dialog(context, R.style.dialogTheme).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setContentView(R.layout.dialog_date_time)
        }

        val calendar = Calendar.getInstance()
        this.text?.let {
            if (it.toString().isEmpty()) return@let
            calendar.time = parseDateTime(it.toString())
        }

        val datePicker: DatePicker = dialog.findViewById(R.id.datePicker)
        datePicker.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        val timePicker: TimePicker = dialog.findViewById(R.id.timePicker)
        timePicker.apply {
            hour = calendar.get(Calendar.HOUR_OF_DAY)
            minute = calendar.get(Calendar.MINUTE)
            setIs24HourView(!simpleSystemTimeFormat.toLocalizedPattern().contains(amPmIdentifier))
        }


        val okButton: Button = dialog.findViewById(R.id.okButton)
        okButton.setOnClickListener {
            val okButtonCalendar = Calendar.getInstance()
            okButtonCalendar.set(
                datePicker.year,
                datePicker.month,
                datePicker.dayOfMonth,
                timePicker.hour,
                timePicker.minute
            )
            val selectedDateTimeString =
                simpleSystemDateFormat.format(okButtonCalendar.time) + SPACE + simpleSystemTimeFormat.format(
                    okButtonCalendar.time
                )
            this.setText(selectedDateTimeString)
            textInputLayout?.error = null
            dialog.dismiss()
        }

        val cancelButton: Button = dialog.findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        val clearButton: Button = dialog.findViewById(R.id.clearButton)
        clearButton.setOnClickListener {
            this.setText("")
            dialog.dismiss()
        }

        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun parseDateTime(dateTime: String) = getDate(DATE_TIME_WITH_SECONDS_FORMAT, dateTime)
}