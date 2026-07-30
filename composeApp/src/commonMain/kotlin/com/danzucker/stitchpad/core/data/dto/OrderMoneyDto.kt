package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape of the owner-only money sub-document at
 * `users/{uid}/orders/{oid}/private/money`.
 *
 * Part of the Owner + Staff feature: all commercially sensitive money on an
 * order lives here so Firestore rules can deny staff read access to it while
 * still letting them read the base order doc (garment, status, measurements).
 *
 * During the dual-write window the base [OrderDto] still carries these same money
 * fields for backward compatibility with older app versions. Slice 8a flipped the
 * owner's read onto this sub-doc (see [ownerId]); the base fields stay until a later
 * slice strips them once a minimum-app-version floor is in place.
 *
 * [itemPrices] relocates the per-item `price` that today lives inside the base
 * doc's `items[]`, keyed by order-item id, so the base items can eventually drop
 * `price` without losing it.
 */
@Serializable
data class OrderMoneyDto(
    // Slice 8a: [ownerId] is the workshop owner's uid. It lets the owner read
    // money for the whole list in one `collectionGroup("private")` query filtered
    // by `ownerId == uid` (a nested-path rule can't authorize a collection-group
    // query). [orderId] carries the parent order id so those results can be joined
    // back onto each order without walking the document's parent chain.
    val ownerId: String = "",
    val orderId: String = "",
    val totalPrice: Double = 0.0,
    val discount: Double = 0.0,
    val discountReason: String? = null,
    val payments: List<PaymentDto> = emptyList(),
    val costs: List<OrderCostDto> = emptyList(),
    val itemPrices: Map<String, Double> = emptyMap(),
)
