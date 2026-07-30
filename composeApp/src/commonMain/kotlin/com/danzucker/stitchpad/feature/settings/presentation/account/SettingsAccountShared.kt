package com.danzucker.stitchpad.feature.settings.presentation.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_leave_workshop_cancel
import stitchpad.composeapp.generated.resources.settings_leave_workshop_confirm
import stitchpad.composeapp.generated.resources.settings_leave_workshop_dialog_body
import stitchpad.composeapp.generated.resources.settings_leave_workshop_dialog_title
import stitchpad.composeapp.generated.resources.sign_out_dialog_body
import stitchpad.composeapp.generated.resources.sign_out_dialog_cancel
import stitchpad.composeapp.generated.resources.sign_out_dialog_confirm
import stitchpad.composeapp.generated.resources.sign_out_dialog_title
import stitchpad.composeapp.generated.resources.signin_provider_apple
import stitchpad.composeapp.generated.resources.signin_provider_email
import stitchpad.composeapp.generated.resources.signin_provider_google
import stitchpad.composeapp.generated.resources.signin_provider_unknown

@Composable
internal fun providerSubtitle(provider: SignInProvider, identifier: String): String {
    val providerLabelRes: StringResource = when (provider) {
        SignInProvider.EMAIL_PASSWORD -> Res.string.signin_provider_email
        SignInProvider.APPLE -> Res.string.signin_provider_apple
        SignInProvider.GOOGLE -> Res.string.signin_provider_google
        SignInProvider.UNKNOWN -> Res.string.signin_provider_unknown
    }
    val label = stringResource(providerLabelRes)
    return if (identifier.isBlank()) label else "$label • $identifier"
}

@Composable
internal fun SignOutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.sign_out_dialog_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(stringResource(Res.string.sign_out_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.sign_out_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.sign_out_dialog_cancel))
            }
        },
    )
}

@Composable
internal fun LeaveWorkshopConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.settings_leave_workshop_dialog_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(stringResource(Res.string.settings_leave_workshop_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.settings_leave_workshop_confirm),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_leave_workshop_cancel))
            }
        },
    )
}
