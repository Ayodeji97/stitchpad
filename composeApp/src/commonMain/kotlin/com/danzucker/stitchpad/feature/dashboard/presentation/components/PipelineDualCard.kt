package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_pipeline_in_progress_header
import stitchpad.composeapp.generated.resources.dashboard_pipeline_not_started_header
import stitchpad.composeapp.generated.resources.dashboard_pipeline_view_all
import stitchpad.composeapp.generated.resources.dashboard_pipeline_view_more

private const val MAX_ROWS_PER_COLUMN = 2
private val NARROW_BREAKPOINT = 360.dp
private val HEADER_ICON_SIZE = 14.dp

data class PipelineColumnData(
    val totalCount: Int,
    val visibleRows: List<DashboardOrderRow>,
)

@Composable
fun PipelineDualCard(
    inProgress: PipelineColumnData,
    notStarted: PipelineColumnData,
    onRowClick: (orderId: String) -> Unit,
    onInProgressMoreClick: () -> Unit,
    onNotStartedMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewAllLabel = stringResource(Res.string.dashboard_pipeline_view_all)
    val viewMoreLabel = stringResource(Res.string.dashboard_pipeline_view_more)
    val inProgressHeader = stringResource(Res.string.dashboard_pipeline_in_progress_header, inProgress.totalCount)
    val notStartedHeader = stringResource(Res.string.dashboard_pipeline_not_started_header, notStarted.totalCount)

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(modifier = Modifier.padding(DesignTokens.space3)) {
            val isNarrow = maxWidth < NARROW_BREAKPOINT
            if (isNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
                    PipelineColumn(
                        headerText = inProgressHeader,
                        headerIcon = Icons.Filled.LocalFireDepartment,
                        accentColor = DesignTokens.success500,
                        data = inProgress,
                        onRowClick = onRowClick,
                        onMoreClick = onInProgressMoreClick,
                        moreLabel = if (inProgress.totalCount > MAX_ROWS_PER_COLUMN) viewMoreLabel else viewAllLabel,
                    )
                    PipelineColumn(
                        headerText = notStartedHeader,
                        headerIcon = Icons.Filled.Close,
                        accentColor = DesignTokens.warning500,
                        data = notStarted,
                        onRowClick = onRowClick,
                        onMoreClick = onNotStartedMoreClick,
                        moreLabel = if (notStarted.totalCount > MAX_ROWS_PER_COLUMN) viewMoreLabel else viewAllLabel,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
                    PipelineColumn(
                        modifier = Modifier.weight(1f),
                        headerText = inProgressHeader,
                        headerIcon = Icons.Filled.LocalFireDepartment,
                        accentColor = DesignTokens.success500,
                        data = inProgress,
                        onRowClick = onRowClick,
                        onMoreClick = onInProgressMoreClick,
                        moreLabel = if (inProgress.totalCount > MAX_ROWS_PER_COLUMN) viewMoreLabel else viewAllLabel,
                    )
                    PipelineColumn(
                        modifier = Modifier.weight(1f),
                        headerText = notStartedHeader,
                        headerIcon = Icons.Filled.Close,
                        accentColor = DesignTokens.warning500,
                        data = notStarted,
                        onRowClick = onRowClick,
                        onMoreClick = onNotStartedMoreClick,
                        moreLabel = if (notStarted.totalCount > MAX_ROWS_PER_COLUMN) viewMoreLabel else viewAllLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineColumn(
    headerText: String,
    headerIcon: ImageVector,
    accentColor: Color,
    data: PipelineColumnData,
    onRowClick: (orderId: String) -> Unit,
    onMoreClick: () -> Unit,
    moreLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = headerIcon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(HEADER_ICON_SIZE),
            )
            Text(
                text = headerText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
        }
        Spacer(Modifier.height(DesignTokens.space2))
        data.visibleRows.take(MAX_ROWS_PER_COLUMN).forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { onRowClick(row.orderId) },
                        role = Role.Button,
                    )
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = row.customerName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = row.primaryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        if (data.totalCount > MAX_ROWS_PER_COLUMN) {
            Spacer(Modifier.height(2.dp))
            TextButton(
                onClick = onMoreClick,
                contentPadding = PaddingValues(horizontal = DesignTokens.space2, vertical = 0.dp),
            ) {
                Text(
                    text = moreLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun samplePipelineColumn(total: Int): PipelineColumnData {
    return PipelineColumnData(
        totalCount = total,
        visibleRows = listOf(
            DashboardOrderRow(
                orderId = "1",
                customerName = "Adeyinka Paul",
                primaryLabel = "Senator Wear · Due in 3d",
                daysUntilDeadline = 3,
            ),
            DashboardOrderRow(
                orderId = "2",
                customerName = "Blessing Tosin",
                primaryLabel = "Agbada · Due in 5d",
                daysUntilDeadline = 5,
            ),
        ),
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineDualCardPreview() {
    StitchPadTheme {
        PipelineDualCard(
            inProgress = samplePipelineColumn(total = 4),
            notStarted = samplePipelineColumn(total = 6),
            onRowClick = {},
            onInProgressMoreClick = {},
            onNotStartedMoreClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(widthDp = 320)
@Composable
private fun PipelineDualCardNarrowPreview() {
    StitchPadTheme {
        PipelineDualCard(
            inProgress = samplePipelineColumn(total = 4),
            notStarted = samplePipelineColumn(total = 6),
            onRowClick = {},
            onInProgressMoreClick = {},
            onNotStartedMoreClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineDualCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        PipelineDualCard(
            inProgress = samplePipelineColumn(total = 4),
            notStarted = samplePipelineColumn(total = 6),
            onRowClick = {},
            onInProgressMoreClick = {},
            onNotStartedMoreClick = {},
        )
    }
}
