package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme

/**
 * Letter avatar — first character of [name] over a deterministic background tint
 * picked from [DesignTokens.avatarColors] using [seedId]'s hashCode. Stable
 * across recompositions and sessions for the same customer.
 *
 * Photo support is a future iteration; for V2 we ship letters only.
 */
@Composable
fun CustomerAvatar(
    name: String,
    seedId: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val isDark = LocalIsDarkTheme.current
    val palette = DesignTokens.avatarColors
    val index = ((seedId.hashCode() % palette.size) + palette.size) % palette.size
    val tone = palette[index]
    val bg: Color = if (isDark) tone.darkBg else tone.lightBg
    val fg: Color = if (isDark) tone.darkText else tone.lightText
    val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
