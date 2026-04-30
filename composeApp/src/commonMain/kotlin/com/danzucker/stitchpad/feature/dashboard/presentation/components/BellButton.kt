package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.cd_notifications

@Composable
fun BellButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasUnread: Boolean = false,
) {
    // Outer Box is sized to the visual footprint (36dp) so the badge TopEnd anchor
    // tracks the icon circle, not the expanded 48dp hit area.
    Box(
        modifier = modifier.size(36.dp),
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
                contentDescription = stringResource(Res.string.cd_notifications),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BellButtonNoBadgePreview() {
    StitchPadTheme {
        BellButton(onClick = {}, hasUnread = false)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BellButtonWithBadgePreview() {
    StitchPadTheme {
        BellButton(onClick = {}, hasUnread = true)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BellButtonNoBadgeDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        BellButton(onClick = {}, hasUnread = false)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun BellButtonWithBadgeDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        BellButton(onClick = {}, hasUnread = true)
    }
}
