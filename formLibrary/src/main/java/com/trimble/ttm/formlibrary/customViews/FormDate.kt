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
import com.trimble.ttm.commons.utils.DateUtil.convertToSystemDateFormat
import com.trimble.ttm.commons.utils.DateUtil.getSystemDateFormat
import com.trimble.ttm.formlibrary.R
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("ViewConstructor")
class FormDate(
    context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    formSaved: Boolean
) : FormDateTimeBaseDialog(context, textInputLayout, formField, formSaved) {

    private val dateTimeFormat: SimpleDateFormat
        get() = getSystemDateFormat(context)

    override val dateTimeDrawableId: Int
        get() = if(formField?.checkForDriverNonEditableFieldInDriverForm() == true)
                    R.drawable.ic_date_dark_grey
                else R.drawable.ic_date_range_white

    init {
       if (formField.uiData.isNotEmpty()) {
           formField.isSystemFormattable = true
           setText(convertToSystemDateFormat(formField.uiData, context.applicationContext))
           formField.isSystemFormattable = false
        }
    }

    override fun showDateTimePickerDialog() {

        val dialog = getDateTimeDialog().apply {
            findViewById<TextView>(R.id.dialogTitle)?.text =
                resources.getString(R.string.chooseDate)
            findViewById<TimePicker>(R.id.timePicker)?.visibility = View.GONE
        }

        var calendar = Calendar.getInstance()
        this.text?.let {
            if (it.toString().isNotEmpty()) {
                dateTimeFormat.parse(it.toString())?.let {  selectedDate ->
                        calendar.time = selectedDate
                }
            }
        }

        val datePicker: DatePicker = dialog.findViewById(R.id.datePicker)
        datePicker.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        dialog.findViewById<Button>(R.id.okButton).let {
            it.setOnClickListener {
                calendar = Calendar.getInstance()
                calendar.set(
                    datePicker.year,
                    datePicker.month,
                    datePicker.dayOfMonth
                )
                val selectedDateTimeString =
                    dateTimeFormat.format(calendar.time)
                this.setText(selectedDateTimeString)
                textInputLayout?.error = null
                dialog.dismiss()
            }
        }

        handleDialogActionsAndShowDialog(dialog)
    }
}