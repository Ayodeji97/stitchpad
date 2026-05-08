package com.danzucker.stitchpad.feature.auth.presentation.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.auth_coming_soon
import stitchpad.composeapp.generated.resources.signup_confirm_password_label
import stitchpad.composeapp.generated.resources.signup_email_label
import stitchpad.composeapp.generated.resources.signup_have_account
import stitchpad.composeapp.generated.resources.signup_log_in
import stitchpad.composeapp.generated.resources.signup_microcopy
import stitchpad.composeapp.generated.resources.signup_name_label
import stitchpad.composeapp.generated.resources.signup_name_placeholder
import stitchpad.composeapp.generated.resources.signup_password_helper
import stitchpad.composeapp.generated.resources.signup_password_label
import stitchpad.composeapp.generated.resources.signup_privacy_link
import stitchpad.composeapp.generated.resources.signup_subtitle
import stitchpad.composeapp.generated.resources.signup_terms_and
import stitchpad.composeapp.generated.resources.signup_terms_link
import stitchpad.composeapp.generated.resources.signup_terms_prefix
import stitchpad.composeapp.generated.resources.signup_title

@Composable
fun SignUpRoot(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: SignUpViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(Res.string.auth_coming_soon)

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
            SignUpEvent.ShowComingSoon -> {
                scope.launch { snackbarHostState.showSnackbar(comingSoon) }
            }
        }
    }

    SignUpScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

@Composable
fun SignUpScreen(
    state: SignUpState,
    snackbarHostState: SnackbarHostState,
    onAction: (SignUpAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.neutral900),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuthHero(height = 240.dp, logoDiameter = 64.dp, showTagline = false)

            AuthCard {
                // 1. Title
                Text(
                    text = stringResource(Res.string.signup_title),
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
                    text = stringResource(Res.string.signup_subtitle),
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color(0xFFA8A49D),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // 3. Full name field
                AuthTextField(
                    label = stringResource(Res.string.signup_name_label),
                    value = state.displayName,
                    onValueChange = { onAction(SignUpAction.OnDisplayNameChange(it)) },
                    leadingIcon = Icons.Outlined.Person,
                    placeholder = stringResource(Res.string.signup_name_placeholder),
                    errorText = state.displayNameError?.asString(),
                )

                // 4. Email field
                AuthTextField(
                    label = stringResource(Res.string.signup_email_label),
                    value = state.email,
                    onValueChange = { onAction(SignUpAction.OnEmailChange(it)) },
                    leadingIcon = Icons.Outlined.Mail,
                    keyboardType = KeyboardType.Email,
                    placeholder = "Enter your email address",
                    errorText = state.emailError?.asString(),
                )

                // 5. Password field
                val passwordHelper = stringResource(Res.string.signup_password_helper)
                AuthTextField(
                    label = stringResource(Res.string.signup_password_label),
                    value = state.password,
                    onValueChange = { onAction(SignUpAction.OnPasswordChange(it)) },
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    isPasswordVisible = state.isPasswordVisible,
                    onTogglePassword = { onAction(SignUpAction.OnTogglePasswordVisibility) },
                    trailingPasswordVisibilityIcon = if (state.isPasswordVisible) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
                    placeholder = "Create a password",
                    helperText = when {
                        state.passwordError != null -> null
                        state.password.length >= 6 -> passwordHelper
                        else -> passwordHelper
                    },
                    helperIcon = when {
                        state.passwordError != null -> null
                        state.password.length >= 6 -> Icons.Outlined.CheckCircle
                        else -> null
                    },
                    isHelperSuccess = state.passwordError == null && state.password.length >= 6,
                    errorText = state.passwordError?.asString(),
                )

                // 6. Confirm password field
                AuthTextField(
                    label = stringResource(Res.string.signup_confirm_password_label),
                    value = state.confirmPassword,
                    onValueChange = { onAction(SignUpAction.OnConfirmPasswordChange(it)) },
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    isPasswordVisible = state.isConfirmPasswordVisible,
                    onTogglePassword = { onAction(SignUpAction.OnToggleConfirmPasswordVisibility) },
                    trailingPasswordVisibilityIcon = if (state.isConfirmPasswordVisible) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
                    placeholder = "Re-enter your password",
                    errorText = state.confirmPasswordError?.asString(),
                )

                // 7. Terms checkbox row
                val termsPrefix = stringResource(Res.string.signup_terms_prefix)
                val termsLink = stringResource(Res.string.signup_terms_link)
                val termsAnd = stringResource(Res.string.signup_terms_and)
                val privacyLink = stringResource(Res.string.signup_privacy_link)
                val checkboxColor = if (state.acceptedTerms) DesignTokens.primary500 else Color(0xFF3A3731)
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(SignUpAction.OnTermsToggle) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(checkboxColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.acceptedTerms) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = DesignTokens.neutral900,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFFF5F2ED), fontSize = 13.sp)) {
                                append("$termsPrefix ")
                            }
                            withStyle(
                                SpanStyle(
                                    color = DesignTokens.primary400,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            ) {
                                append(termsLink)
                            }
                            withStyle(SpanStyle(color = Color(0xFFF5F2ED), fontSize = 13.sp)) {
                                append(" $termsAnd ")
                            }
                            withStyle(
                                SpanStyle(
                                    color = DesignTokens.primary400,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            ) {
                                append(privacyLink)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // 8. Create account button
                Button(
                    onClick = { onAction(SignUpAction.OnSignUpClick) },
                    enabled = !state.isLoading && state.acceptedTerms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesignTokens.primary500,
                        contentColor = DesignTokens.neutral900,
                        disabledContainerColor = DesignTokens.primary500.copy(alpha = 0.5f),
                        disabledContentColor = DesignTokens.neutral900.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        text = "Create account",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                // 9. Footer — already have an account
                val haveAccount = stringResource(Res.string.signup_have_account)
                val logIn = stringResource(Res.string.signup_log_in)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFFA8A49D), fontSize = 14.sp)) {
                            append("$haveAccount ")
                        }
                        withStyle(
                            SpanStyle(
                                color = DesignTokens.primary400,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        ) {
                            append(logIn)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(SignUpAction.OnLoginClick) },
                    textAlign = TextAlign.Center,
                )

                // 10. SSO button row
                SsoButtonRow(
                    onGoogleClick = { onAction(SignUpAction.OnGoogleSignInClick) },
                    onAppleClick = { onAction(SignUpAction.OnAppleSignInClick) },
                    enabled = !state.isSsoLoading,
                )

                // 11. Microcopy
                Text(
                    text = stringResource(Res.string.signup_microcopy),
                    style = TextStyle(
                        fontSize = 12.5.sp,
                        color = Color(0xFF7D7970),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
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
private fun SignUpScreenPreview() {
    StitchPadTheme {
        SignUpScreen(
            state = SignUpState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
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
                confirmPassword = "password123",
                acceptedTerms = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
