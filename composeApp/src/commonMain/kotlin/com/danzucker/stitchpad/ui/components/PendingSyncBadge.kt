package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.sync_not_synced

private val BADGE_DOT_SIZE = 8.dp
private val BADGE_DOT_BORDER = 1.dp

/**
 * Marks a record that exists locally but has not been acknowledged by the server.
 *
 * An outline dot rather than a filled one: this is informational, not an error. The
 * record is safe on the device and will sync on its own.
 */
@Composable
fun PendingSyncBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(BADGE_DOT_SIZE)
                .clip(CircleShape)
                .border(BADGE_DOT_BORDER, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        )
        Text(
            text = stringResource(Res.string.sync_not_synced),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PendingSyncBadgeLightPreview() {
    StitchPadTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(DesignTokens.space4)) {
            PendingSyncBadge()
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PendingSyncBadgeDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(DesignTokens.space4)) {
            PendingSyncBadge()
        }
    }
}
