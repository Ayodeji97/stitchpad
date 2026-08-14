@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.domain.StaffPipelineCounts
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.presentation.DashboardAction
import com.danzucker.stitchpad.feature.dashboard.presentation.DashboardState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.TodayWorkRowUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.buildTodayWorkRows
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_staff_all_orders
import stitchpad.composeapp.generated.resources.dashboard_staff_badge
import stitchpad.composeapp.generated.resources.dashboard_staff_empty_body
import stitchpad.composeapp.generated.resources.dashboard_staff_empty_title
import stitchpad.composeapp.generated.resources.dashboard_staff_greeting
import stitchpad.composeapp.generated.resources.dashboard_staff_needs_attention
import stitchpad.composeapp.generated.resources.dashboard_staff_pipeline
import stitchpad.composeapp.generated.resources.dashboard_staff_stage_ready
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_due_today
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_in_progress
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_mine
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_overdue
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_view
import stitchpad.composeapp.generated.resources.dashboard_staff_workshop_count
import stitchpad.composeapp.generated.resources.order_stage_cutting
import stitchpad.composeapp.generated.resources.order_stage_fitting
import stitchpad.composeapp.generated.resources.order_stage_sewing

/**
 * The money-free STAFF dashboard (Slice 6b). A work view, not a money view: no
 * revenue, no "ready to collect", no goals or collect nudges — those never enter
 * [DashboardState] for a staff member (see DashboardViewModel.updateStaffState).
 *
 * Layout: greeting header (own name + Staff pill + workshop) → three urgency-weighted
 * count tiles (overdue / due today / in progress), each tappable to open the Orders
 * list → a "Needs attention" queue of orders to advance → the pipeline by production
 * stage as one segmented bar. When nothing is overdue or due today, the queue is
 * replaced by an "All caught up" state.
 *
 * [DashboardState.staffPipeline] is null until the first data load lands — that
 * is the loading sentinel (header shows, body is a spinner).
 */
@Composable
fun StaffDashboardContent(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val pipeline = state.staffPipeline
    if (pipeline == null) {
        Column(modifier = modifier.fillMaxSize().padding(horizontal = DesignTokens.space4)) {
            Spacer(Modifier.height(DesignTokens.space4))
            StaffDashboardHeader(state, onAction)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingDots() }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.space4)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space5),
    ) {
        Spacer(Modifier.height(DesignTokens.space4))
        StaffDashboardHeader(state, onAction)
        StaffCountTiles(
            overdue = state.overdue.size,
            dueToday = state.dueToday.size,
            inProgress = pipeline.inProgressTotal,
            mine = state.staffMineCount,
            onOverdueClick = { onAction(DashboardAction.OnViewOverdueClick) },
            onDueTodayClick = { onAction(DashboardAction.OnViewDueTodayClick) },
            onInProgressClick = { onAction(DashboardAction.OnViewPipelineInProgressClick) },
            onMineClick = { onAction(DashboardAction.OnViewMyWorkClick) },
        )
        val rows = remember(state.overdue, state.dueToday, state.viewerMemberId) {
            buildTodayWorkRows(state.overdue, state.dueToday, emptyList(), state.viewerMemberId)
        }
        if (rows.isNotEmpty()) {
            StaffNeedsAttentionSection(
                rows = rows,
                onRowClick = { id -> onAction(DashboardAction.OnOrderClick(id)) },
                onAllOrdersClick = { onAction(DashboardAction.OnViewAllOrdersClick) },
            )
        } else {
            StaffAllCaughtUp()
        }
        if (!pipeline.isEmpty) {
            StaffPipelineBar(pipeline)
        }
    }
}

@Composable
private fun StaffDashboardHeader(
    state: DashboardState,
    onAction: (DashboardAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.dashboard_staff_greeting, state.firstName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DesignTokens.space1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StaffPill()
                val workshop = state.businessName
                if (!workshop.isNullOrBlank()) {
                    Spacer(Modifier.width(DesignTokens.space2))
                    Text(
                        text = workshop,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(DesignTokens.space3))
        UserAvatar(
            name = state.firstName,
            onClick = { onAction(DashboardAction.OnSettingsClick) },
        )
    }
}

@Composable
private fun StaffPill() {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = stringResource(Res.string.dashboard_staff_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 2.dp),
        )
    }
}

