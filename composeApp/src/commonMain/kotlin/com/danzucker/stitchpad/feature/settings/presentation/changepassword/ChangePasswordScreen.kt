package com.danzucker.stitchpad.feature.settings.presentation.changepassword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.settings.presentation.components.ReauthBottomSheet
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.clearFocusOnTap
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.change_password_cta
import stitchpad.composeapp.generated.resources.change_password_helper
import stitchpad.composeapp.generated.resources.change_password_label_confirm
import stitchpad.composeapp.generated.resources.change_password_label_new
import stitchpad.composeapp.generated.resources.change_password_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    state: ChangePasswordState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (ChangePasswordAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.change_password_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(ChangePasswordAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (state.isReauthenticated) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                ) {
                    Button(
                        onClick = { onAction(ChangePasswordAction.OnSubmitClick) },
                        enabled = state.canSubmit,
                        shape = RoundedCornerShape(DesignTokens.radiusLg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = DesignTokens.space4,
                                vertical = DesignTokens.space3,
                            )
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.change_password_cta),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clearFocusOnTap()
                .padding(horizontal = DesignTokens.space4),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Spacer(Modifier.height(DesignTokens.space4))

            // Form is hidden until reauth completes — the bottom sheet is
            // the only interactive surface during the verification step.
            if (state.isReauthenticated) {
                Text(
                    text = stringResource(Res.string.change_password_helper),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = state.newPassword,
                    onValueChange = { onAction(ChangePasswordAction.OnNewPasswordChange(it)) },
                    enabled = !state.isSubmitting,
                    label = { Text(stringResource(Res.string.change_password_label_new)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    isError = state.newPasswordError != null,
                    supportingText = {
                        state.newPasswordError?.let {
                            Text(
                                text = stringResource(it),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = { onAction(ChangePasswordAction.OnConfirmPasswordChange(it)) },
                    enabled = !state.isSubmitting,
                    label = { Text(stringResource(Res.string.change_password_label_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    isError = state.confirmPasswordError != null,
                    supportingText = {
                        state.confirmPasswordError?.let {
                            Text(
                                text = stringResource(it),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.showReauthSheet) {
            val errorText = state.reauthError?.resolve()
            ReauthBottomSheet(
                provider = state.signInProvider,
                email = state.email,
                password = state.reauthPassword,
                onPasswordChange = { onAction(ChangePasswordAction.OnReauthPasswordChange(it)) },
                onConfirm = { onAction(ChangePasswordAction.OnReauthConfirm) },
                onDismiss = { onAction(ChangePasswordAction.OnReauthDismiss) },
                onForgotPassword = { onAction(ChangePasswordAction.OnForgotPassword) },
                isLoading = state.isReauthenticating,
                errorText = errorText,
            )
        }
    }
}

@Composable
private fun UiText.resolve(): String {
    val text = this
    val resolved by produceState(initialValue = "", text) {
        value = when (text) {
            is UiText.DynamicString -> text.value
            is UiText.StringResourceText -> getString(text.id)
        }
    }
    return resolved
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ChangePasswordScreenFormPreview() {
    StitchPadTheme {
        ChangePasswordScreen(
            state = ChangePasswordState(
                isLoading = false,
                email = "folake@stitchpad.app",
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                showReauthSheet = false,
                isReauthenticated = true,
                newPassword = "NewPass1!",
                confirmPassword = "NewPass1!",
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ChangePasswordScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        ChangePasswordScreen(
            state = ChangePasswordState(
                isLoading = false,
                email = "folake@stitchpad.app",
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                showReauthSheet = false,
                isReauthenticated = true,
                newPassword = "abc",
                confirmPassword = "abd",
            ),
            onAction = {},
        )
    }
}
