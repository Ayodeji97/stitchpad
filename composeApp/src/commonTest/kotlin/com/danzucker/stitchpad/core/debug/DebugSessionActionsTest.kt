package com.danzucker.stitchpad.core.debug

import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder

import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.auth.domain.SignOutUseCase
import com.danzucker.stitchpad.feature.notification.push.PushTokenRegistrar
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugSessionActionsTest {

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences
    private lateinit var sessionActions: DebugSessionActions

    @BeforeTest
    fun setUp() {
        authRepository = FakeAuthRepository()
        onboardingPreferences = FakeOnboardingPreferences().apply {
            onboardingSeen = true
            completedWorkshopSetups.add("u1")
        }
        sessionActions = DebugSessionActions(
            authRepository = authRepository,
            onboardingPreferences = onboardingPreferences,
            signOutUseCase = SignOutUseCase(authRepository, NoOpPushTokenRegistrar(), PendingDeepLinkHolder()),
        )
    }

    private class NoOpPushTokenRegistrar : PushTokenRegistrar {
        override suspend fun registerForUser(userId: String) {}
        override suspend fun register(userId: String, token: String) {}
        override suspend fun unregisterForUser(userId: String) {}
        override suspend fun invalidateToken() {}
    }

    @Test
    fun `resetOnboardingFlags clears both flags`() = runTest {
        sessionActions.resetOnboardingFlags()

        assertFalse(onboardingPreferences.onboardingSeen)
        assertFalse(onboardingPreferences.hasCompletedWorkshopSetup("u1"))
    }

    @Test
    fun `switchAccount returns ConfigurationMissing when creds blank`() = runTest {
        val result = sessionActions.switchAccount(email = "", password = "")

        assertTrue(
            result is SessionActionResult.ConfigurationMissing,
            "expected ConfigurationMissing, got $result"
        )
    }

    @Test
    fun `switchAccount signs out then attempts sign in with given creds`() = runTest {
        // Seed a current user so we can verify sign-out clears it.
        authRepository.currentUser = User(
            id = "old-uid",
            email = "old@example.com",
            displayName = "Old",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )

        // FakeAuthRepository.signInWithEmail requires currentUser to be non-null to succeed.
        // switchAccount calls signOut() first (which nulls currentUser), so signInWithEmail
        // returns USER_NOT_FOUND → Failure. This confirms the sign-out + sign-in sequence ran.
        val result = sessionActions.switchAccount("fola@example.com", "password")

        // signOut() was called — currentUser must be null.
        assertNull(authRepository.currentUser, "expected signOut to clear currentUser")
        // signInWithEmail was attempted and returned an error (USER_NOT_FOUND) as expected.
        assertTrue(result is SessionActionResult.Failure, "expected Failure, got $result")
    }
}
