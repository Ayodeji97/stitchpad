package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * The four intent types supported by the V1 Draft Message feature. Each
 * maps to a specific intent label in the server-side prompt builder.
 */
enum class DraftIntent(val wireName: String) {
    BalanceReminder("balance_reminder"),
    PickupReady("pickup_ready"),
    FollowUp("follow_up"),
    CustomNote("custom_note"),
}
