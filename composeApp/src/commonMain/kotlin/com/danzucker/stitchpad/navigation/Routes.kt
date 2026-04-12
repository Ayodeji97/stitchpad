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
data object OrdersPlaceholderRoute

@Serializable
data object DashboardPlaceholderRoute

@Serializable
data object SettingsPlaceholderRoute
