package com.trimble.ttm.formlibrary.utils

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.isNotMultipleChoiceOrMultipleChoiceWithNoTarget
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.customViews.FormEditText
import com.trimble.ttm.formlibrary.utils.UiUtil.hideKeyboard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.core.component.KoinComponent
import java.math.BigDecimal
import java.util.Locale
import java.util.Stack
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

@ExperimentalCoroutinesApi
@Suppress("UNCHECKED_CAST")
object FormUtils  {

    fun getErrorFields(formToValidate: FormTemplate): ArrayList<FormField> {
        val errorFields: ArrayList<FormField> = ArrayList()
        formToValidate.formFieldsList.getNonAutoFields().filter {
            (it.required == 1
                    &&
                    it.uiData.isEmpty() || it.errorMessage.isNotEmpty())
        }.forEach {
            errorFields.add(it)
        }
        return errorFields
    }

    fun ArrayList<FormField>.getNonAutoFields(): List<FormField> {
        return this.filter {
            (
                    it.qtype != FormFieldType.AUTO_VEHICLE_FUEL.ordinal
                            && it.qtype != FormFieldType.AUTO_VEHICLE_LATLONG.ordinal
                            && it.qtype != FormFieldType.AUTO_VEHICLE_LOCATION.ordinal
                            && it.qtype != FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal
                            && it.qtype != FormFieldType.AUTO_DATE_TIME.ordinal
                            && it.qtype != FormFieldType.AUTO_DRIVER_NAME.ordinal
                    )
        }
    }

    fun areAllFieldsEmpty(formToValidate: FormTemplate): Boolean {
        return formToValidate.formFieldsList.none {
            it.uiData.isNotEmpty()
        }
    }

