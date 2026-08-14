@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.domain.FocusQueue
import com.danzucker.stitchpad.feature.dashboard.domain.StaffPipelineCounts
import com.danzucker.stitchpad.feature.dashboard.domain.UrgencyLevel
import com.danzucker.stitchpad.feature.dashboard.domain.computeFocusQueue
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage
import com.danzucker.stitchpad.feature.dashboard.domain.orderCodeFor
import com.danzucker.stitchpad.feature.dashboard.domain.resolveUrgencyLevel
import com.danzucker.stitchpad.feature.dashboard.presentation.DashboardAction
import com.danzucker.stitchpad.feature.dashboard.presentation.DashboardState
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_staff_badge
import stitchpad.composeapp.generated.resources.dashboard_staff_empty_body
import stitchpad.composeapp.generated.resources.dashboard_staff_empty_title
import stitchpad.composeapp.generated.resources.dashboard_staff_greeting
import stitchpad.composeapp.generated.resources.dashboard_staff_pipeline
import stitchpad.composeapp.generated.resources.dashboard_staff_stage_ready
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_due_today
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_in_progress
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_mine
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_overdue
import stitchpad.composeapp.generated.resources.dashboard_staff_tile_view
import stitchpad.composeapp.generated.resources.dashboard_staff_workshop_count
import stitchpad.composeapp.generated.resources.deadline_day_late
import stitchpad.composeapp.generated.resources.deadline_days_late
import stitchpad.composeapp.generated.resources.order_assign_you
import stitchpad.composeapp.generated.resources.order_filter_unassigned
import stitchpad.composeapp.generated.resources.order_stage_cutting
import stitchpad.composeapp.generated.resources.order_stage_fitting
import stitchpad.composeapp.generated.resources.order_stage_pending
import stitchpad.composeapp.generated.resources.order_stage_ready
import stitchpad.composeapp.generated.resources.order_stage_sewing
import stitchpad.composeapp.generated.resources.staff_advance_stage_cta
import stitchpad.composeapp.generated.resources.staff_due_today
import stitchpad.composeapp.generated.resources.staff_due_tomorrow
import stitchpad.composeapp.generated.resources.staff_due_weekday
import stitchpad.composeapp.generated.resources.staff_on_track
import stitchpad.composeapp.generated.resources.staff_stage_now
import stitchpad.composeapp.generated.resources.staff_then_header
import stitchpad.composeapp.generated.resources.staff_unassigned_header_count
import stitchpad.composeapp.generated.resources.staff_up_next_header
import stitchpad.composeapp.generated.resources.weekday_abbrev_fri
import stitchpad.composeapp.generated.resources.weekday_abbrev_mon
import stitchpad.composeapp.generated.resources.weekday_abbrev_sat
import stitchpad.composeapp.generated.resources.weekday_abbrev_sun
import stitchpad.composeapp.generated.resources.weekday_abbrev_thu
import stitchpad.composeapp.generated.resources.weekday_abbrev_tue
import stitchpad.composeapp.generated.resources.weekday_abbrev_wed

/** "Unassigned in the shop" ticket cards render at 70% opacity per the design spec. */
private const val UNASSIGNED_CARD_OPACITY = 0.7f

/**
 * The money-free STAFF dashboard (Slice 6b; focus-queue redesign 2026-08-14). A
 * work view, not a money view: no revenue, no "ready to collect", no goals or
 * collect nudges — those never enter [DashboardState] for a staff member (see
 * DashboardViewModel.updateStaffState).
 *
 * Layout: greeting header (own name + Staff pill + workshop) -> three
 * urgency-weighted count tiles (overdue / due today / in progress) -> the
 * pipeline-by-stage segmented bar -> the focus queue: an "Up next" hero (the
 * single highest-priority order assigned to the viewer, with a one-tap stage
 * advance) -> a "Then" queue of the viewer's remaining assigned tickets -> an
 * "Unassigned in the shop" queue at reduced opacity. When the viewer has no
 * assigned open orders, [StaffAllCaughtUp] renders in place of the hero/then
 * sections (the unassigned shop queue, if any, still renders below it).
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
    val today = state.todayDate
    if (pipeline == null || today == null) {
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
        if (!pipeline.isEmpty) {
            StaffPipelineBar(pipeline)
        }
        val focusQueue = remember(state.staffOpenQueue, state.viewerMemberId) {
            computeFocusQueue(state.staffOpenQueue, state.viewerMemberId)
        }
        StaffFocusQueueSection(
            focusQueue = focusQueue,
            today = today,
            viewerMemberId = state.viewerMemberId,
            advancingOrders = state.advancingOrders,
            onAction = onAction,
        )
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
        StageLegend(cutting, counts.cutting, DesignTokens.warning500),
        StageLegend(sewing, counts.sewing, MaterialTheme.colorScheme.primary),
        StageLegend(fitting, counts.fitting, DesignTokens.sienna500),
        StageLegend(ready, counts.ready, DesignTokens.success500),
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

/** Pipeline-bar legend entry (label + count + swatch colour) — unrelated to [PipelineStage]. */
private data class StageLegend(val label: String, val count: Int, val color: Color)

