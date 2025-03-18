package com.trimble.ttm.commons.composable.customViews

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.CustomOutlineTextField
import com.trimble.ttm.commons.composable.commonComposables.CustomText
import com.trimble.ttm.commons.composable.commonComposables.OutlineTextFieldViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.WorkFlowTheme
import com.trimble.ttm.commons.composable.uiutils.styles.goldenRod
import com.trimble.ttm.commons.composable.utils.formfieldutils.CurrencyThousandSeparatorTransformation
import com.trimble.ttm.commons.composable.utils.formfieldutils.CurrencyTransformation
import com.trimble.ttm.commons.composable.utils.formfieldutils.ThousandSeparatorTransformation
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkIfTheFieldIsEditable
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkValueIsValid
import com.trimble.ttm.commons.composable.utils.formfieldutils.getAlphaModifier
import com.trimble.ttm.commons.composable.utils.formfieldutils.getFieldColor
import com.trimble.ttm.commons.composable.utils.formfieldutils.getLabelText
import com.trimble.ttm.commons.composable.utils.formfieldutils.getLeftJustifiedTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.isTrailingIconVisible
import com.trimble.ttm.commons.composable.utils.formfieldutils.performSendButtonClick
import com.trimble.ttm.commons.composable.utils.formfieldutils.restoreFormFieldUiDataAfterConfigChange
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.utils.CUSTOM_NUMERIC_FIELD
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.VALUE_IS_NOT_IN_RANGE
import com.trimble.ttm.commons.utils.ZERO

var currencySymbol = EMPTY_STRING

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun CustomNumericField(
    formField: FormField,
    isFormSaved: Boolean,
    sendButtonState: State<Boolean?>,
    imeAction: ImeAction = ImeAction.Default,
    focusManager: FocusManager,
    isFormInReadOnlyMode: Boolean
) {
    val textState = rememberSaveable { mutableStateOf(formField.uiData) }
    val errorText = rememberSaveable { mutableStateOf(formField.errorMessage) }
    val textStyle = getLeftJustifiedTextStyle(formField = formField)
    val isEditable = checkIfTheFieldIsEditable(formField, isFormSaved)
    val fieldColor = getFieldColor(formField = formField)
    val keyBoardType = getKeyboardType(decimalDigitsAllowed = formField.numspDec ?: ZERO)
    setCurrencySymbolPrefix(currencyPrefix = formField.numspPre ?: EMPTY_STRING)
    restoreFormFieldUiDataAfterConfigChange(textState = textState, formField = formField)
    Column(
        modifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = CUSTOM_NUMERIC_FIELD }
    ) {
        CustomOutlineTextField(
            outlineTextFieldViewBuilder = OutlineTextFieldViewBuilder(
                text = textState.value,
                onValueChange = {
                    val errorMessage = checkValueIsValid(
                        value = it,
                        decimalDigitsAllowed = formField.numspDec ?: ZERO,
                        minRange = formField.numspMin,
                        maxRange = formField.numspMax
                    )
                    if (errorMessage.isNotEmpty()) {
                        formField.errorMessage = errorMessage
                        if(!errorMessage.contains(VALUE_IS_NOT_IN_RANGE)){
                            formField.uiData = it
                            textState.value = it
                        }
                    } else if (it != formField.uiData) {
                        formField.errorMessage = EMPTY_STRING
                        formField.uiData = it
                        textState.value = it
                    }
                    errorText.value = formField.errorMessage
                },
                enabled = isEditable,
                isError = errorText.value.isNotEmpty(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = keyBoardType,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    },
                    onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                ),
                textStyle = textStyle,
                label = {
                    val labelText =
                        getLabelText(
                            required = formField.required,
                            labelText = formField.qtext,
                            isEditable = isEditable,
                            isFormSaved = isFormSaved,
                            isReadOnlyView = isFormInReadOnlyMode
                        )
                    Text(text = labelText, style = getTextStyle())
                },
                trailingIcon = {
                    if (isTrailingIconVisible(
                            fieldValue = textState.value,
                            isFormSaved = isFormSaved,
                            isEditable = isEditable,
                            isReadOnlyView = isFormInReadOnlyMode
                        )
                    )
                        IconButton(
                            onClick = {
                                formField.uiData = EMPTY_STRING
                                textState.value = EMPTY_STRING
                                errorText.value = EMPTY_STRING
                            },
                            enabled = isEditable
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cancel_white),
                                contentDescription = stringResource(
                                    id = R.string.cancel_button_description
                                )
                            )
                        }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = getAlphaModifier(
                    isFormInReadOnlyMode = isFormInReadOnlyMode
                ).padding(
                    start = dimensionResource(id = R.dimen.padding_5dp),
                    end = dimensionResource(id = R.dimen.padding_5dp),
                    bottom = dimensionResource(id = R.dimen.padding_size_15)
                ).clickable(enabled = isEditable, onClick = {}),
                colors = fieldColor,
                interactionSource = remember { MutableInteractionSource() },
                readOnly = isEditable.not(),
                visualTransformation = setVisualTransformation(
                    (formField.numspTsep ?: ZERO) == 1,
                    (formField.numspPre ?: EMPTY_STRING).trim().isNotEmpty(),
                    formField.numspDec ?: ZERO,
                    formField = formField
                )
            )
        )
        performSendButtonClick(sendButtonState = sendButtonState, errorText = errorText, formField = formField)
        if (errorText.value.isNotEmpty()) {
            CustomText(
                textViewBuilder = TextViewBuilder(
                    text = errorText.value,
                    modifier = Modifier
                        .align(Alignment.Start),
                    textStyle = getTextStyle().copy(color = goldenRod)
                )
            )
        }
    }
}

private fun setVisualTransformation(
    isTspAllowed: Boolean,
    isCurrencyAllowed: Boolean,
    decimalDigitsAllowed: Int,
    formField: FormField
): VisualTransformation {
    return if (isCurrencyAllowed) {
        if (decimalDigitsAllowed > 0 && isTspAllowed) {
            CurrencyThousandSeparatorTransformation(formField = formField)
        } else {
            CurrencyTransformation(formField = formField)
        }
    } else if (isTspAllowed) {
        ThousandSeparatorTransformation(formField = formField)
    } else {
        VisualTransformation.None
    }
}

private fun getKeyboardType(decimalDigitsAllowed: Int): KeyboardType {
    return if (decimalDigitsAllowed > 0) KeyboardType.Decimal else KeyboardType.Number
}

fun setCurrencySymbolPrefix(currencyPrefix: String) {
    currencySymbol = currencyPrefix
}

@Preview(device = Devices.PIXEL_4)
@Composable
fun CustomNumericFieldPreview() {
    WorkFlowTheme {
        CustomNumericField(
            formField = FormField(
                qtype = FormFieldType.NUMERIC_ENHANCED.ordinal,
                required = 1,
                numspTsep = 1,
                numspDec = 0,
                bcMinLength = -1,
                bcMaxLength = 500,
                qtext = "Numeric Field Test"
            ), true,
            remember {
                mutableStateOf(true)
            },
            ImeAction.Next,
            LocalFocusManager.current,
            isFormInReadOnlyMode = false
        )
    }
}