package com.trimble.ttm.commons.composable.customViews

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.constraintlayout.compose.ConstraintLayout
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.CustomTextButton
import com.trimble.ttm.commons.composable.commonComposables.TextButtonViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.azureBlue
import com.trimble.ttm.commons.composable.uiutils.styles.charCoalPurple
import com.trimble.ttm.commons.composable.uiutils.styles.darkGrey
import com.trimble.ttm.commons.composable.uiutils.styles.goldenRod
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize18Sp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.fontSize24Sp
import com.trimble.ttm.commons.composable.uiutils.styles.transparent
import com.trimble.ttm.commons.composable.uiutils.styles.white
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.CANCEL_BUTTON
import com.trimble.ttm.commons.utils.CLEAR_BUTTON
import com.trimble.ttm.commons.utils.CUSTOM_BARCODE_FIELD
import com.trimble.ttm.commons.utils.CUSTOM_DATE_FIELD
import com.trimble.ttm.commons.utils.CUSTOM_DATE_TIME_FIELD
import com.trimble.ttm.commons.utils.CUSTOM_TIME_FIELD
import com.trimble.ttm.commons.utils.DATE_PICKER_SELECTED_DATE_FORMAT
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.OK_BUTTON
import com.trimble.ttm.commons.utils.TITLE_TEXT
import java.util.Calendar

