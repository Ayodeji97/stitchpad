package com.danzucker.stitchpad.feature.auth.presentation.verifyemail

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
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.debug.isDebugBuild
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthCard
import com.danzucker.stitchpad.feature.auth.presentation.components.AuthHero
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.email_verify_change_email
import stitchpad.composeapp.generated.resources.email_verify_check_button
import stitchpad.composeapp.generated.resources.email_verify_resend_button
import stitchpad.composeapp.generated.resources.email_verify_resend_cooldown
import stitchpad.composeapp.generated.resources.email_verify_skip_debug
import stitchpad.composeapp.generated.resources.email_verify_subtitle
import stitchpad.composeapp.generated.resources.email_verify_title

@Composable
fun EmailVerificationRoot(
    onVerified: () -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: EmailVerificationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Re-check verification each time the screen resumes — the user taps the
    // link in their mail app (leaving StitchPad) and returns here. Polling is
    // stopped on pause so we don't burn battery/data while they're away; the
    // resume above re-checks immediately and restarts it.
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(EmailVerificationAction.OnScreenResumed)
        onPauseOrDispose { viewModel.onAction(EmailVerificationAction.OnScreenPaused) }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            EmailVerificationEvent.NavigateToNext -> onVerified()
            EmailVerificationEvent.NavigateToLogin -> onNavigateToLogin()
            is EmailVerificationEvent.ShowError -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve())
            }
            is EmailVerificationEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    EmailVerificationScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

private suspend fun UiText.resolve(): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResourceText -> org.jetbrains.compose.resources.getString(id)
}

@Composable
fun EmailVerificationScreen(
    state: EmailVerificationState,
    snackbarHostState: SnackbarHostState,
    onAction: (EmailVerificationAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.neutral900),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AuthHero()

            AuthCard {
                // 1. Icon — brand accent circle with MarkEmailRead
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(LocalStitchPadColors.current.brandAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MarkEmailRead,
                            contentDescription = null,
                            tint = LocalStitchPadColors.current.brandAccent,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                // 2. Title
                Text(
                    text = stringResource(Res.string.email_verify_title),
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF5F2ED),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // 3. Subtitle with email interpolated
                Text(
                    text = stringResource(Res.string.email_verify_subtitle, state.email),
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color(0xFFA8A49D),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // 4. Primary — "I've verified my email"
                Button(
                    onClick = { onAction(EmailVerificationAction.OnCheckVerificationClick) },
                    enabled = !state.isChecking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = DesignTokens.neutral700,
                        disabledContentColor = DesignTokens.neutral500,
                    ),
                ) {
                    Text(
                        text = stringResource(Res.string.email_verify_check_button),
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    )
                }

                // 5. Secondary — "Resend email" (with cooldown)
                val onCooldown = state.resendCooldownSeconds > 0
                OutlinedButton(
                    onClick = { onAction(EmailVerificationAction.OnResendClick) },
                    enabled = !onCooldown && !state.isResending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = if (onCooldown) {
                            stringResource(Res.string.email_verify_resend_cooldown, state.resendCooldownSeconds)
                        } else {
                            stringResource(Res.string.email_verify_resend_button)
                        },
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                }

                // 6. Use a different email (log out)
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = LocalStitchPadColors.current.brandAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        ) {
                            append(stringResource(Res.string.email_verify_change_email))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(EmailVerificationAction.OnLogOutClick) },
                    textAlign = TextAlign.Center,
                )

                // 7. Debug-only skip — compiled in but only shown in debug builds
                if (isDebugBuild) {
                    Text(
                        text = stringResource(Res.string.email_verify_skip_debug),
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = DesignTokens.neutral500,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(EmailVerificationAction.OnDebugSkipClick) },
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
private fun EmailVerificationScreenPreview() {
    StitchPadTheme {
        EmailVerificationScreen(
            state = EmailVerificationState(email = "tailor@stitchpad.app"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun EmailVerificationScreenCooldownPreview() {
    StitchPadTheme {
        EmailVerificationScreen(
            state = EmailVerificationState(email = "tailor@stitchpad.app", resendCooldownSeconds = 42),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
