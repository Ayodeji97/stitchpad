package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_notifications
import stitchpad.composeapp.generated.resources.cd_notifications_with_count

private const val UNREAD_COUNT_CAP = 9
private const val UNREAD_BUBBLE_SIZE = 18
private const val UNREAD_BUBBLE_ICON_INSET = 36

@Composable
fun BellButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Int = 0,
) {
    // Outer Box is sized to the visual footprint (36dp) so the badge TopEnd anchor
    // tracks the icon circle, not the expanded 48dp hit area.
    Box(
        modifier = modifier.size(UNREAD_BUBBLE_ICON_INSET.dp),
        contentAlignment = Alignment.Center,
    ) {
        // requiredSize lets the 48dp IconButton overflow the 36dp Box without clipping,
        // giving the full Material accessible tap target while keeping the visual at 36dp.
        IconButton(
            onClick = onClick,
            modifier = Modifier.requiredSize(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = if (unreadCount > 0) {
                    stringResource(Res.string.cd_notifications_with_count, unreadCount)
                } else {
                    stringResource(Res.string.cd_notifications)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .requiredSize(UNREAD_BUBBLE_SIZE.dp)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > UNREAD_COUNT_CAP) "9+" else unreadCount.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BellButtonNoBadgePreview() {
    StitchPadTheme {
        BellButton(onClick = {}, unreadCount = 0)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BellButtonWithBadgePreview() {
    StitchPadTheme {
        BellButton(onClick = {}, unreadCount = 3)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BellButtonNoBadgeDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        BellButton(onClick = {}, unreadCount = 0)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BellButtonWithBadgeDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        BellButton(onClick = {}, unreadCount = 3)
    }
}
