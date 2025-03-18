package com.trimble.ttm.commons.composable.customViews

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.CustomIcon
import com.trimble.ttm.commons.composable.commonComposables.CustomOutlineTextField
import com.trimble.ttm.commons.composable.commonComposables.CustomSpacer
import com.trimble.ttm.commons.composable.commonComposables.DefaultModifier
import com.trimble.ttm.commons.composable.commonComposables.IconViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.OutlineTextFieldViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.SpaceViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.darkSlateBlue
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkIfTheFieldIsEditable
import com.trimble.ttm.commons.composable.utils.formfieldutils.getFieldColor
import com.trimble.ttm.commons.composable.utils.formfieldutils.getLabelText
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.performSendButtonClick
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.CUSTOM_DATE_FIELD
import com.trimble.ttm.commons.utils.DATE_FIELD
import com.trimble.ttm.commons.utils.DATE_FIELD_ICON
import com.trimble.ttm.commons.utils.DATE_ICON
import com.trimble.ttm.commons.utils.DateUtil.convertToSystemDateFormat


@Composable
fun CustomDateField(
    formField: FormField,
    isFormSaved: Boolean,
    sendButtonState: State<Boolean?>,
    isFormInReadOnlyMode: Boolean
) {

    val isEditable = checkIfTheFieldIsEditable(formField = formField, isFormSaved = isFormSaved)
    val errorText = rememberSaveable { mutableStateOf(formField.errorMessage) }
    val fieldColor = getFieldColor(formField = formField)
    val context = LocalContext.current
    val dateValue =
        rememberSaveable {
            mutableStateOf(
                if (formField.uiData.isNotEmpty()) {
                    convertToSystemDateFormat(formField.uiData, context)
                } else {
                    formField.uiData
                }
            )
        }
    formField.uiData = dateValue.value
    val dateDialogState = rememberSaveable { mutableStateOf(false) }

    CustomOutlineTextField(
        outlineTextFieldViewBuilder = OutlineTextFieldViewBuilder(
            text = dateValue.value,
            onValueChange = {},
            enabled = isEditable,
            isError = errorText.value.isNotEmpty(),
            readOnly = isEditable,
            keyboardActions = KeyboardActions.Default,
            textStyle = MaterialTheme.typography.subtitle1,
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
                val dateImage = if (isEditable) {
                    R.drawable.ic_date_range_white
                } else {
                    R.drawable.ic_date_dark_grey
                }
                CustomIcon(
                    iconViewBuilder = IconViewBuilder(
                        painter = painterResource(id = dateImage),
                        color = Color.White,
                        contentDescription = DATE_FIELD_ICON,
                        modifier = Modifier.semantics { contentDescription=DATE_ICON }
                    )
                )
            },
            shape = MaterialTheme.shapes.medium,
            maxLines = 1,
            modifier = DefaultModifier.defaultTextFieldModifier
                .semantics { contentDescription = DATE_FIELD }
                .padding(
                    start = dimensionResource(id = R.dimen.padding_5dp),
                    end = dimensionResource(id = R.dimen.padding_5dp),
                    top = dimensionResource(id = R.dimen.padding_8dp),
                    bottom = dimensionResource(id = R.dimen.padding_8dp)
                )
                .clickable(enabled = isEditable, onClick = {})
                .focusable(enabled = isEditable),
            colors = fieldColor,
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                dateDialogState.value = true
                            }
                        }
                    }
                }
        )
    )

    performSendButtonClick(
        sendButtonState = sendButtonState,
        errorText = errorText,
        formField = formField
    ).also {
        DisplayErrorMessage(errorText = errorText)
    }

    DialogComposable(
        dialogState = dateDialogState,
        context = context,
        formField = formField,
        fieldValue = dateValue,
        fromField = CUSTOM_DATE_FIELD
    )

}



@Composable
fun CustomDateDialog(
    dateDialogState: MutableState<Boolean>,
    context: Context,
    formField: FormField,
    dateValue: MutableState<String>
) {
    val pickedDateValue = rememberSaveable {
        mutableStateOf(dateValue.value)
    }
    Column(
        modifier = Modifier
            .background(darkSlateBlue)
            .fillMaxWidth()
            .padding(
                dimensionResource(id = R.dimen.padding_size_20)
            )
    ) {
        DatePickerView(pickedDateValue, dateValue,context,stringResource(id = R.string.select_date))

        CustomSpacer(
            spaceViewBuilder = SpaceViewBuilder(
                modifier = Modifier.height(dimensionResource(id = R.dimen.padding_size_15))
            )
        )

        ClearCancelOkButtonsComposable(context,
            onOkButtonClicked = {
                dateValue.value = pickedDateValue.value
                formField.uiData = dateValue.value
                dateDialogState.value = false
            },
            onCancelButtonClicked = {
                dateDialogState.value = false
            },
            onClearButtonClicked = { clearData ->
                dateValue.value = clearData
                formField.uiData = dateValue.value
                dateDialogState.value = false
            })

    }
}



