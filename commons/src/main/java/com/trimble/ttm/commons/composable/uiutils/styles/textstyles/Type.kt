package com.trimble.ttm.commons.composable.uiutils.styles.textstyles

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.trimble.ttm.commons.composable.uiutils.styles.darkGrey
import com.trimble.ttm.commons.composable.uiutils.styles.white


val Typography = Typography(
    h6 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = fontSize22Sp,
        color = white
    ),
    h5 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = fontSize18Sp,
        color = white
    ),
    h4 = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = fontSize16Sp,
        color = white
    ),
    h3 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = fontSize22Sp,
        color = white
    ),
    h2 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = fontSize14Sp,
        color = white
    ),
    body1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = fontSize26Sp,
        color = white
    ),
    body2 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = fontSize22Sp,
        color = white
    ),
    button = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = fontSize16Sp,
        color = white
    ),
    subtitle1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = fontSize32Sp,
        color = white
    ),
    subtitle2 = TextStyle(
        fontWeight = FontWeight.ExtraLight,
        fontSize = fontSize24Sp,
        color = darkGrey
    )
)