    fun getDistanceBetweenLatLongs(
        location1: Pair<Double, Double>,
        location2: Pair<Double, Double>
    ): Double {
        val earthRadius = 6371 // Radius of the earth in km
        val distLatitude =
            Math.toRadians(location2.first - location1.first)
        val distLongitude = Math.toRadians(location2.second - location1.second)
        val a =
            sin(distLatitude / 2) * sin(distLatitude / 2) +
                    cos(Math.toRadians(location1.first)) * cos(Math.toRadians(location2.first)) *
                    sin(distLongitude / 2) * sin(distLongitude / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val resultInKM = earthRadius * c
        return String.format(Locale.US, "%.2f", resultInKM / 1.609344).toDouble()
    }

    fun getMilesToFeet(miles: Double) = miles * 5280.0

    fun checkIfEditTextIsFocusedAndHideKeyboard(
        context: Activity,
        v: View?,
        ev: MotionEvent?
    ) {
        v?.let { view ->
            if ((ev?.action == MotionEvent.ACTION_UP) &&
                view is EditText &&
                !view.javaClass.name.startsWith("android.webkit.")
            ) {
                val coordinates = IntArray(2)
                view.getLocationOnScreen(coordinates)
                val x: Float = ev.rawX + view.getLeft() - coordinates[0]
                val y: Float = ev.rawY + view.getTop() - coordinates[1]
                if (x < view.getLeft() || x >= view.getRight() || y < view.getTop()
                    || y > view.getBottom()
                ) {
                    hideKeyboard(context)
                    view.clearFocus()
                }
            }
        }
    }

    fun isDecisionTreeForm(userFdlScript: String): Boolean {
        userFdlScript.let {
            if (it.contains(LOOP_START, true) || it.contains(LOOP_END, true) || it.contains(
                    BRANCH_TARGET, true
                ) || it.contains(BRANCH_TO, true)
            ) {
                return true
            }
        }
        return false
    }

    fun Map<String, Any>.getFormRecipients(): HashMap<String, Any> =
        HashMap<String, Any>().also {
            (this[RECIPIENTS_FIELD] as? Map<String, Any>)?.let { recipients ->
                if (recipients.isNotEmpty()) it.putAll(recipients as HashMap<String, Any>)
            }
        }

    fun FormField.validateNumericField(): Boolean {
        return (this.qtype == FormFieldType.NUMERIC.ordinal || this.qtype == FormFieldType.NUMERIC_ENHANCED.ordinal) && !Utils.isNumeric(
            this.uiData,
            this.numspPre
        )
    }

    fun setEditableFieldColors(context: Context, formEditText: FormEditText) {
        formEditText.setTextColor(ContextCompat.getColor(context, R.color.white))
        formEditText.textInputLayout?.defaultHintTextColor =
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
    }

    fun setNonEditableFieldColors(context: Context, formEditText: FormEditText) {
        formEditText.setTextColor(
            ContextCompat.getColor(
                context,
                R.color.nonEditableFieldTextColor
            )
        )
        formEditText.textInputLayout?.defaultHintTextColor =
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    R.color.nonEditableFieldTextColor
                )
            )
        formEditText.background =
            ContextCompat.getDrawable(context, R.drawable.non_editable_rounded_border_text_field)
    }

    fun doesValueNotLieBetweenMinMaxRange(frmField: FormField?, text: String): Boolean {
        frmField?.let { formField ->
            try {
                if (formField.numspMax.isNull() || formField.numspMin.isNull()) return true
                val currencySymbol = formField.numspPre ?: ""
                val roundedValue = text.replace(currencySymbol, "")
                    .replace(",", "")
                    .toDouble()
                    .roundToLong()
                return !(roundedValue.toBigDecimal() > (formField.numspMax ?: BigDecimal("0")) ||
                        roundedValue.toBigDecimal() < (formField.numspMin ?: BigDecimal("0")))

            } catch (nfe: NumberFormatException) {
                Log.i("nfe", "number format exception in FormNumeric field $nfe")
            }
        }
        return true
    }

    fun isNumericFieldNotValidWithinMaxMinRange(text: String, formField: FormField): Boolean {
        return !TextUtils.isEmpty(text) && Utils.isNumeric(
            text,
            formField.numspPre
        ) && !doesValueNotLieBetweenMinMaxRange(formField, text)
    }

    fun checkNumericFieldIsNotValid(text: String, formField: FormField): Boolean {
        return TextUtils.isEmpty(text) || Utils.isNumeric(
            text,
            formField.numspPre
        ) || doesValueNotLieBetweenMinMaxRange(formField, text)
    }

    fun checkValidityOfTheField(text: String, formField: FormField) =
        !(TextUtils.isEmpty(text) && !Utils.isNumeric(
            text,
            formField.numspPre
        ) || !doesValueNotLieBetweenMinMaxRange(formField, text))

    fun getNextQNum(qNum: Int, formTemplate: FormTemplate): Int {
        return formTemplate.formFieldsList.find { it.qnum >= qNum }?.qnum ?: -1
    }

    fun isViewInflationRequired(
        branchTo: Int,
        formField: FormField,
        formTemplate: FormTemplate
    ): Pair<Int, Boolean> {
        val nextQNumToShow = getNextQNum(branchTo + 1, formTemplate)
        val isViewCreationNeeded = if (branchTo > -1) {
            nextQNumToShow == formField.qnum
        } else {
            true
        }
        return Pair(nextQNumToShow, isViewCreationNeeded)
    }

    internal fun getNextFormField(qNum: Int, formTemplate: FormTemplate): FormField? {
        val tempFormFieldList = ArrayList<FormField>()
        tempFormFieldList.addAll(formTemplate.formFieldsList)
        return tempFormFieldList.find { it.qnum >= qNum + 1 }
    }


    /**
     * This method will return true if a loop exists without branching condition at the given formField location. This loop has to be processed and rendered accordingly.
     */
    fun isProcessLoopFieldsRequired(
        formTemplate: FormTemplate,
        formField: FormField,
        formFieldStack: Stack<FormField>
    ): Boolean {
        val nextFormField = getNextFormField(
            formField.qnum,
            formTemplate
        )
        return (nextFormField?.qtype == FormFieldType.LOOP_END.ordinal && formFieldStack.isNotEmpty() && formField.isNotMultipleChoiceOrMultipleChoiceWithNoTarget())
    }
}