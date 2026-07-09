package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
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
    val overdue: List<DashboardOrderRow> = emptyList(),
    val dueToday: List<DashboardOrderRow> = emptyList(),
    val ready: List<DashboardOrderRow> = emptyList(),
    val outstandingAmount: Double = 0.0,
    val outstandingOrderCount: Int = 0,
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
