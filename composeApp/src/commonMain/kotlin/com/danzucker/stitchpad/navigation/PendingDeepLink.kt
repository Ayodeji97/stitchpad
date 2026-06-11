package com.danzucker.stitchpad.navigation

import kotlinx.coroutines.flow.MutableStateFlow

enum class DeepLinkTarget { INBOX, UPGRADE }

/** Plan to pre-select on the Upgrade screen when arriving via a renewal deep link. */
data class UpgradePreselect(val tier: String?, val cadence: String?)

/** Single-shot holder for an external (push-tap or email-link) navigation target. */
class PendingDeepLinkHolder {
    val target = MutableStateFlow<DeepLinkTarget?>(null)
    private var upgradePreselect: UpgradePreselect? = null

    fun set(t: DeepLinkTarget) {
        upgradePreselect = null
        target.value = t
    }

    /** UPGRADE target that also carries the plan to pre-select (from the email deep link). */
    fun setUpgrade(tier: String?, cadence: String?) {
        upgradePreselect = UpgradePreselect(tier, cadence)
        target.value = DeepLinkTarget.UPGRADE
    }

    /** Clears the navigation target only; a pending pre-select survives until consumed. */
    fun clear() {
        target.value = null
    }

    /** One-shot read of the UPGRADE pre-select, consumed by UpgradeViewModel on init. */
    fun consumeUpgradePreselect(): UpgradePreselect? {
        val p = upgradePreselect
        upgradePreselect = null
        return p
    }
}
