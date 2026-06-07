package com.danzucker.stitchpad.feature.settings.presentation.deleteaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.settings.domain.DeletionReason
import com.danzucker.stitchpad.feature.settings.presentation.components.ReauthBottomSheet
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.BackHandler
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.delete_account_dialog_body
import stitchpad.composeapp.generated.resources.delete_account_dialog_continue
import stitchpad.composeapp.generated.resources.delete_account_dialog_title
import stitchpad.composeapp.generated.resources.delete_account_goodbye_body
import stitchpad.composeapp.generated.resources.delete_account_goodbye_cta
import stitchpad.composeapp.generated.resources.delete_account_goodbye_title
import stitchpad.composeapp.generated.resources.delete_account_progress_body
import stitchpad.composeapp.generated.resources.delete_account_progress_title
import stitchpad.composeapp.generated.resources.delete_account_reauth_cta
import stitchpad.composeapp.generated.resources.sign_out_dialog_cancel

@Composable
fun DeleteAccountScreen(
    state: DeleteAccountState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (DeleteAccountAction) -> Unit,
) {
    // Goodbye phase: hijack system back to route through OnGoodbyeContinue, so
    // a back-press from the goodbye screen lands the (already-deleted) user on
    // Login like the CTA does, instead of popping back to a Settings screen
    // that no longer has a valid auth user.
    BackHandler(enabled = state.phase == DeletePhase.Goodbye) {
        onAction(DeleteAccountAction.OnGoodbyeContinue)
    }
    // Processing phase: swallow back so a tap can't tear down the in-flight
    // delete pipeline mid-operation.
    BackHandler(enabled = state.phase == DeletePhase.Processing) { /* swallow */ }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            // Goodbye is the only phase with persistent body content; the others
            // overlay an empty background.
            if (state.phase == DeletePhase.Goodbye) {
                GoodbyeContent(
                    onContinue = { onAction(DeleteAccountAction.OnGoodbyeContinue) },
                )
            }
        }

        when (state.phase) {
            DeletePhase.Confirm -> ConfirmDialog(
                onContinue = { onAction(DeleteAccountAction.OnConfirmContinue) },
                onCancel = { onAction(DeleteAccountAction.OnConfirmCancel) },
            )
            DeletePhase.Reason -> DeleteAccountReasonSheet(
                selectedReason = state.selectedReason,
                additionalNotes = state.additionalNotes,
                canContinue = state.canContinueFromReason,
                onSelectReason = { onAction(DeleteAccountAction.OnReasonSelect(it)) },
                onNotesChange = { onAction(DeleteAccountAction.OnAdditionalNotesChange(it)) },
                onContinue = { onAction(DeleteAccountAction.OnReasonContinue) },
                onCancel = { onAction(DeleteAccountAction.OnReasonCancel) },
            )
            DeletePhase.Reauth -> ReauthBottomSheet(
                provider = state.signInProvider,
                email = state.email,
                password = state.reauthPassword,
                onPasswordChange = { onAction(DeleteAccountAction.OnReauthPasswordChange(it)) },
                onConfirm = { onAction(DeleteAccountAction.OnReauthConfirm) },
                onDismiss = { onAction(DeleteAccountAction.OnReauthCancel) },
                onForgotPassword = { onAction(DeleteAccountAction.OnForgotPassword) },
                confirmButtonLabel = stringResource(Res.string.delete_account_reauth_cta),
                isLoading = state.isReauthenticating,
                errorText = state.reauthError?.resolve(),
            )
            DeletePhase.Processing -> ProcessingDialog()
            DeletePhase.Goodbye -> Unit // body content above
        }
    }
}

@Composable
private fun ConfirmDialog(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(Res.string.delete_account_dialog_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(stringResource(Res.string.delete_account_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(
                    text = stringResource(Res.string.delete_account_dialog_continue),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.sign_out_dialog_cancel))
            }
        },
    )
}

@Composable
private fun ProcessingDialog() {
    AlertDialog(
        onDismissRequest = { /* non-cancellable */ },
        title = {
            Text(
                text = stringResource(Res.string.delete_account_progress_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(stringResource(Res.string.delete_account_progress_body)) },
        confirmButton = {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun GoodbyeContent(onContinue: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space5),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✓",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(DesignTokens.space4))
        Text(
            text = stringResource(Res.string.delete_account_goodbye_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(DesignTokens.space2))
        Text(
            text = stringResource(Res.string.delete_account_goodbye_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.space2),
        )
        Spacer(Modifier.height(DesignTokens.space5))
        StitchPadButton(
            text = stringResource(Res.string.delete_account_goodbye_cta),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
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
private fun DeleteAccountReasonPreview() {
    StitchPadTheme {
        DeleteAccountScreen(
            state = DeleteAccountState(
                isLoading = false,
                email = "folake@stitchpad.app",
                signInProvider = SignInProvider.EMAIL_PASSWORD,
                phase = DeletePhase.Reason,
                selectedReason = DeletionReason.MISSING_FEATURES,
                additionalNotes = "Need a recurring orders feature",
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun DeleteAccountGoodbyePreview() {
    StitchPadTheme {
        DeleteAccountScreen(
            state = DeleteAccountState(
                isLoading = false,
                phase = DeletePhase.Goodbye,
            ),
            onAction = {},
        )
    }
}
