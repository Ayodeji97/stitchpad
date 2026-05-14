package com.danzucker.stitchpad.feature.settings.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens

/**
 * Apple-style grouped section: small uppercase label above a Surface card.
 *
 * Children are usually [SettingsRow] composables. Pass them via the [content]
 * slot as a `Column { … }` of rows; this composable does not auto-divider —
 * the caller should insert [SettingsRowDivider] between adjacent rows so
 * special items (e.g. the danger row split into its own card) compose freely.
 */
@Composable
fun SettingsSectionCard(
    label: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = DesignTokens.space2,
                    end = DesignTokens.space2,
                    top = DesignTokens.space4,
                    bottom = DesignTokens.space2,
                ),
            )
        }
        Surface(
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

/**
 * Indented divider for adjacent rows inside a [SettingsSectionCard]. The 60dp
 * left padding aligns the divider's start edge past the leading icon column.
 */
@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier.padding(start = 60.dp, end = DesignTokens.space4),
    )
}
