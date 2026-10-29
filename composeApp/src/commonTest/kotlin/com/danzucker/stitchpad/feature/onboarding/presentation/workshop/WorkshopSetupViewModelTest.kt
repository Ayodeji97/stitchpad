package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import app.cash.turbine.test
import com.danzucker.stitchpad.core.analytics.FakeAnalytics
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.defaultTimeZone
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.presentation.celebration.CelebrationController
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_business_name_too_short
import stitchpad.composeapp.generated.resources.error_whatsapp_invalid_for_country
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
        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        region: () -> String? = { null },
        language: () -> String = { "en" },
    ) = WorkshopSetupViewModel(
        fakeUserRepository, fakeAuth, onboardingPreferences,
        confirmCodeGenerator = { "1234" },
        analytics = FakeAnalytics(),
        celebrations = CelebrationController(
            preferences = FakeOnboardingPreferences(),
            analytics = FakeAnalytics(),
            authUserIds = emptyFlow(),
            scope = CoroutineScope(UnconfinedTestDispatcher()),
        ),
        deviceRegion = region,
        deviceLanguage = language,
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

        assertEquals(Res.string.error_whatsapp_invalid_for_country, viewModel.state.value.whatsappError)
        assertNull(fakeUserRepository.lastBusinessName)
    }

    @Test
    fun continueWithRepositoryErrorEmitsShowError() = runTest {
        fakeAuth.signUpWithEmail("test@test.com", "pass123", "Test")
        fakeUserRepository.shouldReturnError = DataError.Network.UNKNOWN
        viewModel = createViewModel()

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
        assertEquals(Res.string.error_whatsapp_invalid_for_country, viewModel.state.first().whatsappError)
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

    // --- Country picker (africa expansion PR 1, Task 6) ---

    private fun signedInUser(country: Country = Country.NG, timeZone: String = country.defaultTimeZone) = User(
        id = "u1", email = "x@y.z", displayName = "Tester",
        businessName = null, phoneNumber = null, whatsappNumber = null, avatarColorIndex = 0,
        country = country, timeZone = timeZone,
    )

    @Test
    fun `preselects device region`() {
        viewModel = createViewModel(region = { "GH" })
        assertEquals(Country.GH, viewModel.state.value.country)
        assertEquals("Africa/Accra", viewModel.state.value.timeZone)
    }

    @Test
    fun `lowercase device region is recognised`() {
        viewModel = createViewModel(region = { "sn" })
        assertEquals(Country.SN, viewModel.state.value.country)
    }

    @Test
    fun `unknown region defaults to nigeria`() {
        listOf(null, "", "  ", "US").forEach { region ->
            viewModel = createViewModel(region = { region })
            assertEquals(Country.NG, viewModel.state.value.country, "region=$region")
            assertEquals("Africa/Lagos", viewModel.state.value.timeZone, "region=$region")
        }
    }

    @Test
    fun `chip click opens picker and dismiss closes it`() {
        viewModel.onAction(WorkshopSetupAction.OnCountryChipClick)
        assertTrue(viewModel.state.value.isCountryPickerOpen)
        viewModel.onAction(WorkshopSetupAction.OnDismissCountryPicker)
        assertFalse(viewModel.state.value.isCountryPickerOpen)
    }

    @Test
    fun `selecting country closes picker`() {
        viewModel.onAction(WorkshopSetupAction.OnCountryChipClick)
        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.KE))
        assertFalse(viewModel.state.value.isCountryPickerOpen)
        assertEquals(Country.KE, viewModel.state.value.country)
    }

    @Test
    fun `selecting country resets time zone to its default and revalidates the number`() {
        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.CD))
        viewModel.onAction(WorkshopSetupAction.OnTimeZoneSelected("Africa/Lubumbashi"))
        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.NG))
        assertEquals("Africa/Lagos", viewModel.state.value.timeZone)

        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("244123456"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberBlur)
        assertEquals(Res.string.error_whatsapp_invalid_for_country, viewModel.state.value.whatsappError)

        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.GH))
        assertNull(viewModel.state.value.whatsappError)
        assertEquals("Africa/Accra", viewModel.state.value.timeZone)
    }

    @Test
    fun `selecting a country that rejects the number shows the error`() {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0803 123 4567"))
        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.GH))
        assertEquals(Res.string.error_whatsapp_invalid_for_country, viewModel.state.value.whatsappError)
    }

    @Test
    fun `changing country resets whatsapp confirmation`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("8031234567"))
        viewModel.events.test {
            viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
            assertIs<WorkshopSetupEvent.LaunchWhatsAppConfirm>(awaitItem())
        }
        viewModel.onAction(WorkshopSetupAction.OnConfirmCodeChange("1234"))
        assertTrue(viewModel.state.value.whatsappConfirm.confirmed)

        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.GH))
        assertFalse(viewModel.state.value.whatsappConfirm.confirmed)
    }

    @Test
    fun `dr congo exposes both zones`() {
        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.CD))
        assertEquals("Africa/Kinshasa", viewModel.state.value.timeZone)
        viewModel.onAction(WorkshopSetupAction.OnTimeZoneSelected("Africa/Lubumbashi"))
        assertEquals("Africa/Lubumbashi", viewModel.state.value.timeZone)
    }

    @Test
    fun `time zone outside the country is ignored`() {
        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.GH))
        viewModel.onAction(WorkshopSetupAction.OnTimeZoneSelected("Africa/Lubumbashi"))
        assertEquals("Africa/Accra", viewModel.state.value.timeZone)
    }

    @Test
    fun `continue saves country, time zone, locale and E164`() = runTest {
        fakeAuth.currentUser = signedInUser()
        viewModel = createViewModel(region = { "GH" }, language = { "fr" })

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ama Stitches"))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("0244123456"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        assertIs<WorkshopSetupEvent.NavigateToHome>(viewModel.events.first())
        assertEquals(Country.GH, fakeUserRepository.lastCountry)
        assertEquals("Africa/Accra", fakeUserRepository.lastTimeZone)
        assertEquals("+233244123456", fakeUserRepository.lastWhatsAppNumber)
        assertEquals("fr", fakeUserRepository.lastLocale)
    }

    @Test
    fun `confirm launch uses the selected country`() = runTest {
        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.GH))
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("024 412 3456"))
        viewModel.events.test {
            viewModel.onAction(WorkshopSetupAction.OnConfirmWhatsAppClick)
            val event = awaitItem()
            assertIs<WorkshopSetupEvent.LaunchWhatsAppConfirm>(event)
            assertEquals("+233244123456", event.phoneE164)
        }
    }

    @Test
    fun `existing user re-entering setup keeps their country over the device region`() = runTest {
        val existing = signedInUser(country = Country.GH).copy(locale = "en-GH")
        fakeAuth.currentUser = existing
        fakeUserRepository.userFlow.value = existing
        viewModel = createViewModel(region = { "NG" }, language = { "fr" })

        assertEquals(Country.GH, viewModel.state.value.country)
        assertEquals("Africa/Accra", viewModel.state.value.timeZone)

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ama Stitches"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        assertIs<WorkshopSetupEvent.NavigateToHome>(viewModel.events.first())
        assertEquals(Country.GH, fakeUserRepository.lastCountry)
        assertEquals("Africa/Accra", fakeUserRepository.lastTimeZone)
        assertEquals("en-GH", fakeUserRepository.lastLocale)
    }

    @Test
    fun `existing user keeps their stored dr congo zone`() {
        val existing = signedInUser(country = Country.CD, timeZone = "Africa/Lubumbashi")
        fakeAuth.currentUser = existing
        fakeUserRepository.userFlow.value = existing
        viewModel = createViewModel(region = { "GH" })

        assertEquals(Country.CD, viewModel.state.value.country)
        assertEquals("Africa/Lubumbashi", viewModel.state.value.timeZone)
    }

    @Test
    fun `existing user doc arriving late does not override a manual pick`() {
        fakeAuth.currentUser = signedInUser()
        val late = MutableSharedFlow<User?>(extraBufferCapacity = 1)
        fakeUserRepository.getUserOverride = { Result.Success(late.first()) }
        viewModel = createViewModel(region = { "NG" })

        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.KE))
        assertTrue(late.tryEmit(signedInUser(country = Country.GH)))

        assertEquals(Country.KE, viewModel.state.value.country)
    }

    @Test
    fun `no existing doc uses the device region`() {
        fakeAuth.currentUser = signedInUser()
        fakeUserRepository.userFlow.value = null
        viewModel = createViewModel(region = { "KE" })
        assertEquals(Country.KE, viewModel.state.value.country)
    }

    @Test
    fun `existing user with a zone outside their country gets the country default`() {
        val existing = signedInUser(country = Country.GH, timeZone = "Africa/Lubumbashi")
        fakeAuth.currentUser = existing
        fakeUserRepository.userFlow.value = existing
        viewModel = createViewModel(region = { "NG" })

        assertEquals(Country.GH, viewModel.state.value.country)
        assertEquals("Africa/Accra", viewModel.state.value.timeZone)
    }

    @Test
    fun `rapid continue taps while the doc read is pending write the profile once`() = runTest {
        fakeAuth.currentUser = signedInUser()
        val gate = CompletableDeferred<Result<User?, DataError.Network>>()
        fakeUserRepository.getUserOverride = { gate.await() }
        viewModel = createViewModel(region = { "NG" })
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ama Stitches"))

        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        assertTrue(viewModel.state.value.isLoading, "button shows busy during the doc wait")
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        viewModel.events.test {
            gate.complete(Result.Success(null))
            assertIs<WorkshopSetupEvent.NavigateToHome>(awaitItem())
            expectNoEvents()
        }
        assertEquals(1, fakeUserRepository.createUserProfileCallCount)
    }

    @Test
    fun `doc arriving during the continue wait is what gets saved`() = runTest {
        fakeAuth.currentUser = signedInUser()
        val late = MutableSharedFlow<User?>(extraBufferCapacity = 1)
        fakeUserRepository.getUserOverride = { Result.Success(late.first()) }
        viewModel = createViewModel(region = { "NG" })
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ama Stitches"))
        // A Ghanaian number: invalid under the device region, valid once the GH doc lands.
        viewModel.onAction(WorkshopSetupAction.OnWhatsAppNumberChange("024 412 3456"))

        viewModel.onAction(WorkshopSetupAction.OnContinueClick)
        assertNull(fakeUserRepository.lastUserId, "nothing written before the read lands")
        assertTrue(late.tryEmit(signedInUser(country = Country.GH)))

        assertIs<WorkshopSetupEvent.NavigateToHome>(viewModel.events.first())
        assertEquals(Country.GH, fakeUserRepository.lastCountry)
        assertEquals("Africa/Accra", fakeUserRepository.lastTimeZone)
        assertEquals("+233244123456", fakeUserRepository.lastWhatsAppNumber)
    }

    @Test
    fun `failed doc read leaves the stored market untouched on continue`() = runTest {
        fakeAuth.currentUser = signedInUser(country = Country.GH)
        fakeUserRepository.getUserOverride = { Result.Error(DataError.Network.UNKNOWN) }
        viewModel = createViewModel(region = { "NG" }, language = { "fr" })

        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ama Stitches"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        assertIs<WorkshopSetupEvent.NavigateToHome>(viewModel.events.first())
        assertEquals("u1", fakeUserRepository.lastUserId)
        assertNull(fakeUserRepository.lastCountry, "country (and so currency) not rewritten")
        assertNull(fakeUserRepository.lastTimeZone)
        assertNull(fakeUserRepository.lastLocale)
    }

    @Test
    fun `failed doc read still saves a country the user picked`() = runTest {
        fakeAuth.currentUser = signedInUser()
        fakeUserRepository.getUserOverride = { Result.Error(DataError.Network.UNKNOWN) }
        viewModel = createViewModel(region = { "NG" })

        viewModel.onAction(WorkshopSetupAction.OnCountrySelected(Country.KE))
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Wanjiru Designs"))
        viewModel.onAction(WorkshopSetupAction.OnContinueClick)

        assertIs<WorkshopSetupEvent.NavigateToHome>(viewModel.events.first())
        assertEquals(Country.KE, fakeUserRepository.lastCountry)
        assertEquals("Africa/Nairobi", fakeUserRepository.lastTimeZone)
    }

    @Test
    fun `timed-out doc read lets continue finish without writing the market`() = runTest {
        fakeAuth.currentUser = signedInUser(country = Country.GH)
        fakeUserRepository.getUserOverride = { awaitCancellation() }
        viewModel = createViewModel(region = { "NG" })
        viewModel.onAction(WorkshopSetupAction.OnBusinessNameChange("Ama Stitches"))

        viewModel.events.test {
            viewModel.onAction(WorkshopSetupAction.OnContinueClick)
            advanceTimeBy(2_000)
            assertNull(fakeUserRepository.lastUserId, "still waiting on the read")
            advanceTimeBy(1_500)
            assertIs<WorkshopSetupEvent.NavigateToHome>(awaitItem())
        }
        assertEquals("u1", fakeUserRepository.lastUserId)
        assertNull(fakeUserRepository.lastCountry)
        assertNull(fakeUserRepository.lastTimeZone)
        assertEquals(Country.NG, viewModel.state.value.country, "device region shown, but not saved")
    }
}
