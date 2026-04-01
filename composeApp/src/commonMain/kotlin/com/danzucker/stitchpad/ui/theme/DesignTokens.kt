package com.danzucker.stitchpad.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DesignTokens {

    // Primary Colors (Deep Saffron)
    val primary50 = Color(0xFFFFF8E7)
    val primary100 = Color(0xFFFDEFC4)
    val primary200 = Color(0xFFFBDF8A)
    val primary300 = Color(0xFFF9CC50)
    val primary400 = Color(0xFFF7BB2A)
    val primary500 = Color(0xFFE8A800) // Main brand color
    val primary600 = Color(0xFFC48E00) // Pressed states, button border
    val primary700 = Color(0xFF9E7200)
    val primary800 = Color(0xFF7A5800)
    val primary900 = Color(0xFF4F3800)

    // Neutral / Surface Colors
    val neutral0 = Color(0xFFFFFFFF)
    val neutral50 = Color(0xFFF9F9F8)
    val neutral100 = Color(0xFFF2F1EF)
    val neutral200 = Color(0xFFE5E3DF)
    val neutral300 = Color(0xFFC9C6C0)
    val neutral400 = Color(0xFFA8A49D)
    val neutral500 = Color(0xFF7D7970)
    val neutral600 = Color(0xFF57534C)
    val neutral700 = Color(0xFF3A3731)
    val neutral800 = Color(0xFF252320)
    val neutral900 = Color(0xFF121110)

    // Semantic Colors
    val success500 = Color(0xFF2D9E6B)
    val success50 = Color(0xFFE8F7F1)
    val error500 = Color(0xFFD93B3B)
    val error50 = Color(0xFFFCEAEA)
    val warning500 = Color(0xFFE07B20)
    val warning50 = Color(0xFFFEF3E8)
    val info500 = Color(0xFF2B7FD4)
    val info50 = Color(0xFFE7F2FC)

    // Dark Mode Surfaces
    val darkBg = Color(0xFF121110)
    val darkSurface = Color(0xFF1E1C1A)
    val darkSurface2 = Color(0xFF2A2825)
    val darkBorder = Color(0xFF3A3731)

    // Dark Mode Semantic Backgrounds
    val successDarkBg = Color(0xFF0E3D2A)
    val errorDarkBg = Color(0xFF3D1515)
    val warningDarkBg = Color(0xFF3D2510)
    val infoDarkBg = Color(0xFF0C2A4A)

    // Dark Mode Text
    val darkText = Color(0xFFE5E3DF)
    val darkTextSecondary = Color(0xFFA8A49D)
    val darkTextTertiary = Color(0xFF7D7970)

    // Dark Mode Semantic Text
    val successDarkText = Color(0xFF5EDBA0)
    val errorDarkText = Color(0xFFE89090)
    val warningDarkText = Color(0xFFF0AA60)
    val infoDarkText = Color(0xFF7BB8F0)

    // Order Status Colors
    val statusReceived = Color(0xFF2B7FD4) // Blue
    val statusCutting = Color(0xFFE07B20) // Orange
    val statusSewing = Color(0xFFE8A800) // Saffron
    val statusReady = Color(0xFF2D9E6B) // Green
    val statusDelivered = Color(0xFF7D7970) // Gray
    val statusOverdue = Color(0xFFD93B3B) // Red

    // Primary Button Border
    val primaryButtonBorder = Color(0xFFC48E00)

    // Avatar Colors (6 pairs by name hash)
    data class AvatarColor(
        val lightBg: Color,
        val lightText: Color,
        val darkBg: Color,
        val darkText: Color
    )

    val avatarColors = listOf(
        AvatarColor(Color(0xFFFFF8E7), Color(0xFFE8A800), Color(0xFF4F3800), Color(0xFFF9CC50)), // Saffron
        AvatarColor(Color(0xFFE7F2FC), Color(0xFF2B7FD4), Color(0xFF0C2A4A), Color(0xFF7BB8F0)), // Blue
        AvatarColor(Color(0xFFE8F7F1), Color(0xFF2D9E6B), Color(0xFF0E3D2A), Color(0xFF5EDBA0)), // Green
        AvatarColor(Color(0xFFFEF3E8), Color(0xFFE07B20), Color(0xFF3D2510), Color(0xFFF0AA60)), // Orange
        AvatarColor(Color(0xFFF3EAFA), Color(0xFF7B4DB5), Color(0xFF2D1F3D), Color(0xFFC4A0E8)), // Purple
        AvatarColor(Color(0xFFFBEAF0), Color(0xFFC4447A), Color(0xFF3D152A), Color(0xFFE890B0)) // Pink
    )

    // Typography Sizes
    val displayLg: TextUnit = 32.sp
    val displayMd: TextUnit = 28.sp
    val headingLg: TextUnit = 24.sp
    val headingMd: TextUnit = 20.sp
    val headingSm: TextUnit = 18.sp
    val bodyLg: TextUnit = 16.sp
    val bodyMd: TextUnit = 14.sp
    val bodySm: TextUnit = 13.sp
    val labelLg: TextUnit = 14.sp
    val labelMd: TextUnit = 13.sp
    val labelSm: TextUnit = 11.sp
    val measurement: TextUnit = 15.sp

    // Spacing (8pt base)
    val space1: Dp = 4.dp
    val space2: Dp = 8.dp
    val space3: Dp = 12.dp
    val space4: Dp = 16.dp
    val space5: Dp = 20.dp
    val space6: Dp = 24.dp
    val space8: Dp = 32.dp
    val space10: Dp = 40.dp
    val space12: Dp = 48.dp

    // Border Radius
    val radiusSm: Dp = 6.dp
    val radiusMd: Dp = 10.dp
    val radiusLg: Dp = 14.dp
    val radiusXl: Dp = 20.dp
    val radiusFull: Dp = 999.dp

    // Icon Sizes
    val iconInline: Dp = 16.dp
    val iconList: Dp = 20.dp
    val iconNav: Dp = 24.dp
    val iconFeature: Dp = 32.dp

    // Motion Durations (milliseconds)
    const val durationQuick: Int = 150
    const val durationTransition: Int = 300
    const val durationEmphasis: Int = 400

    // Elevation
    val elevation1: Dp = 1.dp
    val elevation2: Dp = 3.dp
    val elevation3: Dp = 6.dp
    val elevation4: Dp = 8.dp
}
