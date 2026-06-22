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
        val prefs = FakeOnboardingPreferences().apply { completedWorkshopSetups.add("u1") }
        val users = FakeUserRepository().apply { hasWorkshopProfileResult = false }

        val needsSetup = ResolveNeedsWorkshopSetup(prefs, users)("u1")

        assertFalse(needsSetup)
        assertEquals(0, users.hasWorkshopProfileCallCount)
    }

    @Test
    fun localFlagWiped_butRemoteHasWorkshop_returnsFalse_andHealsPerUserFlag() = runTest {
        // The reinstall scenario: local flag gone, but Firestore still has the profile.
        val prefs = FakeOnboardingPreferences()
        val users = FakeUserRepository().apply { hasWorkshopProfileResult = true }

        val needsSetup = ResolveNeedsWorkshopSetup(prefs, users)("u1")

        assertFalse(needsSetup)
        assertTrue(
            prefs.hasConfirmedRemoteWorkshopProfile("u1"),
            "per-user flag should be healed for fast future launches",
        )
        // A remote heal sets the per-user CONFIRMED flag, not the COMPLETED flag.
        assertFalse(prefs.hasCompletedWorkshopSetup("u1"))
    }

    @Test
    fun healingOneUser_doesNotSkipSetupForADifferentUser_onSameDevice() = runTest {
        val prefs = FakeOnboardingPreferences()
        val resolve = ResolveNeedsWorkshopSetup(prefs, FakeUserRepository().apply { hasWorkshopProfileResult = true })
        // User A (existing) signs in on a fresh install and is confirmed.
        assertFalse(resolve("userA"))

        // User B (brand new, no remote profile) then signs in on the same install.
        val resolveForB = ResolveNeedsWorkshopSetup(prefs, FakeUserRepository().apply { hasWorkshopProfileResult = false })
        assertTrue(resolveForB("userB"), "a different account must still be gated through setup")
    }

    @Test
    fun deviceCompletedByOneUser_doesNotSkipSetupForADifferentUser() = runTest {
        // The reported bug: user A finishes (or skips) workshop setup on this install,
        // then user B registers on the SAME build. B must still see the workshop screen.
        val prefs = FakeOnboardingPreferences().apply { completedWorkshopSetups.add("userA") }
        val resolveForB = ResolveNeedsWorkshopSetup(prefs, FakeUserRepository().apply { hasWorkshopProfileResult = false })
        assertTrue(resolveForB("userB"), "a different account must still be gated through setup")
    }

    @Test
    fun localFlagWiped_andRemoteEmpty_returnsTrue() = runTest {
        // A genuinely new (or skipped) user with no workshop data still sees setup.
        val prefs = FakeOnboardingPreferences()
        val users = FakeUserRepository().apply { hasWorkshopProfileResult = false }

        val needsSetup = ResolveNeedsWorkshopSetup(prefs, users)("u1")

        assertTrue(needsSetup)
        assertFalse(prefs.hasCompletedWorkshopSetup("u1"))
    }
}
