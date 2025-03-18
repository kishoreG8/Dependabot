package com.trimble.ttm.commons.composable.uiutils.styles

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Shapes
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.largeShapeDp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.mediumShapeDp
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.smallShapeDp

val Shapes = Shapes(
    small = RoundedCornerShape(smallShapeDp),
    medium = RoundedCornerShape(mediumShapeDp),
    large = RoundedCornerShape(largeShapeDp)
)