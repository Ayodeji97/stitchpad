package com.danzucker.stitchpad.feature.auth.presentation.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignUpRoot(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: SignUpViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SignUpEvent.NavigateToLogin -> onNavigateToLogin()
            SignUpEvent.NavigateToHome -> onNavigateToHome()
            is SignUpEvent.ShowError -> {
                val message = when (val text = event.message) {
                    is UiText.DynamicString -> text.value
                    is UiText.StringResourceText -> text.id.toString()
                }
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    SignUpScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@Composable
fun SignUpScreen(
    state: SignUpState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (SignUpAction) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = DesignTokens.space4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.space10))
            Text(
                text = "Create account",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(DesignTokens.space8))

            OutlinedTextField(
                value = state.displayName,
                onValueChange = { onAction(SignUpAction.OnDisplayNameChange(it)) },
                label = { Text("Full name") },
                isError = state.displayNameError != null,
                supportingText = state.displayNameError?.let { error ->
                    {
                        Text(error.asString())
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(DesignTokens.space3))

            OutlinedTextField(
                value = state.email,
                onValueChange = { onAction(SignUpAction.OnEmailChange(it)) },
                label = { Text("Email") },
                isError = state.emailError != null,
                supportingText = state.emailError?.let { error ->
                    {
                        Text(error.asString())
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(DesignTokens.space3))

            OutlinedTextField(
                value = state.password,
                onValueChange = { onAction(SignUpAction.OnPasswordChange(it)) },
                label = { Text("Password") },
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { error ->
                    {
                        Text(error.asString())
                    }
                },
                visualTransformation = if (state.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(DesignTokens.space3))

            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = { onAction(SignUpAction.OnConfirmPasswordChange(it)) },
                label = { Text("Confirm password") },
                isError = state.confirmPasswordError != null,
                supportingText = state.confirmPasswordError?.let { error ->
                    {
                        Text(error.asString())
                    }
                },
                visualTransformation = if (state.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(DesignTokens.space6))

            Button(
                onClick = { onAction(SignUpAction.OnSignUpClick) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create Account")
                }
            }
            Spacer(modifier = Modifier.height(DesignTokens.space4))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onAction(SignUpAction.OnLoginClick) }) {
                    Text("Sign In")
                }
            }
            Spacer(modifier = Modifier.height(DesignTokens.space10))
        }
    }
}

@Composable
@Preview
private fun SignUpScreenPreview() {
    StitchPadTheme {
        SignUpScreen(
            state = SignUpState(),
            onAction = {}
        )
    }
}

@Composable
@Preview
private fun SignUpScreenFilledPreview() {
    StitchPadTheme {
        SignUpScreen(
            state = SignUpState(
                displayName = "Ade Fashions",
                email = "ade@gmail.com",
                password = "password123",
                confirmPassword = "password123"
            ),
            onAction = {}
        )
    }
}
