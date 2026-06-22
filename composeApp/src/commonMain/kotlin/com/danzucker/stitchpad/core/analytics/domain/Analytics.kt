package com.danzucker.stitchpad.core.analytics.domain

/**
 * Fire-and-forget product analytics. Implementations MUST NOT throw or block — a failed
 * analytics call must never affect a user flow. Injected into ViewModels; never called
 * from composables.
 */
interface Analytics {
    fun logEvent(event: AnalyticsEvent)
    fun logScreenView(screenName: String)
    fun setUserId(userId: String?)
    fun setUserProperty(property: AnalyticsUserProperty, value: String)
}

/** GA4 user properties (set once, attached to every event for segmentation). */
enum class AnalyticsUserProperty(val key: String) {
    SUBSCRIPTION_TIER("subscription_tier"),
}
