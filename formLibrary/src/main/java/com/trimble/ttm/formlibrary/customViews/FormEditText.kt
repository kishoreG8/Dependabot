package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.util.Linkify
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MASK
import android.view.MotionEvent.ACTION_SCROLL
import android.view.View.OnFocusChangeListener
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.checkForDriverNonEditableFieldInDriverForm
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.manager.FormFieldManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils.setEditableFieldColors
import com.trimble.ttm.formlibrary.utils.FormUtils.setNonEditableFieldColors
import com.trimble.ttm.formlibrary.utils.UiUtil

const val NOT_EDITABLE = 0
const val REQUIRED = 1
const val MIN_LINES = 1
const val MAX_LINES = 6
const val LINE_SPACE = 1.0f
const val LINE_SPACE_MULTIPLIER = 1.2f
const val MAX_TEXT_LENGTH_PER_LINE = 60

/* The primary constructor works here that we set the box outline theme in the xml
but still the that was not set properly so we are passing the theme to the TextInputEditText in the custom class.
so, the box outline setting the theme as expected in TextInputLayout */
open class FormEditText(
    context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    var isFormSaved: Boolean
) : TextInputEditText(
    context, null, R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox
) {
    val formFieldManager : FormFieldManager by lazy {
        FormFieldManager()
    }
    var textInputLayout: TextInputLayout? = textInputLayout
    var formField: FormField? = formField

    private var _isSingleLine = false

    init {
        this.id = formField.qnum
        showSoftInputOnFocus = !isFormSaved
        isLongClickable = !isFormSaved
        if(isFormSaved){
            makeEditTextNonEditable()
        }
        textInputLayout.hint = if (formField.required == REQUIRED) String.format(
            resources.getString(R.string.component_hint_required),
            formField.qtext
        )
        else String.format(
            resources.getString(R.string.component_hint_optional),
            formField.qtext
        )
        if (formField.driverEditable == NOT_EDITABLE) {
            showSoftInputOnFocus = false
            makeEditTextNonEditable()
        }
        setDefaultParams()
    }

    fun disableEditText(){
        isEnabled = formFieldManager.isEnabled
        isFocusable = formFieldManager.isFocusable
        isClickable = formFieldManager.isClickable
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setDefaultParams() {
        this.apply {
            if(formField?.checkForDriverNonEditableFieldInDriverForm() == true)
                setNonEditableFieldColors(context,this)
            else setEditableFieldColors(context,this)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minimumHeight = resources.getDimensionPixelSize(R.dimen.defaultFormsEditTextHeight)
            (layoutParams as LinearLayout.LayoutParams).setMargins(
                resources.getDimension(R.dimen.defaultLeftRightMargin).toInt(),
                resources.getDimension(R.dimen.defaultTopBottomMargin).toInt(),
                resources.getDimension(R.dimen.defaultLeftRightMargin).toInt(),
                resources.getDimension(R.dimen.defaultTopBottomMargin).toInt()
            )
            setPadding(resources.getDimensionPixelSize(R.dimen.defaultFormsEditTextPadding))
            setLineSpacing(LINE_SPACE, LINE_SPACE_MULTIPLIER)
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = MIN_LINES
            maxLines = MAX_LINES
            scrollBarStyle = SCROLLBARS_INSIDE_INSET
            overScrollMode = OVER_SCROLL_ALWAYS
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            formField?.uiData?.let {
                this.setText(
                    it
                )
            }
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = true
            autoLinkMask = Linkify.WEB_URLS
            movementMethod = LinkScrollingMovementMethod()
            setTextChangeListener()

            setFocusChangeListener()

            setTouchListener()
        }
    }

    private fun setTextChangeListener() {
        this.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if( formField?.isSystemFormattable == false && (isFormSaved || formField?.driverEditable == NOT_EDITABLE)){
                    if(s.toString() != (formField?.uiData)){
                        this@FormEditText.removeTextChangedListener(this)
                        setText(
                            formField?.uiData ?: EMPTY_STRING
                        )
                        this@FormEditText.addTextChangedListener(this)
                    }
                }else{
                    formField?.uiData = s.toString()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                //Ignore
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(!isFormSaved && formField?.driverEditable != NOT_EDITABLE){
                    formField?.uiData = s.toString()
                    onFocusChangeListener = OnFocusChangeListener { _, _ ->
                        textInputLayout?.error = null
                    }
                }

            }
        })
    }

    private fun setFocusChangeListener(){
        this.onFocusChangeListener = OnFocusChangeListener { view, hasFocus ->
            if(hasFocus && (isFormSaved || formField?.driverEditable == NOT_EDITABLE)){
                //Code to hide keypad if user clicks on non editable field while currently on editable field with keypad open
                UiUtil.hideKeyboard(context,view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setTouchListener() {
        setOnTouchListener(OnTouchListener { v, event ->
            formField?.driverEditable?.let {
                val textLength = this@FormEditText.textInputLayout?.editText?.text?.length
                if (lineCount <= MAX_LINES && formFieldManager.isFormFieldNotEditable(isFormSaved, it)) {
                    isFocusable = false
                    isClickable = textLength!! > MAX_TEXT_LENGTH_PER_LINE
                    isFocusableInTouchMode = false
                }

                if (event.action == ACTION_DOWN && formField?.qtype != FormFieldType.PASSWORD.ordinal) {
                    this@FormEditText.textInputLayout?.editText?.let{ editText ->
                        editText.isSingleLine = _isSingleLine
                        _isSingleLine = !_isSingleLine
                    }
                }

                if (isLineCountGreaterThanMaxLines(it)) {
                    this@FormEditText.textInputLayout?.boxStrokeColor =
                        ContextCompat.getColor(context, R.color.defaultOutlineBoxBorderColor)
                    this@FormEditText.isCursorVisible = false
                }
            }

            if (this.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                when (event.action and ACTION_MASK) {
                    ACTION_SCROLL -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                        return@OnTouchListener true
                    }
                }
            }
            false
        })
    }

    private fun isLineCountGreaterThanMaxLines(lineCount: Int): Boolean {
        return lineCount > MAX_LINES && formFieldManager.isFormFieldNotEditable(isFormSaved, lineCount)
    }

    private fun makeEditTextNonEditable(){
        this.setOnLongClickListener(OnLongClickListener { false })
        this.showSoftInputOnFocus = false
        this.isLongClickable = false
        this.setTextIsSelectable(false)
        this.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
                menu?.clear()
                return false
            }

            override fun onActionItemClicked(actionMode: ActionMode?, item: MenuItem?): Boolean {
                return false
            }

            override fun onDestroyActionMode(actionMode: ActionMode?) {
                //Ignore as we are preventing the menu from popping up.
            }
        }
    }

    fun makeEditTextEditable() {
        this.setOnLongClickListener(OnLongClickListener { true })
        this.showSoftInputOnFocus = true
        this.isLongClickable = true
        this.setTextIsSelectable(true)
    }

}