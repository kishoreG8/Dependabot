package com.trimble.ttm.commons.composable.customViews

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.constraintlayout.compose.ConstraintLayout
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.CustomIcon
import com.trimble.ttm.commons.composable.commonComposables.CustomOutlineTextField
import com.trimble.ttm.commons.composable.commonComposables.CustomSpacer
import com.trimble.ttm.commons.composable.commonComposables.CustomText
import com.trimble.ttm.commons.composable.commonComposables.CustomTextButton
import com.trimble.ttm.commons.composable.commonComposables.DefaultModifier
import com.trimble.ttm.commons.composable.commonComposables.IconViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.OutlineTextFieldViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.SpaceViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextButtonViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.darkSlateBlue
import com.trimble.ttm.commons.composable.uiutils.styles.dimGrey
import com.trimble.ttm.commons.composable.uiutils.styles.goldenRod
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize16Sp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize20Sp
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkIfTheFieldIsEditable
import com.trimble.ttm.commons.composable.utils.formfieldutils.getFieldColor
import com.trimble.ttm.commons.composable.utils.formfieldutils.getLabelText
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.performSendButtonClick
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.BARCODE_FIELD
import com.trimble.ttm.commons.utils.BARCODE_FIELD_ICON
import com.trimble.ttm.commons.utils.BARCODE_LIST_ITEM
import com.trimble.ttm.commons.utils.BARCODE_SCAN_LIST_ITEM
import com.trimble.ttm.commons.utils.CANCEL_BUTTON
import com.trimble.ttm.commons.utils.CLOSE_ICON_LIST_ITEM
import com.trimble.ttm.commons.utils.CUSTOM_BARCODE_FIELD
import com.trimble.ttm.commons.utils.SAVE_BUTTON
import com.trimble.ttm.commons.utils.TAP_TO_SCAN
import com.trimble.ttm.commons.utils.TAP_TO_SCAN_CLICK
import com.trimble.ttm.commons.utils.barcodeScanner
import kotlinx.coroutines.launch

@Composable
fun CustomBarcodeField(
    formField: FormField,
    isFormSaved: Boolean,
    sendButtonState: State<Boolean?>,
    isFormInReadOnlyMode: Boolean
) {
    val barcodeValue =
        rememberSaveable { mutableStateOf(formField.uiData) }
    val isEditable = checkIfTheFieldIsEditable(formField = formField, isFormSaved = isFormSaved)
    val errorText = rememberSaveable { mutableStateOf(formField.errorMessage) }
    val context = LocalContext.current
    val barcodeDialogState = rememberSaveable { mutableStateOf(false) }


    CustomOutlineTextField(
        outlineTextFieldViewBuilder = OutlineTextFieldViewBuilder(
            text = barcodeValue.value,
            onValueChange = {},
            enabled = isEditable,
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
                val barcodeImage = if (isEditable) {
                    R.drawable.ic_barcode
                } else {
                    R.drawable.ic_barcode_gray
                }
                CustomIcon(
                    iconViewBuilder = IconViewBuilder(
                        painter = painterResource(id = barcodeImage),
                        color = Color.White,
                        contentDescription = BARCODE_FIELD_ICON,
                        modifier = Modifier
                    )
                )
            },
            shape = MaterialTheme.shapes.medium,
            maxLines = 1,
            modifier = DefaultModifier.defaultTextFieldModifier
                .semantics { contentDescription = BARCODE_FIELD }
                .padding(
                    start = dimensionResource(id = R.dimen.padding_5dp),
                    end = dimensionResource(id = R.dimen.padding_5dp),
                    top = dimensionResource(id = R.dimen.padding_8dp),
                    bottom = dimensionResource(id = R.dimen.padding_8dp)
                )
                .clickable(enabled = isEditable, onClick = {})
                .focusable(enabled = isEditable),
            colors = getFieldColor(formField = formField),
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                barcodeDialogState.value = true
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
        dialogState = barcodeDialogState,
        context = context,
        formField = formField,
        fieldValue = barcodeValue,
        fromField = CUSTOM_BARCODE_FIELD
    )

}

