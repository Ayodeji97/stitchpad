package com.danzucker.stitchpad.navigation

import kotlinx.coroutines.flow.MutableStateFlow

enum class DeepLinkTarget { INBOX, UPGRADE, CLAIM_GIFT }

/** Plan to pre-select on the Upgrade screen when arriving via a renewal deep link. */
data class UpgradePreselect(val tier: String?, val cadence: String?)

/** Single-shot holder for an external (push-tap or email-link) navigation target. */
class PendingDeepLinkHolder {
    val target = MutableStateFlow<DeepLinkTarget?>(null)
    private var upgradePreselect: UpgradePreselect? = null
    private var claimGiftCode: String? = null
    private var referralCode: String? = null

    fun set(t: DeepLinkTarget) {
        upgradePreselect = null
        claimGiftCode = null
        target.value = t
    }

    /** UPGRADE target that also carries the plan to pre-select (from the email deep link). */
    fun setUpgrade(tier: String?, cadence: String?) {
        upgradePreselect = UpgradePreselect(tier, cadence)
        claimGiftCode = null
        target.value = DeepLinkTarget.UPGRADE
    }

    /** CLAIM_GIFT target carrying the bearer code from the gift email's claim link. */
    fun setClaimGift(code: String) {
        upgradePreselect = null
        claimGiftCode = code
        target.value = DeepLinkTarget.CLAIM_GIFT
    }

    /**
     * Stashes a captured referral code (from the Play Install Referrer or a /r/ App
     * Link). Unlike the others this does NOT set a navigation target — referral
     * attribution is a silent, background submit, not a screen the user lands on.
     */
    fun setReferralCode(code: String) {
        referralCode = code
    }

    /** Clears the navigation target only; a pending pre-select/code survives until consumed. */
    fun clear() {
        target.value = null
    }

    /** One-shot read of the UPGRADE pre-select, consumed by UpgradeViewModel on init. */
    fun consumeUpgradePreselect(): UpgradePreselect? {
        val p = upgradePreselect
        upgradePreselect = null
        return p
    }

    /** One-shot read of the claim code, consumed by RedeemGiftViewModel on init. */
    fun consumeClaimGiftCode(): String? {
        val c = claimGiftCode
        claimGiftCode = null
        return c
    }

    /** One-shot read of the referral code, consumed by ReferralAttributionCoordinator. */
    fun consumeReferralCode(): String? {
        val c = referralCode
        referralCode = null
        return c
    }
}
