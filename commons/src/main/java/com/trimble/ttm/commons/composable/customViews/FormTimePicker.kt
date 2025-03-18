package com.trimble.ttm.commons.composable.customViews

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.CustomIcon
import com.trimble.ttm.commons.composable.commonComposables.CustomOutlineTextField
import com.trimble.ttm.commons.composable.commonComposables.CustomSpacer
import com.trimble.ttm.commons.composable.commonComposables.CustomText
import com.trimble.ttm.commons.composable.commonComposables.DefaultModifier
import com.trimble.ttm.commons.composable.commonComposables.IconViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.OutlineTextFieldViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.SpaceViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.darkSlateBlue
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkIfTheFieldIsEditable
import com.trimble.ttm.commons.composable.utils.formfieldutils.getFieldColor
import com.trimble.ttm.commons.composable.utils.formfieldutils.getLabelText
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.performSendButtonClick
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.CUSTOM_TIME_FIELD
import com.trimble.ttm.commons.utils.DateUtil.convertToSystemTimeFormat
import com.trimble.ttm.commons.utils.DateUtil.getSystemTimeFormat
import com.trimble.ttm.commons.utils.TIME_FIELD
import com.trimble.ttm.commons.utils.TIME_FIELD_ICON
import com.trimble.ttm.commons.utils.TIME_ICON


@Composable
fun CustomTimeField(
    formField: FormField,
    isFormSaved: Boolean,
    sendButtonState: State<Boolean?>,
    isFormInReadOnlyMode: Boolean
) {

    val isEditable = checkIfTheFieldIsEditable(formField = formField, isFormSaved = isFormSaved)
    val errorText = rememberSaveable { mutableStateOf(formField.errorMessage) }
    val fieldColor = getFieldColor(formField = formField)
    val context = LocalContext.current

    fun getTimeFieldValue() : String{
        return if (formField.uiData.isNotEmpty()) {
            convertToSystemTimeFormat(formField.uiData, context)
        } else {
            formField.uiData
        }
    }

    val timeValue =
        rememberSaveable {
            mutableStateOf(
                getTimeFieldValue()
            )
        }

    formField.uiData = timeValue.value
    val timeDialogState = rememberSaveable { mutableStateOf(false) }

    CustomOutlineTextField(
        outlineTextFieldViewBuilder = OutlineTextFieldViewBuilder(
            text = timeValue.value,
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
                val timeImage = if (isEditable) {
                    R.drawable.ic_access_time
                } else {
                    R.drawable.ic_time_dark_grey
                }
                CustomIcon(
                    iconViewBuilder = IconViewBuilder(
                        painter = painterResource(id = timeImage),
                        color = Color.White,
                        contentDescription = TIME_FIELD_ICON,
                        modifier = Modifier.semantics { contentDescription = TIME_ICON }
                    )
                )
            },
            shape = MaterialTheme.shapes.medium,
            maxLines = 1,
            modifier = DefaultModifier.defaultTextFieldModifier
                .semantics { contentDescription = TIME_FIELD }
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
                                timeDialogState.value = true
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
       dialogState = timeDialogState,
        context = context,
        formField = formField,
       fieldValue = timeValue,
       fromField = CUSTOM_TIME_FIELD
    )

}

@Composable
fun CustomTimeDialog(
    timeDialogState: MutableState<Boolean>,
    context: Context,
    formField: FormField,
    timeValue: MutableState<String>
) {
    val pickedTimeValue = rememberSaveable {
        mutableStateOf(timeValue.value)
    }
    Column(
        modifier = Modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .background(darkSlateBlue)
            .padding(
                dimensionResource(id = R.dimen.padding_size_20)
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CustomText(
            textViewBuilder = TextViewBuilder(
                text = stringResource(id = R.string.select_time),
                modifier = Modifier.align(Alignment.Start),
                textStyle = getTextStyle().copy(fontSize = dimensionResource(id = R.dimen.font_size_22sp).value.sp)
            )
        )
        CustomSpacer(
            spaceViewBuilder = SpaceViewBuilder(
                modifier = Modifier.height(dimensionResource(id = R.dimen.padding_size_15))
            )
        )

        TimePickerView(pickedTimeValue, timeValue,getSystemTimeFormat(context).toPattern(),context)

        CustomSpacer(
            spaceViewBuilder = SpaceViewBuilder(
                modifier = Modifier.height(dimensionResource(id = R.dimen.padding_size_15))
            )
        )

        ClearCancelOkButtonsComposable(context,
            onOkButtonClicked = {
                timeValue.value = pickedTimeValue.value
                formField.uiData = timeValue.value
                timeDialogState.value = false
            },
            onCancelButtonClicked = {
                timeDialogState.value = false
            },
            onClearButtonClicked = {clearData ->
                timeValue.value = clearData
                formField.uiData = timeValue.value
                timeDialogState.value = false
            })

    }
}
