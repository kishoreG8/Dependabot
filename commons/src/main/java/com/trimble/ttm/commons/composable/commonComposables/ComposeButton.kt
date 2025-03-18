package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.trimble.ttm.commons.composable.uiutils.styles.darkSlateBlue


data class ButtonViewBuilder(
    val onclick: () -> Unit,
    val modifier: Modifier,
    var enabled: Boolean = true,
    val border: BorderStroke = BorderStroke(0.dp, Color.Unspecified),
    val shape: Shape = RectangleShape,
    val color: Color = darkSlateBlue
)

@Composable
fun CustomButton(buttonViewBuilder: ButtonViewBuilder, textViewBuilder: TextViewBuilder) {
    Button(
        onClick = buttonViewBuilder.onclick,
        modifier = buttonViewBuilder.modifier,
        enabled = buttonViewBuilder.enabled,
        border = buttonViewBuilder.border,
        shape = buttonViewBuilder.shape,
        colors = ButtonDefaults.buttonColors(backgroundColor = buttonViewBuilder.color)
    ) {
        CustomText(textViewBuilder)
    }
}