package com.danzucker.stitchpad.feature.auth.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.onboarding.presentation.components.StitchPadLogo
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_password_hide
import stitchpad.composeapp.generated.resources.cd_password_show
import stitchpad.composeapp.generated.resources.login_button
import stitchpad.composeapp.generated.resources.login_email_label
import stitchpad.composeapp.generated.resources.login_forgot_password
import stitchpad.composeapp.generated.resources.login_no_account
import stitchpad.composeapp.generated.resources.login_password_hint
import stitchpad.composeapp.generated.resources.login_password_label
import stitchpad.composeapp.generated.resources.login_sign_up
import stitchpad.composeapp.generated.resources.login_title
import stitchpad.composeapp.generated.resources.placeholder_email
import stitchpad.composeapp.generated.resources.placeholder_password

@Composable
fun LoginRoot(
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            LoginEvent.NavigateToSignUp -> onNavigateToSignUp()
            LoginEvent.NavigateToForgotPassword -> onNavigateToForgotPassword()
            LoginEvent.NavigateToHome -> onNavigateToHome()
            is LoginEvent.ShowError -> {
                scope.launch {
                    val message = when (val text = event.message) {
                        is UiText.DynamicString -> text.value
                        is UiText.StringResourceText -> org.jetbrains.compose.resources.getString(text.id)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    LoginScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    state: LoginState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (LoginAction) -> Unit
) {
    val inputColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface
    )
    var hasEmailFocused by remember { mutableStateOf(false) }
    var hasPasswordFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .verticalScroll(rememberScrollState())
        ) {
            // Saffron header with logo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(DesignTokens.primary500),
                contentAlignment = Alignment.Center
            ) {
                StitchPadLogo(size = 64.dp)
            }

            // White card overlapping header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = DesignTokens.space4, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(28.dp))

                // Email field
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(Res.string.login_email_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val emailInteractionSource = remember { MutableInteractionSource() }
                    BasicTextField(
                        value = state.email,
                        onValueChange = { onAction(LoginAction.OnEmailChange(it)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        interactionSource = emailInteractionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    hasEmailFocused = true
                                } else if (hasEmailFocused) {
                                    onAction(LoginAction.OnEmailBlur)
                                }
                            },
                        decorationBox = { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = state.email,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = emailInteractionSource,
                                isError = state.emailError != null,
                                placeholder = { Text(stringResource(Res.string.placeholder_email)) },
                                supportingText = state.emailError?.let { error -> { Text(error.asString()) } },
                                colors = inputColors,
                                container = {
                                    OutlinedTextFieldDefaults.ContainerBox(
                                        enabled = true,
                                        isError = state.emailError != null,
                                        interactionSource = emailInteractionSource,
                                        colors = inputColors,
                                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                                        focusedBorderThickness = 1.dp,
                                        unfocusedBorderThickness = 1.dp
                                    )
                                }
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.space3))

                // Password field
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(Res.string.login_password_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val passwordVisualTransformation = if (state.isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    }
                    val passwordInteractionSource = remember { MutableInteractionSource() }
                    BasicTextField(
                        value = state.password,
                        onValueChange = { onAction(LoginAction.OnPasswordChange(it)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        visualTransformation = passwordVisualTransformation,
                        interactionSource = passwordInteractionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    hasPasswordFocused = true
                                } else if (hasPasswordFocused) {
                                    onAction(LoginAction.OnPasswordBlur)
                                }
                            },
                        decorationBox = { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = state.password,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = passwordVisualTransformation,
                                interactionSource = passwordInteractionSource,
                                isError = state.passwordError != null,
                                placeholder = { Text(stringResource(Res.string.placeholder_password)) },
                                supportingText = state.passwordError?.let { error ->
                                    {
                                        Text(error.asString())
                                    }
                                } ?: {
                                    Text(
                                        text = stringResource(Res.string.login_password_hint),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onAction(LoginAction.OnTogglePasswordVisibility) }
                                    ) {
                                        Icon(
                                            imageVector = if (state.isPasswordVisible) {
                                                Icons.Outlined.VisibilityOff
                                            } else {
                                                Icons.Outlined.Visibility
                                            },
                                            contentDescription = if (state.isPasswordVisible) {
                                                stringResource(Res.string.cd_password_hide)
                                            } else {
                                                stringResource(Res.string.cd_password_show)
                                            },
                                            tint = DesignTokens.neutral400
                                        )
                                    }
                                },
                                colors = inputColors,
                                container = {
                                    OutlinedTextFieldDefaults.ContainerBox(
                                        enabled = true,
                                        isError = state.passwordError != null,
                                        interactionSource = passwordInteractionSource,
                                        colors = inputColors,
                                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                                        focusedBorderThickness = 1.dp,
                                        unfocusedBorderThickness = 1.dp
                                    )
                                }
                            )
                        }
                    )
                }

                // Forgot password link
                TextButton(
                    onClick = { onAction(LoginAction.OnForgotPasswordClick) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(Res.string.login_forgot_password),
                        style = MaterialTheme.typography.labelMedium,
                        color = DesignTokens.primary500
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.space3))

                // Sign In button
                Button(
                    onClick = { onAction(LoginAction.OnLoginClick) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(Res.string.login_button))
                    }
                }
                Spacer(modifier = Modifier.height(DesignTokens.space4))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.login_no_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onAction(LoginAction.OnSignUpClick) }) {
                        Text(stringResource(Res.string.login_sign_up))
                    }
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun LoginScreenPreview() {
    StitchPadTheme {
        LoginScreen(state = LoginState(), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun LoginScreenFilledPreview() {
    StitchPadTheme {
        LoginScreen(
            state = LoginState(
                email = "tailor@stitchpad.app",
                password = "password123"
            ),
            onAction = {}
        )
    }
}
