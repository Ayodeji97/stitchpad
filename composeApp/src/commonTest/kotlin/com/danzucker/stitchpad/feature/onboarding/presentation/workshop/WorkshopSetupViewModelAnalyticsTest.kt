package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopSetupViewModelAnalyticsTest {

    private lateinit var viewModel: WorkshopSetupViewModel
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences
    private lateinit var fakeAnalytics: FakeAnalytics

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeUserRepository = FakeUserRepository()
        fakeAuth = FakeAuthRepository().apply {
            currentUser = User(
                id = "u1",
                email = "tailor@test.com",
                displayName = "Tailor",
                businessName = null,
                phoneNumber = null,
                whatsappNumber = null,
                avatarColorIndex = 0,
            )
        }
        onboardingPreferences = FakeOnboardingPreferences()
        fakeAnalytics = FakeAnalytics()
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            analytics = fakeAnalytics,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful save logs WorkshopSetupCompleted`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        viewModel.events.first() // await NavigateToHome

        assertTrue(
            fakeAnalytics.events.contains(AnalyticsEvent.WorkshopSetupCompleted),
            "Expected WorkshopSetupCompleted in analytics events but got: ${fakeAnalytics.events}",
        )
    }

    @Test
    fun `skip does NOT log WorkshopSetupCompleted`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnSkipClick)

        viewModel.events.first() // await NavigateToHome

        assertFalse(
            fakeAnalytics.events.contains(AnalyticsEvent.WorkshopSetupCompleted),
            "Expected WorkshopSetupCompleted NOT to be logged on skip but it was",
        )
    }
}
