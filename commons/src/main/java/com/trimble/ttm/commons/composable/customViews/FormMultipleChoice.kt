package com.trimble.ttm.commons.composable.customViews

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.CustomOutlineTextField
import com.trimble.ttm.commons.composable.commonComposables.CustomText
import com.trimble.ttm.commons.composable.commonComposables.DefaultModifier
import com.trimble.ttm.commons.composable.commonComposables.OutlineTextFieldViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.darkGrey
import com.trimble.ttm.commons.composable.uiutils.styles.darkSlateGrey
import com.trimble.ttm.commons.composable.uiutils.styles.goldenRod
import com.trimble.ttm.commons.composable.uiutils.styles.slateGrey
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkIfTheFieldIsEditable
import com.trimble.ttm.commons.composable.utils.formfieldutils.getAlphaModifier
import com.trimble.ttm.commons.composable.utils.formfieldutils.getFieldColor
import com.trimble.ttm.commons.composable.utils.formfieldutils.getLabelText
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.performSendButtonClick
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.multipleChoiceDriverInputNeeded
import com.trimble.ttm.commons.utils.EMPTY_STRING


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CustomMultipleChoice(
    formField: FormField,
    isFormSaved: Boolean,
    responseLambda: (FormChoice?) -> Triple<Int, Int, Int>,
    sendButtonState: State<Boolean?>,
    renderFormContent: @Composable (Triple<Int, Int, Int>) -> Unit,
    isFormInReadOnlyMode : Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    val callBackReturnFlow = remember { mutableStateOf(false) }
    var selectedOptionText by rememberSaveable { mutableStateOf(formField.uiData) }
    val selectedFormChoice = remember {
        mutableStateOf(
            FormChoice(
                qnum = -1,
                choicenum = -1,
                value = EMPTY_STRING,
                formid = -1
            )
        )
    }
    val errorText = rememberSaveable { mutableStateOf(EMPTY_STRING) }
    val isEditable = checkIfTheFieldIsEditable(formField, isFormSaved)
    val icon = getLeadingIcon(isExpanded = isExpanded, isEditable = isEditable)
    val fieldColor = getFieldColor(formField = formField)
    formField.formChoiceList?.sortWith { c1, c2 -> c1.choicenum - c2.choicenum }
    restoreValuesAfterConfigChange(formField = formField, selectedOptionText = selectedOptionText, selectedFormChoice = selectedFormChoice)
    Column(modifier = getAlphaModifier(isFormInReadOnlyMode = isFormInReadOnlyMode).fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = {
                isExpanded = !isExpanded
            },
            modifier = Modifier
                .scrollable(
                    enabled = isEditable,
                    state = rememberScrollState(),
                    orientation = Orientation.Vertical,
                    flingBehavior = ScrollableDefaults.flingBehavior()
                )
                .padding(
                    start = dimensionResource(id = R.dimen.padding_5dp),
                    end = dimensionResource(id = R.dimen.padding_5dp),
                    bottom = dimensionResource(id = R.dimen.padding_size_15)
                )
        ) {
            CustomOutlineTextField(
                outlineTextFieldViewBuilder = OutlineTextFieldViewBuilder(
                    modifier = DefaultModifier.defaultTextFieldModifier,
                    text = selectedOptionText,
                    readOnly = true,
                    shape = MaterialTheme.shapes.medium,
                    enabled = isEditable,
                    onValueChange = {
                        formField.uiData = it
                        selectedOptionText = formField.uiData
                    },
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
                                isReadOnlyForm = isFormInReadOnlyMode,
                                isEditable = isEditable
                            )
                        ) {
                            Icon(
                                icon,
                                "contentDescription", tint = darkGrey
                            )
                        }
                    },
                    isError = errorText.value.isNotEmpty(),
                    colors = fieldColor,
                    interactionSource = remember { MutableInteractionSource() },
                    textStyle = getTextStyle()
                )
            )
            DropdownMenu(
                expanded = isDropDownExpansionEnable(isExpanded = isExpanded, isEditable = isEditable),
                onDismissRequest = { isExpanded = false },
                modifier = Modifier
                    .background(darkSlateGrey)
                    .exposedDropdownSize(true)
                    .clickable(enabled = isEditable, onClick = {})
            ) {
                formField.formChoiceList?.forEachIndexed { index, selectionOption ->
                    DropdownMenuItem(
                        content = {
                            Column {
                                CustomText(
                                    textViewBuilder = TextViewBuilder(
                                        text = selectionOption.value,
                                        modifier = Modifier
                                            .align(Alignment.Start),
                                        textStyle = getTextStyle()
                                    )
                                )
                                if (index != formField.formChoiceList?.lastIndex)
                                    Divider(
                                        color = slateGrey,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                            }
                        },
                        onClick = {
                            formField.uiData = selectionOption.value
                            selectedFormChoice.value = selectionOption
                            selectedOptionText = formField.uiData
                            callBackReturnFlow.value = true
                            isExpanded = false
                            formField.errorMessage = EMPTY_STRING
                            errorText.value = formField.errorMessage
                        }
                    )
                }
            }
        }
        performSendButtonClick(sendButtonState = sendButtonState, errorText = errorText, formField = formField)
        if(errorText.value.isNotEmpty()) {
            CustomText(
                textViewBuilder = TextViewBuilder(
                    text = errorText.value,
                    textStyle = getTextStyle().copy(color = goldenRod),
                    modifier = Modifier.align(Alignment.Start)
                )
            )
        }
    }
    SendResponse(
        formField = formField,
        responseLambda = responseLambda,
        renderFormContent = renderFormContent,
        selectedFormChoice = selectedFormChoice,
        callBackReturnFlow = callBackReturnFlow
    )
}

@Composable
fun SendResponse(
    formField: FormField,
    responseLambda: (FormChoice?) -> Triple<Int, Int, Int>,
    renderFormContent: @Composable (Triple<Int, Int, Int>) -> Unit,
    selectedFormChoice: State<FormChoice>,
    callBackReturnFlow: State<Boolean>
) {
    if (callBackReturnFlow.value && formField.multipleChoiceDriverInputNeeded()) {
        renderFormContent(responseLambda(selectedFormChoice.value))
        return
    }
}

private fun getLeadingIcon(isExpanded: Boolean, isEditable: Boolean): ImageVector {
    return if (isExpanded && isEditable) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
}

// During config change the values in the selected choice gets lost because Currently compose doesn't provide a way to to store user defined object during config changes.
// Compose supports only the primitive type so we're retrieving it like this
private fun restoreValuesAfterConfigChange(
    formField: FormField,
    selectedOptionText: String,
    selectedFormChoice: MutableState<FormChoice>
) {
    if (selectedOptionText != formField.uiData) {
        formField.uiData = selectedOptionText
        if(formField.multipleChoiceDriverInputNeeded()) {
            selectedFormChoice.value =
                formField.formChoiceList?.find { it.value == formField.uiData } ?: FormChoice(
                    qnum = -1,
                    choicenum = -1,
                    value = EMPTY_STRING,
                    formid = -1
                )
        }
    }
}

private fun isDropDownExpansionEnable(isExpanded:Boolean,isEditable: Boolean) = isExpanded && isEditable

private fun isTrailingIconVisible(isReadOnlyForm:Boolean,isEditable: Boolean) = isEditable && isReadOnlyForm.not()