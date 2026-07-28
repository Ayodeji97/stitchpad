package com.danzucker.stitchpad.core.domain.session

/**
 * Lifecycle of a staff member's membership in a workshop.
 *
 * PENDING — redeemed an invite, awaiting owner approval (no data access yet).
 * ACTIVE  — approved; may read the owner's tree per the staff rules.
 * REVOKED — owner removed them; treated as no longer staff (reverts to owner-of-self).
 */
enum class MembershipStatus {
    PENDING,
    ACTIVE,
    REVOKED,
    ;

    companion object {
        /**
         * Parses the backend wire value ('pending'/'active'/'revoked', set by the
         * staff Cloud Functions). Unknown or missing → null (no membership), which
         * the resolver treats as owner-of-self.
         */
        fun fromWire(value: String?): MembershipStatus? = when (value?.lowercase()) {
            "pending" -> PENDING
            "active" -> ACTIVE
            "revoked" -> REVOKED
            else -> null
        }
    }
}
