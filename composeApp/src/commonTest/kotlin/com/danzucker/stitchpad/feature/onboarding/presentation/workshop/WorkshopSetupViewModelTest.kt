package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopSetupViewModelTest {

    private lateinit var viewModel: WorkshopSetupViewModel
    private lateinit var userRepository: FakeUserRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        userRepository = FakeUserRepository()
        authRepository = FakeAuthRepository()
        onboardingPreferences = FakeOnboardingPreferences()
        authRepository.shouldReturnError = null
        viewModel = WorkshopSetupViewModel(userRepository, authRepository, onboardingPreferences)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsEmpty() {
        val state = viewModel.state.value
        assertEquals("", state.businessName)
        assertEquals("", state.phone)
        assertFalse(state.isLoading)
    }

    @Test
    fun onBusinessNameChangeUpdatesState() {
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        assertEquals("Ade Fashions", viewModel.state.value.businessName)
    }

    @Test
    fun onPhoneChangeUpdatesState() {
        viewModel.onAction(WorkshopSetupAction.OnPhoneChange("+2348012345678"))
        assertEquals("+2348012345678", viewModel.state.value.phone)
    }

    @Test
    fun skipEmitsNavigateToHome() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnSkipClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
    }

    @Test
    fun skipDoesNotWriteToRepository() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnSkipClick)
        viewModel.events.first()

        assertNull(userRepository.lastUserId)
    }

    @Test
    fun continueWithDataWritesToRepositoryAndNavigates() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")

        viewModel = WorkshopSetupViewModel(userRepository, authRepository, onboardingPreferences)
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnPhoneChange("+2348012345678"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertEquals("Ade Fashions", userRepository.lastBusinessName)
        assertEquals("+2348012345678", userRepository.lastPhone)
    }

    @Test
    fun continueWithEmptyFieldsBehavesLikeSkip() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertNull(userRepository.lastUserId)
    }

    @Test
    fun continueWithOnlyBusinessNameWritesToRepository() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        viewModel = WorkshopSetupViewModel(userRepository, authRepository, onboardingPreferences)

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertEquals("Ade Fashions", userRepository.lastBusinessName)
    }

    @Test
    fun continueWithRepositoryErrorEmitsShowError() = runTest {
        authRepository.signUpWithEmail("test@test.com", "pass123", "Test")
        userRepository.shouldReturnError = DataError.Network.UNKNOWN
        viewModel = WorkshopSetupViewModel(userRepository, authRepository, onboardingPreferences)

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.ShowError>(event)
    }
}
