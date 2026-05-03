package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.Measurement
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_link_measurements
import stitchpad.composeapp.generated.resources.order_detail_measurements_preview
import stitchpad.composeapp.generated.resources.order_detail_no_measurements

private const val MAX_PREVIEW_TILES = 3

@Composable
fun OrderMeasurementsPreviewCard(
    measurement: Measurement?,
    primaryFieldLabels: List<String>,
    onCardClick: () -> Unit,
    onLinkMeasurementsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick, role = Role.Button),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            ) {
                SectionIconTile(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(Res.string.order_detail_measurements_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(DesignTokens.space3))

            if (measurement != null) {
                val unitSuffix = if (measurement.unit == MeasurementUnit.INCHES) "in" else "cm"
                val tileLabels = primaryFieldLabels.take(MAX_PREVIEW_TILES)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    tileLabels.forEach { label ->
                        val value = measurement.fields[label]
                        MeasurementTile(
                            label = label,
                            value = if (value != null) "%.0f $unitSuffix".format(value) else "—",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = stringResource(Res.string.order_detail_no_measurements),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onLinkMeasurementsClick) {
                        Text(
                            text = stringResource(Res.string.order_detail_link_measurements),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = DesignTokens.space2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(DesignTokens.space1))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderMeasurementsPreviewCardPopulatedLightPreview() {
    StitchPadTheme {
        OrderMeasurementsPreviewCard(
            measurement = Measurement(
                id = "m1",
                customerId = "c1",
                gender = CustomerGender.MALE,
                fields = mapOf("Chest" to 42.0, "Waist" to 34.0, "Length" to 38.0),
                unit = MeasurementUnit.INCHES,
                notes = null,
                dateTaken = 0L,
                createdAt = 0L,
            ),
            primaryFieldLabels = listOf("Chest", "Waist", "Length"),
            onCardClick = {},
            onLinkMeasurementsClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderMeasurementsPreviewCardPopulatedDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderMeasurementsPreviewCard(
            measurement = Measurement(
                id = "m1",
                customerId = "c1",
                gender = CustomerGender.MALE,
                fields = mapOf("Chest" to 42.0, "Waist" to 34.0, "Length" to 38.0),
                unit = MeasurementUnit.INCHES,
                notes = null,
                dateTaken = 0L,
                createdAt = 0L,
            ),
            primaryFieldLabels = listOf("Chest", "Waist", "Length"),
            onCardClick = {},
            onLinkMeasurementsClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderMeasurementsPreviewCardEmptyLightPreview() {
    StitchPadTheme {
        OrderMeasurementsPreviewCard(
            measurement = null,
            primaryFieldLabels = listOf("Chest", "Waist", "Length"),
            onCardClick = {},
            onLinkMeasurementsClick = {},
        )
    }
}

// endregion
