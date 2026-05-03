package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.dashboard.domain.model.Buckets
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusResolution
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_collect_deposit
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_send_reminder
import stitchpad.composeapp.generated.resources.dashboard_nba_cta_view_order
import stitchpad.composeapp.generated.resources.focus_brand_new_cta
import stitchpad.composeapp.generated.resources.focus_brand_new_supporting
import stitchpad.composeapp.generated.resources.focus_brand_new_title
import stitchpad.composeapp.generated.resources.focus_busy_cta
import stitchpad.composeapp.generated.resources.focus_busy_section_label
import stitchpad.composeapp.generated.resources.focus_busy_supporting
import stitchpad.composeapp.generated.resources.focus_busy_supporting_due_today_only
import stitchpad.composeapp.generated.resources.focus_busy_supporting_due_today_only_one
import stitchpad.composeapp.generated.resources.focus_busy_supporting_overdue_only
import stitchpad.composeapp.generated.resources.focus_busy_supporting_overdue_only_one
import stitchpad.composeapp.generated.resources.focus_busy_title
import stitchpad.composeapp.generated.resources.focus_busy_title_one
import stitchpad.composeapp.generated.resources.focus_earn_section_label
import stitchpad.composeapp.generated.resources.focus_earn_supporting
import stitchpad.composeapp.generated.resources.focus_earn_title
import stitchpad.composeapp.generated.resources.focus_earn_title_one
import stitchpad.composeapp.generated.resources.focus_first_order_cta
import stitchpad.composeapp.generated.resources.focus_first_order_cta_subtitle
import stitchpad.composeapp.generated.resources.focus_first_order_supporting
import stitchpad.composeapp.generated.resources.focus_first_order_supporting_multi
import stitchpad.composeapp.generated.resources.focus_first_order_title
import stitchpad.composeapp.generated.resources.focus_pickup_cta
import stitchpad.composeapp.generated.resources.focus_pickup_section_label
import stitchpad.composeapp.generated.resources.focus_pickup_supporting
import stitchpad.composeapp.generated.resources.focus_pickup_title
import stitchpad.composeapp.generated.resources.focus_pickup_title_one
import stitchpad.composeapp.generated.resources.focus_quiet_cta
import stitchpad.composeapp.generated.resources.focus_quiet_cta_no_candidate
import stitchpad.composeapp.generated.resources.focus_quiet_section_label
import stitchpad.composeapp.generated.resources.focus_quiet_supporting
import stitchpad.composeapp.generated.resources.focus_quiet_title
import stitchpad.composeapp.generated.resources.focus_setup_order_cta
import stitchpad.composeapp.generated.resources.focus_setup_order_supporting
import stitchpad.composeapp.generated.resources.focus_setup_order_title
import stitchpad.composeapp.generated.resources.focus_steady_cta
import stitchpad.composeapp.generated.resources.focus_steady_section_label
import stitchpad.composeapp.generated.resources.focus_steady_supporting
import stitchpad.composeapp.generated.resources.focus_steady_supporting_one
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
        // Stay on FirstCustomer while the user is still completing the
        // first-order setup. Once a second order exists, the user has
        // moved past onboarding even if the original is still incomplete.
        if (orders.size == 1 && orders.first().deadline == null) {
            return DashboardUiState.FirstCustomer
        }
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
    // Sealed-type dispatch over every DashboardUiState. Splitting each branch
    // into its own helper would scatter related copy/CTA logic across eight
    // files for no clarity gain — the single when keeps the priority order
    // visible at a glance.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun resolveFocus(
        uiState: DashboardUiState,
        buckets: Buckets,
        nextBestActions: List<NextBestAction>,
        customers: List<Customer>,
        reconnect: List<ReconnectCandidate>,
        // Default empty so legacy tests that don't yet exercise the
        // setup-order sub-case keep working unchanged. Production callers
        // always pass the real list.
        orders: List<Order> = emptyList(),
    ): FocusResolution = when (uiState) {
        DashboardUiState.FirstCustomer -> {
            // Two sub-cases:
            //  - No order yet → "Time for your first order"
            //  - One order created without a due date → "Finish setting up
            //    [customer]'s order" (resolveUiState keeps us in FirstCustomer
            //    while this is true so the setup checklist stays visible).
            val incompleteOrder = orders.singleOrNull()?.takeIf { it.deadline == null }
            if (incompleteOrder != null) {
                val firstName = incompleteOrder.customerName
                    .trim().substringBefore(' ').trim()
                    .ifEmpty { incompleteOrder.customerName }
                FocusResolution(
                    variant = FocusVariant.FirstOrder,
                    headline = UiText.StringResourceText(
                        Res.string.focus_setup_order_title,
                        arrayOf(firstName)
                    ),
                    supporting = UiText.StringResourceText(
                        Res.string.focus_setup_order_supporting
                    ),
                    ctaLabel = UiText.StringResourceText(Res.string.focus_setup_order_cta),
                    ctaSubtitle = null
                )
            } else {
                // With one customer the hero is personalised — name in the
                // supporting line and a "for {name}" subtitle under the CTA.
                // With two or more, locking the hero onto a single customer
                // makes the user feel forced to start with that one — drop
                // the name and let them pick on the next screen.
                val onlyCustomer = customers.singleOrNull()
                FocusResolution(
                    variant = FocusVariant.FirstOrder,
                    headline = UiText.StringResourceText(Res.string.focus_first_order_title),
                    supporting = if (onlyCustomer != null) {
                        UiText.StringResourceText(
                            Res.string.focus_first_order_supporting,
                            arrayOf(onlyCustomer.name)
                        )
                    } else {
                        UiText.StringResourceText(Res.string.focus_first_order_supporting_multi)
                    },
                    ctaLabel = UiText.StringResourceText(Res.string.focus_first_order_cta),
                    ctaSubtitle = if (onlyCustomer != null) {
                        UiText.StringResourceText(
                            Res.string.focus_first_order_cta_subtitle,
                            arrayOf(onlyCustomer.name)
                        )
                    } else {
                        null
                    }
                )
            }
        }
        DashboardUiState.BusyDay -> {
            // Headline counts only what the supporting line counts (overdue + dueToday).
            // Ready orders show up in the READY tile + Today's Work green-stripe rows;
            // they do not inflate the urgency number. resolveUiState guarantees
            // overdue.isNotEmpty() || dueToday.isNotEmpty() here, so urgentCount >= 1.
            val overdueCount = buckets.overdue.size
            val dueTodayCount = buckets.dueToday.size
            val urgentCount = overdueCount + dueTodayCount
            val firstUrgent = buckets.overdue.firstOrNull()
                ?: buckets.dueToday.firstOrNull()
                ?: buckets.ready.firstOrNull()
            // Title: singular form when urgentCount == 1, plural otherwise.
            val titleRes = if (urgentCount == 1) {
                Res.string.focus_busy_title_one
            } else {
                Res.string.focus_busy_title
            }
            // Supporting line: collapse the "0 ·" half when only one bucket
            // is non-empty so the user doesn't read padding zeros.
            val supportingText = when {
                overdueCount > 0 && dueTodayCount == 0 -> UiText.StringResourceText(
                    if (overdueCount == 1) {
                        Res.string.focus_busy_supporting_overdue_only_one
                    } else {
                        Res.string.focus_busy_supporting_overdue_only
                    },
                    arrayOf(overdueCount)
                )
                dueTodayCount > 0 && overdueCount == 0 -> UiText.StringResourceText(
                    if (dueTodayCount == 1) {
                        Res.string.focus_busy_supporting_due_today_only_one
                    } else {
                        Res.string.focus_busy_supporting_due_today_only
                    },
                    arrayOf(dueTodayCount)
                )
                else -> UiText.StringResourceText(
                    Res.string.focus_busy_supporting,
                    arrayOf(overdueCount, dueTodayCount)
                )
            }
            FocusResolution(
                variant = FocusVariant.Focus,
                sectionLabel = UiText.StringResourceText(Res.string.focus_busy_section_label),
                headline = if (urgentCount == 1) {
                    UiText.StringResourceText(titleRes)
                } else {
                    UiText.StringResourceText(titleRes, arrayOf(urgentCount))
                },
                supporting = supportingText,
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
            val readyCount = buckets.ready.size
            FocusResolution(
                variant = FocusVariant.Pickup,
                sectionLabel = UiText.StringResourceText(Res.string.focus_pickup_section_label),
                headline = if (readyCount == 1) {
                    UiText.StringResourceText(Res.string.focus_pickup_title_one)
                } else {
                    UiText.StringResourceText(
                        Res.string.focus_pickup_title,
                        arrayOf(readyCount)
                    )
                },
                supporting = UiText.StringResourceText(Res.string.focus_pickup_supporting),
                ctaLabel = UiText.StringResourceText(
                    Res.string.focus_pickup_cta,
                    arrayOf(firstReady.customerName)
                )
            )
        }
        DashboardUiState.NbaActive -> {
            val topNba = nextBestActions.first()
            val titleRes = if (nextBestActions.size == 1) {
                Res.string.focus_earn_title_one
            } else {
                Res.string.focus_earn_title
            }
            // CTA mirrors the action surfaced on the top NBA card so the hero
            // tells the user what they'll do, not just that there's something to do.
            val ctaRes = when (topNba.type) {
                NextBestActionType.CollectOverdue,
                NextBestActionType.CollectOnReady -> Res.string.dashboard_nba_cta_send_reminder
                NextBestActionType.CollectDeposit -> Res.string.dashboard_nba_cta_collect_deposit
                NextBestActionType.FinishStale,
                NextBestActionType.DeliverStale,
                NextBestActionType.StartSoon -> Res.string.dashboard_nba_cta_view_order
            }
            FocusResolution(
                variant = FocusVariant.Earn,
                sectionLabel = UiText.StringResourceText(Res.string.focus_earn_section_label),
                headline = UiText.StringResourceText(
                    titleRes,
                    arrayOf(nextBestActions.size)
                ),
                supporting = UiText.StringResourceText(
                    Res.string.focus_earn_supporting,
                    arrayOf(topNba.customerName)
                ),
                ctaLabel = UiText.StringResourceText(ctaRes)
            )
        }
        DashboardUiState.PipelineSteady -> {
            val pipelineTotal = buckets.pipelineInProgressTotal + buckets.pipelinePendingTotal
            val supportingRes = if (pipelineTotal == 1) {
                Res.string.focus_steady_supporting_one
            } else {
                Res.string.focus_steady_supporting
            }
            FocusResolution(
                variant = FocusVariant.Steady,
                headline = UiText.StringResourceText(Res.string.focus_steady_title),
                supporting = UiText.StringResourceText(
                    supportingRes,
                    arrayOf(pipelineTotal)
                ),
                ctaLabel = UiText.StringResourceText(Res.string.focus_steady_cta),
                sectionLabel = UiText.StringResourceText(Res.string.focus_steady_section_label)
            )
        }
        DashboardUiState.QuietDay -> {
            val topReconnect = reconnect.firstOrNull()
            FocusResolution(
                variant = FocusVariant.Quiet,
                sectionLabel = UiText.StringResourceText(Res.string.focus_quiet_section_label),
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
