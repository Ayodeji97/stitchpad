package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.dashboard.domain.model.Buckets
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusResolution
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.focus_brand_new_cta
import stitchpad.composeapp.generated.resources.focus_brand_new_supporting
import stitchpad.composeapp.generated.resources.focus_brand_new_title
import stitchpad.composeapp.generated.resources.focus_busy_cta
import stitchpad.composeapp.generated.resources.focus_busy_supporting
import stitchpad.composeapp.generated.resources.focus_busy_title
import stitchpad.composeapp.generated.resources.focus_earn_cta
import stitchpad.composeapp.generated.resources.focus_earn_supporting
import stitchpad.composeapp.generated.resources.focus_earn_title
import stitchpad.composeapp.generated.resources.focus_first_order_cta
import stitchpad.composeapp.generated.resources.focus_first_order_cta_subtitle
import stitchpad.composeapp.generated.resources.focus_first_order_supporting
import stitchpad.composeapp.generated.resources.focus_first_order_title
import stitchpad.composeapp.generated.resources.focus_pickup_cta
import stitchpad.composeapp.generated.resources.focus_pickup_supporting
import stitchpad.composeapp.generated.resources.focus_pickup_title
import stitchpad.composeapp.generated.resources.focus_quiet_cta
import stitchpad.composeapp.generated.resources.focus_quiet_cta_no_candidate
import stitchpad.composeapp.generated.resources.focus_quiet_supporting
import stitchpad.composeapp.generated.resources.focus_quiet_title
import stitchpad.composeapp.generated.resources.focus_steady_cta
import stitchpad.composeapp.generated.resources.focus_steady_supporting
import stitchpad.composeapp.generated.resources.focus_steady_title

/**
 * Resolves the canonical screen-level [DashboardUiState] and the matching
 * [FocusResolution] ([IllustratedFocusCard] variant + copy + CTA label) from the
 * already-computed buckets, NBAs, and reconnect candidates.
 *
 * `resolveUiState` is the single source of priority truth — every other UI
 * decision (focus variant, copy, CTA) derives from its result. `resolveFocus`
 * pivots on a sealed-type when so the compiler enforces every state being
 * handled.
 *
 * `Loading` is never returned by [resolveUiState] — that's the initial state
 * cleared by the first data emission.
 */
object FocusResolver {

    /**
     * Priority (first match wins):
     *   BrandNew → FirstCustomer → BusyDay → ReadyForPickup → NbaActive → PipelineSteady → QuietDay
     *
     * `BusyDay` is reserved for genuine urgency (overdue or due-today). A day where
     * the only triage signal is ready-for-pickup is its own calmer state — see
     * [DashboardUiState.ReadyForPickup] for why.
     */
    @Suppress("ReturnCount")
    fun resolveUiState(
        buckets: Buckets,
        nextBestActions: List<NextBestAction>,
        orders: List<Order>,
        customers: List<Customer>
    ): DashboardUiState {
        if (orders.isEmpty() && customers.isEmpty()) return DashboardUiState.BrandNew
        if (orders.isEmpty()) return DashboardUiState.FirstCustomer
        if (buckets.overdue.isNotEmpty() || buckets.dueToday.isNotEmpty()) {
            return DashboardUiState.BusyDay
        }
        if (buckets.ready.isNotEmpty()) return DashboardUiState.ReadyForPickup
        if (nextBestActions.isNotEmpty()) return DashboardUiState.NbaActive
        val pipelineTotal = buckets.pipelineInProgressTotal + buckets.pipelinePendingTotal
        if (pipelineTotal > 0) return DashboardUiState.PipelineSteady
        return DashboardUiState.QuietDay
    }

