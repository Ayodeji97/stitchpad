package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.isSystemInDarkTheme
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
import stitchpad.composeapp.generated.resources.dashboard_pipeline_empty_supporting
import stitchpad.composeapp.generated.resources.dashboard_pipeline_empty_title
import stitchpad.composeapp.generated.resources.dashboard_pipeline_in_progress
import stitchpad.composeapp.generated.resources.dashboard_pipeline_pending
import stitchpad.composeapp.generated.resources.dashboard_section_pipeline

/**
 * V1-style stacked work-pipeline section.
 *
 * Renders a "Work pipeline" section header followed by either:
 *  - the stacked "In progress (n)" / "Not started yet (n)" subsections when
 *    there is work, or
 *  - an empty-state illustration card with a saffron-tinted backdrop when
 *    both totals are zero.
 *
 * The header sits above whatever inner state is rendered so the label
 * never disappears between empty and populated.
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
    val isEmpty = inProgressTotal == 0 && notStartedTotal == 0

    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.dashboard_section_pipeline),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isEmpty) {
            // Saffron tint backdrop. Kept very low — saffron against a
            // near-black surface saturates into brown quickly, so dark
            // gets a tighter alpha than light.
            val backdropAlpha = if (isSystemInDarkTheme()) 0.08f else 0.12f
            EmptyIllustrationCard(
                slot = EmptyIllustrationSlot.Pipeline,
                title = stringResource(Res.string.dashboard_pipeline_empty_title),
                supporting = stringResource(Res.string.dashboard_pipeline_empty_supporting),
                illustrationBackground = MaterialTheme.colorScheme.primary.copy(alpha = backdropAlpha),
                largeIllustration = true,
            )
            return@Column
        }
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

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineSectionEmptyLightPreview() {
    StitchPadTheme {
        PipelineSection(
            inProgress = emptyList(),
            inProgressTotal = 0,
            notStarted = emptyList(),
            notStartedTotal = 0,
            onRowClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineSectionEmptyDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        PipelineSection(
            inProgress = emptyList(),
            inProgressTotal = 0,
            notStarted = emptyList(),
            notStartedTotal = 0,
            onRowClick = {},
        )
    }
}