@Composable
fun ClearCancelOkButtonsComposable(
    context: Context,
    onOkButtonClicked: () -> Unit,
    onCancelButtonClicked: () -> Unit,
    onClearButtonClicked: (String) -> Unit
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        val (clearButton, cancelButton, saveButton) = createRefs()
        CustomTextButton(
            textButtonViewBuilder = TextButtonViewBuilder(
                onclick = {
                    onClearButtonClicked(EMPTY_STRING)
                },
                modifier = Modifier.constrainAs(clearButton) {
                    start.linkTo(parent.start)
                    top.linkTo(parent.top)
                },
                colors = ButtonDefaults.textButtonColors()
            ), textViewBuilder = TextViewBuilder(
                text = stringResource(id = R.string.clear_text),
                modifier = Modifier.semantics { contentDescription = CLEAR_BUTTON },
                textStyle = getTextStyle().copy(color = goldenRod)
            )
        )
        CustomTextButton(
            textButtonViewBuilder = TextButtonViewBuilder(
                onclick = {
                    onCancelButtonClicked()
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
                    onOkButtonClicked()
                },
                modifier = Modifier
                    .semantics { contentDescription = OK_BUTTON }
                    .constrainAs(saveButton) {
                        end.linkTo(parent.end)
                        top.linkTo(parent.top)
                    },
                colors = ButtonDefaults.textButtonColors()
            ), textViewBuilder = TextViewBuilder(
                text = stringResource(id = R.string.ok_text),
                modifier = Modifier,
                textStyle = getTextStyle().copy(color = goldenRod)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerView(pickedDateValue: MutableState<String>, dateValue: MutableState<String>, context: Context, title : String) {

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (dateValue.value.isNotEmpty()) {
            DateUtil.convertDateStringToMilliseconds(dateValue.value, context)
        } else {
            DateUtil.getCurrentTimeInMillisInUTC()
        }
    )

    val selectedDate = datePickerState.selectedDateMillis?.let {
        DateUtil.convertMillisToDateWithGivenFormat(it, DATE_PICKER_SELECTED_DATE_FORMAT)
    }

    pickedDateValue.value =
        datePickerState.selectedDateMillis?.let {
            DateUtil.convertMillisecondsToDeviceFormatDate(it, context)
        }.toString()

    val datePickerHeadlinePadding = PaddingValues(
        start = dimensionResource(id = R.dimen.dp_20),
        end = dimensionResource(id = R.dimen.dp_10),
        bottom = dimensionResource(id = R.dimen.dp_10)
    )
    val datePickerTitlePadding = PaddingValues(
        start = dimensionResource(id = R.dimen.dp_20),
        end = dimensionResource(id = R.dimen.dp_10),
        top = dimensionResource(id = R.dimen.dp_10)
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        DatePicker(
            state = datePickerState,
            showModeToggle = true,
            headline = {
                if (selectedDate != null) {
                    Text(
                        modifier = Modifier.padding(datePickerHeadlinePadding),
                        text = selectedDate,
                        color = white,
                        fontSize = fontSize24Sp
                    )
                }
            },
            title = {
                Text(
                    modifier = Modifier.padding(datePickerTitlePadding)
                        .semantics { contentDescription = TITLE_TEXT },
                    text = title,
                    color = white,
                    fontSize = fontSize18Sp
                )
            },
            colors = DatePickerDefaults.colors(
                containerColor = charCoalPurple,
                titleContentColor = white,
                headlineContentColor = white,
                subheadContentColor = white,
                weekdayContentColor = darkGrey,
                dayContentColor = white,
                selectedDayContainerColor = goldenRod,
                todayContentColor = white,
                todayDateBorderColor = white,
                yearContentColor = white,
                selectedYearContainerColor = goldenRod,
                selectedYearContentColor = white,
                dividerColor = transparent,
                navigationContentColor = white,
                selectedDayContentColor = azureBlue,
                currentYearContentColor = white,
                dateTextFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = white,
                    unfocusedTextColor = white,
                    focusedBorderColor = azureBlue,
                    unfocusedBorderColor = white,
                    unfocusedLabelColor = white,
                    focusedLabelColor = white,
                    cursorColor = azureBlue
                )
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerView(
    pickedTimeValue: MutableState<String>,
    timeValue: MutableState<String>,
    timeFormat : String,
    context: Context
) {
    var selectedHour by rememberSaveable {
        mutableIntStateOf(DateUtil.getCalendar().get(Calendar.HOUR_OF_DAY))
    }

    var selectedMinute by rememberSaveable {
        mutableIntStateOf(DateUtil.getCalendar().get(Calendar.MINUTE))
    }

    val is24HourFormat: Boolean = DateFormat.is24HourFormat(context)
    val formatter = rememberSaveable {
        DateUtil.getTimeFormat(DateUtil.getSystemTimeFormat(context).toPattern())
    }

    if (timeValue.value.isNotEmpty()) {
        selectedHour = DateUtil.getHourAndMinuteFromTimeString(timeValue.value, timeFormat).first
        selectedMinute = DateUtil.getHourAndMinuteFromTimeString(timeValue.value, timeFormat).second
    }

    val timePickerState = rememberTimePickerState(
        initialHour =
        selectedHour, initialMinute =
        selectedMinute, is24Hour = is24HourFormat
    )


    val selectedCalender = DateUtil.getCalendar()
    selectedCalender.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
    selectedCalender.set(Calendar.MINUTE, timePickerState.minute)
    selectedCalender.isLenient = false

    pickedTimeValue.value = formatter.format(selectedCalender.time)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        TimePicker(
            state = timePickerState,

            colors = TimePickerDefaults.colors(
                containerColor = charCoalPurple,
                selectorColor = goldenRod,
                clockDialSelectedContentColor = white,
                clockDialUnselectedContentColor = white,
                clockDialColor = charCoalPurple,
                periodSelectorBorderColor = white,
                periodSelectorSelectedContainerColor = white,
                periodSelectorUnselectedContainerColor = darkGrey,
                periodSelectorSelectedContentColor = goldenRod,
                periodSelectorUnselectedContentColor = white,
                timeSelectorSelectedContainerColor = white,
                timeSelectorUnselectedContainerColor = darkGrey,
                timeSelectorSelectedContentColor = goldenRod,
                timeSelectorUnselectedContentColor = white

            ),
            layoutType = TimePickerDefaults.layoutType()

        )
    }
}

@Composable
fun DialogComposable(
    dialogState: MutableState<Boolean>,
    context: Context,
    formField: FormField,
    fieldValue: MutableState<String>,
    fromField: String
) {
    if (dialogState.value) {
        DisplayDialog(
            dialogState = dialogState,
            context = context,
            formField = formField,
            fieldValue = fieldValue,
            fromField = fromField
        )
    }
}

@Composable
fun DisplayDialog(
    dialogState: MutableState<Boolean>,
    context: Context,
    formField: FormField,
    fieldValue: MutableState<String>,
    fromField : String
) {
    Dialog(
        onDismissRequest = {
            dialogState.value = false
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        when(fromField){
           CUSTOM_DATE_FIELD -> {
               CustomDateDialog(
                   dateDialogState = dialogState,
                   context = context,
                   formField = formField,
                   dateValue = fieldValue
               )
           }
            CUSTOM_TIME_FIELD -> {
                CustomTimeDialog(
                    timeDialogState = dialogState,
                    context = context,
                    formField = formField,
                    timeValue = fieldValue
                )
            }
            CUSTOM_DATE_TIME_FIELD -> {
                CustomDateTimeDialog(
                    dateDialogState = dialogState,
                    context = context,
                    formField = formField,
                    dateTimeValue = fieldValue
                )
            }
            CUSTOM_BARCODE_FIELD -> {
                CustomBarcodeDialog(
                    barcodeDialogState = dialogState,
                    context = context,
                    formField = formField,
                    barcodeValue = fieldValue
                )
            }
        }
    }
}