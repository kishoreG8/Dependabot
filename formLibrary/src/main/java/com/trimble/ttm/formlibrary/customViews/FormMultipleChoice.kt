package com.trimble.ttm.formlibrary.customViews

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.checkForDriverNonEditableFieldInDriverForm
import com.trimble.ttm.commons.model.multipleChoiceDriverInputNeeded
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.SpinnerCustomAdapter


class FormMultipleChoice(
    context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    isFormSaved: Boolean,
    responseLambda: (FormChoice?) -> Unit
) : AppCompatAutoCompleteTextView(
    context, null, R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox_ExposedDropdownMenu
), PendingClickListener {
    private val dropdownItems = arrayListOf<String>()
    private var formField: FormField? = null
    private var responseLambda: ((FormChoice?) -> Unit)? = null
    private var i: Int = -1

    init {
        if (formField.driverEditable == NOT_EDITABLE) {
            this.isEnabled = false
            this.isFocusable = false
            this.isClickable = false
        } else {
            isClickable = !isFormSaved
            isEnabled = !isFormSaved
            isFocusable = !isFormSaved
        }
        id = formField.qnum
        if (formField.required == 0) {
            dropdownItems.add(String())
        }
        formField.formChoiceList?.sortWith { c1, c2 -> c1.choicenum - c2.choicenum }
        formField.formChoiceList?.forEach { dropdownItems.add(it.value) }
        inputType = 0
        background = null
        if (formField.uiData.isNotEmpty()) {
            formField.formChoiceList?.find { it -> it.value == formField.uiData }
                ?.let { _ ->
                    setText(formField.uiData)
                    this.formField = formField
                    this.responseLambda = responseLambda
                    this.i = dropdownItems.indexOf(formField.uiData)
                }
        }
        onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>, _: View, i: Int, _: Long ->
                if (formField.uiData != dropdownItems[i]) {
                    formField.uiData = dropdownItems[i]
                    textInputLayout.error = null
                    performClickAction(
                        formField,
                        responseLambda,
                        i
                    )
                }
            }
        textInputLayout.hint = if (formField.required == REQUIRED) String.format(
            resources.getString(R.string.component_hint_required),
            formField.qtext
        )
        else String.format(
            resources.getString(R.string.component_hint_optional),
            formField.qtext
        )
        setDropDownBackgroundResource(R.color.white)
        if(formField.checkForDriverNonEditableFieldInDriverForm())
            setNonEditableFieldColors(textInputLayout)
        else setEditableFieldColors(textInputLayout)
        if (formField.driverEditable != NOT_EDITABLE && !isFormSaved)
            setAdapter(
                SpinnerCustomAdapter(context, R.layout.custom_text_view, dropdownItems)
            )
        setLayoutParams()
    }

    private fun performClickAction(
        formField: FormField,
        responseLambda: (FormChoice?) -> Unit,
        i: Int
    ) {
        if (formField.multipleChoiceDriverInputNeeded()) {
            responseLambda(formField.formChoiceList?.get(getFieldIndex(formField.required, i)))
        }
    }

    private fun getFieldIndex(required: Int, i: Int): Int {
        return if (required == 1) {
            i
        } else {
            i-1
        }
    }

    private fun setEditableFieldColors(textInputLayout: TextInputLayout) {
        setTextColor(ContextCompat.getColor(context, R.color.white))
        textInputLayout.defaultHintTextColor =
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
    }

    private fun setNonEditableFieldColors(textInputLayout: TextInputLayout){
        setTextColor(ContextCompat.getColor(context, R.color.nonEditableFieldTextColor))
        textInputLayout.defaultHintTextColor =
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.nonEditableFieldTextColor))
        background = ContextCompat.getDrawable(context, R.drawable.non_editable_rounded_border_text_field)
    }

    private fun setLayoutParams() {
        apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.defaultFormsEditTextHeight)
            )
            (layoutParams as LinearLayout.LayoutParams).setMargins(
                resources.getDimension(R.dimen.defaultLeftRightMargin).toInt(),
                resources.getDimension(R.dimen.defaultTopBottomMargin).toInt(),
                resources.getDimension(R.dimen.defaultLeftRightMargin).toInt(),
                resources.getDimension(R.dimen.defaultTopBottomMargin).toInt()
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(resources.getDimensionPixelSize(R.dimen.defaultFormsEditTextPadding))
        }
    }



    override fun executePendingClick() {
        formField?.let {
            responseLambda?.let { response -> performClickAction(it, response, i) }
        }
    }
}

interface PendingClickListener {
    fun executePendingClick()
}
