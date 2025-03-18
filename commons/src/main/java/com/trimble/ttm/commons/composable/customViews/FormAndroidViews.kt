package com.trimble.ttm.commons.composable.customViews

import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.utils.formfieldutils.getImageAndSignatureFieldModifier
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.performSendButtonClick
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.EMPTY_STRING

@Composable
fun CustomAndroidView(view: View, sendButtonState: State<Boolean?>, formField: FormField) {
    val errorText = rememberSaveable { mutableStateOf(EMPTY_STRING) }
    val context = LocalContext.current
    Column(modifier = getImageAndSignatureFieldModifier(formField = formField,context = context)){
        AndroidView(factory = { view })
        performSendButtonClick(sendButtonState = sendButtonState, errorText = errorText, formField = formField)
        if (errorText.value.isNotEmpty()) {
            if (view is TextInputLayout) {
                if (formField.uiData.isEmpty() && formField.required == 1) {
                    SpannableString("*"+context.getString(R.string.cannot_be_empty)).let {
                        it.setSpan(
                            ForegroundColorSpan(
                                ContextCompat.getColor(
                                    context, R.color.outlineBoxErrorColor
                                )
                            ), 0, it.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                        )
                        it.setSpan(
                            AbsoluteSizeSpan(getTextStyle().fontSize.value.toInt(), true), 0, it.length, 0
                        )
                        view.error = it
                    }
                } else {
                    view.error = EMPTY_STRING
                }
            } else {
                view.background = context.getDrawable(R.drawable.error_rounded_border_drawable)
            }
        }
    }
}