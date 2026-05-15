package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens

/**
 * Themed text field for auth screens — icon prefix + optional eye-toggle suffix.
 * Always renders on dark surfaces (matches AuthCard).
 */
@Suppress("CyclomaticComplexMethod")
@Composable
fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    trailingPasswordVisibilityIcon: ImageVector? = null,
    passwordVisibilityContentDescription: String? = null,
    errorText: String? = null,
    helperText: String? = null,
    helperIcon: ImageVector? = null,
    isHelperSuccess: Boolean = false,
    onFocusLost: (() -> Unit)? = null,
) {
    val borderColor = when {
        errorText != null -> DesignTokens.error500
        else -> Color(0xFF3A3731)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF5F2ED),
                ),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2A2825))
                .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            val wasFocused = remember { mutableStateOf(false) }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (wasFocused.value && !focusState.isFocused) {
                            onFocusLost?.invoke()
                        }
                        wasFocused.value = focusState.isFocused
                    },
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
            if (isPassword && onTogglePassword != null && trailingPasswordVisibilityIcon != null) {
                IconButton(onClick = onTogglePassword, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = trailingPasswordVisibilityIcon,
                        contentDescription = passwordVisibilityContentDescription,
                        tint = Color(0xFF7D7970),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        when {
            errorText != null -> Text(
                text = errorText,
                style = TextStyle(fontSize = 12.5.sp, color = DesignTokens.error500),
            )
            helperText != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (helperIcon != null) {
                    Icon(
                        imageVector = helperIcon,
                        contentDescription = null,
                        tint = if (isHelperSuccess) DesignTokens.success500 else Color(0xFFA8A49D),
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = helperText,
                    style = TextStyle(
                        fontSize = 12.5.sp,
                        color = if (isHelperSuccess) DesignTokens.success500 else Color(0xFFA8A49D),
                    ),
                )
            }
        }
    }
}
