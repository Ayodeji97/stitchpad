package com.danzucker.stitchpad.core.analytics.domain

/**
 * Type-safe analytics events. Each maps to its GA4 snake_case name + params in ONE place
 * so client constants can't drift. NO PII — counts, enums, and tier only.
 */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, Any> get() = emptyMap()

    /** [method] ∈ email|google|apple — which auth method created the account. */
    data class SignUp(val method: String) : AnalyticsEvent {
        override val name = "sign_up"
        override val params = mapOf("method" to method)
    }

    /** An existing account signed in. [method] ∈ email|google|apple. */
    data class Login(val method: String) : AnalyticsEvent {
        override val name = "login"
        override val params = mapOf("method" to method)
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

    data object MeasurementAdded : AnalyticsEvent {
        override val name = "measurement_added"
    }

    data class MeasurementDetailViewed(val source: String) : AnalyticsEvent {
        override val name = "measurement_detail_viewed"
        override val params = mapOf("source" to source)
    }

    data class MeasurementShared(val format: String) : AnalyticsEvent {
        override val name = "measurement_shared"
        override val params = mapOf("format" to format)
    }

    data class OrderStatusAdvanced(val status: String) : AnalyticsEvent {
        override val name = "order_status_advanced"
        override val params = mapOf("status" to status)
    }

    data class PaymentRecorded(val isFullyPaid: Boolean) : AnalyticsEvent {
        override val name = "payment_recorded"

        // String, not Boolean: GA4 param types are string/number; keep it queryable.
        override val params = mapOf("is_fully_paid" to isFullyPaid.toString())
    }

    data class ReceiptSent(val documentType: String, val format: String) : AnalyticsEvent {
        override val name = "receipt_sent"
        override val params = mapOf("document_type" to documentType, "format" to format)
    }

    data class WhatsAppMessageSent(val context: String) : AnalyticsEvent {
        override val name = "whatsapp_message_sent"
        override val params = mapOf("context" to context)
    }

    data class CelebrationShown(val milestone: String) : AnalyticsEvent {
        override val name = "celebration_shown"
        override val params = mapOf("milestone" to milestone)
    }

    /**
     * A referral code attributed successfully (fresh, not an idempotent replay).
     * [source] must be a [com.danzucker.stitchpad.feature.referral.domain.ReferralSource.wire]
     * value; [surface] ∈ signup|settings.
     */
    data class ReferralCodeApplied(val source: String, val surface: String) : AnalyticsEvent {
        override val name = "referral_code_applied"
        override val params = mapOf("source" to source, "surface" to surface)
    }
}