@Composable
fun BarcodeDialogComposable(
    barcodeDialogState: MutableState<Boolean>,
    context: Context,
    formField: FormField,
    barcodeValue: MutableState<String>
) {
    if (barcodeDialogState.value) {
        DisplayBarcodeDialog(
            barcodeDialogState = barcodeDialogState,
            context = context,
            formField = formField,
            barcodeValue = barcodeValue
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DisplayBarcodeDialog(
    barcodeDialogState: MutableState<Boolean>,
    context: Context,
    formField: FormField,
    barcodeValue: MutableState<String>
) {
    Dialog(
        onDismissRequest = {
            barcodeDialogState.value = false
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {


    }
}

@Composable
fun CustomBarcodeDialog(
    barcodeDialogState: MutableState<Boolean>,
    context: Context,
    formField: FormField,
    barcodeValue: MutableState<String>,

    ) {
    var barcodeList by rememberSaveable {
        mutableStateOf(
            barcodeValue.value.split(",").filter { it.isNotBlank() }.toList()
        )
    }

    if (formField.bcLimitMultiples <= barcodeList.size) {
        Toast.makeText(
            context,
            context.getString(R.string.scan_bar_code_info_allowed_count), Toast.LENGTH_SHORT
        ).show()
    }

    Column(
        modifier = Modifier
            .background(darkSlateBlue)
            .fillMaxWidth()
            .padding(
                dimensionResource(id = R.dimen.padding_size_20)
            )
    ) {
        CustomText(
            textViewBuilder = TextViewBuilder(
                text = stringResource(id = R.string.scan_barcode),
                modifier = Modifier.align(Alignment.Start),
                textStyle = getTextStyle().copy(fontSize = dimensionResource(id = R.dimen.signature_title_size).value.sp)
            )
        )
        CustomSpacer(
            spaceViewBuilder = SpaceViewBuilder(
                modifier = Modifier.height(dimensionResource(id = R.dimen.padding_size_15))
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(id = R.dimen.dp_300))// Adjust the width of the outlined box
                .border(
                    width = dimensionResource(id = R.dimen.dp_1), // Adjust the border width
                    color = Color.White, // Set the border color
                    shape = RoundedCornerShape(dimensionResource(id = R.dimen.dp_8))
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                TapToScanLayout(
                    formField = formField,
                    context = context,
                    onBarcodeDataAdded = { barcodeScannerData ->
                        barcodeList = barcodeList
                            .toMutableList()
                            .apply {
                                add(barcodeScannerData)
                            }
                    },
                    barcodeListSize = barcodeList.size
                )

                BarcodesListLayout(barcodeList, onBarcodeDataRemoved = { index ->
                    barcodeList = barcodeList
                        .toMutableList()
                        .apply {
                            removeAt(index)
                        }
                })
            }
        }

        CustomSpacer(
            spaceViewBuilder = SpaceViewBuilder(
                modifier = Modifier.height(dimensionResource(id = R.dimen.padding_size_15))
            )
        )

        CancelAndSaveButtons(context,barcodeList,
            barcodeDialogState={dialogState->
            barcodeDialogState.value = dialogState
        },
            onSaveButtonClicked = {barcodeData->
                barcodeValue.value = barcodeData
                formField.uiData = barcodeValue.value
            })

    }
}

@Composable
fun TapToScanLayout(
    formField: FormField,
    context: Context, barcodeListSize: Int,
    onBarcodeDataAdded: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.dp_80))
            .padding(dimensionResource(id = R.dimen.dp_10))
            .border(
                width = dimensionResource(id = R.dimen.dp_1),
                color = dimGrey,
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.dp_8))
            )
            .semantics { contentDescription = TAP_TO_SCAN_CLICK }
            .clickable {

                coroutineScope.launch {
                    if (formField.bcLimitMultiples <= barcodeListSize) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.scan_bar_code_error_exceed_count),
                                Toast.LENGTH_LONG
                            )
                            .show()
                    } else {
                        val barcodeScannerData = barcodeScanner(context)
                        if (barcodeScannerData.isNotBlank()) {
                            onBarcodeDataAdded(barcodeScannerData)

                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(id = R.dimen.dp_16)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.tap_here_to_scan),
                color = Color.White, // Change text color if needed
                fontSize = fontSize20Sp
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_barcode), // Replace with your icon resource
                contentDescription = TAP_TO_SCAN,
                modifier = Modifier.size(dimensionResource(id = R.dimen.dp_40)),
                tint = Color.White // Change icon color if needed
            )
        }
    }
}

