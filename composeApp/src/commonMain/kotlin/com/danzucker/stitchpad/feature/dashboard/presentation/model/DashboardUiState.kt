package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * The user-facing rendering state of the dashboard. Exactly one variant is active
 * at any time. Replaces the previous boolean cascade (isLoading + isBrandNew +
 * isAllClear) by making impossible combinations unrepresentable — you cannot be
 * both `BrandNew` and `BusyDay` simultaneously, which the boolean version allowed.
 *
 * Resolution priority (first match wins, see [DashboardViewModel.resolveUiState]):
 *   Loading → BrandNew → FirstCustomer → BusyDay → ReadyForPickup → NbaActive → PipelineSteady → QuietDay
 *
 * Data needed for rendering (orders, customers, pipeline, NBA, weeklyGoal, …) lives
 * on [DashboardState] alongside this. Sections render conditionally based on the
 * active variant — e.g. TodayWorkCard only renders for [BusyDay]; ReconnectChipStrip
 * renders for [FirstCustomer] / [QuietDay] when candidates exist.
 */
sealed interface DashboardUiState {

    /** Initial data load in progress. Renders centered `LoadingDots`. */
    data object Loading : DashboardUiState

    /**
     * Zero customers AND zero orders — the absolute fresh-install state.
     * Renders a blank header; [IllustratedFocusCard] and other adaptive sections
     * are NOT shown.
     */
    data object BrandNew : DashboardUiState

    /**
     * One or more customers exist, but no orders yet. The reported empty-screen
     * bug — previously fell through to the green "All clear" banner.
     * Renders [IllustratedFocusCard] (FirstOrder variant) + [ReconnectChipStrip]
     * (just-added customer) + empty affordances for NBA/Pipeline.
     */
    data object FirstCustomer : DashboardUiState

    /**
     * Has historical data but the day is calm: no urgent triage, no NBA-eligible
     * actions, no pipeline movement. Common state for established tailors on a
     * slow morning.
     * Renders [IllustratedFocusCard] (Quiet variant) with reconnect-prompt CTA +
     * [ReconnectChipStrip] + empty affordances for NBA/Pipeline.
     */
    data object QuietDay : DashboardUiState

    /**
     * Pipeline has work-in-flight (PENDING or IN_PROGRESS orders) but nothing is
     * urgent and nothing qualifies for an NBA suggestion. Tailor is on track.
     * Renders [IllustratedFocusCard] (Steady variant) + PipelineDualCard (in-progress +
     * not-started columns) + collapsed reconnect strip.
     */
    data object PipelineSteady : DashboardUiState

    /**
     * Revenue-driving NBA suggestions exist (e.g. CollectDeposit, StartSoon) but
     * triage is empty. The "earn opportunity" state — encourages action.
     * Renders [IllustratedFocusCard] (Earn variant) + NBA carousel + PipelineDualCard.
     */
    data object NbaActive : DashboardUiState

    /**
     * Triage is active — at least one overdue or due-today order. The default
     * high-energy state when the workshop is busy. Ready-for-pickup-only days
     * resolve to [ReadyForPickup] instead so we don't paint good news red.
     * Renders [IllustratedFocusCard] (Focus variant, red accent) + [TodayWorkCard]
     * + NBA carousel + PipelineDualCard.
     */
    data object BusyDay : DashboardUiState

    /**
     * No overdue / due-today work, but at least one order is finished and
     * waiting for the customer to collect. Calmer than [BusyDay] — a ready
     * order is future revenue + a satisfied customer pickup, not a fire.
     * Renders [IllustratedFocusCard] (Pickup variant, green accent) + NBA carousel
     * + PipelineDualCard. [TodayWorkCard] is intentionally suppressed here since
     * the focus card already pins the top ready customer and the Pipeline card
     * covers the rest — avoids surfacing the same one-or-two orders three
     * times within the visible viewport.
     */
    data object ReadyForPickup : DashboardUiState
}

/**
 * Convenience: maps each non-Loading, non-BrandNew [DashboardUiState] to its
 * matching [FocusVariant] for [IllustratedFocusCard]. Returns `null` for states
 * that don't render the card (Loading, BrandNew).
 */
val DashboardUiState.focusVariant: FocusVariant?
    get() = when (this) {
        DashboardUiState.FirstCustomer -> FocusVariant.FirstOrder
        DashboardUiState.QuietDay -> FocusVariant.Quiet
        DashboardUiState.PipelineSteady -> FocusVariant.Steady
        DashboardUiState.NbaActive -> FocusVariant.Earn
        DashboardUiState.BusyDay -> FocusVariant.Focus
        DashboardUiState.ReadyForPickup -> FocusVariant.Pickup
        DashboardUiState.Loading, DashboardUiState.BrandNew -> null
    }
