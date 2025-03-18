package com.trimble.ttm.commons.composable.utils.formfieldutils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.VALUE_IS_NOT_IN_RANGE
import kotlin.math.abs

class ThousandSeparatorTransformation(private val formField: FormField) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {


        val outputText = convertToThousandSeparatedString(value = text.text)

        if(!formField.errorMessage.contains(VALUE_IS_NOT_IN_RANGE)){
            formField.uiData = outputText
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val transformedOffset = offset + abs(outputText.length - text.length)
                return try {
                    transformedOffset
                } catch (exception: Exception) {
                    offset
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val transformedOffset = offset - abs(outputText.length - text.length)
                return try {
                    if(transformedOffset < 0) offset else transformedOffset
                } catch (exception: Exception) {
                    offset
                }
            }
        }

        return TransformedText(
            text = AnnotatedString(outputText),
            offsetMapping = offsetMapping
        )
    }
}

