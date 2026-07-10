package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.model.User
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
import stitchpad.composeapp.generated.resources.error_business_name_too_short
import stitchpad.composeapp.generated.resources.error_whatsapp_invalid
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopSetupViewModelTest {

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
        fakeAuth.shouldReturnError = null
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            confirmCodeGenerator = { "1234" },
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

        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            confirmCodeGenerator = { "1234" },
            analytics = FakeAnalytics(),
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )
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
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            confirmCodeGenerator = { "1234" },
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
        assertEquals("Ade Fashions", fakeUserRepository.lastBusinessName)
        assertNull(fakeUserRepository.lastWhatsAppNumber)
    }

    @Test
    fun continueWithBusinessNameAndInvalidWhatsAppSetsWhatsAppError() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            confirmCodeGenerator = { "1234" },
            analytics = FakeAnalytics(),
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        runCurrent()

        assertEquals(Res.string.error_whatsapp_invalid, viewModel.state.value.whatsappError)
        assertNull(fakeUserRepository.lastBusinessName)
    }

    @Test
    fun continueWithRepositoryErrorEmitsShowError() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeUserRepository.shouldReturnError = DataError.Network.UNKNOWN
        viewModel = WorkshopSetupViewModel(
            fakeUserRepository, fakeAuth, onboardingPreferences,
            confirmCodeGenerator = { "1234" },
            analytics = FakeAnalytics(),
            celebrations = CelebrationController(
                preferences = FakeOnboardingPreferences(),
                analytics = FakeAnalytics(),
                authUserIds = emptyFlow(),
                scope = CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803 123 4567"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.ShowError>(event)
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

    @Test
    fun confirmFlow_correctCode_marksConfirmedAndEmitsLaunch() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))

        viewModel.events.test {
            viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
            val event = awaitItem()
            assertIs<WorkshopSetupEvent.LaunchWhatsAppConfirm>(event)
            assertEquals("+2348031234567", event.phoneE164)
            assertEquals("1234", event.code)
        }

        assertTrue(viewModel.state.value.whatsappConfirm.promptVisible)

        viewModel.onAction(WorkshopSetupAction.OnConfirmCodeChange("1234"))
        val confirm = viewModel.state.value.whatsappConfirm
        assertTrue(confirm.confirmed)
        assertFalse(confirm.promptVisible)
        assertNull(confirm.error)
    }

    @Test
    fun confirmFlow_wrongCode_setsErrorNotConfirmed() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))
        viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
        viewModel.onAction(WorkshopSetupAction.OnConfirmCodeChange("9999"))

        val confirm = viewModel.state.value.whatsappConfirm
        assertFalse(confirm.confirmed)
        assertNotNull(confirm.error)
    }

    @Test
    fun editingNumber_resetsConfirmed() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))
        viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
        viewModel.onAction(WorkshopSetupAction.OnConfirmCodeChange("1234"))
        assertTrue(viewModel.state.value.whatsappConfirm.confirmed)

        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234568"))
        assertFalse(viewModel.state.value.whatsappConfirm.confirmed)
    }

    @Test
    fun confirmClick_invalidNumber_setsWhatsappErrorAndNoPrompt() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("123"))
        viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)

        assertNotNull(viewModel.state.value.whatsappError)
        assertFalse(viewModel.state.value.whatsappConfirm.promptVisible)
    }

    @Test
    fun confirmThenContinue_persistsWhatsappConfirmedTrue() = runTest {
        fakeAuth.currentUser = User(
            id = "u1", email = "x@y.z", displayName = "Tester",
            businessName = null, phoneNumber = null, whatsappNumber = null, avatarColorIndex = 0,
        )

        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))

        viewModel.events.test {
            // OnConfirmWhatsAppClick sends LaunchWhatsAppConfirm — consume it
            viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
            assertIs<WorkshopSetupEvent.LaunchWhatsAppConfirm>(awaitItem())

            viewModel.onAction(WorkshopSetupAction.OnConfirmCodeChange("1234"))
            viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
            viewModel.onAction(WorkshopSetupAction.OnContinueClick)

            assertIs<WorkshopSetupEvent.NavigateToHome>(awaitItem())
        }

        assertEquals(true, fakeUserRepository.lastWhatsappConfirmed)
    }

    @Test
    fun unconfirmedNumber_persistsWhatsappConfirmedFalse() = runTest {
        fakeAuth.currentUser = User(
            id = "u1", email = "x@y.z", displayName = "Tester",
            businessName = null, phoneNumber = null, whatsappNumber = null, avatarColorIndex = 0,
        )

        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))
        // Deliberately skip OnConfirmWhatsAppClick / OnConfirmCodeChange — number not confirmed
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ade Fashions"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        val event = viewModel.events.first()
        assertIs<WorkshopSetupEvent.NavigateToHome>(event)
        assertEquals(false, fakeUserRepository.lastWhatsappConfirmed)
    }

    @Test
    fun dismissConfirm_afterConfirmClick_clearsPromptAndLeavesConfirmedFalse() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))

        viewModel.events.test {
            viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
            assertIs<WorkshopSetupEvent.LaunchWhatsAppConfirm>(awaitItem())
        }

        assertTrue(viewModel.state.value.whatsappConfirm.promptVisible)

        viewModel.onAction(WorkshopSetupAction.OnDismissConfirm)

        val confirm = viewModel.state.value.whatsappConfirm
        assertFalse(confirm.promptVisible)
        assertEquals("", confirm.input)
        assertNull(confirm.code)
        assertNull(confirm.error)
        assertFalse(confirm.confirmed)
    }
}
