package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_bank_account_name_required
import stitchpad.composeapp.generated.resources.error_bank_account_number_invalid
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelBankTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        repo: FakeUserRepository,
        existingUser: User? = null,
    ): EditProfileViewModel {
        val authRepo = FakeAuthRepository().apply {
            currentUser = User(
                id = "u1",
                email = "u@x",
                displayName = "U",
                businessName = "Ade Fashions",
                phoneNumber = null,
                whatsappNumber = null,
                avatarColorIndex = 0,
            )
        }
        repo.userFlow.value = existingUser
        return EditProfileViewModel(
            authRepository = authRepo,
            userRepository = repo,
            savedStateHandle = SavedStateHandle(),
            compressLogo = { it },
        )
    }

    @Test
    fun `loadCurrentProfile hydrates bank fields from Firestore user`() = runTest {
        val repo = FakeUserRepository()
        val seeded = User(
            id = "u1",
            email = "u@x",
            displayName = "U",
            businessName = "Ade Fashions",
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
            bankName = "GTBank",
            bankAccountName = "Fola Joy Enterprises",
            bankAccountNumber = "0123456789",
        )
        val vm = buildVm(repo, existingUser = seeded)
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        val state = vm.state.value
        assertEquals("GTBank", state.bankName)
        assertEquals("Fola Joy Enterprises", state.bankAccountName)
        assertEquals("0123456789", state.bankAccountNumber)
        assertEquals("GTBank", state.originalBankName)
        collectJob.cancel()
    }

    @Test
    fun `partial bank input flags errors and blocks save`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(repo)
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        vm.onAction(EditProfileAction.OnBankNameChange("GTBank"))
        vm.onAction(EditProfileAction.OnSaveClick)
        runCurrent()

        val state = vm.state.value
        assertEquals(
            Res.string.error_bank_account_name_required,
            state.bankAccountNameError,
        )
        assertEquals(
            Res.string.error_bank_account_number_invalid,
            state.bankAccountNumberError,
        )
        // Save bailed before updateProfile.
        assertNull(repo.lastUserId)
        collectJob.cancel()
    }

    @Test
    fun `clearing existing bank fields writes nulls so Firestore drops the keys`() = runTest {
        val repo = FakeUserRepository()
        val seeded = User(
            id = "u1",
            email = "u@x",
            displayName = "U",
            businessName = "Ade Fashions",
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
            bankName = "GTBank",
            bankAccountName = "Fola Joy Enterprises",
            bankAccountNumber = "0123456789",
        )
        val vm = buildVm(repo, existingUser = seeded)
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        vm.onAction(EditProfileAction.OnBankNameChange(""))
        vm.onAction(EditProfileAction.OnBankAccountNameChange(""))
        vm.onAction(EditProfileAction.OnBankAccountNumberChange(""))
        vm.onAction(EditProfileAction.OnSaveClick)
        runCurrent()

        assertNull(repo.lastBankName)
        assertNull(repo.lastBankAccountName)
        assertNull(repo.lastBankAccountNumber)
        collectJob.cancel()
    }

    @Test
    fun `valid bank trio writes to repository on save`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(repo)
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        // Business name is required for save to clear validation. Set it explicitly
        // since the test does not hydrate a firestoreUser.
        vm.onAction(EditProfileAction.OnBusinessNameChange("Ade Fashions"))
        vm.onAction(EditProfileAction.OnBankNameChange("GTBank"))
        vm.onAction(EditProfileAction.OnBankAccountNameChange("Fola Joy Enterprises"))
        vm.onAction(EditProfileAction.OnBankAccountNumberChange("0123456789"))
        vm.onAction(EditProfileAction.OnSaveClick)
        runCurrent()

        assertEquals("GTBank", repo.lastBankName)
        assertEquals("Fola Joy Enterprises", repo.lastBankAccountName)
        assertEquals("0123456789", repo.lastBankAccountNumber)
        collectJob.cancel()
    }

    @Test
    fun `account number input filters non-digits and caps at 10`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(repo)
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        vm.onAction(EditProfileAction.OnBankAccountNumberChange("01-23 ABC 456 789 1234567"))
        runCurrent()

        assertEquals("0123456789", vm.state.value.bankAccountNumber)
        collectJob.cancel()
    }
}
