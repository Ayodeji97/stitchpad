package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Order

/**
 * Display label for the Task 8 assignee filter chip ("Assigned to <name>"), a sibling
 * of [allChipSelected] in OrderChipSelection.kt — kept pure so it's testable without
 * spinning up the ViewModel.
 *
 * [assigneeFilter] is a member id (matches [Order.assignedMemberId]) or
 * [OrderListFilter.ASSIGNEE_NONE_ID] for "unassigned", which has no name to look up —
 * that case returns null so the Screen falls back to a fixed
 * `Res.string.order_filter_unassigned` string instead of formatting one. Otherwise,
 * falls back to the raw [assigneeFilter] id itself when no order in [orders] has a
 * matching [Order.assignedMemberId] yet (e.g. right after the deep link lands and the
 * first Firestore snapshot hasn't arrived, or the matching row never got a
 * denormalized name) — so the chip never renders a blank label.
 */
internal fun assigneeFilterLabelName(orders: List<Order>, assigneeFilter: String): String? =
    if (assigneeFilter == OrderListFilter.ASSIGNEE_NONE_ID) {
        null
    } else {
        orders.firstOrNull { it.assignedMemberId == assigneeFilter }?.assignedMemberName ?: assigneeFilter
    }
