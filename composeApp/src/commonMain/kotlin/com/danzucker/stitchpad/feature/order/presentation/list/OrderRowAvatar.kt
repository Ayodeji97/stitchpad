package com.danzucker.stitchpad.feature.order.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

val OrderRowAvatarSize = 36.dp

/**
 * Avatar shown at the start of an Orders list row. Named distinctly from the design-system
 * `ui.components.CustomerAvatar` because the shape, size, seed, and initials logic differ.
 */
@Composable
fun OrderRowAvatar(
    name: String,
    customerId: String,
    modifier: Modifier = Modifier
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString()
    val colors = DesignTokens.avatarColors
    val avatar = remember(customerId) {
        colors[customerId.hashCode().mod(colors.size)]
    }
    val isDark = isSystemInDarkTheme()
    val (bg, fg) = if (initial == null) {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    } else if (isDark) {
        avatar.darkBg to avatar.darkText
    } else {
        avatar.lightBg to avatar.lightText
    }

    Box(
        modifier = modifier
            .size(OrderRowAvatarSize)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial ?: "?",
            color = fg,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun OrderRowAvatarPreview() {
    StitchPadTheme {
        OrderRowAvatar(name = "Fola Sunday", customerId = "c-12345")
    }
}
