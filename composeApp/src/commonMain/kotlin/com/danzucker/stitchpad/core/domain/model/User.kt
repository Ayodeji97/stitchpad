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
    /**
     * Bank details surfaced on Invoice and Deposit Receipt documents so customers
     * can pay via direct transfer. The three fields are a logical group — they are
     * either all set or all null. Validation in the form layer enforces this.
     */
    val bankName: String? = null,
    val bankAccountName: String? = null,
    val bankAccountNumber: String? = null,
    /**
     * True once the tailor passed the "Confirm on WhatsApp" round-trip for the
     * current [whatsappNumber]. Proves WhatsApp *reachability*, not ownership.
     * Resets to false whenever the number is edited.
     */
    val whatsappConfirmed: Boolean = false,
    /**
     * Whether the tailor receives the daily operational email digest (orders
     * due soon / overdue / outstanding balance). Opt-out: true by default, so a
     * legacy doc with the field absent still gets the digest. Flipped from
     * Settings → Notifications.
     */
    val dailyDigestEmailEnabled: Boolean = true,
    /** Whether the tailor receives the daily push reminder. Opt-out: true by default. */
    val dailyPushEnabled: Boolean = true,
    /**
     * Whether the tailor receives tips & announcements push (the twice-weekly
     * engagement nudge). Separate from [dailyPushEnabled] on purpose: muting tips
     * must not also mute deadline and money-owed reminders. Opt-out: true by default.
     */
    val announcementsPushEnabled: Boolean = true,
    /** The tailor's own outbound referral code for the Founding Tailors program.
     *  Null until first minted server-side via getOrCreateMyReferralLink. */
    val referralCode: String? = null,
    /** The tailor's market. Drives phone parsing, currency and default time zone. Legacy docs are NG. */
    val country: Country = Country.DEFAULT,
    /** Currency for money display. Always the [country]'s currency today; stored so it can diverge later. */
    val currency: Currency = Country.DEFAULT.currency,
    /** IANA time zone id (for example `Africa/Lagos`) used for due dates and reminders. */
    val timeZone: String = Country.DEFAULT.defaultTimeZone,
    /** Optional BCP-47 locale tag; null means use the [country]'s default language. */
    val locale: String? = null,
)
