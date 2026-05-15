package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_pipeline_empty_cta
import stitchpad.composeapp.generated.resources.dashboard_pipeline_empty_section
import stitchpad.composeapp.generated.resources.dashboard_pipeline_empty_supporting
import stitchpad.composeapp.generated.resources.dashboard_pipeline_empty_title
import stitchpad.composeapp.generated.resources.dashboard_pipeline_in_progress_v2
import stitchpad.composeapp.generated.resources.dashboard_pipeline_pending_v2
import stitchpad.composeapp.generated.resources.dashboard_section_pipeline

/**
 * V1-style stacked work-pipeline section.
 *
 * Renders a "Work pipeline" section header followed by either:
 *  - the stacked "In progress (n)" / "Not started yet (n)" subsections when
 *    there is work, or
 *  - an empty-state illustration card with a brand-tinted backdrop when
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
    onCreateOrderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmpty = inProgressTotal == 0 && notStartedTotal == 0

    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier,
    ) {
        PipelineHeader()
        if (isEmpty) {
            PipelineEmptyHeroCard(
                sectionLabel = stringResource(Res.string.dashboard_pipeline_empty_section),
                title = stringResource(Res.string.dashboard_pipeline_empty_title),
                supporting = stringResource(Res.string.dashboard_pipeline_empty_supporting),
                ctaLabel = stringResource(Res.string.dashboard_pipeline_empty_cta),
                onCtaClick = onCreateOrderClick,
            )
            return@Column
        }
        if (inProgressTotal > 0) {
            PipelineSubsection(
                title = stringResource(
                    Res.string.dashboard_pipeline_in_progress_v2,
                    inProgressTotal,
                ),
                rows = inProgress,
                onRowClick = onRowClick,
            )
        }
        if (notStartedTotal > 0) {
            PipelineSubsection(
                title = stringResource(
                    Res.string.dashboard_pipeline_pending_v2,
                    notStartedTotal,
                ),
                rows = notStarted,
                onRowClick = onRowClick,
            )
        }
    }
}

@Composable
private fun PipelineHeader() {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        // Brand primary vertical accent bar pegged to the section title's height —
        // a small visual marker that ties Work Pipeline to the brand design
        // language without adding a full badge + subtitle header.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusFull))
                .background(scheme.primary),
        )
        Text(
            text = stringResource(Res.string.dashboard_section_pipeline),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun PipelineSubsection(
    title: String,
    rows: List<DashboardOrderRow>,
    onRowClick: (orderId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(0.dp))
        rows.forEach { row ->
            PipelineOrderRow(
                row = row,
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
            onCreateOrderClick = {},
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
            onCreateOrderClick = {},
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
            onCreateOrderClick = {},
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
            onCreateOrderClick = {},
        )
    }
}
