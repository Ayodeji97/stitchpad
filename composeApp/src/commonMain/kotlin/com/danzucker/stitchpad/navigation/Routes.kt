package com.danzucker.stitchpad.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object OnboardingRoute

/**
 * Logged-out video landing — the single unauthenticated entry point. Reached after
 * the onboarding slides on a fresh install, and directly from Splash / on sign-out
 * for returning users. [LoginRoute] is now only reached from here ("Sign in"), the
 * Sign Up "Log in" link, Forgot Password, and the debug menu.
 */
@Serializable
data object WelcomeRoute

@Serializable
data object LoginRoute

@Serializable
data object SignUpRoute

@Serializable
data object ForgotPasswordRoute

@Serializable
data object EmailVerificationRoute

@Serializable
data object WorkshopSetupRoute

/**
 * Staff onboarding — a signed-in user redeems an owner's invite code to join a
 * workshop. Reached from a JOIN_WORKSHOP deep link (code prefilled) or the "join
 * instead" fork on [WorkshopSetupRoute]. On success it routes to [StaffPendingRoute].
 * [declined] is true when the user landed here because the owner declined/removed
 * a prior pending request, so the screen can explain why they're back.
 */
@Serializable
data class RedeemInviteRoute(val declined: Boolean = false)

/**
 * Staff onboarding — the waiting screen after redeeming, until the owner approves.
 * Auto-advances to [HomeRoute] the moment the approval claim lands. [workshopName]
 * is passed from the redeem step; null on a cold start straight into the pending state.
 * [fromRedeem] is true only when reached directly from a successful redeem — the
 * screen then waits for the session provider to reflect the join before it can treat
 * a (briefly stale) owner-of-self session as a decline.
 */
@Serializable
data class StaffPendingRoute(val workshopName: String? = null, val fromRedeem: Boolean = false)

/**
 * Owner-side team management (Slice 7) — invite a teammate, approve/decline pending
 * requests, and revoke active staff. Reached from Settings, owner-only.
 */
@Serializable
data object TeamRoute

@Serializable
data object HomeRoute

@Serializable
data object CustomerListRoute

@Serializable
data class CustomerFormRoute(val customerId: String? = null)

@Serializable
data class CustomerDetailRoute(val customerId: String)

@Serializable
data class MeasurementFormRoute(
    val customerId: String,
    val measurementId: String? = null,
    val linkToOrderId: String? = null,
    val fromCustomerCreation: Boolean = false,
    /** Launched from the detail screen's empty-state CTA — post-save must pop that detail entry too. */
    val fromEmptyDetail: Boolean = false,
)

@Serializable
data class MeasurementDetailRoute(
    val customerId: String,
    /** null = empty mode: the customer is confirmed to have zero measurements; the screen shows the add-first hero. */
    val measurementId: String?,
    val source: String,
    val fromSave: Boolean = false,
)

@Serializable
data class StyleFoldersRoute(val customerId: String? = null)

@Serializable
data class StyleGalleryRoute(
    val customerId: String? = null,
    val folderId: String? = null,
)

@Serializable
data class StyleFormRoute(
    val customerId: String? = null,
    val styleId: String? = null,
    val linkToOrderId: String? = null,
    val linkToItemId: String? = null,
    val folderId: String? = null,
    val readOnly: Boolean = false,
)

@Serializable
data object OrderListRoute

@Serializable
data class OrderFormRoute(
    val orderId: String? = null,
    val seedFromOrderId: String? = null,
    val customerId: String? = null,
)

@Serializable
data class OrderDetailRoute(val orderId: String)

@Serializable
data object DashboardRoute

/**
 * Gate screen pushed from the dashboard when the user is BrandNew and
 * taps an action that requires an existing customer ("Create first order"
 * hero CTA, "Create order" tile, "Measurement" tile). The screen itself
 * routes onward to [CustomerFormRoute].
 */
@Serializable
data object AddCustomerFirstRoute

@Serializable
data object GoalSetupRoute

@Serializable
data object SettingsRoute

@Serializable
data object EditProfileRoute

@Serializable
data object ChangeEmailRoute

@Serializable
data object ChangePasswordRoute

@Serializable
data object ReferralCodeRoute

@Serializable
data object DeleteAccountRoute

@Serializable
data object ToCollectRoute

@Serializable
data object ReportsRoute

@Serializable
data object DebugMenuRoute

@Serializable
data object DraftMessageRoute

@Serializable
data object UpgradeRoute

@Serializable
data object RedeemGiftRoute

@Serializable
data object ShareGiftLinkRoute

@Serializable
data object FoundersNoteRoute

@Serializable
data object SettingsAccountRoute

@Serializable
data object SettingsInviteRewardsRoute

@Serializable
data object SettingsHelpSupportRoute

@Serializable
data object SettingsLegalAboutRoute

@Serializable
data object NotificationsInboxRoute

@Serializable
data object HelpTutorialsRoute

@Serializable
data class TutorialPlayerRoute(val tutorialId: String)