    /**
     * For `Loading` and `BrandNew` returns a placeholder bundle that the screen
     * ignores — those states render LoadingDots / blank header instead.
     */
    @Suppress("LongMethod")
    fun resolveFocus(
        uiState: DashboardUiState,
        buckets: Buckets,
        nextBestActions: List<NextBestAction>,
        customers: List<Customer>,
        reconnect: List<ReconnectCandidate>
    ): FocusResolution = when (uiState) {
        DashboardUiState.FirstCustomer -> {
            val firstCustomer = customers.first()
            FocusResolution(
                variant = FocusVariant.FirstOrder,
                headline = UiText.StringResourceText(Res.string.focus_first_order_title),
                supporting = UiText.StringResourceText(
                    Res.string.focus_first_order_supporting,
                    arrayOf(firstCustomer.name)
                ),
                ctaLabel = UiText.StringResourceText(Res.string.focus_first_order_cta),
                ctaSubtitle = UiText.StringResourceText(
                    Res.string.focus_first_order_cta_subtitle,
                    arrayOf(firstCustomer.name)
                )
            )
        }
        DashboardUiState.BusyDay -> {
            // Headline counts only what the supporting line counts (overdue + dueToday).
            // Ready orders show up in the READY tile + Today's Work green-stripe rows;
            // they do not inflate the urgency number. resolveUiState guarantees
            // overdue.isNotEmpty() || dueToday.isNotEmpty() here, so urgentCount >= 1.
            val urgentCount = buckets.overdue.size + buckets.dueToday.size
            val firstUrgent = buckets.overdue.firstOrNull()
                ?: buckets.dueToday.firstOrNull()
                ?: buckets.ready.firstOrNull()
            FocusResolution(
                variant = FocusVariant.Focus,
                headline = UiText.StringResourceText(
                    Res.string.focus_busy_title,
                    arrayOf(urgentCount)
                ),
                supporting = UiText.StringResourceText(
                    Res.string.focus_busy_supporting,
                    arrayOf(buckets.overdue.size, buckets.dueToday.size)
                ),
                ctaLabel = firstUrgent?.let {
                    UiText.StringResourceText(
                        Res.string.focus_busy_cta,
                        arrayOf(it.customerName)
                    )
                }
            )
        }
        DashboardUiState.ReadyForPickup -> {
            val firstReady = buckets.ready.first()
            FocusResolution(
                variant = FocusVariant.Pickup,
                headline = UiText.StringResourceText(
                    Res.string.focus_pickup_title,
                    arrayOf(buckets.ready.size)
                ),
                supporting = UiText.StringResourceText(Res.string.focus_pickup_supporting),
                ctaLabel = UiText.StringResourceText(
                    Res.string.focus_pickup_cta,
                    arrayOf(firstReady.customerName)
                )
            )
        }
        DashboardUiState.NbaActive -> {
            val topNba = nextBestActions.first()
            FocusResolution(
                variant = FocusVariant.Earn,
                headline = UiText.StringResourceText(
                    Res.string.focus_earn_title,
                    arrayOf(nextBestActions.size)
                ),
                supporting = UiText.StringResourceText(
                    Res.string.focus_earn_supporting,
                    arrayOf(topNba.customerName)
                ),
                ctaLabel = UiText.StringResourceText(Res.string.focus_earn_cta)
            )
        }
        DashboardUiState.PipelineSteady -> {
            val pipelineTotal = buckets.pipelineInProgressTotal + buckets.pipelinePendingTotal
            FocusResolution(
                variant = FocusVariant.Steady,
                headline = UiText.StringResourceText(Res.string.focus_steady_title),
                supporting = UiText.StringResourceText(
                    Res.string.focus_steady_supporting,
                    arrayOf(pipelineTotal)
                ),
                ctaLabel = UiText.StringResourceText(Res.string.focus_steady_cta)
            )
        }
        DashboardUiState.QuietDay -> {
            val topReconnect = reconnect.firstOrNull()
            FocusResolution(
                variant = FocusVariant.Quiet,
                headline = UiText.StringResourceText(Res.string.focus_quiet_title),
                supporting = UiText.StringResourceText(Res.string.focus_quiet_supporting),
                ctaLabel = if (topReconnect != null) {
                    UiText.StringResourceText(
                        Res.string.focus_quiet_cta,
                        arrayOf(topReconnect.customerName)
                    )
                } else {
                    UiText.StringResourceText(Res.string.focus_quiet_cta_no_candidate)
                }
            )
        }
        DashboardUiState.BrandNew -> FocusResolution(
            variant = FocusVariant.BrandNew,
            headline = UiText.StringResourceText(Res.string.focus_brand_new_title),
            supporting = UiText.StringResourceText(Res.string.focus_brand_new_supporting),
            ctaLabel = UiText.StringResourceText(Res.string.focus_brand_new_cta)
        )
        DashboardUiState.Loading -> FocusResolution(
            // Loading is the transient initial state before data arrives.
            // The screen renders LoadingDots instead of the focus card, so
            // this bundle is a placeholder that is never displayed.
            variant = FocusVariant.Quiet,
            headline = UiText.StringResourceText(Res.string.focus_quiet_title),
            supporting = null,
            ctaLabel = null
        )
    }
}
