package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * UI bundle for the "Your customer" card shown on the FirstCustomer state.
 *
 * Surfaces the most recently added customer with quick affordances: open
 * detail (whole card tap) and message via WhatsApp (round button). Only
 * populated by the ViewModel when [DashboardUiState.FirstCustomer] is
 * resolved — null otherwise.
 *
 * @param daysSinceAdded 0 means "Added today"; positive values render as
 *   "Added N days ago".
 * @param hasOrders Always false on FirstCustomer (the "No orders yet"
 *   badge depends on this). Kept as a field so the same model can be
 *   reused in later states without renaming.
 */
data class CustomerReadyUi(
    val customerId: String,
    val name: String,
    val phone: String,
    val daysSinceAdded: Int,
    val hasOrders: Boolean,
)
