package com.trimble.ttm.commons.composable.utils.formfieldutils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.VALUE_IS_NOT_IN_RANGE
import kotlin.math.abs

class CurrencyTransformation(private val formField: FormField) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {

        val formattedText = convertToCurrency(removeSpecialCharacters(value = text.text))

        if(!formField.errorMessage.contains(VALUE_IS_NOT_IN_RANGE)){
            formField.uiData = formattedText
        }

        val offsetMapping = object : OffsetMapping {

            override fun originalToTransformed(offset: Int): Int {
                val transformedOffset = offset + abs(text.length - formattedText.length)
                return try {
                    transformedOffset
                } catch (exception: Exception) {
                    offset
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val transformedOffset = offset - abs(formattedText.length - text.length)
                return try {
                    if(transformedOffset < 0) offset else transformedOffset
                } catch (exception: Exception) {
                    offset
                }
            }
        }

        return TransformedText(
            text = AnnotatedString(formattedText),
            offsetMapping = offsetMapping
        )
    }
}