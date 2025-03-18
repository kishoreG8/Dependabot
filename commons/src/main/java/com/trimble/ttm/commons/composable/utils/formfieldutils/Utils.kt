package com.trimble.ttm.commons.composable.utils.formfieldutils

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.DefaultModifier
import com.trimble.ttm.commons.composable.customViews.currencySymbol
import com.trimble.ttm.commons.composable.uiutils.styles.charCoal
import com.trimble.ttm.commons.composable.uiutils.styles.darkGrey
import com.trimble.ttm.commons.composable.uiutils.styles.goldenRod
import com.trimble.ttm.commons.composable.uiutils.styles.silverGrey
import com.trimble.ttm.commons.composable.uiutils.styles.vividCerulean
import com.trimble.ttm.commons.composable.uiutils.styles.white
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.checkForDriverNonEditableFieldInDriverForm
import com.trimble.ttm.commons.model.isDriverEditable
import com.trimble.ttm.commons.utils.DECIMAL_DIGITS_ARE_NOT_ALLOWED
import com.trimble.ttm.commons.utils.DECIMAL_RANGE_EXCEEDS
import com.trimble.ttm.commons.utils.DOT
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FIELD_CAN_NOT_BE_EMPTY
import com.trimble.ttm.commons.utils.FORM_NOT_A_VALID_NUMBER
import com.trimble.ttm.commons.utils.READ_ONLY_VIEW_ALPHA
import com.trimble.ttm.commons.utils.REQUIRED_TEXT
import com.trimble.ttm.commons.utils.VALUE_IS_NOT_IN_RANGE
import com.trimble.ttm.commons.utils.ZERO
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.regex.Pattern
import kotlin.math.absoluteValue

@Composable
fun getLabelText(
    required: Int,
    labelText: String,
    isEditable: Boolean,
    isFormSaved: Boolean,
    isReadOnlyView: Boolean
): AnnotatedString {
    val superScript = SpanStyle(
        color = Color.Red,
        fontSize = getTextStyle().fontSize
    )
    return if (isEditable && isFormSaved.not() && isReadOnlyView.not() && required == 1) {
        buildAnnotatedString {
            append(labelText + REQUIRED_TEXT)
            withStyle(superScript) {
                append("*")
            }
        }
    } else if (required == 1) {
        buildAnnotatedString {
            append("$labelText$REQUIRED_TEXT*")
        }
    } else {
        buildAnnotatedString {
            append(labelText)
        }
    }
}

fun checkIfTheFieldIsEditable(formField: FormField, isFormSaved: Boolean): Boolean {
    return formField.isDriverEditable() && !isFormSaved
}

@Composable
fun getTextStyle(): TextStyle {
    return TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = LocalTextStyle.current.fontSize,
        color = white
    )
}

@Composable
fun getTextStyle(color: Color): TextStyle{
    return TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = LocalTextStyle.current.fontSize,
        color = color
    )
}

@Composable
fun getFieldColor(formField: FormField): TextFieldColors {
    return if (!formField.checkForDriverNonEditableFieldInDriverForm()) {
        editableFieldColor()
    } else {
        nonEditableFieldColor()
    }
}

@Composable
fun nonEditableFieldColor(): TextFieldColors {
    return TextFieldDefaults.outlinedTextFieldColors(
        disabledTextColor = colorResource(id = R.color.nonEditableFieldTextColor),
        disabledLabelColor = colorResource(id = R.color.nonEditableFieldTextColor),
        disabledPlaceholderColor = colorResource(id = R.color.nonEditableFieldTextColor),
        backgroundColor = colorResource(id = R.color.nonEditableFieldBackground),
        focusedBorderColor = colorResource(id = R.color.nonEditableFieldTextColor),
        unfocusedBorderColor = colorResource(id = R.color.nonEditableFieldTextColor),
    )
}

@Composable
fun editableFieldColor(): TextFieldColors {
    return TextFieldDefaults.outlinedTextFieldColors(
        backgroundColor = charCoal,
        focusedBorderColor = vividCerulean,
        unfocusedBorderColor = silverGrey,
        cursorColor = vividCerulean,
        errorBorderColor = goldenRod,
        errorLabelColor = goldenRod,
        focusedLabelColor = vividCerulean,
        textColor = white,
        unfocusedLabelColor = white,
        trailingIconColor = white
    )
}

@Composable
fun getLeftJustifiedTextStyle(formField: FormField): TextStyle {
    return if ((formField.numspLeftjust ?: ZERO) == 1) getTextStyle().copy(
        textAlign = TextAlign.Left
    ) else getTextStyle()
}

@SuppressLint("ModifierFactoryExtensionFunction")
fun getAlphaModifier(isFormInReadOnlyMode: Boolean): Modifier {
    val modifier = DefaultModifier.defaultTextFieldModifier
    return if (isFormInReadOnlyMode) {
        modifier.alpha(READ_ONLY_VIEW_ALPHA)
    } else {
        modifier.alpha(1.0f)
    }
}

// During config change at first the value in the textState will be empty and
// It calls onValueChange and Make formField.uiData to empty then
// It retrieve the value and store it in the textState mutable value but during this change onValueChange is not getting called
// So we're retrieving the value from the textState and store it in formField.uiData
fun restoreFormFieldUiDataAfterConfigChange(textState: MutableState<String>, formField: FormField) {
    if (textState.value != formField.uiData) {
        formField.uiData = textState.value
    }
}

