package com.danzucker.stitchpad.feature.auth.presentation.forgotpassword

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import stitchpad.composeapp.generated.resources.forgot_password_back_to_login
import stitchpad.composeapp.generated.resources.forgot_password_button
import stitchpad.composeapp.generated.resources.forgot_password_email_icon
import stitchpad.composeapp.generated.resources.forgot_password_subtitle
import stitchpad.composeapp.generated.resources.forgot_password_success_message
import stitchpad.composeapp.generated.resources.forgot_password_success_title
import stitchpad.composeapp.generated.resources.forgot_password_title
import stitchpad.composeapp.generated.resources.login_email_label
import stitchpad.composeapp.generated.resources.placeholder_email

@Composable
fun ForgotPasswordRoot(
    onNavigateToLogin: () -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ForgotPasswordEvent.NavigateToLogin -> onNavigateToLogin()
            is ForgotPasswordEvent.ShowError -> {
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

    ForgotPasswordScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (ForgotPasswordAction) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                    .padding(horizontal = DesignTokens.space4, vertical = 28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.isSuccess) {
                    SuccessContent(
                        email = state.email,
                        onBackToLogin = { onAction(ForgotPasswordAction.OnBackToLoginClick) }
                    )
                } else {
                    FormContent(state = state, onAction = onAction)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormContent(
    state: ForgotPasswordState,
    onAction: (ForgotPasswordAction) -> Unit
) {
    val inputColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface
    )
    var hasEmailFocused by remember { mutableStateOf(false) }

    Text(
        text = stringResource(Res.string.forgot_password_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(Res.string.forgot_password_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(28.dp))

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
            onValueChange = { onAction(ForgotPasswordAction.OnEmailChange(it)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            interactionSource = emailInteractionSource,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hasEmailFocused = true
                    } else if (hasEmailFocused) {
                        onAction(ForgotPasswordAction.OnEmailBlur)
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
    Spacer(modifier = Modifier.height(28.dp))

    Button(
        onClick = { onAction(ForgotPasswordAction.OnSendClick) },
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
            Text(stringResource(Res.string.forgot_password_button))
        }
    }
    Spacer(modifier = Modifier.height(DesignTokens.space3))

    TextButton(onClick = { onAction(ForgotPasswordAction.OnBackToLoginClick) }) {
        Text(
            text = stringResource(Res.string.forgot_password_back_to_login),
            color = DesignTokens.primary500
        )
    }
}

@Composable
private fun SuccessContent(
    email: String,
    onBackToLogin: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(Res.string.forgot_password_email_icon),
        style = MaterialTheme.typography.displayMedium
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(Res.string.forgot_password_success_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(Res.string.forgot_password_success_message, email),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onBackToLogin,
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(stringResource(Res.string.forgot_password_back_to_login))
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ForgotPasswordScreenPreview() {
    StitchPadTheme {
        ForgotPasswordScreen(state = ForgotPasswordState(), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ForgotPasswordScreenSuccessPreview() {
    StitchPadTheme {
        ForgotPasswordScreen(
            state = ForgotPasswordState(
                email = "danny@gmail.com",
                isSuccess = true
            ),
            onAction = {}
        )
    }
}
