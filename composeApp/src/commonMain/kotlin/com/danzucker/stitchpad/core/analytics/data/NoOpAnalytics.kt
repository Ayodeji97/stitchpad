package com.danzucker.stitchpad.core.analytics.data

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty

/** No-op analytics for previews and tests. */
class NoOpAnalytics : Analytics {
    override fun logEvent(event: AnalyticsEvent) = Unit
    override fun logScreenView(screenName: String) = Unit
    override fun setUserId(userId: String?) = Unit
    override fun setUserProperty(property: AnalyticsUserProperty, value: String) = Unit
}
