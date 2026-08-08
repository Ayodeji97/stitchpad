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
 * tiles today) and by [OrderListViewModel]'s seeding of [OrderListState] on cold entry.
 * Only [IN_PROGRESS] and [MY_WORK] are currently consumed; [OVERDUE]/[DUE_TODAY] are
 * reserved for a future deadline filter that does not exist yet in [OrderListState].
 */
object OrderListFilter {
    const val OVERDUE = "overdue"
    const val DUE_TODAY = "due-today"
    const val IN_PROGRESS = "in-progress"
    const val MY_WORK = "my-work"
}
