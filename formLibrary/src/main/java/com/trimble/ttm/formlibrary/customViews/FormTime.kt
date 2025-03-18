package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.checkForDriverNonEditableFieldInDriverForm
import com.trimble.ttm.commons.utils.DateUtil.convertToSystemTimeFormat
import com.trimble.ttm.commons.utils.DateUtil.getDate
import com.trimble.ttm.commons.utils.DateUtil.getSystemTimeFormat
import com.trimble.ttm.formlibrary.R
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("ViewConstructor")
class FormTime(
    context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    formSaved: Boolean
) : FormDateTimeBaseDialog(context, textInputLayout, formField, formSaved) {

    private val amPmIdentifier = "a"

    override val dateTimeDrawableId: Int
        get() =
            if(formField?.checkForDriverNonEditableFieldInDriverForm() == true)
                R.drawable.ic_time_dark_grey
            else R.drawable.ic_access_time


    private val systemTimeFormat: SimpleDateFormat
        get() = getSystemTimeFormat(context)

    init {
        if (formField.uiData.isNotEmpty()) {
            formField.isSystemFormattable = true
            setText(convertToSystemTimeFormat(formField.uiData, context))
            formField.isSystemFormattable = false
        }
    }

    @Suppress("DEPRECATION")
    override fun showDateTimePickerDialog() {

        val dialog = getDateTimeDialog().apply {
            findViewById<TextView>(R.id.dialogTitle)?.text =
                resources.getString(R.string.chooseTime)
            findViewById<DatePicker>(R.id.datePicker)?.visibility = View.GONE
        }
        var calendar = Calendar.getInstance()
        this.text?.let {
            if (it.toString().isEmpty()) return@let
            calendar.time = parseTime(it.toString())
        }

        val timePicker: TimePicker = dialog.findViewById(R.id.timePicker)
        timePicker.apply {
            hour = calendar.get(Calendar.HOUR_OF_DAY)
            minute = calendar.get(Calendar.MINUTE)
            setIs24HourView(!systemTimeFormat.toLocalizedPattern().contains(amPmIdentifier))
        }

        dialog.findViewById<Button>(R.id.okButton).let {
            it.setOnClickListener {
                calendar = Calendar.getInstance()

                calendar.set(
                    Calendar.YEAR,
                    Calendar.MONTH,
                    Calendar.DATE,
                    timePicker.hour,
                    timePicker.minute
                )
                val selectedDateTimeString =
                    systemTimeFormat.format(calendar.time)
                this.setText(selectedDateTimeString)
                textInputLayout?.error = null
                dialog.dismiss()
            }
        }

        handleDialogActionsAndShowDialog(dialog)
    }

    private fun parseTime(time: String): Date {
        var computedDate = Date()
        try {
            computedDate = getDate(systemTimeFormat.toLocalizedPattern(),time)

        } catch (e: ParseException) {
            try {
                computedDate = getDate("HH:mm", time)
            } catch (e: ParseException) {
                try {
                    computedDate = getDate("KK:mm aaa", time)
                } catch (e: ParseException) {
                    //Ignored
                }
            }
        }
        return computedDate
    }
}