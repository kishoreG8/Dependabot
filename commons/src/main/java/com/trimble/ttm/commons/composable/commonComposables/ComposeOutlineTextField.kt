package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.trimble.ttm.commons.composable.uiutils.styles.textstyles.paddingSize1Dp

data class OutlineTextFieldViewBuilder(
    var text: String = "",
    val modifier: Modifier,
    val onValueChange: (String) -> Unit,
    var enabled: Boolean = true,
    val readOnly: Boolean = false,
    var isError: Boolean = false,
    val textStyle: TextStyle,
    val label: @Composable (() -> Unit)? = null, // Header or Title for the field
    val placeholder: @Composable (() -> Unit)? = null, // Hint for the field
    val leadingIcon: @Composable (() -> Unit)? = null, // It will add icon at the start of the field
    val trailingIcon: @Composable (() -> Unit)? = null,  // It will add icon at the end of the field
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,  // It used to decide the keyboard type like numeric,text etc
    val keyboardActions: KeyboardActions = KeyboardActions(), // Used to provide IME actions like Done, Next in keyboard
    val maxLines: Int = Int.MAX_VALUE, // Defines the max input lines in the field
    val shape: Shape = RectangleShape,
    val singleLine: Boolean = false,
    val interactionSource: MutableInteractionSource,
    val visualTransformation: VisualTransformation = VisualTransformation.None, // Defines the visual representation of the text in the field like normal text or password
    val colors: TextFieldColors
)

object DefaultModifier {
    val defaultTextFieldModifier = Modifier
        .fillMaxWidth()
        .border(
            paddingSize1Dp,
            Color.Unspecified
        )
}

@Composable
fun CustomOutlineTextField(outlineTextFieldViewBuilder: OutlineTextFieldViewBuilder) {
    OutlinedTextField(
        value = outlineTextFieldViewBuilder.text,
        onValueChange = outlineTextFieldViewBuilder.onValueChange,
        modifier = outlineTextFieldViewBuilder.modifier,
        enabled = outlineTextFieldViewBuilder.enabled,
        readOnly = outlineTextFieldViewBuilder.readOnly,
        textStyle = outlineTextFieldViewBuilder.textStyle,
        label = outlineTextFieldViewBuilder.label,
        placeholder = outlineTextFieldViewBuilder.placeholder,
        leadingIcon = outlineTextFieldViewBuilder.leadingIcon,
        trailingIcon = outlineTextFieldViewBuilder.trailingIcon,
        keyboardOptions = outlineTextFieldViewBuilder.keyboardOptions,
        keyboardActions = outlineTextFieldViewBuilder.keyboardActions,
        maxLines = outlineTextFieldViewBuilder.maxLines,
        isError = outlineTextFieldViewBuilder.isError,
        shape = outlineTextFieldViewBuilder.shape,
        singleLine = outlineTextFieldViewBuilder.singleLine,
        interactionSource = outlineTextFieldViewBuilder.interactionSource,
        visualTransformation = outlineTextFieldViewBuilder.visualTransformation,
        colors = outlineTextFieldViewBuilder.colors
    )
}