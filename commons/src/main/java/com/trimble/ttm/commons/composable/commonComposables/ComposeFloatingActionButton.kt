package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

data class FloatingActionButtonViewBuilder(
    val onclick: () -> Unit,
    val modifier: Modifier,
    val shape: Shape = CircleShape
)

@Composable
fun CustomFloatingActionButton(
    floatingActionButtonViewBuilder: FloatingActionButtonViewBuilder,
    iconViewBuilder: IconViewBuilder
) {
    FloatingActionButton(
        onClick = floatingActionButtonViewBuilder.onclick,
        modifier = floatingActionButtonViewBuilder.modifier,
        shape = floatingActionButtonViewBuilder.shape
    ) {
        Icon(
            painter = iconViewBuilder.painter,
            contentDescription = iconViewBuilder.contentDescription,
            modifier = iconViewBuilder.modifier,
            tint = iconViewBuilder.color
        )
    }
}