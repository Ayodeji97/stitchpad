package com.danzucker.stitchpad.core.domain.model

/**
 * Slot state on a customer doc. ACTIVE customers are visible + fully
 * usable; LOCKED customers are visible (grayed) but read-only,
 * with an Upgrade or Swap CTA.
 *
 * Default for new and existing customers is ACTIVE — the server
 * reconciliation function (Task 7) flips customers to LOCKED only
 * when the user is over their effective cap.
 */
enum class CustomerSlotState(val wireValue: String) {
    ACTIVE("active"),
    LOCKED("locked");

    companion object {
        fun fromWire(value: String?): CustomerSlotState = when (value?.lowercase()) {
            "locked" -> LOCKED
            else -> ACTIVE
        }
    }
}
