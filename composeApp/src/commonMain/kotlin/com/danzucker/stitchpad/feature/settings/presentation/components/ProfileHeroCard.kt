package com.danzucker.stitchpad.feature.settings.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

/**
 * Six pre-tinted avatar gradients, indexed by [User.avatarColorIndex] (0..5).
 * Index falls back to saffron for any out-of-range value.
 */
val AvatarGradients: List<Pair<Color, Color>> = listOf(
    Color(0xFFFFB733) to Color(0xFFB07F00), // 0 saffron
    Color(0xFFF58A82) to Color(0xFFB0322A), // 1 rose
    Color(0xFF4FBE8B) to Color(0xFF1F6B49), // 2 emerald
    Color(0xFF5C8DF5) to Color(0xFF1B3F9C), // 3 cobalt
    Color(0xFFA077C2) to Color(0xFF4F2E73), // 4 aubergine
    Color(0xFF5A5550) to Color(0xFF181615), // 5 charcoal
)

internal fun avatarBrush(colorIndex: Int): Brush {
    val safeIndex = colorIndex.coerceIn(0, AvatarGradients.lastIndex)
    val (start, end) = AvatarGradients[safeIndex]
    return Brush.linearGradient(listOf(start, end))
}

@Composable
fun ProfileHeroCard(
    businessName: String,
    subtitle: String,
    avatarColorIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = businessName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradientBrush = avatarBrush(avatarColorIndex)
    val cardGradient = Brush.linearGradient(
        listOf(
            DesignTokens.primary50,
            MaterialTheme.colorScheme.surface,
        ),
    )

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.background(cardGradient)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(gradientBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = businessName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfileHeroCardPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(DesignTokens.space3),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                ProfileHeroCard(
                    businessName = "Folake's Atelier",
                    subtitle = "+234 803 555 0142 · folake@stitchpad.app",
                    avatarColorIndex = 0,
                    onClick = {},
                )
                ProfileHeroCard(
                    businessName = "Bola Couture",
                    subtitle = "+234 802 999 1234",
                    avatarColorIndex = 3,
                    onClick = {},
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfileHeroCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ProfileHeroCard(
                businessName = "Folake's Atelier",
                subtitle = "+234 803 555 0142 · folake@stitchpad.app",
                avatarColorIndex = 4,
                onClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}
