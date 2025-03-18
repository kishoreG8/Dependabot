package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale


data class ImageViewBuilder(
    var image: Painter,
    val contentDescription: String = "",
    val modifier: Modifier = Modifier,
    val alignment: Alignment = Alignment.Center,
    val contentScale: ContentScale = ContentScale.None, // It used to scale the size of the image
    val alpha: Float = DefaultAlpha, // It decides the transparency of the image
    val colorFilter: ColorFilter? = null // Used to change the color of the image
)

@Composable
fun CustomImage(imageViewBuilder: ImageViewBuilder){
    Image(
        painter = imageViewBuilder.image,
        contentDescription = imageViewBuilder.contentDescription,
        modifier = imageViewBuilder.modifier,
        alignment = imageViewBuilder.alignment,
        contentScale = imageViewBuilder.contentScale,
        alpha = imageViewBuilder.alpha,
        colorFilter = imageViewBuilder.colorFilter
    )
}