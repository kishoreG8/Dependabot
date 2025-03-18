package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.ButtonColors
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class TextButtonViewBuilder(
    val onclick: () -> Unit,
    val modifier: Modifier,
    var enabled: Boolean = true,
    val border: BorderStroke = BorderStroke(0.dp, Color.Unspecified),
    val shape: Shape = RectangleShape,
    val colors: ButtonColors
)

@Composable
fun CustomTextButton(textButtonViewBuilder: TextButtonViewBuilder,textViewBuilder: TextViewBuilder) {
    TextButton(
        onClick = textButtonViewBuilder.onclick,
        modifier = textButtonViewBuilder.modifier,
        enabled = textButtonViewBuilder.enabled,
        border = textButtonViewBuilder.border,
        shape = textButtonViewBuilder.shape,
        colors = textButtonViewBuilder.colors,
    ) {
        CustomText(textViewBuilder = textViewBuilder)
    }
}