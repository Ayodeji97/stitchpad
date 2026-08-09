package com.danzucker.stitchpad.feature.order.presentation.list

sealed interface OrderListEvent {
    data object NavigateToOrderForm : OrderListEvent
    data object NavigateToAddCustomerFirst : OrderListEvent
    data class NavigateToOrderDetail(val orderId: String) : OrderListEvent
    data object OrderRestored : OrderListEvent
}

/**
 * String values accepted by [com.danzucker.stitchpad.navigation.OrderListRoute.initialFilter] —
 * shared by every caller that navigates here with a filter (the dashboard's staff count
 * tiles today, and Task 9's Team workload rows for [ASSIGNEE_PREFIX]) and by
 * [OrderListViewModel]'s seeding of [OrderListState] on cold entry. [IN_PROGRESS],
 * [MY_WORK], and any `"$ASSIGNEE_PREFIX<memberId>"` / [ASSIGNEE_UNASSIGNED] value are
 * consumed; [OVERDUE]/[DUE_TODAY] are reserved for a future deadline filter that does
 * not exist yet in [OrderListState].
 */
object OrderListFilter {
    const val OVERDUE = "overdue"
    const val DUE_TODAY = "due-today"
    const val IN_PROGRESS = "in-progress"
    const val MY_WORK = "my-work"

    /** Prefix for an `assignee:<memberId>` deep link (Task 8) — see [assignee]. */
    const val ASSIGNEE_PREFIX = "assignee:"

    /**
     * The value [ASSIGNEE_PREFIX] is stripped down to for the "unassigned" bucket —
     * shared by [OrderListViewModel]'s seeding logic, `filterAndSort`'s assignee
     * branch, and [assigneeFilterLabelName], so the sentinel lives in one place.
     */
    const val ASSIGNEE_NONE_ID = "none"

    /** Deep-links into the orders assigned to nobody. */
    const val ASSIGNEE_UNASSIGNED = "assignee:none"

    /** Deep-links into the orders assigned to [memberId]. */
    fun assignee(memberId: String): String = "$ASSIGNEE_PREFIX$memberId"
}
