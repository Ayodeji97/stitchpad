package com.danzucker.stitchpad.feature.gift.presentation.redeem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gift_accept_sheet_body_no_email
import stitchpad.composeapp.generated.resources.gift_accept_sheet_body_with_email
import stitchpad.composeapp.generated.resources.gift_accept_sheet_cancel
import stitchpad.composeapp.generated.resources.gift_accept_sheet_confirm
import stitchpad.composeapp.generated.resources.gift_accept_sheet_title

/**
 * Confirmation that the bearer gift is about to land on THIS account — the explicit
 * "Accept" step the design calls for, so a tap-through deep link never silently
 * applies a gift to the wrong account. Names the account email when known.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptGiftSheet(
    accountEmail: String?,
    isRedeeming: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        AcceptGiftSheetContent(
            accountEmail = accountEmail,
            isRedeeming = isRedeeming,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun AcceptGiftSheetContent(
    accountEmail: String?,
    isRedeeming: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = DesignTokens.space5),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CardGiftcard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }

        Text(
            text = stringResource(Res.string.gift_accept_sheet_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = if (accountEmail.isNullOrBlank()) {
                stringResource(Res.string.gift_accept_sheet_body_no_email)
            } else {
                stringResource(Res.string.gift_accept_sheet_body_with_email, accountEmail)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = onConfirm,
            enabled = !isRedeeming,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(top = DesignTokens.space2),
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (isRedeeming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(Res.string.gift_accept_sheet_confirm),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        TextButton(onClick = onDismiss, enabled = !isRedeeming) {
            Text(text = stringResource(Res.string.gift_accept_sheet_cancel))
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun AcceptGiftSheetContentPreview() {
    StitchPadTheme {
        AcceptGiftSheetContent(
            accountEmail = "ada@example.com",
            isRedeeming = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun AcceptGiftSheetContentDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        AcceptGiftSheetContent(
            accountEmail = null,
            isRedeeming = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
