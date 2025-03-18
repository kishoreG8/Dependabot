package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


data class IconButtonViewBuilder(
    val onclick :() -> Unit,
    val modifier: Modifier,
    var enabled: Boolean = true,
)

@Composable
fun CustomIconButton(iconButtonViewBuilder: IconButtonViewBuilder, iconViewBuilder: IconViewBuilder) {
    IconButton(
        onClick = iconButtonViewBuilder.onclick,
        modifier = iconButtonViewBuilder.modifier,
        enabled = iconButtonViewBuilder.enabled
    ) {
        CustomIcon(iconViewBuilder)
    }
}