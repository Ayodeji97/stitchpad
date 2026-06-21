package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors

@Composable
internal actual fun AuthPlatformTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    placeholder: String,
    // Compose's BasicTextField is already exposed to TalkBack; no extra label needed.
    @Suppress("UNUSED_PARAMETER") accessibilityLabel: String,
    autofill: AuthAutofill,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean,
    isPasswordVisible: Boolean,
    onFocusChange: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focusState ->
            onFocusChange(focusState.isFocused)
        },
        singleLine = true,
        cursorBrush = SolidColor(LocalStitchPadColors.current.brandAccent),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 15.sp,
            color = Color(0xFFF5F2ED),
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword && keyboardType == KeyboardType.Text) {
                KeyboardType.Password
            } else {
                keyboardType
            },
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() },
        ),
        visualTransformation = when {
            isPassword && !isPasswordVisible -> PasswordVisualTransformation()
            else -> VisualTransformation.None
        },
        decorationBox = { inner ->
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    style = TextStyle(fontSize = 15.sp, color = Color(0xFF7D7970)),
                )
            }
            inner()
        },
    )
}
