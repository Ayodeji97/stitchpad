package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.presentation.UiText

sealed interface TeamEvent {
    data object NavigateBack : TeamEvent
    data class CopyToClipboard(val text: String) : TeamEvent
    data class ShareInviteLink(val url: String) : TeamEvent
    data class ShowSnackbar(val text: UiText) : TeamEvent

    /**
     * Deep-links to the Orders list pre-filtered by assignee (Task 9). [initialFilter] is
     * an [com.danzucker.stitchpad.feature.order.presentation.list.OrderListFilter] value —
     * `assignee(memberId)` or `ASSIGNEE_UNASSIGNED` — passed straight through to
     * `OrderListRoute(initialFilter = ...)`, mirroring the dashboard's
     * `DashboardEvent.NavigateToOrders` wiring (Phase 2a Task 8).
     */
    data class NavigateToMemberOrders(val initialFilter: String) : TeamEvent
}