@Composable
private fun StaffCountTiles(
    overdue: Int,
    dueToday: Int,
    inProgress: Int,
    mine: Int,
    onOverdueClick: () -> Unit,
    onDueTodayClick: () -> Unit,
    onInProgressClick: () -> Unit,
    onMineClick: () -> Unit,
) {
    // IntrinsicSize.Min keeps all four tiles the height of the tallest, so the "View"
    // affordance on populated tiles never leaves a zero-count tile looking clipped.
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        StaffCountTile(
            count = overdue,
            label = stringResource(Res.string.dashboard_staff_tile_overdue),
            accent = DesignTokens.error500,
            tinted = true,
            onClick = onOverdueClick,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StaffCountTile(
            count = dueToday,
            label = stringResource(Res.string.dashboard_staff_tile_due_today),
            accent = DesignTokens.warning500,
            tinted = true,
            onClick = onDueTodayClick,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StaffCountTile(
            count = inProgress,
            label = stringResource(Res.string.dashboard_staff_tile_in_progress),
            accent = MaterialTheme.colorScheme.primary,
            tinted = false,
            onClick = onInProgressClick,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StaffCountTile(
            count = mine,
            label = stringResource(Res.string.dashboard_staff_tile_mine),
            accent = MaterialTheme.colorScheme.primary,
            tinted = false,
            onClick = onMineClick,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/**
 * One urgency-weighted, tappable count tile. Overdue (critical) and Due-today
 * (warning) carry a soft accent wash + a full-height severity bar so they visibly
 * outrank the calm, neutral In-progress tile. A zero count drops the wash and the
 * "View" affordance and renders in a muted neutral — nothing to chase, no alarm.
 */
@Composable
private fun StaffCountTile(
    count: Int,
    label: String,
    accent: Color,
    tinted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isZero = count == 0
    val urgent = tinted && !isZero
    val barColor = when {
        isZero -> MaterialTheme.colorScheme.outlineVariant
        else -> accent
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = if (urgent) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = if (urgent) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(barColor),
            )
            Column(modifier = Modifier.padding(DesignTokens.space3)) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isZero) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isZero) {
                    Spacer(Modifier.height(DesignTokens.space1))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.dashboard_staff_tile_view),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffNeedsAttentionSection(
    rows: List<TodayWorkRowUi>,
    onRowClick: (orderId: String) -> Unit,
    onAllOrdersClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.dashboard_staff_needs_attention),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(
                onClick = onAllOrdersClick,
                contentPadding = PaddingValues(horizontal = DesignTokens.space2, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.dashboard_staff_all_orders),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        rows.forEach { row ->
            TodayWorkRichRow(row = row, onClick = { onRowClick(row.orderId) })
        }
    }
}

/**
 * The production pipeline as a single glanceable unit: a proportional segmented bar
 * (each stage's share of everything in the workshop) over a compact four-item legend.
 * Replaces the previous four separate tiles — same information, far less noise. Only
 * rendered when at least one stage is non-empty (see the caller's `!pipeline.isEmpty`).
 */
@Composable
private fun StaffPipelineBar(counts: StaffPipelineCounts) {
    val cutting = stringResource(Res.string.order_stage_cutting)
    val sewing = stringResource(Res.string.order_stage_sewing)
    val fitting = stringResource(Res.string.order_stage_fitting)
    val ready = stringResource(Res.string.dashboard_staff_stage_ready)
    val stages = listOf(
        PipelineStage(cutting, counts.cutting, DesignTokens.warning500),
        PipelineStage(sewing, counts.sewing, MaterialTheme.colorScheme.primary),
        PipelineStage(fitting, counts.fitting, DesignTokens.sienna500),
        PipelineStage(ready, counts.ready, DesignTokens.success500),
    )
    val total = stages.sumOf { it.count }

    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.dashboard_staff_pipeline),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.dashboard_staff_workshop_count, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusSm)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            stages.filter { it.count > 0 }.forEach { stage ->
                Box(
                    modifier = Modifier
                        .weight(stage.count.toFloat())
                        .fillMaxHeight()
                        .background(stage.color),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            stages.forEach { stage ->
                StageLegendItem(stage = stage, modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class PipelineStage(val label: String, val count: Int, val color: Color)

@Composable
private fun StageLegendItem(stage: PipelineStage, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(stage.color),
            )
            Spacer(Modifier.width(DesignTokens.space1))
            Text(
                text = stage.count.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stage.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StaffAllCaughtUp() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = DesignTokens.success500,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(DesignTokens.space3))
        Text(
            text = stringResource(Res.string.dashboard_staff_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(DesignTokens.space1))
        Text(
            text = stringResource(Res.string.dashboard_staff_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val previewStaffState = DashboardState(
    isStaff = true,
    firstName = "Gabby",
    businessName = "Ade Fashions",
    staffPipeline = StaffPipelineCounts(cutting = 3, sewing = 5, fitting = 3, ready = 4),
    staffMineCount = 4,
    viewerMemberId = "uid-gabby",
    overdue = listOf(
        DashboardOrderRow(
            orderId = "1",
            customerName = "Chidi O.",
            primaryLabel = "Agbada",
            daysLate = 2,
            assignedMemberId = "uid-gabby",
            assignedMemberName = "Gabby",
        ),
    ),
    dueToday = listOf(
        DashboardOrderRow(
            orderId = "2",
            customerName = "Amaka N.",
            primaryLabel = "Kaftan",
            assignedMemberId = "uid-tunde",
            assignedMemberName = "Tunde",
        ),
        DashboardOrderRow(orderId = "3", customerName = "Bola A.", primaryLabel = "Gown"),
    ),
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StaffDashboardContentPreview() {
    StitchPadTheme {
        StaffDashboardContent(state = previewStaffState, onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StaffDashboardContentDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        StaffDashboardContent(state = previewStaffState, onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StaffDashboardAllCaughtUpPreview() {
    StitchPadTheme {
        StaffDashboardContent(
            state = previewStaffState.copy(
                overdue = emptyList(),
                dueToday = emptyList(),
                staffPipeline = StaffPipelineCounts(cutting = 0, sewing = 4, fitting = 0, ready = 2),
            ),
            onAction = {},
        )
    }
}
