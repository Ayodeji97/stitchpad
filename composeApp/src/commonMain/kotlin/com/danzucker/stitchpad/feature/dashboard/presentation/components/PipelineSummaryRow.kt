@file:Suppress("MatchingDeclarationName") // file holds PipelineSummarySegment + the summary composable

package com.danzucker.stitchpad.feature.dashboard.presentation.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_workshop_summary_cd
import stitchpad.composeapp.generated.resources.dashboard_workshop_summary_in_progress
import stitchpad.composeapp.generated.resources.dashboard_workshop_summary_not_started
import stitchpad.composeapp.generated.resources.dashboard_workshop_summary_title

/** Which count segments the workshop summary row shows. */
enum class PipelineSummarySegment { InProgress, NotStarted }

/**
 * Pure decision for the summary subtitle: include a segment only when its
 * count is non-zero, in-progress before not-started. The caller only renders
 * the row when the combined total is &gt; 0, so this returns a non-empty list
 * in practice.
 */
internal fun pipelineSummarySegments(
    inProgressTotal: Int,
    notStartedTotal: Int,
): List<PipelineSummarySegment> = buildList {
    if (inProgressTotal > 0) add(PipelineSummarySegment.InProgress)
    if (notStartedTotal > 0) add(PipelineSummarySegment.NotStarted)
}

/**
 * One-line replacement for the old two-bucket Work Pipeline section. Shows
 * the workshop counts and opens the Orders tab (the full, grouped order
 * book) on tap. Counts only — no order rows — so it can never double-render
 * an order already shown in an NBA card.
 */
@Composable
fun PipelineSummaryRow(
    inProgressTotal: Int,
    notStartedTotal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve both labels up front — stringResource is @Composable and can't be
    // called from inside the (non-composable) joinToString transform lambda below.
    val inProgressLabel = stringResource(Res.string.dashboard_workshop_summary_in_progress, inProgressTotal)
    val notStartedLabel = stringResource(Res.string.dashboard_workshop_summary_not_started, notStartedTotal)
    val subtitle = pipelineSummarySegments(inProgressTotal, notStartedTotal)
        .joinToString(" · ") { segment ->
            when (segment) {
                PipelineSummarySegment.InProgress -> inProgressLabel
                PipelineSummarySegment.NotStarted -> notStartedLabel
            }
        }
    val cd = stringResource(
        Res.string.dashboard_workshop_summary_cd,
        inProgressTotal,
        notStartedTotal,
    )
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd },
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.dashboard_workshop_summary_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineSummaryRowBothPreview() {
    StitchPadTheme {
        PipelineSummaryRow(inProgressTotal = 3, notStartedTotal = 2, onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineSummaryRowInProgressOnlyPreview() {
    StitchPadTheme {
        PipelineSummaryRow(inProgressTotal = 4, notStartedTotal = 0, onClick = {})
    }
}
