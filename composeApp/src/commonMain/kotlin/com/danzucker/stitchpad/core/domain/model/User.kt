package com.danzucker.stitchpad.core.domain.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val businessName: String?,
    val phoneNumber: String?,
    val whatsappNumber: String?,
    val avatarColorIndex: Int,
    /**
     * Welcome bonus AI coins remaining on the user doc. Fast path for UI like
     * PlanCard (Settings) so it can render "X of 30 used" during First Month
     * without hitting `users/{uid}/usage/smart_drafts`. Server-side truth for
     * gating Smart calls is the usage doc's `bonusBalance` — this mirror is
     * for display only.
     *
     * Null for accounts that predate the V1.0 bonus field — UI should fall
     * back to `WELCOME_BONUS_COIN_COUNT` (30) for those.
     */
    val bonusCoins: Int? = null,
    /** Resolved Firebase Storage download URL for the user's brand logo. Null = no logo set. */
    val businessLogoUrl: String? = null,
    /** Firebase Storage path for the logo. Used for deletion and replacement. Null = no logo set. */
    val businessLogoStoragePath: String? = null,
)
