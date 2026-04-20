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

@Composable
fun CustomerAvatar(
    name: String,
    customerId: String,
    modifier: Modifier = Modifier
) {
    val colors = DesignTokens.avatarColors
    val avatar = remember(customerId) {
        colors[customerId.hashCode().mod(colors.size)]
    }
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) avatar.darkBg else avatar.lightBg
    val fg = if (isDark) avatar.darkText else avatar.lightText
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun CustomerAvatarPreview() {
    StitchPadTheme {
        CustomerAvatar(name = "Fola Sunday", customerId = "c-12345")
    }
}
