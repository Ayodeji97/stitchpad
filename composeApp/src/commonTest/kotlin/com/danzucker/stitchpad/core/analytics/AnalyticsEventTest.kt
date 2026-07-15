package com.danzucker.stitchpad.core.analytics

import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsEventTest {

    @Test
    fun parameterlessEventsUseSnakeCaseNamesAndNoParams() {
        assertEquals("workshop_setup_completed", AnalyticsEvent.WorkshopSetupCompleted.name)
        assertEquals("customer_created", AnalyticsEvent.CustomerCreated.name)
        assertEquals("order_created", AnalyticsEvent.OrderCreated.name)
        assertTrue(AnalyticsEvent.WorkshopSetupCompleted.params.isEmpty())
    }

    @Test
    fun signUpCarriesMethodParam() {
        val event = AnalyticsEvent.SignUp(method = "google")
        assertEquals("sign_up", event.name)
        assertEquals(mapOf("method" to "google"), event.params)
    }

    @Test
    fun loginCarriesMethodParam() {
        val event = AnalyticsEvent.Login(method = "email")
        assertEquals("login", event.name)
        assertEquals(mapOf("method" to "email"), event.params)
    }

    @Test
    fun referralCodeAppliedCarriesSourceAndSurface() {
        val event = AnalyticsEvent.ReferralCodeApplied(source = "manual", surface = "settings")
        assertEquals("referral_code_applied", event.name)
        assertEquals(mapOf("source" to "manual", "surface" to "settings"), event.params)
    }

    @Test
    fun aiFeatureUsedCarriesFeatureParam() {
        val event = AnalyticsEvent.AiFeatureUsed(feature = "draft_message")
        assertEquals("ai_feature_used", event.name)
        assertEquals(mapOf("feature" to "draft_message"), event.params)
    }

    @Test
    fun upgradeCompletedCarriesTierParam() {
        val event = AnalyticsEvent.UpgradeCompleted(tier = "pro")
        assertEquals("upgrade_completed", event.name)
        assertEquals(mapOf("tier" to "pro"), event.params)
    }

    @Test
    fun measurementAddedNameNoParams() {
        val e = AnalyticsEvent.MeasurementAdded
        assertEquals("measurement_added", e.name)
        assertTrue(e.params.isEmpty())
    }

    @Test
    fun orderStatusAdvancedCarriesStatus() {
        val e = AnalyticsEvent.OrderStatusAdvanced(status = "ready")
        assertEquals("order_status_advanced", e.name)
        assertEquals(mapOf("status" to "ready"), e.params)
    }

    @Test
    fun paymentRecordedCarriesIsFullyPaidAsString() {
        assertEquals(mapOf("is_fully_paid" to "true"), AnalyticsEvent.PaymentRecorded(isFullyPaid = true).params)
        assertEquals(mapOf("is_fully_paid" to "false"), AnalyticsEvent.PaymentRecorded(isFullyPaid = false).params)
        assertEquals("payment_recorded", AnalyticsEvent.PaymentRecorded(true).name)
    }

    @Test
    fun receiptSentCarriesDocTypeAndFormat() {
        val e = AnalyticsEvent.ReceiptSent(documentType = "invoice", format = "pdf")
        assertEquals("receipt_sent", e.name)
        assertEquals(mapOf("document_type" to "invoice", "format" to "pdf"), e.params)
    }

    @Test
    fun whatsAppMessageSentCarriesContext() {
        val e = AnalyticsEvent.WhatsAppMessageSent(context = "draft_message")
        assertEquals("whatsapp_message_sent", e.name)
        assertEquals(mapOf("context" to "draft_message"), e.params)
    }
}
