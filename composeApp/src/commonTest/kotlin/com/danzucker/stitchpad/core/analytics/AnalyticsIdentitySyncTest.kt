package com.danzucker.stitchpad.core.analytics

import com.danzucker.stitchpad.core.analytics.data.AnalyticsIdentitySync
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsIdentitySyncTest {

    @Test
    fun setsUserIdOnAuthChangesAndTierOnEntitlementChanges() = runTest {
        val analytics = FakeAnalytics()
        val userIdFlow = MutableStateFlow<String?>(null)
        val tierFlow = MutableStateFlow("free")
        val sync = AnalyticsIdentitySync(
            userIdSource = userIdFlow,
            tierSource = tierFlow,
            analytics = analytics,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        sync.start()

        userIdFlow.value = "uid-123"
        tierFlow.value = "pro"

        assertEquals(listOf(null, "uid-123"), analytics.userIds)
        assertEquals(
            listOf(
                AnalyticsUserProperty.SUBSCRIPTION_TIER to "free",
                AnalyticsUserProperty.SUBSCRIPTION_TIER to "pro",
            ),
            analytics.userProperties,
        )
    }
}
