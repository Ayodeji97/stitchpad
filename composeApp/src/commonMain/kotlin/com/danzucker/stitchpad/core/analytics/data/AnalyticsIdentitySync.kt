package com.danzucker.stitchpad.core.analytics.data

import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the analytics identity in sync with the session: the Firebase user id (so journeys
 * can be stitched per-user in BigQuery) and the subscription_tier user property (so every
 * funnel is segmentable by plan). App-scoped; started once at startup.
 */
class AnalyticsIdentitySync(
    private val userIdSource: Flow<String?>,
    private val tierSource: Flow<String>,
    private val analytics: Analytics,
    private val scope: CoroutineScope,
) {
    fun start() {
        userIdSource
            .onEach { analytics.setUserId(it) }
            .launchIn(scope)
        tierSource
            .onEach { analytics.setUserProperty(AnalyticsUserProperty.SUBSCRIPTION_TIER, it) }
            .launchIn(scope)
    }
}
