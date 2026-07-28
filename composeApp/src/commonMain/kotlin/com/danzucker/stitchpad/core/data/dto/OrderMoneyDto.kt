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
 * During the dual-write window (Slice 2) the base [OrderDto] still carries these
 * same money fields for backward compatibility with older app versions; the
 * owner app keeps reading them from the base doc. This sub-doc is written in
 * parallel so a later slice can flip reads here and strip the base fields once a
 * minimum-app-version floor is in place.
 *
 * [itemPrices] relocates the per-item `price` that today lives inside the base
 * doc's `items[]`, keyed by order-item id, so the base items can eventually drop
 * `price` without losing it.
 */
@Serializable
data class OrderMoneyDto(
    val totalPrice: Double = 0.0,
    val discount: Double = 0.0,
    val discountReason: String? = null,
    val payments: List<PaymentDto> = emptyList(),
    val costs: List<OrderCostDto> = emptyList(),
    val itemPrices: Map<String, Double> = emptyMap(),
)
