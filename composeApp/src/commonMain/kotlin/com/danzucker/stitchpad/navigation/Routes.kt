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
data object DeleteAccountRoute

@Serializable
data object ReportsRoute

@Serializable
data object DebugMenuRoute

@Serializable
data object DraftMessageRoute

@Serializable
data object UpgradeRoute

@Serializable
data object FoundersNoteRoute

@Serializable
data object NotificationsInboxRoute
