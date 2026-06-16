package com.danzucker.stitchpad.feature.onboarding.domain

import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolveNeedsWorkshopSetupTest {

    @Test
    fun localFlagCompleted_returnsFalse_withoutHittingRemote() = runTest {
        val prefs = FakeOnboardingPreferences().apply { workshopSetupCompleted = true }
        val users = FakeUserRepository().apply { hasWorkshopProfileResult = false }

        val needsSetup = ResolveNeedsWorkshopSetup(prefs, users)("u1")

        assertFalse(needsSetup)
        assertEquals(0, users.hasWorkshopProfileCallCount)
    }

    @Test
    fun localFlagWiped_butRemoteHasWorkshop_returnsFalse_andHealsLocalFlag() = runTest {
        // The reinstall scenario: local flag gone, but Firestore still has the profile.
        val prefs = FakeOnboardingPreferences().apply { workshopSetupCompleted = false }
        val users = FakeUserRepository().apply { hasWorkshopProfileResult = true }

        val needsSetup = ResolveNeedsWorkshopSetup(prefs, users)("u1")

        assertFalse(needsSetup)
        assertTrue(prefs.hasCompletedWorkshopSetup(), "local flag should be healed for fast future launches")
    }

    @Test
    fun localFlagWiped_andRemoteEmpty_returnsTrue() = runTest {
        // A genuinely new (or skipped) user with no workshop data still sees setup.
        val prefs = FakeOnboardingPreferences().apply { workshopSetupCompleted = false }
        val users = FakeUserRepository().apply { hasWorkshopProfileResult = false }

        val needsSetup = ResolveNeedsWorkshopSetup(prefs, users)("u1")

        assertTrue(needsSetup)
        assertFalse(prefs.hasCompletedWorkshopSetup())
    }
}
