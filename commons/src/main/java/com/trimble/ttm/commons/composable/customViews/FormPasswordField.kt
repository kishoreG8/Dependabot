package com.trimble.ttm.commons.composable.customViews

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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.CustomOutlineTextField
import com.trimble.ttm.commons.composable.commonComposables.CustomText
import com.trimble.ttm.commons.composable.commonComposables.OutlineTextFieldViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.goldenRod
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkIfTheFieldIsEditable
import com.trimble.ttm.commons.composable.utils.formfieldutils.getAlphaModifier
import com.trimble.ttm.commons.composable.utils.formfieldutils.getFieldColor
import com.trimble.ttm.commons.composable.utils.formfieldutils.getLabelText
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.isTrailingIconVisible
import com.trimble.ttm.commons.composable.utils.formfieldutils.performSendButtonClick
import com.trimble.ttm.commons.composable.utils.formfieldutils.restoreFormFieldUiDataAfterConfigChange
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.EMPTY_STRING

@Composable
fun CustomPasswordField(
    formField: FormField,
    isFormSaved: Boolean,
    sendButtonState: State<Boolean?>,
    imeAction: ImeAction = ImeAction.Default,
    focusManager: FocusManager,
    isFormInReadOnlyMode: Boolean
) {
    val textState = rememberSaveable { mutableStateOf(formField.uiData) }
    val errorText = rememberSaveable { mutableStateOf(formField.errorMessage) }
    val isEditable = checkIfTheFieldIsEditable(formField, isFormSaved)
    val fieldColor = getFieldColor(formField = formField)
    restoreFormFieldUiDataAfterConfigChange(textState = textState, formField = formField)
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        CustomOutlineTextField(
            outlineTextFieldViewBuilder = OutlineTextFieldViewBuilder(
                text = textState.value,
                onValueChange =
                {
                    formField.uiData = it
                    textState.value = formField.uiData
                    formField.errorMessage = EMPTY_STRING
                    errorText.value = formField.errorMessage
                },
                maxLines = MAX_LINES,
                enabled = isEditable,
                isError = errorText.value.isNotEmpty(),
                readOnly = isEditable.not(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Password,
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
                textStyle = getTextStyle(),
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
                        IconButton(onClick = {
                            textState.value = EMPTY_STRING
                            formField.uiData = EMPTY_STRING
                            errorText.value = EMPTY_STRING
                        }, enabled = isEditable) {
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
                ),
                colors = fieldColor,
                interactionSource = remember { MutableInteractionSource() },
                visualTransformation = PasswordVisualTransformation('.')
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