package com.danzucker.stitchpad.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

/**
 * Theme verification preview — renders a color swatch grid + type
 * specimen using only theme tokens. Run from Android Studio's Compose
 * preview pane in <1s without launching the emulator.
 *
 * Two variants below: light and dark. If either renders broken (wrong
 * colors, missing font, etc.), the theme wiring is wrong.
 */
@Preview
@Composable
private fun StitchPadThemePreview_Light() {
    StitchPadTheme(darkTheme = false) {
        ThemeSpecimenContent()
    }
}

@Preview
@Composable
private fun StitchPadThemePreview_Dark() {
    StitchPadTheme(darkTheme = true) {
        ThemeSpecimenContent()
    }
}

@Composable
private fun ThemeSpecimenContent() {
    val brand = LocalStitchPadColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "StitchPad",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "ADIRE ATELIER · THEME SPECIMEN",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "The smart work pad for tailors.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Body text in Manrope renders here. Measurements use mono.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Bust 38 · Waist 30 · Hips 40",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFamily(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // Color swatch grid — one row, the key brand slots.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("primary", MaterialTheme.colorScheme.primary)
                Swatch("secondary", MaterialTheme.colorScheme.secondary)
                Swatch("tertiary", MaterialTheme.colorScheme.tertiary)
                Swatch("heritage", brand.heritageAccent)
                Swatch("brandAccent", brand.brandAccent)
            }

            // Surfaces row.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("background", MaterialTheme.colorScheme.background)
                Swatch("surface", MaterialTheme.colorScheme.surface)
                Swatch("surfaceVariant", MaterialTheme.colorScheme.surfaceVariant)
                Swatch("outline", MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun Swatch(label: String, color: Color) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color, RoundedCornerShape(10.dp))
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
