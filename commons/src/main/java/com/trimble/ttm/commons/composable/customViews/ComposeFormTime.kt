package com.trimble.ttm.commons.composable.customViews

import android.annotation.SuppressLint
import android.widget.TimePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.commonComposables.ButtonViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.CustomButton
import com.trimble.ttm.commons.composable.commonComposables.CustomIcon
import com.trimble.ttm.commons.composable.commonComposables.CustomOutlineTextField
import com.trimble.ttm.commons.composable.commonComposables.CustomSpacer
import com.trimble.ttm.commons.composable.commonComposables.CustomText
import com.trimble.ttm.commons.composable.commonComposables.DefaultModifier
import com.trimble.ttm.commons.composable.commonComposables.IconViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.OutlineTextFieldViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.SpaceViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.darkGrey
import com.trimble.ttm.commons.composable.uiutils.styles.darkSlateBlue
import com.trimble.ttm.commons.composable.uiutils.styles.silverGrey
import com.trimble.ttm.commons.composable.uiutils.styles.vividCerulean
import com.trimble.ttm.commons.utils.DateUtil
import java.util.Calendar

@SuppressLint("SimpleDateFormat")
@Composable
fun CustomTimePicker(enabled: Boolean = true) {
    val timeValue = rememberSaveable { mutableStateOf("") }
    val isDialogDisplayed = rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val calender = DateUtil.getCalendar()
    val timePicker = TimePicker(context)
    CustomOutlineTextField(
        outlineTextFieldViewBuilder = OutlineTextFieldViewBuilder(
            text = timeValue.value,
            onValueChange = { timeValue.value = it },
            enabled = enabled,
            readOnly = enabled,
            keyboardActions = KeyboardActions.Default,
            textStyle = MaterialTheme.typography.subtitle1,
            label = {
                CustomText(
                    textViewBuilder = TextViewBuilder(
                        text = "Select Time*",
                        modifier = Modifier.padding(1.dp),
                        textStyle = MaterialTheme.typography.subtitle1
                    )
                )
            },
            trailingIcon = {
                if (!enabled) {
                    CustomIcon(
                        iconViewBuilder = IconViewBuilder(
                            painter = painterResource(id = R.drawable.ic_time_dark_grey),
                            modifier = Modifier,
                            color = darkGrey
                        )
                    )
                } else {
                    CustomIcon(
                        iconViewBuilder = IconViewBuilder(
                            painter = painterResource(id = R.drawable.ic_access_time),
                            color = Color.White,
                            modifier = Modifier
                        )
                    )
                }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = DefaultModifier.defaultTextFieldModifier
                .padding(
                    start = dimensionResource(id = R.dimen.padding_5dp),
                    end = dimensionResource(id = R.dimen.padding_5dp),
                    top = dimensionResource(id = R.dimen.padding_8dp),
                    bottom = dimensionResource(id = R.dimen.padding_8dp)
                )
                .clickable(enabled = enabled) {}
                .focusable(enabled = enabled),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = darkSlateBlue,
                focusedBorderColor = vividCerulean,
                unfocusedBorderColor = silverGrey
            ),
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                isDialogDisplayed.value = true
                            }
                        }
                    }
                }
        )
    )
    if (isDialogDisplayed.value) {
        var dialogWidth = LocalConfiguration.current.screenWidthDp
        dialogWidth -= dialogWidth / 4
        Dialog(onDismissRequest = { isDialogDisplayed.value = !isDialogDisplayed.value }) {
            Column(
                modifier = Modifier
                    .width(dialogWidth.dp)
            ) {
                CustomText(
                    textViewBuilder = TextViewBuilder(
                        text = "Choose Time",
                        textStyle = MaterialTheme.typography.subtitle1,
                        modifier = Modifier.align(Alignment.Start)
                    )
                )
                AndroidView(
                    {
                        timePicker.apply {
                            this.setBackgroundColor(0xFFFFA500.toInt())
                        }
                    },
                    update = {
                        timePicker.setOnTimeChangedListener { _, hour, minute ->
                            calender.set(
                                Calendar.YEAR,
                                Calendar.MONTH,
                                Calendar.DAY_OF_MONTH,
                                hour,
                                minute
                            )
                            timeValue.value = DateUtil.getSystemTimeFormat(context).format(calender.time)
                        }
                    },
                    modifier = Modifier.wrapContentWidth()
                )
                CustomSpacer(spaceViewBuilder = SpaceViewBuilder(modifier = Modifier.width(10.dp)))
                Box(modifier = Modifier.fillMaxWidth()) {
                    CustomButton(
                        buttonViewBuilder = ButtonViewBuilder(
                            onclick = {
                                timeValue.value = ""
                                isDialogDisplayed.value = false
                            },
                            modifier = Modifier.align(Alignment.TopStart)
                        ),
                        textViewBuilder = TextViewBuilder(
                            text = "CLEAR",
                            modifier = Modifier,
                            textStyle = MaterialTheme.typography.h3
                        )
                    )
                    CustomButton(
                        buttonViewBuilder = ButtonViewBuilder(
                            onclick = { isDialogDisplayed.value = false },
                            modifier = Modifier.align(Alignment.TopCenter)
                        ),
                        textViewBuilder = TextViewBuilder(
                            text = "CANCEL",
                            modifier = Modifier,
                            textStyle = MaterialTheme.typography.h3
                        )
                    )
                    CustomButton(
                        buttonViewBuilder = ButtonViewBuilder(
                            onclick = { isDialogDisplayed.value = false },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ),
                        textViewBuilder = TextViewBuilder(
                            text = "OK",
                            modifier = Modifier,
                            textStyle = MaterialTheme.typography.h3
                        )
                    )
                }
            }
        }
    }
}