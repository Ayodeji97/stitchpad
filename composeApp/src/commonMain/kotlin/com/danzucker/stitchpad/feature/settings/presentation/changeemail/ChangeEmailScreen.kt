package com.danzucker.stitchpad.feature.settings.presentation.changeemail

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.settings.presentation.components.ReauthBottomSheet
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.change_email_cta
import stitchpad.composeapp.generated.resources.change_email_helper
import stitchpad.composeapp.generated.resources.change_email_label_new
import stitchpad.composeapp.generated.resources.change_email_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeEmailScreen(
    state: ChangeEmailState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (ChangeEmailAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.change_email_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(ChangeEmailAction.OnBackClick) }) {
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
                        onClick = { onAction(ChangeEmailAction.OnSubmitClick) },
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
                            containerColor = DesignTokens.primary500,
                            contentColor = Color(0xFF181615),
                        ),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                color = Color(0xFF181615),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.change_email_cta),
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
                .padding(horizontal = DesignTokens.space4),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Spacer(Modifier.height(DesignTokens.space4))

            // Until reauth completes, the form is hidden behind the reauth
            // bottom sheet — showing both at once made it unclear what to
            // interact with first.
            if (state.isReauthenticated) {
                Text(
                    text = stringResource(Res.string.change_email_helper),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = state.currentEmail,
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Current email") },
                    singleLine = true,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.newEmail,
                    onValueChange = { onAction(ChangeEmailAction.OnNewEmailChange(it)) },
                    enabled = !state.isSubmitting,
                    label = { Text(stringResource(Res.string.change_email_label_new)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    isError = state.emailError != null,
                    supportingText = {
                        state.emailError?.let {
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
                email = state.currentEmail,
                password = state.reauthPassword,
                onPasswordChange = { onAction(ChangeEmailAction.OnReauthPasswordChange(it)) },
                onConfirm = { onAction(ChangeEmailAction.OnReauthConfirm) },
                onDismiss = { onAction(ChangeEmailAction.OnReauthDismiss) },
                onForgotPassword = { onAction(ChangeEmailAction.OnForgotPassword) },
                isLoading = state.isReauthenticating,
                errorText = errorText,
            )
        }
    }
}

/**
 * Resolve a [UiText] into a plain String inside a composable. Uses
 * [produceState] so the resolved value updates if the underlying resource
 * changes (e.g. locale switch).
 */
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
private fun ChangeEmailScreenReauthPreview() {
    StitchPadTheme {
        ChangeEmailScreen(
            state = ChangeEmailState(
                isLoading = false,
                currentEmail = "folake@stitchpad.app",
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                showReauthSheet = true,
                isReauthenticated = false,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ChangeEmailScreenFormPreview() {
    StitchPadTheme {
        ChangeEmailScreen(
            state = ChangeEmailState(
                isLoading = false,
                currentEmail = "folake@stitchpad.app",
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                showReauthSheet = false,
                isReauthenticated = true,
                newEmail = "folake.new@stitchpad.app",
            ),
            onAction = {},
        )
    }
}
