package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.dashboard.domain.StaffPipelineCounts
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage
import com.danzucker.stitchpad.feature.dashboard.presentation.model.CustomerReadyUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FirstOrderSetupUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.MeasurementsPickerUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import com.danzucker.stitchpad.feature.dashboard.presentation.model.WeeklyGoalUi
import kotlinx.datetime.LocalDate

enum class Greeting { MORNING, AFTERNOON, EVENING }

data class DashboardState(
    /**
     * Canonical screen state — drives the top-level render branch in DashboardScreen.
     * See [DashboardUiState] for each variant's render contract.
     */
    val uiState: DashboardUiState = DashboardUiState.Loading,
    val firstName: String = "",
    val businessName: String? = null,
    val businessLogoUrl: String? = null,
    val greeting: Greeting = Greeting.MORNING,
    val todayDate: LocalDate? = null,
    // Owner + Staff: true when the signed-in user is an ACTIVE staff member.
    // Flips the whole dashboard to the money-free staff work view (a distinct
    // top-level render branch), set from the ActiveWorkshopProvider session as
    // soon as it resolves so the header never flashes the owner layout.
    val isStaff: Boolean = false,
    // The signed-in person's own member id (owner's authUid, or the active staff
    // session's authUid) — set for both roles from the same `authUser.id` source
    // in DashboardViewModel. Lets order-card rows resolve "Assigned to you" the
    // same way for an owner viewing their own dashboard or a staff member viewing
    // theirs. Null until the first load resolves it.
    val viewerMemberId: String? = null,
    // Staff-only production-stage counts (money-free) for the pipeline strip and
    // the "In progress" tile. Null until the staff dashboard's first data load
    // completes — doubles as the staff loading sentinel (isStaff && null = spinner).
    val staffPipeline: StaffPipelineCounts? = null,
    // Staff-only: count of orders currently assigned to the signed-in staff member
    // (Order.assignedMemberId == the session's authUid) — drives the "Mine" count tile.
    val staffMineCount: Int = 0,
    // Staff-only (focus-queue design, 2026-08-14): the full, uncapped, money-free
    // candidate pool (Buckets.openQueue) the "Up next" hero, "Then" queue, and
    // "Unassigned in the shop" sections are derived from via computeFocusQueue.
    val staffOpenQueue: List<DashboardOrderRow> = emptyList(),
    // Staff-only: orders with a stage-advance CTA tap currently in flight, keyed by
    // orderId, valued by the stage the order was AT when the tap landed. The CTA
    // disables while its orderId is a key here. Self-heals: updateStaffState prunes
    // an entry the moment the live order's stage no longer matches the recorded
    // value (the repository listener "echoed" the change, or the tap turned out to
    // be an error and was explicitly cleared) — no dedicated timeout/cleanup path.
    val advancingOrders: Map<String, PipelineStage> = emptyMap(),
    val overdue: List<DashboardOrderRow> = emptyList(),
    val dueToday: List<DashboardOrderRow> = emptyList(),
    val ready: List<DashboardOrderRow> = emptyList(),
    val outstandingAmount: Double = 0.0,
    val outstandingOrderCount: Int = 0,
    val outstandingOverdueCount: Int = 0,
    val nextBestActions: List<NextBestAction> = emptyList(),
    val pipelineInProgress: List<DashboardOrderRow> = emptyList(),
    val pipelineInProgressTotal: Int = 0,
    val pipelinePending: List<DashboardOrderRow> = emptyList(),
    val pipelinePendingTotal: Int = 0,
    // Focus today (always-on adaptive header)
    val focusVariant: FocusVariant = FocusVariant.Quiet,
    val focusHeadline: UiText? = null,
    val focusSupporting: UiText? = null,
    val focusCtaLabel: UiText? = null,
    val focusCtaSubtitle: UiText? = null,
    /** Optional uppercase pill above the title in prominent variants (e.g. "● CALM DAY"). */
    val focusSectionLabel: UiText? = null,
    // Reconnect (S2/S3/S4 surfaces)
    val reconnectCandidates: List<ReconnectCandidate> = emptyList(),
    // "Your customer" card on FirstCustomer state — most recently added
    // customer with quick-message + open-detail affordances. Null otherwise.
    val customerReady: CustomerReadyUi? = null,
    // Drives the persistent "Order setup" checklist. Non-null while the
    // first order is still missing a due date or a deposit; null after both
    // are set, or once the user has more than one order (past onboarding).
    val firstOrderSetup: FirstOrderSetupUi? = null,
    // Weekly goal — null when the user hasn't set one. Sourced from WeeklyGoalRepository.
    val weeklyGoal: WeeklyGoalUi? = null,
    // Last-known remaining Smart Suggestions free-tier quota (null = unknown,
    // i.e. the user hasn't drafted anything yet this session or is premium).
    // Mirrored from the SmartUsageStore singleton.
    val smartFreeQuotaRemaining: Int? = null,
    // Welcome-ending banner — shown when the user's free welcome window is
    // within 3 days of expiring. Days-left is null when showWelcomeBanner is
    // false so rendering code can guard on both fields safely.
    val welcomeBannerDaysLeft: Int? = null,
    val showWelcomeBanner: Boolean = false,
    // Community banner — shown when remote config enables it with a usable link
    // and the user hasn't dismissed/joined. communityUrl is null when hidden.
    val communityUrl: String? = null,
    val showCommunityBanner: Boolean = false,
    // Live unread notification count — drives the bell badge in the dashboard header.
    val unreadNotificationCount: Int = 0,
    // Measurements shortcut's customer picker sheet — null means closed.
    val measurementsPicker: MeasurementsPickerUi? = null,
    val errorMessage: UiText? = null
)
