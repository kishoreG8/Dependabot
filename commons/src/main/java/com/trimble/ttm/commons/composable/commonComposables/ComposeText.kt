package com.trimble.ttm.commons.composable.commonComposables


import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.trimble.ttm.commons.utils.EMPTY_STRING

data class TextViewBuilder(
    var text: String = EMPTY_STRING,
    var annotatedText: AnnotatedString = AnnotatedString(EMPTY_STRING),
    val alignment: TextAlign? = null,
    val modifier: Modifier,
    val textStyle: TextStyle
)


@Composable
fun CustomText(textViewBuilder: TextViewBuilder) {
    Text(
        text = textViewBuilder.text,
        textAlign = textViewBuilder.alignment,
        modifier = textViewBuilder.modifier,
        style = textViewBuilder.textStyle
    )
}

@Composable
fun CustomAnnotatedText(textViewBuilder: TextViewBuilder){
    Text(
        text = textViewBuilder.annotatedText,
        textAlign = textViewBuilder.alignment,
        modifier = textViewBuilder.modifier,
        style = textViewBuilder.textStyle
    )
}