package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.core.presentation.celebration.Milestone
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopSetupViewModelCelebrationTest {

    private lateinit var viewModel: WorkshopSetupViewModel
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences
    private lateinit var fakeAnalytics: FakeAnalytics
    private lateinit var celebrations: CelebrationController

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
        celebrations = CelebrationController(
            preferences = FakeOnboardingPreferences(),
            analytics = FakeAnalytics(),
            authUserIds = emptyFlow(),
            scope = CoroutineScope(UnconfinedTestDispatcher()),
        )
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            analytics = fakeAnalytics,
            celebrations = celebrations,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful continue triggers WorkshopReady`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        viewModel.events.first() // await NavigateToHome

        assertEquals(Milestone.WorkshopReady, celebrations.current.value)
    }

    @Test
    fun `skip ALSO triggers WorkshopReady`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnSkipClick)
        viewModel.events.first() // await NavigateToHome

        assertEquals(Milestone.WorkshopReady, celebrations.current.value)
    }
}
