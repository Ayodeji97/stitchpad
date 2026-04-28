package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * The user-facing rendering state of the dashboard. Exactly one variant is active
 * at any time. Replaces the previous boolean cascade (isLoading + isBrandNew +
 * isAllClear) by making impossible combinations unrepresentable — you cannot be
 * both `BrandNew` and `BusyDay` simultaneously, which the boolean version allowed.
 *
 * Resolution priority (first match wins, see [DashboardViewModel.resolveUiState]):
 *   Loading → BrandNew → FirstCustomer → BusyDay → NbaActive → PipelineSteady → QuietDay
 *
 * Data needed for rendering (orders, customers, pipeline, NBA, weeklyGoal, …) lives
 * on [DashboardState] alongside this. Sections render conditionally based on the
 * active variant — e.g. TileGrid only renders for [BusyDay]; ReconnectStrip only for
 * [FirstCustomer] / [QuietDay]; QuickStartTiles only for [FirstCustomer].
 */
sealed interface DashboardUiState {

    /** Initial data load in progress. Renders centered `LoadingDots`. */
    data object Loading : DashboardUiState

    /**
     * Zero customers AND zero orders — the absolute fresh-install state.
     * Renders `WelcomeHero` ("Add first customer") full-bleed; the FocusTodayCard
     * and other adaptive sections are NOT shown.
     */
    data object BrandNew : DashboardUiState

    /**
     * One or more customers exist, but no orders yet. The reported empty-screen
     * bug — previously fell through to the green "All clear" banner.
     * Renders FocusTodayCard (FirstOrder variant) + QuickStartTiles +
     * ReconnectStrip (just-added customer) + empty affordances for NBA/Pipeline.
     */
    data object FirstCustomer : DashboardUiState

    /**
     * Has historical data but the day is calm: no urgent triage, no NBA-eligible
     * actions, no pipeline movement. Common state for established tailors on a
     * slow morning.
     * Renders FocusTodayCard (Quiet variant) with reconnect-prompt CTA +
     * ReconnectStrip + empty affordances for NBA/Pipeline.
     */
    data object QuietDay : DashboardUiState

    /**
     * Pipeline has work-in-flight (PENDING or IN_PROGRESS orders) but nothing is
     * urgent and nothing qualifies for an NBA suggestion. Tailor is on track.
     * Renders FocusTodayCard (Steady variant) + PipelineSection (in-progress +
     * not-started subsections) + collapsed Reconnect strip.
     */
    data object PipelineSteady : DashboardUiState

    /**
     * Revenue-driving NBA suggestions exist (e.g. CollectDeposit, StartSoon) but
     * triage is empty. The "earn opportunity" state — encourages action.
     * Renders FocusTodayCard (Earn variant) + NBA carousel + PipelineSection.
     */
    data object NbaActive : DashboardUiState

    /**
     * Triage is active — at least one of overdue / due-today / ready-for-pickup /
     * unpaid balance. The default high-energy state when the workshop is busy.
     * Renders FocusTodayCard (Focus variant, red accent) + TileGrid +
     * TodaysWorkList + NBA carousel + PipelineSection.
     */
    data object BusyDay : DashboardUiState
}

/**
 * Convenience: maps each non-Loading, non-BrandNew [DashboardUiState] to its
 * matching [FocusVariant] for the FocusTodayCard. Returns `null` for states that
 * don't render the card (Loading, BrandNew).
 */
val DashboardUiState.focusVariant: FocusVariant?
    get() = when (this) {
        DashboardUiState.FirstCustomer -> FocusVariant.FirstOrder
        DashboardUiState.QuietDay -> FocusVariant.Quiet
        DashboardUiState.PipelineSteady -> FocusVariant.Steady
        DashboardUiState.NbaActive -> FocusVariant.Earn
        DashboardUiState.BusyDay -> FocusVariant.Focus
        DashboardUiState.Loading, DashboardUiState.BrandNew -> null
    }