@Composable
private fun StageLegendItem(stage: StageLegend, modifier: Modifier = Modifier) {
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

// ── Focus queue ──────────────────────────────────────────────────

/**
 * The "Up next" hero + "Then" queue + "Unassigned in the shop" sections. When
 * [FocusQueue.hero] is null the viewer has no assigned open orders — renders
 * [StaffAllCaughtUp] in its place, but the unassigned shop queue (if any)
 * still renders below it (per the design spec: "the existing StaffAllCaughtUp
 * shows above the shop queue").
 */
@Composable
private fun StaffFocusQueueSection(
    focusQueue: FocusQueue,
    today: LocalDate,
    viewerMemberId: String?,
    advancingOrders: Map<String, PipelineStage>,
    onAction: (DashboardAction) -> Unit,
) {
    val hero = focusQueue.hero
    if (hero != null) {
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
            FocusSectionHeader(stringResource(Res.string.staff_up_next_header))
            UpNextHero(
                row = hero,
                today = today,
                viewerMemberId = viewerMemberId,
                isAdvancing = advancingOrders.containsKey(hero.orderId),
                onAdvance = { fromStage -> onAction(DashboardAction.OnAdvanceStage(hero.orderId, fromStage)) },
                onClick = { onAction(DashboardAction.OnOrderClick(hero.orderId)) },
            )
        }
        if (focusQueue.thenQueue.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                FocusSectionHeader(stringResource(Res.string.staff_then_header))
                focusQueue.thenQueue.forEach { row ->
                    TicketRow(
                        row = row,
                        today = today,
                        viewerMemberId = viewerMemberId,
                        dimmed = false,
                        onClick = { onAction(DashboardAction.OnOrderClick(row.orderId)) },
                    )
                }
            }
        }
    } else {
        StaffAllCaughtUp()
    }
    if (focusQueue.unassigned.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
            FocusSectionHeader(
                stringResource(Res.string.staff_unassigned_header_count, focusQueue.unassigned.size)
            )
            focusQueue.unassigned.forEach { row ->
                TicketRow(
                    row = row,
                    today = today,
                    viewerMemberId = viewerMemberId,
                    dimmed = true,
                    onClick = { onAction(DashboardAction.OnOrderClick(row.orderId)) },
                )
            }
        }
    }
}

