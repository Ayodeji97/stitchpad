package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.model.defaultTimeZone
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_whatsapp_invalid_for_country
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun user(
        country: Country = Country.NG,
        timeZone: String = country.defaultTimeZone,
        whatsapp: String? = null,
        confirmed: Boolean = false,
    ) = User(
        id = "u1",
        email = "u@x",
        displayName = "U",
        businessName = "Ade Fashions",
        phoneNumber = null,
        whatsappNumber = whatsapp,
        avatarColorIndex = 0,
        whatsappConfirmed = confirmed,
        country = country,
        timeZone = timeZone,
    )

    private fun TestScope.loadedVm(repo: FakeUserRepository, existing: User): EditProfileViewModel {
        val authRepo = FakeAuthRepository().apply { currentUser = user() }
        repo.userFlow.value = existing
        val vm = EditProfileViewModel(
            authRepository = authRepo,
            userRepository = repo,
            savedStateHandle = SavedStateHandle(),
            compressLogo = { it },
            confirmCodeGenerator = { "1234" },
        )
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    @Test
    fun `loads user's country`() = runTest {
        val vm = loadedVm(FakeUserRepository(), user(country = Country.GH))

        assertEquals(Country.GH, vm.state.value.country)
        assertEquals(Country.GH.defaultTimeZone, vm.state.value.timeZone)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `changing country revalidates whatsapp`() = runTest {
        // A valid Nigerian number is not a valid Ghanaian one.
        val vm = loadedVm(FakeUserRepository(), user(whatsapp = "+2348031234567"))
        assertNull(vm.state.value.whatsappError)

        vm.onAction(EditProfileAction.OnCountrySelected(Country.GH))
        runCurrent()

        assertEquals(Res.string.error_whatsapp_invalid_for_country, vm.state.value.whatsappError)
        assertEquals(Country.GH, vm.state.value.country)
        assertEquals(Country.GH.defaultTimeZone, vm.state.value.timeZone)
        assertFalse(vm.state.value.isCountryPickerOpen)
    }

    @Test
    fun `save sends country and zone`() = runTest {
        val repo = FakeUserRepository()
        val vm = loadedVm(repo, user())

        vm.onAction(EditProfileAction.OnCountrySelected(Country.GH))
        vm.onAction(EditProfileAction.OnSaveClick)
        runCurrent()

        assertEquals(Country.GH, repo.lastCountry)
        assertEquals(Country.GH.defaultTimeZone, repo.lastTimeZone)
    }

    @Test
    fun `save sends picked zone for multi-zone country`() = runTest {
        val repo = FakeUserRepository()
        val vm = loadedVm(repo, user())

        vm.onAction(EditProfileAction.OnCountrySelected(Country.CD))
        vm.onAction(EditProfileAction.OnTimeZoneSelected("Africa/Lubumbashi"))
        vm.onAction(EditProfileAction.OnSaveClick)
        runCurrent()

        assertEquals(Country.CD, repo.lastCountry)
        assertEquals("Africa/Lubumbashi", repo.lastTimeZone)
    }

    @Test
    fun `ordinary save leaves country and zone unchanged`() = runTest {
        val repo = FakeUserRepository()
        val vm = loadedVm(repo, user(country = Country.GH))

        vm.onAction(EditProfileAction.OnBusinessNameChange("New Name"))
        vm.onAction(EditProfileAction.OnSaveClick)
        runCurrent()

        assertNull(repo.lastCountry)
        assertNull(repo.lastTimeZone)
    }

    @Test
    fun `changing the whatsapp country resets whatsappConfirmed`() = runTest {
        val vm = loadedVm(
            FakeUserRepository(),
            user(whatsapp = "+2348031234567", confirmed = true),
        )
        assertTrue(vm.state.value.whatsappConfirm.confirmed)

        vm.onAction(EditProfileAction.OnCountrySelected(Country.GH))
        runCurrent()

        assertFalse(vm.state.value.whatsappConfirm.confirmed)
    }

    @Test
    fun `picking the same country keeps confirmation`() = runTest {
        val vm = loadedVm(
            FakeUserRepository(),
            user(whatsapp = "+2348031234567", confirmed = true),
        )

        vm.onAction(EditProfileAction.OnCountrySelected(Country.NG))
        runCurrent()

        assertTrue(vm.state.value.whatsappConfirm.confirmed)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `whatsapp is validated and saved against the selected country`() = runTest {
        val repo = FakeUserRepository()
        val vm = loadedVm(repo, user(country = Country.GH))

        vm.onAction(EditProfileAction.OnWhatsappChange("0241234567"))
        vm.onAction(EditProfileAction.OnSaveClick)
        runCurrent()

        assertNull(vm.state.value.whatsappError)
        assertEquals("+233241234567", repo.lastWhatsAppNumber)
    }
}
