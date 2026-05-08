package com.danzucker.stitchpad.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object OnboardingRoute

@Serializable
data object LoginRoute

@Serializable
data object SignUpRoute

@Serializable
data object ForgotPasswordRoute

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
)

@Serializable
data class StyleGalleryRoute(val customerId: String)

@Serializable
data class StyleFormRoute(
    val customerId: String,
    val styleId: String? = null,
    val linkToOrderId: String? = null,
)

@Serializable
data object OrderListRoute

@Serializable
data class OrderFormRoute(
    val orderId: String? = null,
    val seedFromOrderId: String? = null,
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
data object SettingsPlaceholderRoute

@Serializable
data object ReportsRoute
