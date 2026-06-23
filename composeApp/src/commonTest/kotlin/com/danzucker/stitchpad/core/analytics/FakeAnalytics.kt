package com.danzucker.stitchpad.core.analytics

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty

/** Records calls for assertions in ViewModel tests. */
class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val screenViews = mutableListOf<String>()
    val userIds = mutableListOf<String?>()
    val userProperties = mutableListOf<Pair<AnalyticsUserProperty, String>>()

    override fun logEvent(event: AnalyticsEvent) { events += event }
    override fun logScreenView(screenName: String) { screenViews += screenName }
    override fun setUserId(userId: String?) { userIds += userId }
    override fun setUserProperty(property: AnalyticsUserProperty, value: String) {
        userProperties += property to value
    }
}
