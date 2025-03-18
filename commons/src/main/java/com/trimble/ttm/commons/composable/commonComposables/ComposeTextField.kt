package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation

data class TextFieldViewBuilder(
    var text: String = "",
    val modifier: Modifier,
    val onValueChange: (String) -> Unit,
    var enabled: Boolean = true,
    val readOnly: Boolean = false,
    val textStyle: TextStyle,
    val label: @Composable (() -> Unit)? = null, // Header or Title for the field
    val placeholder: @Composable (() -> Unit)? = null, // Hint for the field
    val leadingIcon: @Composable (() -> Unit)? = null, // It will add icon at the start of the field
    val trailingIcon: @Composable (() -> Unit)? = null,  // It will add icon at the end of the field
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,  // It used to decide the keyboard type like numeric,text etc
    val keyboardActions: KeyboardActions = KeyboardActions(), // Used to provide IME actions like Done, Next in keyboard
    val maxLines: Int = Int.MAX_VALUE, // Defines the max input lines in the field
    val shape: Shape = RectangleShape,
    val visualTransformation: VisualTransformation = VisualTransformation.None, // Defines the visual representation of the text in the field like normal text or password
    val colors: TextFieldColors
)


@Composable
fun CustomTextField(textFieldViewBuilder: TextFieldViewBuilder) {
    TextField(
        value = textFieldViewBuilder.text,
        onValueChange = textFieldViewBuilder.onValueChange,
        modifier = textFieldViewBuilder.modifier,
        enabled = textFieldViewBuilder.enabled,
        readOnly = textFieldViewBuilder.readOnly,
        textStyle = textFieldViewBuilder.textStyle,
        label = textFieldViewBuilder.label,
        placeholder = textFieldViewBuilder.placeholder,
        leadingIcon = textFieldViewBuilder.leadingIcon,
        trailingIcon = textFieldViewBuilder.trailingIcon,
        keyboardOptions = textFieldViewBuilder.keyboardOptions,
        keyboardActions = textFieldViewBuilder.keyboardActions,
        maxLines = textFieldViewBuilder.maxLines,
        shape = textFieldViewBuilder.shape,
        colors = textFieldViewBuilder.colors,
    )
}