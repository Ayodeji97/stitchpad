package com.danzucker.stitchpad.feature.settings.presentation.home

import com.danzucker.stitchpad.core.config.domain.isUsableCommunityInviteUrl
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.preferences.ReceiptImageStyle
import com.danzucker.stitchpad.core.domain.preferences.ThemePreference
import com.danzucker.stitchpad.feature.auth.domain.SignInProvider
import com.danzucker.stitchpad.feature.settings.domain.SubscriptionStatus
import org.jetbrains.compose.resources.StringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.settings_hero_plan_atelier
import stitchpad.composeapp.generated.resources.settings_hero_plan_pro

data class SettingsState(
    val isLoading: Boolean = true,
    val businessName: String = "",
    val businessLogoUrl: String? = null,
    val email: String = "",
    val whatsappNumber: String? = null,
    val avatarColorIndex: Int = 0,
    val signInProvider: SignInProvider = SignInProvider.UNKNOWN,
    val maskedSignInIdentifier: String = "",
    val subscriptionTier: SubscriptionTier = SubscriptionTier.FREE,
    val subscriptionStatus: SubscriptionStatus? = null,
    val customerCount: Int = 0,
    /** null means unlimited (Pro / Atelier tier). */
    val customerLimit: Int? = null,
    /**
     * AI drafts the user has consumed this month against their tier-derived monthly limit.
     * Defaults to 0 when the [InMemorySmartUsageStore] cache hasn't been hydrated yet —
     * see the chip-staleness quirk in the V1.0 design spec; the conversion-critical signal
     * is the server's authoritative `free_tier_exhausted` error, not this number.
     */
    val aiDraftsUsed: Int = 0,
    /** null means unlimited (Atelier). 5 for Free, 50 for Pro. */
    val aiDraftLimit: Int? = 5,
    /**
     * True only while the user is inside their First Month window. Drives PlanCard to
     * show AI usage as the headline + "unlimited customers" framing (per V1.0 spec).
     */
    val isFirstMonth: Boolean = false,
    /** Calendar days remaining in the First Month window, null when not in it. */
    val welcomeDaysLeft: Int? = null,
    val measurementUnit: MeasurementUnit = MeasurementUnit.INCHES,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val receiptImageStyle: ReceiptImageStyle = ReceiptImageStyle.LIGHT,
    val dailyDigestEmailEnabled: Boolean = true,
    val dailyPushEnabled: Boolean = true,
    val pushReminderSupported: Boolean = true,
    val showSignOutDialog: Boolean = false,
    val isSigningOut: Boolean = false,
    val communityEnabled: Boolean = false,
    val communityUrl: String? = null,
    val settingsHubEnabled: Boolean = false,
    // Slice 7: true for an owner session; drives the owner-only "Team" row.
    val isOwner: Boolean = false,
    /**
     * True only for an approved staff member operating inside an owner's workshop.
     * Drives the reduced Settings surface (owner-only entries hidden) and the
     * staff-only "Leave this workshop" action.
     */
    val isActiveStaff: Boolean = false,
    val showLeaveWorkshopDialog: Boolean = false,
    val isLeavingWorkshop: Boolean = false,
    /**
     * Version label for the footer, e.g. "1.2.0 (595)". The build number makes
     * support screenshots unambiguous: re-cut tester builds share a versionName
     * and differ only in build number. Falls back to the bare version when the
     * build number can't be read (the platform build number is nullable).
     */
    val appVersionLabel: String = "",
) {

    /** Settings community row shows only when remotely enabled with a usable link. */
    val showCommunityRow: Boolean
        get() = communityEnabled && isUsableCommunityInviteUrl(communityUrl)

    /**
     * Combined display string for the profile hero subtitle.
     * The separator uses a Unicode escape (U+2022 BULLET) rather than a literal
     * glyph so any downstream encoding mismatch can't mangle it to mojibake.
     */
    val heroSubtitle: String
        get() = listOfNotNull(
            whatsappNumber?.takeIf { it.isNotBlank() },
            email.takeIf { it.isNotBlank() },
        ).joinToString(separator = " • ")

    /** Change password row only renders for email/password providers. */
    val showChangePasswordRow: Boolean get() = signInProvider == SignInProvider.EMAIL_PASSWORD

    /**
     * Change-email row is hidden for SSO users — their email is owned by the
     * identity provider (Google/Apple), Firebase rejects
     * `verifyBeforeUpdateEmail` for federated accounts, and the Sign-in method
     * row already shows the same email above.
     */
    val showChangeEmailRow: Boolean get() = signInProvider == SignInProvider.EMAIL_PASSWORD

    /**
     * Pro/Atelier badge label for the profile hero. Null for FREE — keeps the
     * hero clean while paid tiers get a visible cue. Derived from the real
     * subscriptionTier sourced from EntitlementsProvider. (Historical note:
     * an earlier in-memory entitlement stub hardcoded `isPremium = false` and
     * caused this badge to stay empty even for real Pro users — fixed by
     * routing everything through EntitlementsProvider.)
     */
    val proBadgeLabel: StringResource?
        get() = when (subscriptionTier) {
            SubscriptionTier.PRO -> Res.string.settings_hero_plan_pro
            SubscriptionTier.ATELIER -> Res.string.settings_hero_plan_atelier
            SubscriptionTier.FREE -> null
        }
}
