package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trimble.ttm.commons.composable.uiutils.styles.vividCerulean

data class CircularProgressBarViewBuilder(
    val modifier: Modifier = Modifier.padding(0.dp),
    val color: Color = vividCerulean,
    val strokeWidth: Int = 3
)

@Composable
fun CustomCircularProgressBar(circularProgressBarViewBuilder: CircularProgressBarViewBuilder) {
    CircularProgressIndicator(
        modifier = circularProgressBarViewBuilder.modifier,
        color = circularProgressBarViewBuilder.color,
        strokeWidth = circularProgressBarViewBuilder.strokeWidth.dp
    )
}