@Composable
fun BarcodesListLayout(barcodeList: List<String>, onBarcodeDataRemoved: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.dp_200))
    ) {
        if (barcodeList.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                barcodeList.forEachIndexed { index, item ->
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimensionResource(id = R.dimen.dp_50))
                                .padding(dimensionResource(id = R.dimen.dp_8))
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        placeable.place(0, 0)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier
                                        .padding(end = dimensionResource(id = R.dimen.dp_16)) // Add padding to the left side
                                        .background(Color.Transparent) // Transparent background for text
                                        .padding(dimensionResource(id = R.dimen.dp_4)), // Add padding to the text
                                    color = Color.White,
                                    fontSize = fontSize16Sp
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .semantics {
                                            contentDescription = BARCODE_LIST_ITEM
                                        }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_barcode),
                                        contentDescription = BARCODE_SCAN_LIST_ITEM,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(dimensionResource(id = R.dimen.dp_40))
                                            .padding(end = dimensionResource(id = R.dimen.dp_8))

                                    )
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_cancel_white),
                                        contentDescription = CLOSE_ICON_LIST_ITEM,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(dimensionResource(id = R.dimen.dp_40))
                                            .clickable {
                                                // Remove the item from the list when Close Icon is clicked
                                                onBarcodeDataRemoved(index)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CancelAndSaveButtons(context: Context,
                         barcodeList: List<String>,
                         barcodeDialogState:(Boolean)->Unit,
                         onSaveButtonClicked:(String) ->Unit){
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        val (cancelButton, saveButton) = createRefs()
        CustomTextButton(
            textButtonViewBuilder = TextButtonViewBuilder(
                onclick = {
                    barcodeDialogState(false)
                },
                modifier = Modifier
                    .semantics { contentDescription = CANCEL_BUTTON }
                    .constrainAs(cancelButton) {
                        end.linkTo(
                            saveButton.start,
                            context.resources.getDimension(R.dimen.padding_8dp).dp
                        )
                        baseline.linkTo(saveButton.baseline)
                    },
                colors = ButtonDefaults.textButtonColors()
            ), textViewBuilder = TextViewBuilder(
                text = stringResource(id = R.string.cancel_text),
                modifier = Modifier,
                textStyle = getTextStyle().copy(color = goldenRod)
            )
        )
        CustomTextButton(
            textButtonViewBuilder = TextButtonViewBuilder(
                onclick = {
                    onSaveButtonClicked(barcodeList.joinToString(","))
                    barcodeDialogState(false)
                },
                modifier = Modifier
                    .semantics { contentDescription = SAVE_BUTTON }
                    .constrainAs(saveButton) {
                        end.linkTo(parent.end)
                        top.linkTo(parent.top)
                    },
                colors = ButtonDefaults.textButtonColors()
            ), textViewBuilder = TextViewBuilder(
                text = stringResource(id = R.string.save),
                modifier = Modifier,
                textStyle = getTextStyle().copy(color = goldenRod)
            )
        )
    }
}
