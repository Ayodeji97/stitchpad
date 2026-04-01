package com.danzucker.stitchpad.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.jetbrains_mono_medium
import stitchpad.composeapp.generated.resources.jetbrains_mono_regular
import stitchpad.composeapp.generated.resources.plus_jakarta_sans_bold
import stitchpad.composeapp.generated.resources.plus_jakarta_sans_medium
import stitchpad.composeapp.generated.resources.plus_jakarta_sans_regular
import stitchpad.composeapp.generated.resources.plus_jakarta_sans_semibold

@Composable
fun PlusJakartaSansFamily(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

@Composable
fun JetBrainsMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
)

@Composable
fun StitchPadTypography(): Typography {
    val plusJakartaSans = PlusJakartaSansFamily()

    return Typography(
        displayLarge = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.Bold,
            fontSize = DesignTokens.displayLg,
            lineHeight = DesignTokens.displayLg * 1.25f,
        ),
        displayMedium = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.Bold,
            fontSize = DesignTokens.displayMd,
            lineHeight = DesignTokens.displayMd * 1.29f,
        ),
        headlineLarge = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingLg,
            lineHeight = DesignTokens.headingLg * 1.33f,
        ),
        headlineMedium = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingMd,
            lineHeight = DesignTokens.headingMd * 1.4f,
        ),
        headlineSmall = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingSm,
            lineHeight = DesignTokens.headingSm * 1.44f,
        ),
        bodyLarge = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodyLg,
            lineHeight = DesignTokens.bodyLg * 1.5f,
        ),
        bodyMedium = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodyMd,
            lineHeight = DesignTokens.bodyMd * 1.57f,
        ),
        bodySmall = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodySm,
            lineHeight = DesignTokens.bodySm * 1.54f,
        ),
        labelLarge = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelLg,
            lineHeight = DesignTokens.labelLg * 1.43f,
        ),
        labelMedium = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelMd,
            lineHeight = DesignTokens.labelMd * 1.38f,
        ),
        labelSmall = TextStyle(
            fontFamily = plusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelSm,
            lineHeight = DesignTokens.labelSm * 1.45f,
        ),
    )
}
