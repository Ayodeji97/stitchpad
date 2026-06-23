package com.danzucker.stitchpad.core.analytics

import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsEventTest {

    @Test
    fun parameterlessEventsUseSnakeCaseNamesAndNoParams() {
        assertEquals("sign_up", AnalyticsEvent.SignUp.name)
        assertEquals("workshop_setup_completed", AnalyticsEvent.WorkshopSetupCompleted.name)
        assertEquals("customer_created", AnalyticsEvent.CustomerCreated.name)
        assertEquals("order_created", AnalyticsEvent.OrderCreated.name)
        assertTrue(AnalyticsEvent.SignUp.params.isEmpty())
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
}
