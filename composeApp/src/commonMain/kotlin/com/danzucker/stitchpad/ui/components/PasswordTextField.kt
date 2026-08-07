package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_password_hide
import stitchpad.composeapp.generated.resources.cd_password_show

/**
 * Themed password field with the same eye-toggle affordance as the auth screens.
 *
 * The auth screens use [com.danzucker.stitchpad.feature.auth.presentation.components.AuthTextField],
 * which is hard-wired to the dark AuthCard surface. In-app surfaces (settings
 * sheets and screens) follow the active theme, so this wraps [OutlinedTextField]
 * instead while keeping the toggle behaviour identical.
 *
 * [isVisible] is hoisted rather than remembered locally so the toggle lives in
 * the ViewModel with the rest of the screen state.
 */
@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    isError: Boolean = false,
    supportingText: String? = null,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (isVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (isVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (isVisible) Res.string.cd_password_hide else Res.string.cd_password_show,
                    ),
                )
            }
        },
        isError = isError,
        supportingText = supportingText?.let { text ->
            {
                Text(
                    text = text,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = colors,
        modifier = modifier.fillMaxWidth(),
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PasswordTextFieldHiddenPreview() {
    StitchPadTheme {
        PasswordTextField(
            value = "correcthorsebattery",
            onValueChange = {},
            label = "Password",
            isVisible = false,
            onToggleVisibility = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PasswordTextFieldVisibleErrorPreviewDark() {
    StitchPadTheme(darkTheme = true) {
        PasswordTextField(
            value = "wrongpass",
            onValueChange = {},
            label = "Password",
            isVisible = true,
            onToggleVisibility = {},
            isError = true,
            supportingText = "That password doesn't match our records.",
            modifier = Modifier.padding(16.dp),
        )
    }
}
