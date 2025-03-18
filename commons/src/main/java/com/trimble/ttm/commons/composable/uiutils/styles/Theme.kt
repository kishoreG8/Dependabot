package com.trimble.ttm.commons.composable.uiutils.styles

import android.annotation.SuppressLint
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable


@Composable
fun WorkFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = LightColor,
        shapes = Shapes,
        content = content
    )
}


@SuppressLint("ConflictingOnColor")
val LightColor = darkColors(
    primary = charCoalPurple,
    primaryVariant = darkSlateBlue,
    secondary = vividCerulean,
    secondaryVariant = vividCerulean,
    surface = darkSlateBlue,
    background = darkSlateBlue,
    onBackground = white,
    onSurface = white
)