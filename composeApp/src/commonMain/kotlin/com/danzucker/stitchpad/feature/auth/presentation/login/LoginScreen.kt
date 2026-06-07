package com.danzucker.stitchpad.feature.auth.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthCard
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthHero
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthTextField
import com.danzucker.stitchpad.feature.auth.presentation.components.SsoButtonRow
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.danzucker.stitchpad.util.clearFocusOnTap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.auth_coming_soon
import stitchpad.composeapp.generated.resources.cd_password_hide
import stitchpad.composeapp.generated.resources.cd_password_show
import stitchpad.composeapp.generated.resources.login_button
import stitchpad.composeapp.generated.resources.login_email_label
import stitchpad.composeapp.generated.resources.login_forgot_password
import stitchpad.composeapp.generated.resources.login_no_account
import stitchpad.composeapp.generated.resources.login_password_hint
import stitchpad.composeapp.generated.resources.login_password_label
import stitchpad.composeapp.generated.resources.login_secure_microcopy
import stitchpad.composeapp.generated.resources.login_sign_up
import stitchpad.composeapp.generated.resources.login_subtitle
import stitchpad.composeapp.generated.resources.login_title
import stitchpad.composeapp.generated.resources.placeholder_email
import stitchpad.composeapp.generated.resources.placeholder_password

@Composable
fun LoginRoot(
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToHome: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.auth_coming_soon)

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
            LoginEvent.ShowComingSoon -> {
                scope.launch { snackbarHostState.showSnackbar(comingSoon) }
            }
        }
    }

    LoginScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

@Composable
fun LoginScreen(
    state: LoginState,
    snackbarHostState: SnackbarHostState,
    onAction: (LoginAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.neutral900)
            .clearFocusOnTap(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuthHero()

            AuthCard {
                // 1. Title
                Text(
                    text = stringResource(Res.string.login_title),
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF5F2ED),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // 2. Subtitle
                Text(
                    text = stringResource(Res.string.login_subtitle),
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color(0xFFA8A49D),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // 3. Email field
                AuthTextField(
                    label = stringResource(Res.string.login_email_label),
                    value = state.email,
                    onValueChange = { onAction(LoginAction.OnEmailChange(it)) },
                    leadingIcon = Icons.Outlined.Mail,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    placeholder = stringResource(Res.string.placeholder_email),
                    errorText = state.emailError?.asString(),
                    onFocusLost = { onAction(LoginAction.OnEmailBlur) },
                )

                // 4. Password field + helper / Forgot password row, tightly grouped
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthTextField(
                        label = stringResource(Res.string.login_password_label),
                        value = state.password,
                        onValueChange = { onAction(LoginAction.OnPasswordChange(it)) },
                        leadingIcon = Icons.Outlined.Lock,
                        imeAction = ImeAction.Done,
                        isPassword = true,
                        isPasswordVisible = state.isPasswordVisible,
                        onTogglePassword = { onAction(LoginAction.OnTogglePasswordVisibility) },
                        trailingPasswordVisibilityIcon = if (state.isPasswordVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        passwordVisibilityContentDescription = stringResource(
                            if (state.isPasswordVisible) {
                                Res.string.cd_password_hide
                            } else {
                                Res.string.cd_password_show
                            }
                        ),
                        placeholder = stringResource(Res.string.placeholder_password),
                        errorText = state.passwordError?.asString(),
                        onFocusLost = { onAction(LoginAction.OnPasswordBlur) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.passwordError == null) {
                                stringResource(Res.string.login_password_hint)
                            } else {
                                ""
                            },
                            style = TextStyle(fontSize = 12.5.sp, color = Color(0xFFA8A49D)),
                        )
                        Text(
                            text = stringResource(Res.string.login_forgot_password),
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalStitchPadColors.current.brandAccent,
                            ),
                            modifier = Modifier.clickable { onAction(LoginAction.OnForgotPasswordClick) },
                        )
                    }
                }

                // 5. Sign-in button
                StitchPadButton(
                    text = stringResource(Res.string.login_button),
                    onClick = { onAction(LoginAction.OnLoginClick) },
                    enabled = state.email.isNotBlank() && state.password.isNotBlank(),
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 6. Sign-up footer
                val noAccount = stringResource(Res.string.login_no_account)
                val signUp = stringResource(Res.string.login_sign_up)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFFA8A49D), fontSize = 14.sp)) {
                            append("$noAccount ")
                        }
                        withStyle(
                            SpanStyle(
                                color = LocalStitchPadColors.current.brandAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        ) {
                            append(signUp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(LoginAction.OnSignUpClick) },
                    textAlign = TextAlign.Center,
                )

                // 7. SSO button row
                SsoButtonRow(
                    onGoogleClick = { onAction(LoginAction.OnGoogleSignInClick) },
                    onAppleClick = { onAction(LoginAction.OnAppleSignInClick) },
                    enabled = !state.isSsoLoading,
                )

                // 8. Secure microcopy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.login_secure_microcopy),
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            color = Color(0xFF7D7970),
                            textAlign = TextAlign.Center,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .padding(horizontal = 16.dp),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun LoginScreenPreview() {
    StitchPadTheme {
        LoginScreen(
            state = LoginState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
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
                password = "password123",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
