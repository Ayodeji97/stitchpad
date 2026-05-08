package com.danzucker.stitchpad.feature.settings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.reauth_cancel
import stitchpad.composeapp.generated.resources.reauth_cta_apple
import stitchpad.composeapp.generated.resources.reauth_cta_google
import stitchpad.composeapp.generated.resources.reauth_cta_password
import stitchpad.composeapp.generated.resources.reauth_forgot_password
import stitchpad.composeapp.generated.resources.reauth_label_email
import stitchpad.composeapp.generated.resources.reauth_label_password
import stitchpad.composeapp.generated.resources.reauth_subtitle
import stitchpad.composeapp.generated.resources.reauth_title

/**
 * Provider-aware re-authentication sheet, reused by Change Email,
 * Change Password, and Delete Account.
 *
 * The caller controls visibility — wrap this in `if (state.showReauth)`
 * and supply [onConfirm] / [onDismiss] callbacks. The form is stateless;
 * password text is hoisted to the caller's ViewModel so reauth events stay
 * in MVI flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReauthBottomSheet(
    provider: SignInProvider,
    email: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onForgotPassword: () -> Unit,
    confirmButtonLabel: String = stringResource(Res.string.reauth_cta_password),
    isLoading: Boolean = false,
    errorText: String? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = DesignTokens.space4,
                    end = DesignTokens.space4,
                    bottom = DesignTokens.space5,
                ),
        ) {
            Text(
                text = stringResource(Res.string.reauth_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.reauth_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(DesignTokens.space4))

            when (provider) {
                SignInProvider.EMAIL_PASSWORD -> ReauthPasswordForm(
                    email = email,
                    password = password,
                    onPasswordChange = onPasswordChange,
                    errorText = errorText,
                )
                SignInProvider.APPLE,
                SignInProvider.GOOGLE,
                SignInProvider.UNKNOWN -> ReauthSsoForm(
                    provider = provider,
                    errorText = errorText,
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.space4))
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.reauth_cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = !isLoading && (provider != SignInProvider.EMAIL_PASSWORD || password.isNotBlank()),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = confirmButtonLabel, fontWeight = FontWeight.Bold)
                }
            }

            if (provider == SignInProvider.EMAIL_PASSWORD) {
                Spacer(modifier = Modifier.height(DesignTokens.space3))
                TextButton(
                    onClick = onForgotPassword,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(Res.string.reauth_forgot_password),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReauthPasswordForm(
    email: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    errorText: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
        OutlinedTextField(
            value = email,
            onValueChange = {},
            enabled = false,
            label = { Text(stringResource(Res.string.reauth_label_email)) },
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(Res.string.reauth_label_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            isError = errorText != null,
            supportingText = if (errorText != null) {
                { Text(errorText, color = MaterialTheme.colorScheme.error) }
            } else null,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReauthSsoForm(
    provider: SignInProvider,
    errorText: String?,
) {
    val ctaText = when (provider) {
        SignInProvider.APPLE -> stringResource(Res.string.reauth_cta_apple)
        SignInProvider.GOOGLE -> stringResource(Res.string.reauth_cta_google)
        SignInProvider.EMAIL_PASSWORD,
        SignInProvider.UNKNOWN -> stringResource(Res.string.reauth_cta_password)
    }
    Column {
        Text(
            text = ctaText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (errorText != null) {
            Spacer(modifier = Modifier.height(DesignTokens.space2))
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ReauthBottomSheetPreview() {
    StitchPadTheme {
        // ModalBottomSheet doesn't render in @Preview; this preview just composes
        // the inner content for layout review.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.space4),
        ) {
            ReauthPasswordForm(
                email = "folake@stitchpad.app",
                password = "",
                onPasswordChange = {},
                errorText = null,
            )
        }
    }
}
