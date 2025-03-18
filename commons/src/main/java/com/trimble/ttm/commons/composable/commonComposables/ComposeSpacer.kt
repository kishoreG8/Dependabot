package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class SpaceViewBuilder(
    val modifier: Modifier
)

@Composable
fun CustomSpacer(spaceViewBuilder: SpaceViewBuilder){
    Spacer(modifier = spaceViewBuilder.modifier)
}