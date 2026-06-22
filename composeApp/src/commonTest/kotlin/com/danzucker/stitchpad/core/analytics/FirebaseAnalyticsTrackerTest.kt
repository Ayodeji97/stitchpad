package com.danzucker.stitchpad.core.analytics

import com.danzucker.stitchpad.core.analytics.data.AnalyticsSink
import com.danzucker.stitchpad.core.analytics.data.FirebaseAnalyticsTracker
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingSink(val throwOnEvent: Boolean = false) : AnalyticsSink {
    val logged = mutableListOf<Pair<String, Map<String, Any>?>>()
    private var _userId: String? = "unset"
    val properties = mutableListOf<Pair<String, String>>()
    override fun logEvent(name: String, parameters: Map<String, Any>?) {
        if (throwOnEvent) error("boom")
        logged += name to parameters
    }
    override fun setUserId(id: String?) { _userId = id }
    override fun setUserProperty(name: String, value: String) { properties += name to value }
}

class FirebaseAnalyticsTrackerTest {

    @Test
    fun logEventForwardsNameAndParams_nullWhenEmpty() {
        val sink = RecordingSink()
        val tracker = FirebaseAnalyticsTracker(sink)
        tracker.logEvent(AnalyticsEvent.SignUp)
        tracker.logEvent(AnalyticsEvent.AiFeatureUsed(feature = "draft_message"))
        assertEquals("sign_up" to null, sink.logged[0])
        assertEquals("ai_feature_used" to mapOf("feature" to "draft_message"), sink.logged[1])
    }

    @Test
    fun logScreenViewEmitsReservedEvent() {
        val sink = RecordingSink()
        FirebaseAnalyticsTracker(sink).logScreenView("HomeRoute")
        assertEquals("screen_view" to mapOf("screen_name" to "HomeRoute"), sink.logged[0])
    }

    @Test
    fun setUserPropertyUsesKey() {
        val sink = RecordingSink()
        FirebaseAnalyticsTracker(sink).setUserProperty(AnalyticsUserProperty.SUBSCRIPTION_TIER, "pro")
        assertEquals("subscription_tier" to "pro", sink.properties[0])
    }

    @Test
    fun sinkExceptionsAreSwallowed() {
        val tracker = FirebaseAnalyticsTracker(RecordingSink(throwOnEvent = true))
        tracker.logEvent(AnalyticsEvent.OrderCreated) // must NOT throw
    }
}
