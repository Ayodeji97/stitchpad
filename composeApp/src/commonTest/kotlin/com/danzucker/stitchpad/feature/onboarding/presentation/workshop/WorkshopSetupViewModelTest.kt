package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import com.danzucker.stitchpad.core.coroutines.ApplicationScope
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_business_name_too_short
import stitchpad.composeapp.generated.resources.error_whatsapp_invalid
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
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var onboardingPreferences: FakeOnboardingPreferences
    private lateinit var appScope: CoroutineScope

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeUserRepository = FakeUserRepository()
        fakeAuth = FakeAuthRepository()
        onboardingPreferences = FakeOnboardingPreferences()
        fakeAuth.shouldReturnError = null
        // ApplicationScope must dispatch on Dispatchers.Main (= UnconfinedTestDispatcher here)
        // so fire-and-forget writes from the VM run eagerly and tests can assert against
        // the fake repository without needing advanceUntilIdle.
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): WorkshopSetupViewModel =
        WorkshopSetupViewModel(
            userRepository = fakeUserRepository,
            authRepository = fakeAuth,
            onboardingPreferences = onboardingPreferences,
            applicationScope = ApplicationScope(appScope),
        )

    @Test
    fun initialStateIsEmpty() {
        val state = viewModel.state.value
        assertEquals("", state.businessName)
        assertEquals("", state.whatsappNumber)
        assertFalse(state.isLoading)
    }

    @Test
    fun onBusinessNameChangeUpdatesState() {
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        assertEquals("Ade Fashions", viewModel.state.value.businessName)
    }

    @Test
    fun onWhatsAppNumberChangeUpdatesState() {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803 123 4567"))
        assertEquals("0803 123 4567", viewModel.state.value.whatsappNumber)
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

        assertNull(fakeUserRepository.lastUserId)
    }

    @Test
    fun continueWithDataWritesToRepositoryAndNavigates() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")

        viewModel = createViewModel()
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803 123 4567"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertEquals("Ade Fashions", fakeUserRepository.lastBusinessName)
        assertEquals("+2348031234567", fakeUserRepository.lastWhatsAppNumber)
    }

    @Test
    fun continueWithEmptyFieldsSetsBusinessNameErrorAndDoesNotWrite() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(Res.string.error_business_name_too_short, state.businessNameError)
        assertNull(state.whatsappError)
        assertNull(fakeUserRepository.lastUserId)
    }

    @Test
    fun continueWithOnlyBusinessNameWritesProfileWithNullWhatsApp() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        viewModel = createViewModel()

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertEquals("Ade Fashions", fakeUserRepository.lastBusinessName)
        assertNull(fakeUserRepository.lastWhatsAppNumber)
    }

    @Test
    fun continueWithBusinessNameAndInvalidWhatsAppSetsWhatsAppError() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        viewModel = createViewModel()

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        runCurrent()

        assertEquals(Res.string.error_whatsapp_invalid, viewModel.state.value.whatsappError)
        assertNull(fakeUserRepository.lastBusinessName)
    }

    @Test
    fun continueWithRepositoryError_stillNavigatesToHome_andDoesNotEmitShowError() = runTest {
        // V0 fire-and-forget contract: createUserProfile failure is swallowed so onboarding
        // completes locally even when offline. The local DataStore flag is set and the user
        // lands on Home; the profile syncs to Firestore when the network returns.
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeUserRepository.shouldReturnError = DataError.Network.UNKNOWN
        viewModel = createViewModel()

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803 123 4567"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
    }

    @Test
    fun `OnWhatsAppNumberChange filters non-digit-spacing chars`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803-abc-123-4567"))
        // dashes are allowed; letters are stripped; double-dash is the expected output
        assertEquals("0803--123-4567", viewModel.state.first().whatsappNumber)
    }

    @Test
    fun `OnWhatsAppNumberChange preserves formatted E164 paste with 13 digits`() = runTest {
        // 17 raw chars but only 13 digits — must not get truncated mid-number.
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("+234 803 123 4567"))
        assertEquals("+234 803 123 4567", viewModel.state.first().whatsappNumber)
    }

    @Test
    fun `OnWhatsAppNumberChange caps at 13 digits and drops the rest`() = runTest {
        // 20 digits typed straight — only the first 13 should land in state.
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("12345678901234567890"))
        assertEquals("1234567890123", viewModel.state.first().whatsappNumber)
    }

    @Test
    fun `OnWhatsAppNumberBlur sets error when number is too short`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803123"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberBlur)
        assertEquals(Res.string.error_whatsapp_invalid, viewModel.state.first().whatsappError)
    }

    @Test
    fun `OnWhatsAppNumberBlur sets no error when number is valid`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803 123 4567"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberBlur)
        assertNull(viewModel.state.first().whatsappError)
    }

    @Test
    fun `OnWhatsAppNumberBlur with empty field sets no error`() = runTest {
        // whatsappNumber is "" by default — blur should short-circuit, no error set
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberBlur)
        assertNull(viewModel.state.first().whatsappError)
    }

    @Test
    fun `OnContinueClick submits whatsappNumber as plus-prefixed E164`() = runTest {
        fakeAuth.currentUser = User(id = "u1", email = "x@y.z", displayName = "Tester", businessName = null, phoneNumber = null, whatsappNumber = null, avatarColorIndex = 0)
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803 123 4567"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        runCurrent()
        assertEquals("+2348031234567", fakeUserRepository.lastWhatsAppNumber)
    }
}
