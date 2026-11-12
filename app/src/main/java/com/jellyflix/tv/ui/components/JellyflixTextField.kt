package com.jellyflix.tv.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

private val FieldBorder = Color(0xFF808080)
private val FieldBorderFocused = Color(0xFFE50914)
private val FieldText = Color(0xFFFFFFFF)
private val FieldLabel = Color(0xFFB3B3B3)
private val FieldContainer = Color(0xFF181818)
private val FieldContainerFocused = Color(0xFF2F2F2F)
private val FieldPlaceholder = Color(0xFF808080)
private val FieldCursor = Color(0xFFE50914)

/**
 * OutlinedTextField styled for the Jellyflix dark theme. Plain M3 uses its
 * default light-scheme colors when dropped into a tv-material Surface, which
 * renders black-on-black text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyflixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        label = { Text(label, color = FieldLabel) },
        placeholder = placeholder?.let { { Text(it, color = FieldPlaceholder) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FieldText,
            unfocusedTextColor = FieldText,
            disabledTextColor = FieldText,
            focusedContainerColor = FieldContainerFocused,
            unfocusedContainerColor = FieldContainer,
            disabledContainerColor = FieldContainer,
            cursorColor = FieldCursor,
            focusedBorderColor = FieldBorderFocused,
            unfocusedBorderColor = FieldBorder,
            focusedLabelColor = FieldBorderFocused,
            unfocusedLabelColor = FieldLabel,
        ),
        modifier = modifier,
    )
}

/** Password variant, same styling but with default password transformation. */
@Composable
fun JellyflixPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) = JellyflixTextField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
)
