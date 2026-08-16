package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.presentation.UiText

data class OrderListState(
    val orders: List<Order> = emptyList(),
    val statusFilter: OrderStatus? = null,
    /**
     * When true, every naira on the list is hidden — price, payment status figures,
     * and profit — regardless of whether an order has recorded costs. Default false
     * shows everything (including profit-when-costed): the eye toggle's job is
     * privacy from bystanders, not opt-in margin reveal.
     */
    val hideAmounts: Boolean = false,
    /**
     * True when the signed-in user is an approved staff member (Slice 6c). Staff
     * may view garment / status / dates / customer name but never money, so every
     * price / payment-status / profit affordance on the list is hidden.
     */
    val isActiveStaff: Boolean = false,
    /**
     * The signed-in user's Firebase Auth uid (Task 8; Task 7 dropped the staff-only
     * gate), captured from the same
     * [com.danzucker.stitchpad.core.domain.session.WorkshopSession] collection that sets
     * [isActiveStaff] — populated for BOTH an owner and a staff session, unlike
     * [com.danzucker.stitchpad.feature.order.presentation.detail.OrderDetailState.staffAuthUid],
     * which is a separate, staff-only contract. Used to match [Order.assignedMemberId] for
     * the "My work" filter — distinct from `workshopUid`, which staff never own.
     */
    val sessionAuthUid: String? = null,
    /**
     * When true, [orders] is narrowed to orders assigned to [sessionAuthUid] (owner or
     * staff — Task 7). Applies to the ACTIVE list only — the Archived view ignores this
     * flag entirely.
     */
    val myWorkOnly: Boolean = false,
    /** When true, [orders] holds archived orders (Restore affordance) instead of the active list. */
    val showArchived: Boolean = false,
    /**
     * A member id (matches [Order.assignedMemberId]), or [OrderListFilter.ASSIGNEE_NONE_ID]
     * for "unassigned", seeded from an `assignee:` deep link (Task 8 — Task 9 navigates here
     * from a Team screen workload row) or set to null by [OrderListAction.OnClearAssigneeFilter].
     * Like [myWorkOnly], applies to the ACTIVE list only — the Archived view ignores it.
     */
    val assigneeFilter: String? = null,
    /**
     * Display label for the assignee chip when [assigneeFilter] is an id (not "unassigned",
     * which the Screen renders via a fixed string instead). Recomputed in the VM via the pure
     * [assigneeFilterLabelName] helper whenever [assigneeFilter] is active — kept in state,
     * not derived in the Screen, because the Screen never sees the unfiltered order list.
     */
    val assigneeFilterName: String? = null,
    val isLoading: Boolean = true,
    /**
     * Whether the archived snapshot is still loading. Tracked separately from
     * [isLoading] (which the active stream owns) so the Archived view shows a
     * spinner — not the empty state — until archived orders have actually loaded.
     */
    val isArchivedLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val orderToDelete: Order? = null,
    val errorMessage: UiText? = null
)
