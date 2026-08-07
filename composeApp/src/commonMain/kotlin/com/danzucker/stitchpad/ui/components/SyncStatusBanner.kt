package com.danzucker.stitchpad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.core.domain.model.SyncStatus
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.sync_status_offline
import stitchpad.composeapp.generated.resources.sync_status_syncing

/**
 * Thin banner surfacing [SyncStatus] to the tailor. Renders nothing when [SyncStatus.SYNCED] —
 * the app is offline-first by design, so a synced app should stay invisible; only the exception
 * states need to announce themselves.
 *
 * Tone is reassuring, not alarming: the record is already safe on the device either way. This
 * communicates "not on the server yet", not "at risk of loss" — no error colouring.
 */
@Composable
fun SyncStatusBanner(
    status: SyncStatus,
    modifier: Modifier = Modifier,
) {
    val syncingMessage = stringResource(Res.string.sync_status_syncing)
    val offlineMessage = stringResource(Res.string.sync_status_offline)
    val message = when (status) {
        SyncStatus.SYNCED -> null
        SyncStatus.SYNCING -> syncingMessage
        SyncStatus.OFFLINE -> offlineMessage
    }
    AnimatedVisibility(visible = message != null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = DesignTokens.space2, horizontal = DesignTokens.space4),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SyncStatusBannerSyncedLightPreview() {
    StitchPadTheme(darkTheme = false) {
        SyncStatusBanner(status = SyncStatus.SYNCED)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SyncStatusBannerSyncedDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SyncStatusBanner(status = SyncStatus.SYNCED)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SyncStatusBannerSyncingLightPreview() {
    StitchPadTheme(darkTheme = false) {
        SyncStatusBanner(status = SyncStatus.SYNCING)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SyncStatusBannerSyncingDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SyncStatusBanner(status = SyncStatus.SYNCING)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SyncStatusBannerOfflineLightPreview() {
    StitchPadTheme(darkTheme = false) {
        SyncStatusBanner(status = SyncStatus.OFFLINE)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SyncStatusBannerOfflineDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        SyncStatusBanner(status = SyncStatus.OFFLINE)
    }
}
