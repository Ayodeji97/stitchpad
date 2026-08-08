package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.presentation.UiText

data class OrderListState(
    val orders: List<Order> = emptyList(),
    val statusFilter: OrderStatus? = null,
    val showProfit: Boolean = false,
    /**
     * True when the signed-in user is an approved staff member (Slice 6c). Staff
     * may view garment / status / dates / customer name but never money, so every
     * price / payment-status / profit affordance on the list is hidden.
     */
    val isActiveStaff: Boolean = false,
    /**
     * The signed-in user's Firebase Auth uid (Task 8), captured from the same
     * [com.danzucker.stitchpad.core.domain.session.WorkshopSession] collection that sets
     * [isActiveStaff]. Used to match [Order.assignedMemberId] for the "My work" filter —
     * distinct from `workshopUid`, which staff never own.
     */
    val staffAuthUid: String? = null,
    /**
     * When true (staff only), [orders] is narrowed to orders assigned to [staffAuthUid].
     * Applies to the ACTIVE list only — the Archived view ignores this flag entirely.
     */
    val myWorkOnly: Boolean = false,
    /** When true, [orders] holds archived orders (Restore affordance) instead of the active list. */
    val showArchived: Boolean = false,
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
