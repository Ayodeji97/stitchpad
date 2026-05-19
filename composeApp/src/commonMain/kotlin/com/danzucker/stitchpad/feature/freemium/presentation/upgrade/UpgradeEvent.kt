package com.danzucker.stitchpad.feature.freemium.presentation.upgrade

sealed interface UpgradeEvent {
    data class OpenExternalBrowser(val url: String) : UpgradeEvent

    /**
     * Emitted when the observed tier rises (e.g. Paystack webhook completes
     * and the user-doc tier transitions Free → Pro) while the Upgrade screen
     * is open. The screen pops back so the user lands on the now-upgraded
     * Settings/PlanCard instead of staring at the upgrade picker.
     */
    data object UpgradeDetected : UpgradeEvent
}
