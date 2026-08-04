package com.danzucker.stitchpad.feature.review.presentation

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.domain.Analytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsUserProperty
import com.danzucker.stitchpad.feature.review.domain.ReviewOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewControllerTest {
    private val dayMs = 24L * 60 * 60 * 1000
    private var nowMillis = 1_000L * dayMs
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun logEvent(event: AnalyticsEvent) { events += event }
        override fun logScreenView(screenName: String) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(property: AnalyticsUserProperty, value: String) {}
    }

    private fun controller(
        prefs: FakeReviewPreferences,
        launcher: FakeStoreReviewLauncher,
        users: MutableStateFlow<String?>,
        scope: CoroutineScope,
    ) = ReviewController(
        preferences = prefs,
        analytics = analytics,
        launcher = launcher,
        authUserIds = users,
        scope = scope,
        now = { nowMillis },
    )

    private fun eligiblePrefs() = FakeReviewPreferences(
        installedAtMillis = nowMillis - 5 * dayMs,
        distinctOpenDays = 3,
        ordersByUser = mutableMapOf("u1" to 4),
    )

    @Test
    fun arm_from_delight_shows_sheet_when_eligible() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val c = controller(eligiblePrefs(), FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        assertTrue(c.current.value)
        assertTrue(events.any { it is AnalyticsEvent.ReviewPromptShown })
    }

    @Test
    fun arm_from_delight_no_show_when_too_few_orders() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs().also { it.ordersByUser["u1"] = 1 }
        val c = controller(prefs, FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        assertFalse(c.current.value)
    }

    @Test
    fun love_it_requests_native_review_and_records_rated() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs()
        val launcher = FakeStoreReviewLauncher()
        val c = controller(prefs, launcher, MutableStateFlow("u1"), scope)
        c.armFromDelight()
        c.onLoveIt()
        assertFalse(c.current.value)
        assertEquals(1, launcher.inAppRequests)
        assertEquals(ReviewOutcome.RATED, prefs.outcomeByUser["u1"])
        assertTrue(events.any { it is AnalyticsEvent.ReviewInAppRequested })
    }

    @Test
    fun not_really_emits_feedback_effect_and_records_feedback() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs()
        val c = controller(prefs, FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        c.effects.test {
            c.onNotReally()
            val effect = awaitItem()
            assertTrue(effect is ReviewEffect.OpenFeedback)
            assertEquals(ReviewConfig.FEEDBACK_URL, (effect as ReviewEffect.OpenFeedback).url)
        }
        assertEquals(ReviewOutcome.GAVE_FEEDBACK, prefs.outcomeByUser["u1"])
    }

    @Test
    fun dismiss_records_dismissed() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs()
        val c = controller(prefs, FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        c.onDismiss()
        assertEquals(ReviewOutcome.DISMISSED, prefs.outcomeByUser["u1"])
        assertFalse(c.current.value)
    }

    @Test
    fun rated_user_within_cooldown_is_not_re_armed() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val prefs = eligiblePrefs().also {
            it.outcomeByUser["u1"] = ReviewOutcome.RATED
            it.lastPromptByUser["u1"] = nowMillis - 10 * dayMs
        }
        val c = controller(prefs, FakeStoreReviewLauncher(), MutableStateFlow("u1"), scope)
        c.armFromDelight()
        assertFalse(c.current.value)
    }

    @Test
    fun auth_user_change_clears_visible_prompt() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val users = MutableStateFlow<String?>("u1")
        val c = controller(eligiblePrefs(), FakeStoreReviewLauncher(), users, scope)
        c.armFromDelight()
        assertTrue(c.current.value)
        users.value = "u2"
        assertFalse(c.current.value)
    }
}