@Composable
private fun FocusSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * The single highest-priority actionable order assigned to the viewer: customer
 * name + garment, an urgency chip, a 5-segment stage stepper (done = indigo,
 * current = saffron — the heritage accent's sanctioned use on this screen), a
 * full-width "Mark ‹stage› done -> ‹next›" CTA, and a tear-line footer (order
 * code left, "You" chip right). [isAdvancing] disables the CTA without changing
 * the visible stage (no optimistic update — see [DashboardAction.OnAdvanceStage]'s
 * KDoc for why).
 */
@Composable
private fun UpNextHero(
    row: DashboardOrderRow,
    today: LocalDate,
    viewerMemberId: String?,
    isAdvancing: Boolean,
    onAdvance: (PipelineStage) -> Unit,
    onClick: () -> Unit,
) {
    val stage = row.stage ?: return
    val nextStage = stage.next()
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            Column(Modifier.padding(DesignTokens.space4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = row.customerName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = row.primaryLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(DesignTokens.space2))
                    UrgencyChip(daysLate = row.daysLate, daysUntilDeadline = row.daysUntilDeadline, today = today)
                }
                Spacer(Modifier.height(DesignTokens.space4))
                HeroStageStepper(stage = stage)
                if (nextStage != null) {
                    Spacer(Modifier.height(DesignTokens.space4))
                    StitchPadButton(
                        text = stringResource(
                            Res.string.staff_advance_stage_cta,
                            stageLabel(stage),
                            stageLabel(nextStage),
                        ),
                        onClick = { onAdvance(stage) },
                        isLoading = isAdvancing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HeroFooter(row = row, viewerMemberId = viewerMemberId)
        }
    }
}

@Composable
private fun HeroFooter(row: DashboardOrderRow, viewerMemberId: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tearLineTop(MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = orderCodeFor(row.orderId),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssigneeChip(
            assignedMemberId = row.assignedMemberId,
            assignedMemberName = row.assignedMemberName,
            viewerMemberId = viewerMemberId,
        )
    }
}

/**
 * 5-segment stepper (Pending/Cutting/Sewing/Fitting/Ready): done = indigo,
 * current = saffron, upcoming = neutral outline. Stepline labels: done stages
 * show a checkmark, the current stage shows "‹Stage› — now", upcoming stages
 * show their plain name.
 */
@Composable
private fun HeroStageStepper(stage: PipelineStage) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PipelineStage.entries.forEach { s ->
                HeroStepNode(done = s.ordinal < stage.ordinal, current = s == stage)
            }
        }
        Spacer(Modifier.height(DesignTokens.space1))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PipelineStage.entries.forEach { s ->
                val done = s.ordinal < stage.ordinal
                val current = s == stage
                Text(
                    text = when {
                        done -> "✓"
                        current -> stringResource(Res.string.staff_stage_now, stageLabel(s))
                        else -> stageLabel(s)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        current -> DesignTokens.saffron500
                        done -> DesignTokens.indigo500
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroStepNode(done: Boolean, current: Boolean) {
    val color = when {
        done -> DesignTokens.indigo500
        current -> DesignTokens.saffron500
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(if (current) 16.dp else 12.dp)
            .clip(CircleShape)
            .then(
                if (done || current) {
                    Modifier.background(color)
                } else {
                    Modifier.border(1.5.dp, color, CircleShape)
                }
            ),
    )
}

/**
 * A compact ticket card for the "Then" and "Unassigned in the shop" sections:
 * avatar + name + garment + urgency chip, then a tear-line footer with stage
 * dots + stage name (left) and an assignee chip (right). [dimmed] renders the
 * whole card at 70% opacity for the unassigned section.
 */
@Composable
private fun TicketRow(
    row: DashboardOrderRow,
    today: LocalDate,
    viewerMemberId: String?,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val stage = row.stage ?: return
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) UNASSIGNED_CARD_OPACITY else 1f)
            .clickable(onClick = onClick),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(DesignTokens.space3),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TicketAvatar(name = row.customerName)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = row.primaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                UrgencyChip(daysLate = row.daysLate, daysUntilDeadline = row.daysUntilDeadline, today = today)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tearLineTop(MaterialTheme.colorScheme.outlineVariant)
                    .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StageDots(stage = stage)
                    Spacer(Modifier.width(DesignTokens.space1))
                    Text(
                        text = stageLabel(stage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssigneeChip(
                    assignedMemberId = row.assignedMemberId,
                    assignedMemberName = row.assignedMemberName,
                    viewerMemberId = viewerMemberId,
                )
            }
        }
    }
}

@Composable
private fun TicketAvatar(name: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** `●●●○○` — done = indigo, current = saffron, upcoming = neutral. */
@Composable
private fun StageDots(stage: PipelineStage) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        PipelineStage.entries.forEach { s ->
            val color = when {
                s.ordinal < stage.ordinal -> DesignTokens.indigo500
                s == stage -> DesignTokens.saffron500
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
    }
}

/** "You" (filled indigo, viewer-assigned) / teammate name (quiet outline) / "Unassigned" (quiet outline). */
@Composable
private fun AssigneeChip(assignedMemberId: String?, assignedMemberName: String?, viewerMemberId: String?) {
    val isViewer = assignedMemberId != null && assignedMemberId == viewerMemberId
    val text = when {
        assignedMemberId == null -> stringResource(Res.string.order_filter_unassigned)
        isViewer -> stringResource(Res.string.order_assign_you)
        else -> assignedMemberName ?: assignedMemberId
    }
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = if (isViewer) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (isViewer) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (isViewer) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 3.dp),
        )
    }
}

/** Calibrated urgency chip — late (red) / soon (amber) / ok (neutral, "On track"). */
@Composable
private fun UrgencyChip(daysLate: Int?, daysUntilDeadline: Int?, today: LocalDate) {
    val level = resolveUrgencyLevel(daysLate, daysUntilDeadline)
    val color = when (level) {
        UrgencyLevel.LATE -> DesignTokens.error500
        UrgencyLevel.SOON -> DesignTokens.warning500
        UrgencyLevel.OK -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when (level) {
        UrgencyLevel.LATE -> lateText(daysLate)
        UrgencyLevel.SOON -> soonText(daysUntilDeadline, today)
        UrgencyLevel.OK -> stringResource(Res.string.staff_on_track)
    }
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = color.copy(alpha = if (level == UrgencyLevel.OK) 0f else 0.12f),
        border = if (level == UrgencyLevel.OK) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = DesignTokens.space2, vertical = 3.dp),
        )
    }
}

