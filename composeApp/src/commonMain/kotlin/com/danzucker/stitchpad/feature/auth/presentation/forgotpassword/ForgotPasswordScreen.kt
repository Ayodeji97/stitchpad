package com.danzucker.stitchpad.feature.auth.presentation.forgotpassword

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthHeroVariant
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthTextField
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.forgot_password_back_to_login
import stitchpad.composeapp.generated.resources.forgot_password_button
import stitchpad.composeapp.generated.resources.forgot_password_subtitle
import stitchpad.composeapp.generated.resources.forgot_password_success_message
import stitchpad.composeapp.generated.resources.forgot_password_success_title
import stitchpad.composeapp.generated.resources.forgot_password_title
import stitchpad.composeapp.generated.resources.login_email_label
import stitchpad.composeapp.generated.resources.placeholder_email

@Composable
fun ForgotPasswordRoot(
    onNavigateToLogin: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        onAction = viewModel::onAction,
    )
}

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    snackbarHostState: SnackbarHostState,
    onAction: (ForgotPasswordAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.neutral900),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuthHero(variant = AuthHeroVariant.Utility)

            AuthCard {
                if (state.isSuccess) {
                    SuccessContent(
                        email = state.email,
                        onBackToLogin = { onAction(ForgotPasswordAction.OnBackToLoginClick) },
                    )
                } else {
                    FormContent(state = state, onAction = onAction)
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

@Composable
private fun FormContent(
    state: ForgotPasswordState,
    onAction: (ForgotPasswordAction) -> Unit,
) {
    // 1. Title
    Text(
        text = stringResource(Res.string.forgot_password_title),
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
        text = stringResource(Res.string.forgot_password_subtitle),
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
        onValueChange = { onAction(ForgotPasswordAction.OnEmailChange(it)) },
        leadingIcon = Icons.Outlined.Mail,
        keyboardType = KeyboardType.Email,
        placeholder = stringResource(Res.string.placeholder_email),
        errorText = state.emailError?.asString(),
        onFocusLost = { onAction(ForgotPasswordAction.OnEmailBlur) },
    )

    // 4. Send button
    Button(
        onClick = { onAction(ForgotPasswordAction.OnSendClick) },
        enabled = !state.isLoading && state.email.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DesignTokens.primary500,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = DesignTokens.neutral700,
            disabledContentColor = DesignTokens.neutral500,
        ),
    ) {
        Text(
            text = stringResource(Res.string.forgot_password_button),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }

    // 5. Back-to-login footer link
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = DesignTokens.primary400,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            ) {
                append(stringResource(Res.string.forgot_password_back_to_login))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction(ForgotPasswordAction.OnBackToLoginClick) },
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SuccessContent(
    email: String,
    onBackToLogin: () -> Unit,
) {
    // 1. Success icon — saffron circle with MarkEmailRead
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(DesignTokens.primary500.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MarkEmailRead,
                contentDescription = null,
                tint = DesignTokens.primary500,
                modifier = Modifier.size(36.dp),
            )
        }
    }

    // 2. Title
    Text(
        text = stringResource(Res.string.forgot_password_success_title),
        style = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFF5F2ED),
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )

    // 3. Message with email interpolated
    Text(
        text = stringResource(Res.string.forgot_password_success_message, email),
        style = TextStyle(
            fontSize = 14.sp,
            color = Color(0xFFA8A49D),
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )

    // 4. Back-to-login primary button
    Button(
        onClick = onBackToLogin,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DesignTokens.primary500,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = stringResource(Res.string.forgot_password_back_to_login),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ForgotPasswordScreenPreview() {
    StitchPadTheme {
        ForgotPasswordScreen(
            state = ForgotPasswordState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ForgotPasswordScreenFilledPreview() {
    StitchPadTheme {
        ForgotPasswordScreen(
            state = ForgotPasswordState(email = "tailor@stitchpad.app"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ForgotPasswordScreenSuccessPreview() {
    StitchPadTheme {
        ForgotPasswordScreen(
            state = ForgotPasswordState(
                email = "tailor@stitchpad.app",
                isSuccess = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
