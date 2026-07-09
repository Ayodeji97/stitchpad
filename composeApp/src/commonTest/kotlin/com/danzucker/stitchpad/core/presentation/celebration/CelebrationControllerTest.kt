package com.danzucker.stitchpad.core.presentation.celebration

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CelebrationControllerTest {

    private lateinit var preferences: FakeOnboardingPreferences
    private lateinit var analytics: FakeAnalytics
    private lateinit var authUserIds: MutableSharedFlow<String?>
    private lateinit var controller: CelebrationController

    @BeforeTest
    fun setUp() {
        preferences = FakeOnboardingPreferences()
        analytics = FakeAnalytics()
        authUserIds = MutableSharedFlow()
        controller = CelebrationController(
            preferences = preferences,
            analytics = analytics,
            authUserIds = authUserIds,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    @Test
    fun firstTriggerShowsAndPersistsAndLogs() = runTest {
        controller.trigger("u1", Milestone.WorkshopReady)
        assertEquals(Milestone.WorkshopReady, controller.current.value)
        assertTrue(preferences.hasCelebrated("u1", "workshop_ready"))
        assertTrue(analytics.events.contains(AnalyticsEvent.CelebrationShown("workshop_ready")))
    }

    @Test
    fun secondTriggerOfSameMilestoneIsNoOp() = runTest {
        controller.trigger("u1", Milestone.FirstCustomer("Adaeze"))
        controller.dismiss()
        controller.trigger("u1", Milestone.FirstCustomer("Bola"))
        assertNull(controller.current.value)
        assertEquals(1, analytics.events.size)
    }

    @Test
    fun differentUsersCelebrateIndependently() = runTest {
        controller.trigger("u1", Milestone.WorkshopReady)
        controller.dismiss()
        controller.trigger("u2", Milestone.WorkshopReady)
        assertEquals(Milestone.WorkshopReady, controller.current.value)
    }

    @Test
    fun triggerWhileShowingQueuesAndDismissPromotes() = runTest {
        controller.trigger("u1", Milestone.FirstCustomer("Adaeze"))
        controller.trigger("u1", Milestone.FirstOrder("Adaeze"))
        assertEquals(Milestone.FirstCustomer("Adaeze"), controller.current.value)
        controller.dismiss()
        assertEquals(Milestone.FirstOrder("Adaeze"), controller.current.value)
        controller.dismiss()
        assertNull(controller.current.value)
    }

    @Test
    fun authChangeClearsCurrentAndQueue() = runTest {
        controller.trigger("u1", Milestone.FirstCustomer("Adaeze"))
        controller.trigger("u1", Milestone.FirstOrder("Adaeze"))
        authUserIds.emit(null) // sign-out
        controller.current.test {
            assertNull(awaitItem())
        }
        controller.dismiss() // must NOT resurrect the queued item
        assertNull(controller.current.value)
    }
}