/** Reuses the app's existing days-late string (singular/plural), matching [DeadlineLine]'s convention. */
@Composable
private fun lateText(daysLate: Int?): String = when (daysLate) {
    1 -> stringResource(Res.string.deadline_day_late)
    else -> stringResource(Res.string.deadline_days_late, daysLate ?: 0)
}

@Composable
private fun soonText(daysUntilDeadline: Int?, today: LocalDate): String = when (daysUntilDeadline) {
    0 -> stringResource(Res.string.staff_due_today)
    1 -> stringResource(Res.string.staff_due_tomorrow)
    else -> {
        val days = daysUntilDeadline ?: 0
        val date = LocalDate.fromEpochDays(today.toEpochDays() + days)
        stringResource(Res.string.staff_due_weekday, stringResource(weekdayAbbrevRes(date.dayOfWeek)))
    }
}

private fun weekdayAbbrevRes(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> Res.string.weekday_abbrev_mon
    DayOfWeek.TUESDAY -> Res.string.weekday_abbrev_tue
    DayOfWeek.WEDNESDAY -> Res.string.weekday_abbrev_wed
    DayOfWeek.THURSDAY -> Res.string.weekday_abbrev_thu
    DayOfWeek.FRIDAY -> Res.string.weekday_abbrev_fri
    DayOfWeek.SATURDAY -> Res.string.weekday_abbrev_sat
    DayOfWeek.SUNDAY -> Res.string.weekday_abbrev_sun
}

@Composable
private fun stageLabel(stage: PipelineStage): String = when (stage) {
    PipelineStage.PENDING -> stringResource(Res.string.order_stage_pending)
    PipelineStage.CUTTING -> stringResource(Res.string.order_stage_cutting)
    PipelineStage.SEWING -> stringResource(Res.string.order_stage_sewing)
    PipelineStage.FITTING -> stringResource(Res.string.order_stage_fitting)
    PipelineStage.READY -> stringResource(Res.string.order_stage_ready)
}

/** Dashed top border — the ticket/hero "tear-line" footer divider. */
private fun Modifier.tearLineTop(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f),
    )
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

// region — Previews

/** Preview row assigned to "Gabby" (the previewed viewer) — `.copy(assignedMemberId = null, ...)` for an unassigned sample. */
private fun previewRow(
    orderId: String,
    customerName: String,
    primaryLabel: String,
    stage: PipelineStage,
    daysLate: Int? = null,
    daysUntilDeadline: Int? = null,
) = DashboardOrderRow(
    orderId = orderId,
    customerName = customerName,
    primaryLabel = primaryLabel,
    daysLate = daysLate,
    daysUntilDeadline = daysUntilDeadline,
    assignedMemberId = "uid-gabby",
    assignedMemberName = "Gabby",
    stage = stage,
)

private val previewStaffState = DashboardState(
    isStaff = true,
    firstName = "Gabby",
    businessName = "Ade Fashions",
    todayDate = LocalDate(2026, 8, 14),
    staffPipeline = StaffPipelineCounts(cutting = 3, sewing = 5, fitting = 3, ready = 4),
    staffMineCount = 4,
    viewerMemberId = "uid-gabby",
    overdue = listOf(
        previewRow(
            orderId = "1",
            customerName = "Chidi O.",
            primaryLabel = "Agbada",
            stage = PipelineStage.CUTTING,
            daysLate = 2,
        ),
    ),
    dueToday = emptyList(),
    staffOpenQueue = listOf(
        previewRow(
            orderId = "ord-c3d4",
            customerName = "Chidi Okafor",
            primaryLabel = "Agbada · 1",
            stage = PipelineStage.CUTTING,
            daysLate = 2,
        ),
        previewRow(
            orderId = "ord-a1b2",
            customerName = "Amaka Nwosu",
            primaryLabel = "Kaftan · 2",
            stage = PipelineStage.SEWING,
            daysUntilDeadline = 1,
        ),
        previewRow(
            orderId = "ord-9f8e",
            customerName = "Tunde Bakare",
            primaryLabel = "Senator suit · 1",
            stage = PipelineStage.FITTING,
            daysUntilDeadline = 6,
        ),
        previewRow(
            orderId = "ord-7c6d",
            customerName = "Bola Adeyemi",
            primaryLabel = "Gown · 1",
            stage = PipelineStage.PENDING,
        ).copy(assignedMemberId = null, assignedMemberName = null),
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
                staffOpenQueue = emptyList(),
                staffPipeline = StaffPipelineCounts(cutting = 0, sewing = 4, fitting = 0, ready = 2),
            ),
            onAction = {},
        )
    }
}

// endregion
