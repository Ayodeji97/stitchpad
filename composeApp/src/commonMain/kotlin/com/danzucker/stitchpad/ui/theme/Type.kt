package com.danzucker.stitchpad.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.fraunces_variable
import stitchpad.composeapp.generated.resources.jetbrains_mono_medium
import stitchpad.composeapp.generated.resources.jetbrains_mono_regular
import stitchpad.composeapp.generated.resources.manrope_bold
import stitchpad.composeapp.generated.resources.manrope_medium
import stitchpad.composeapp.generated.resources.manrope_regular
import stitchpad.composeapp.generated.resources.manrope_semibold

/**
 * Display family — Fraunces, loaded as a single variable font referenced
 * four times at different weights. Compose's Font(resource, weight) maps
 * each entry to the closest weight in the variable file's wght axis.
 *
 * Variable-axis tuning (SOFT, opsz) is V0-out-of-scope per the rebrand
 * spec — uneven platform support across CMP. Revisit as a follow-up.
 */
@Composable
fun FrauncesFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_variable, FontWeight.Normal),
    Font(Res.font.fraunces_variable, FontWeight.Medium),
    Font(Res.font.fraunces_variable, FontWeight.SemiBold),
    Font(Res.font.fraunces_variable, FontWeight.Bold),
)

/**
 * Body family — Manrope, four static weight files.
 */
@Composable
fun ManropeFamily(): FontFamily = FontFamily(
    Font(Res.font.manrope_regular, FontWeight.Normal),
    Font(Res.font.manrope_medium, FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold, FontWeight.Bold),
)

/**
 * Mono family — JetBrains Mono, kept verbatim from the saffron era.
 * Used for measurements + due dates at the component level (not in the
 * Material Typography map).
 */
@Composable
fun JetBrainsMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
)

@Composable
fun StitchPadTypography(): Typography {
    val fraunces = FrauncesFamily()
    val manrope = ManropeFamily()

    return Typography(
        // Display + Headline — Fraunces (was PlusJakartaSans).
        displayLarge = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.displayLg,
            lineHeight = DesignTokens.displayLg * 1.25f,
        ),
        displayMedium = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.displayMd,
            lineHeight = DesignTokens.displayMd * 1.29f,
        ),
        headlineLarge = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingLg,
            lineHeight = DesignTokens.headingLg * 1.33f,
        ),
        headlineMedium = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingMd,
            lineHeight = DesignTokens.headingMd * 1.4f,
        ),
        headlineSmall = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingSm,
            lineHeight = DesignTokens.headingSm * 1.44f,
        ),
        // Title — Manrope. Section-heading / card-title / list-item primary
        // text. titleLarge/Medium/Small share sizes with headingSm/bodyLg/labelLg
        // (18/16/14sp) — StitchPad compresses M3's default 22/16/14sp scale.
        // Differentiation from neighbors is by font family (Manrope vs Fraunces
        // on headlineSmall) and weight (SemiBold vs Medium on labelLarge).
        titleLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingSm,
            lineHeight = DesignTokens.headingSm * 1.33f,
        ),
        titleMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.bodyLg,
            lineHeight = DesignTokens.bodyLg * 1.4f,
        ),
        titleSmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.labelLg,
            lineHeight = DesignTokens.labelLg * 1.43f,
        ),
        // Body + Label — Manrope (was PlusJakartaSans).
        bodyLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodyLg,
            lineHeight = DesignTokens.bodyLg * 1.5f,
        ),
        bodyMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodyMd,
            lineHeight = DesignTokens.bodyMd * 1.57f,
        ),
        bodySmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodySm,
            lineHeight = DesignTokens.bodySm * 1.54f,
        ),
        labelLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelLg,
            lineHeight = DesignTokens.labelLg * 1.43f,
        ),
        labelMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelMd,
            lineHeight = DesignTokens.labelMd * 1.38f,
        ),
        labelSmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelSm,
            lineHeight = DesignTokens.labelSm * 1.45f,
        ),
    )
}
