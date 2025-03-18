package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trimble.ttm.commons.composable.uiutils.styles.charCoalPurple


data class CheckBoxViewBuilder(
    var checked: Boolean = false,
    val onCheckChange: (Boolean) -> Unit,
    val modifier: Modifier = Modifier.padding(0.dp),
    val enabled: Boolean = true,
    val checkMarkColor: Color = charCoalPurple,
    val checkedColor: Color = Color.White,
    val unCheckedColor: Color = Color.White
)


@Composable
fun CustomCheckBox(checkBoxViewBuilder: CheckBoxViewBuilder) {
    Checkbox(
        checked = checkBoxViewBuilder.checked,
        onCheckedChange = checkBoxViewBuilder.onCheckChange,
        modifier = checkBoxViewBuilder.modifier,
        colors = CheckboxDefaults.colors(
            checkmarkColor = checkBoxViewBuilder.checkMarkColor,
            checkedColor = checkBoxViewBuilder.checkedColor,
            uncheckedColor = checkBoxViewBuilder.unCheckedColor
        ),
        enabled = checkBoxViewBuilder.enabled
    )
}