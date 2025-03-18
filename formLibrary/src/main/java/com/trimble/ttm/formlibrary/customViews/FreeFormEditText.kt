package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.text.util.Linkify
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.model.FormField

@SuppressLint("ViewConstructor")
class FreeFormEditText(
    context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    isFormSaved: Boolean,
    editTextHeight: Int
) : FormEditText(context, textInputLayout, formField, isFormSaved) {

    init {
        setFreeFormEditTextAttributes(editTextHeight, isFormSaved)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setFreeFormEditTextAttributes(height: Int, formSaved: Boolean) {
        gravity = Gravity.TOP
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        maxLines = height
        isVerticalScrollBarEnabled = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
        autoLinkMask = Linkify.WEB_URLS
        movementMethod = LinkScrollingMovementMethod()

        if (formSaved) {
            isEnabled = true
            isFocusableInTouchMode = false
        }
        setOnTouchListener { view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                view.parent.requestDisallowInterceptTouchEvent(true)
            } else if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) {
                view.parent.requestDisallowInterceptTouchEvent(false)
            }
            view.onTouchEvent(motionEvent)
        }
    }
}