fun isTrailingIconVisible(
    fieldValue: String,
    isFormSaved: Boolean,
    isEditable: Boolean,
    isReadOnlyView: Boolean
): Boolean {
    return fieldValue.isNotEmpty() && isFormSaved.not() && isEditable && isReadOnlyView.not()
}

@SuppressLint("ModifierFactoryExtensionFunction")
fun getImageAndSignatureFieldModifier(formField: FormField, context: Context): Modifier {
    return if (formField.qtype == FormFieldType.IMAGE_REFERENCE.ordinal || formField.qtype == FormFieldType.SIGNATURE_CAPTURE.ordinal) Modifier.padding(
        start = context.resources.getDimension(R.dimen.padding_5dp).absoluteValue.dp,
        end = context.resources.getDimension(R.dimen.padding_5dp).absoluteValue.dp,
        bottom = context.resources.getDimension(R.dimen.padding_size_15).absoluteValue.dp
    ) else Modifier
}

fun convertToThousandSeparatedString(value: String): String {
    val symbols = DecimalFormat().decimalFormatSymbols
    val decimalSeparator = symbols.decimalSeparator
    var outputText = ""
    if (value.isNotEmpty()) {
        try {
            val integerPart : Long
            val decimalPart : String
            val number = value.toDouble()
            integerPart = number.toLong()
            outputText += NumberFormat.getIntegerInstance().format(integerPart)
            if (value.contains(decimalSeparator)) {
                decimalPart = value.substring(value.indexOf(decimalSeparator))
                if (decimalPart.isNotEmpty()) {
                    outputText += decimalPart
                }
            }
        } catch (exception: Exception) {
            outputText = value
        }
    }
    return outputText
}

fun countSeparatorAndCurrencySymbol(value: String): Int =
    value.count { it.toString().matches(("[^0-9.]").toRegex()) }

fun checkTheRequiredFieldsFilledOrNot(formField: FormField): String {
    return if (formField.required == 1 && formField.uiData.isEmpty()) {
        "*$FIELD_CAN_NOT_BE_EMPTY"
    } else {
        EMPTY_STRING
    }
}

fun isNumeric(value: String): Boolean {
    return Pattern.compile("-?\\d+(.\\d+)*,?").matcher(value).matches() && valueShouldContainUtmostOneDecimalPoint(value = value) && valueShouldContainNegativeSymbolAtFront(value = value)
}

fun valueShouldContainUtmostOneDecimalPoint(value: String) = value.filter { it == '.' }.length <= 1

fun valueShouldContainNegativeSymbolAtFront(value: String) : Boolean {
    val index = value.indexOf("-")
    return index <= 1 && (value.filter { it == '-' }.length <= 1)
}


fun checkValueIsValid(
    value: String,
    decimalDigitsAllowed: Int,
    minRange: BigDecimal?,
    maxRange: BigDecimal?
): String {
    if (value.isNotEmpty() && isNumeric(value)) {
        val formattedValue = removeSpecialCharacters(value = value).toBigDecimal()
        return if ((minRange != null && maxRange != null) && (formattedValue< minRange || formattedValue > maxRange)) {
            "$VALUE_IS_NOT_IN_RANGE $minRange , $maxRange"
        } else {
            checkIfDecimalDigitInRange(
                value = value,
                decimalDigitsAllowed = decimalDigitsAllowed
            )
        }
    } else {
        return if (!isNumeric(value) && value.isNotEmpty()) {
            FORM_NOT_A_VALID_NUMBER
        } else {
            EMPTY_STRING
        }
    }
}

fun convertToCurrency(value: String): String {
    return if (value.isNotEmpty() && !value.contains(currencySymbol)) "$currencySymbol$value" else value
}

fun removeSpecialCharacters(value: String): String {
    return if (value.isNotEmpty()) {
        value.replace(("[^0-9.]").toRegex(), EMPTY_STRING)
    } else {
        EMPTY_STRING
    }
}

fun checkIfDecimalDigitInRange(decimalDigitsAllowed: Int, value: String): String {
    return if (decimalDigitsAllowed > 0 && value.contains(DOT)) {
        if (value.substringAfter(DOT).length <= decimalDigitsAllowed) {
            EMPTY_STRING
        } else {
            "$DECIMAL_RANGE_EXCEEDS $decimalDigitsAllowed"
        }
    } else {
        if (decimalDigitsAllowed == 0 && value.contains(DOT))
            DECIMAL_DIGITS_ARE_NOT_ALLOWED
        else EMPTY_STRING
    }
}

@Composable
fun getSignatureAndImageRefBorderColor(
    isEditable: Boolean,
    errorText: State<String>
): Color {
    return if(errorText.value.isNotEmpty()){
        colorResource(id = R.color.outlineBoxErrorColor)
    } else if (isEditable) {
        colorResource(id = R.color.composeDefaultOutlineBoxBorderColor)
    } else {
        colorResource(id = R.color.darkGrey)
    }
}

fun getSignatureAndImageRefTextColor(isEditable: Boolean): Color {
    return if (!isEditable) {
        darkGrey
    } else {
        white
    }
}

fun performSendButtonClick(sendButtonState: State<Boolean?>, errorText: MutableState<String>, formField: FormField){
    if (sendButtonState.value == true) {
        formField.errorMessage =
            formField.errorMessage.ifEmpty { checkTheRequiredFieldsFilledOrNot(formField) }
        errorText.value = formField.errorMessage
    }
}
fun shouldClearSignatureCanvas(
    signatureDialogState: Boolean,
    formField: FormField
): Boolean {
    return signatureDialogState && formField.uiData.isEmpty()
}

