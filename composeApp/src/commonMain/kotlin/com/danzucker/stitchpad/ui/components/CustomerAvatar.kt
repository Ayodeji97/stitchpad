package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
@Composable
fun CustomerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = DesignTokens.space10
) {
    val isDark = isSystemInDarkTheme()
    val colorIndex = name.hashCode().and(Int.MAX_VALUE) % DesignTokens.avatarColors.size
    val pair = DesignTokens.avatarColors[colorIndex]
    val bg = if (isDark) pair.darkBg else pair.lightBg
    val fg = if (isDark) pair.darkText else pair.lightText

    val initials = name.trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(bg)
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = fg
        )
    }
}
