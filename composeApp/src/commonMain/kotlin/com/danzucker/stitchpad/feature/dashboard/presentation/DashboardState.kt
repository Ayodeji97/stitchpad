package com.danzucker.stitchpad.feature.dashboard.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.presentation.model.CustomerReadyUi
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
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
    // Reconnect (S2/S3/S4 surfaces)
    val reconnectCandidates: List<ReconnectCandidate> = emptyList(),
    // "Your customer" card on FirstCustomer state — most recently added
    // customer with quick-message + open-detail affordances. Null otherwise.
    val customerReady: CustomerReadyUi? = null,
    // Weekly goal — null when the user hasn't set one. Sourced from WeeklyGoalRepository.
    val weeklyGoal: WeeklyGoalUi? = null,
    val errorMessage: UiText? = null
)
