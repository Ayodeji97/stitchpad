package com.danzucker.stitchpad.core.analytics.domain

/**
 * Type-safe analytics events. Each maps to its GA4 snake_case name + params in ONE place
 * so client constants can't drift. NO PII — counts, enums, and tier only.
 */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, Any> get() = emptyMap()

    data object SignUp : AnalyticsEvent {
        override val name = "sign_up"
    }

    data object WorkshopSetupCompleted : AnalyticsEvent {
        override val name = "workshop_setup_completed"
    }

    data object CustomerCreated : AnalyticsEvent {
        override val name = "customer_created"
    }

    data object OrderCreated : AnalyticsEvent {
        override val name = "order_created"
    }

    data class AiFeatureUsed(val feature: String) : AnalyticsEvent {
        override val name = "ai_feature_used"
        override val params = mapOf("feature" to feature)
    }

    data class UpgradeCompleted(val tier: String) : AnalyticsEvent {
        override val name = "upgrade_completed"
        override val params = mapOf("tier" to tier)
    }
}
