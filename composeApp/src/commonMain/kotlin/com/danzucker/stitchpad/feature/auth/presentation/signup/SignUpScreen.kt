package com.danzucker.stitchpad.feature.auth.presentation.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val inputColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = DesignTokens.space4, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Create account",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(28.dp))

                // Full name field
                LabeledField(label = "Full name") {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = { onAction(SignUpAction.OnDisplayNameChange(it)) },
                        placeholder = { Text("Ade Fashions") },
                        isError = state.displayNameError != null,
                        supportingText = state.displayNameError?.let { error ->
                            {
                                Text(error.asString())
                            }
                        },
                        colors = inputColors,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.space3))

                // Email field
                LabeledField(label = "Email") {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = { onAction(SignUpAction.OnEmailChange(it)) },
                        placeholder = { Text("tailor@gmail.com") },
                        isError = state.emailError != null,
                        supportingText = state.emailError?.let { error ->
                            {
                                Text(error.asString())
                            }
                        },
                        colors = inputColors,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.space3))

                // Password field
                LabeledField(label = "Password") {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onAction(SignUpAction.OnPasswordChange(it)) },
                        placeholder = { Text("••••••••") },
                        isError = state.passwordError != null,
                        supportingText = state.passwordError?.let { error ->
                            {
                                Text(error.asString())
                            }
                        } ?: {
                            Text(
                                text = "At least 6 characters",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { onAction(SignUpAction.OnTogglePasswordVisibility) }
                            ) {
                                Icon(
                                    imageVector = if (state.isPasswordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (state.isPasswordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                    tint = DesignTokens.neutral400
                                )
                            }
                        },
                        colors = inputColors,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
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
                }
                Spacer(modifier = Modifier.height(DesignTokens.space3))

                // Confirm password field
                LabeledField(label = "Confirm password") {
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = { onAction(SignUpAction.OnConfirmPasswordChange(it)) },
                        placeholder = { Text("••••••••") },
                        isError = state.confirmPasswordError != null,
                        supportingText = state.confirmPasswordError?.let { error ->
                            {
                                Text(error.asString())
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { onAction(SignUpAction.OnTogglePasswordVisibility) }
                            ) {
                                Icon(
                                    imageVector = if (state.isPasswordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (state.isPasswordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                    tint = DesignTokens.neutral400
                                )
                            }
                        },
                        colors = inputColors,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
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
                }
                Spacer(modifier = Modifier.height(28.dp))

                // Register button
                Button(
                    onClick = { onAction(SignUpAction.OnSignUpClick) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Register")
                    }
                }
                Spacer(modifier = Modifier.height(DesignTokens.space4))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Already have an account? ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Log in",
                        style = MaterialTheme.typography.labelLarge,
                        color = DesignTokens.primary500,
                        modifier = Modifier
                            .clickable { onAction(SignUpAction.OnLoginClick) }
                            .padding(DesignTokens.space2)
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.space10))
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun SignUpScreenPreview() {
    StitchPadTheme {
        SignUpScreen(state = SignUpState(), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
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
