package com.trimble.ttm.commons.composable.commonComposables


import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.trimble.ttm.commons.composable.uiutils.styles.amber

data class IconViewBuilder(
    val painter: Painter,
    val contentDescription: String = "",
    val modifier: Modifier,
    val color: Color = amber
)

@Composable
fun CustomIcon(iconViewBuilder: IconViewBuilder) {
    Icon(
        painter = iconViewBuilder.painter, contentDescription = iconViewBuilder.contentDescription,
        modifier = iconViewBuilder.modifier, tint = iconViewBuilder.color
    )
}