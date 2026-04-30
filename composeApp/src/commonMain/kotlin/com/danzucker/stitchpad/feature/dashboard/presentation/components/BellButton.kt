package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@Composable
fun BellButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasUnread: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = "Notifications",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .clip(CircleShape),
            )
        }
    }
}

@Preview
@Composable
private fun BellButtonNoBadgePreview() {
    StitchPadTheme {
        BellButton(onClick = {}, hasUnread = false)
    }
}

@Preview
@Composable
private fun BellButtonWithBadgePreview() {
    StitchPadTheme {
        BellButton(onClick = {}, hasUnread = true)
    }
}
