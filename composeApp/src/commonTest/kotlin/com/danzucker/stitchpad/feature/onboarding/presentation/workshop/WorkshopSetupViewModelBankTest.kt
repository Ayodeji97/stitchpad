package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_bank_account_name_required
import stitchpad.composeapp.generated.resources.error_bank_account_number_invalid
import stitchpad.composeapp.generated.resources.error_bank_name_required
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopSetupViewModelBankTest {

    private lateinit var viewModel: WorkshopSetupViewModel
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeUserRepository = FakeUserRepository()
        fakeAuth = FakeAuthRepository()
        onboardingPreferences = FakeOnboardingPreferences()
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            analytics = FakeAnalytics(),
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `all blank bank fields pass validation and write nulls to repository`() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            analytics = FakeAnalytics(),
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        val event = viewModel.events.first()

        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertNull(fakeUserRepository.lastBankName)
        assertNull(fakeUserRepository.lastBankAccountName)
        assertNull(fakeUserRepository.lastBankAccountNumber)
    }

    @Test
    fun `partial bank input flags missing fields and blocks save`() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            analytics = FakeAnalytics(),
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnBankNameChange("GTBank"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        runCurrent()

        val state = viewModel.state.value
        assertNull(state.bankNameError)
        assertEquals(Res.string.error_bank_account_name_required, state.bankAccountNameError)
        assertEquals(Res.string.error_bank_account_number_invalid, state.bankAccountNumberError)
        // No write happened — Continue bailed before createUserProfile.
        assertNull(fakeUserRepository.lastUserId)
    }

    @Test
    fun `account number must be exactly 10 digits`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnBankNameChange("GTBank"))
        viewModel.onAction(WorkshopSetupAction.OnBankAccountNameChange("Fola Joy"))
        viewModel.onAction(WorkshopSetupAction.OnBankAccountNumberChange("12345"))
        viewModel.onAction(WorkshopSetupAction.OnBankAccountNumberBlur)
        runCurrent()

        assertEquals(
            Res.string.error_bank_account_number_invalid,
            viewModel.state.first().bankAccountNumberError,
        )
    }

    @Test
    fun `bank account number input strips non-digit characters and caps at 10`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnBankAccountNumberChange("01-23 ABC 456 789 1234567"))
        assertEquals("0123456789", viewModel.state.first().bankAccountNumber)
    }

    @Test
    fun `bank name shorter than 2 chars fails`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnBankNameChange("G"))
        viewModel.onAction(WorkshopSetupAction.OnBankAccountNameChange("Fola Joy"))
        viewModel.onAction(WorkshopSetupAction.OnBankAccountNumberChange("0123456789"))
        viewModel.onAction(WorkshopSetupAction.OnBankNameBlur)
        runCurrent()

        assertEquals(Res.string.error_bank_name_required, viewModel.state.first().bankNameError)
    }

    @Test
    fun `valid bank trio is written to repository on Continue`() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            analytics = FakeAnalytics(),
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnBankNameChange("GTBank"))
        viewModel.onAction(WorkshopSetupAction.OnBankAccountNameChange("Fola Joy Enterprises"))
        viewModel.onAction(WorkshopSetupAction.OnBankAccountNumberChange("0123456789"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        val event = viewModel.events.first()

        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertEquals("GTBank", fakeUserRepository.lastBankName)
        assertEquals("Fola Joy Enterprises", fakeUserRepository.lastBankAccountName)
        assertEquals("0123456789", fakeUserRepository.lastBankAccountNumber)
    }

    @Test
    fun `OnTogglePaymentDetails flips expanded flag`() = runTest {
        assertEquals(false, viewModel.state.first().isPaymentDetailsExpanded)
        viewModel.onAction(WorkshopSetupAction.OnTogglePaymentDetails)
        assertEquals(true, viewModel.state.first().isPaymentDetailsExpanded)
        viewModel.onAction(WorkshopSetupAction.OnTogglePaymentDetails)
        assertEquals(false, viewModel.state.first().isPaymentDetailsExpanded)
    }
}
