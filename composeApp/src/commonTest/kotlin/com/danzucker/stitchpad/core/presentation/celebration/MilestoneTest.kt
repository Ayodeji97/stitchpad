package com.danzucker.stitchpad.core.presentation.celebration

import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MilestoneTest {

    @Test
    fun milestoneKeysAreStable() {
        assertEquals("workshop_ready", Milestone.WorkshopReady.key)
        assertEquals("first_customer", Milestone.FirstCustomer("Adaeze").key)
        assertEquals("first_order", Milestone.FirstOrder("Adaeze").key)
    }

    @Test
    fun celebrationFlagsArePerUserAndPerMilestone() = runTest {
        val prefs = FakeOnboardingPreferences()
        assertFalse(prefs.hasCelebrated("u1", "first_customer"))
        prefs.setCelebrated("u1", "first_customer")
        assertTrue(prefs.hasCelebrated("u1", "first_customer"))
        assertFalse(prefs.hasCelebrated("u2", "first_customer"))
        assertFalse(prefs.hasCelebrated("u1", "first_order"))
    }

    @Test
    fun clearCelebrationsForDebugClearsAllFlags() = runTest {
        val prefs = FakeOnboardingPreferences()
        prefs.setCelebrated("u1", "first_customer")
        prefs.setCelebrated("u2", "workshop_ready")
        prefs.clearCelebrationsForDebug()
        assertFalse(prefs.hasCelebrated("u1", "first_customer"))
        assertFalse(prefs.hasCelebrated("u2", "workshop_ready"))
    }
}
