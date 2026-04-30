package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.ui.components.AccentedOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_due_in_days
import stitchpad.composeapp.generated.resources.dashboard_pipeline_in_progress
import stitchpad.composeapp.generated.resources.dashboard_pipeline_pending
import stitchpad.composeapp.generated.resources.dashboard_section_pipeline

/**
 * V1-style stacked pipeline section.
 *
 * Renders a "PIPELINE" section label followed by two stacked subsections:
 * "In progress (n)" and "Not started yet (n)". Each subsection only renders
 * when its total count is > 0. Each row is a full-width [AccentedOrderRow]
 * using a neutral accent colour (outline / surfaceVariant), matching the
 * original V1 accentColorsFor(RowAccent.Pipeline) output.
 */
@Composable
fun PipelineSection(
    inProgress: List<DashboardOrderRow>,
    inProgressTotal: Int,
    notStarted: List<DashboardOrderRow>,
    notStartedTotal: Int,
    onRowClick: (orderId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentFg = MaterialTheme.colorScheme.outline
    val accentBg = MaterialTheme.colorScheme.surfaceVariant

    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.dashboard_section_pipeline).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (inProgressTotal > 0) {
            PipelineSubsection(
                title = stringResource(Res.string.dashboard_pipeline_in_progress, inProgressTotal),
                rows = inProgress,
                accentFg = accentFg,
                accentBg = accentBg,
                onRowClick = onRowClick,
            )
        }
        if (notStartedTotal > 0) {
            PipelineSubsection(
                title = stringResource(Res.string.dashboard_pipeline_pending, notStartedTotal),
                rows = notStarted,
                accentFg = accentFg,
                accentBg = accentBg,
                onRowClick = onRowClick,
            )
        }
    }
}

@Composable
private fun PipelineSubsection(
    title: String,
    rows: List<DashboardOrderRow>,
    accentFg: Color,
    accentBg: Color,
    onRowClick: (orderId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        rows.forEach { row ->
            val chipText = row.daysUntilDeadline
                ?.let { stringResource(Res.string.dashboard_due_in_days, it) }
                .orEmpty()
            AccentedOrderRow(
                customerName = row.customerName,
                primaryLabel = row.primaryLabel,
                accentColor = accentFg,
                chipText = chipText,
                chipTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                chipBackground = accentBg,
                onClick = { onRowClick(row.orderId) },
            )
        }
    }
}

// — preview helpers —

private fun sampleInProgressRows(): List<DashboardOrderRow> = listOf(
    DashboardOrderRow(
        orderId = "1",
        customerName = "Adeyinka Paul",
        primaryLabel = "Senator Wear",
        daysUntilDeadline = 3,
    ),
    DashboardOrderRow(
        orderId = "2",
        customerName = "Blessing Tosin",
        primaryLabel = "Agbada",
        daysUntilDeadline = 5,
    ),
    DashboardOrderRow(
        orderId = "3",
        customerName = "Chukwuemeka Eze",
        primaryLabel = "Native Kaftan",
        daysUntilDeadline = null,
    ),
)

private fun sampleNotStartedRows(): List<DashboardOrderRow> = listOf(
    DashboardOrderRow(
        orderId = "4",
        customerName = "Funmilayo Adebayo",
        primaryLabel = "Wedding Dress",
        daysUntilDeadline = 14,
    ),
    DashboardOrderRow(
        orderId = "5",
        customerName = "Taiwo Okafor",
        primaryLabel = "Suit",
        daysUntilDeadline = null,
    ),
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineSectionLightPreview() {
    StitchPadTheme {
        PipelineSection(
            inProgress = sampleInProgressRows(),
            inProgressTotal = 3,
            notStarted = sampleNotStartedRows(),
            notStartedTotal = 2,
            onRowClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineSectionDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        PipelineSection(
            inProgress = sampleInProgressRows(),
            inProgressTotal = 3,
            notStarted = sampleNotStartedRows(),
            notStartedTotal = 2,
            onRowClick = {},
        )
    }
}
