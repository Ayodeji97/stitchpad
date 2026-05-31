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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelWhatsappTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun userWith(
        whatsapp: String = "",
        confirmed: Boolean = false,
    ): User = User(
        id = "u1",
        email = "u@x",
        displayName = "U",
        businessName = "Ade Fashions",
        phoneNumber = null,
        whatsappNumber = whatsapp.ifBlank { null },
        avatarColorIndex = 0,
        whatsappConfirmed = confirmed,
    )

    private fun buildVm(
        repo: FakeUserRepository,
        existingUser: User? = null,
        confirmCodeGenerator: () -> String = { "0000" },
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
            confirmCodeGenerator = confirmCodeGenerator,
        )
    }

    @Test
    fun `confirmFlow_correctCode_confirmsState`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(
            repo = repo,
            existingUser = userWith(whatsapp = "+2348031234567", confirmed = false),
            confirmCodeGenerator = { "1234" },
        )
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        vm.onAction(EditProfileAction.OnConfirmWhatsAppClick)
        runCurrent()

        vm.onAction(EditProfileAction.OnConfirmCodeChange("1234"))
        runCurrent()

        assertTrue(vm.state.value.whatsappConfirm.confirmed)
        collectJob.cancel()
    }

    @Test
    fun `saveUnrelatedField_preservesConfirmedTrue`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(
            repo = repo,
            existingUser = userWith(whatsapp = "+2348031234567", confirmed = true),
        )
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        // Change an unrelated field (business name) and save — confirmed must carry through
        vm.onAction(EditProfileAction.OnBusinessNameChange("New Name"))
        vm.onAction(EditProfileAction.OnSaveClick)
        runCurrent()

        assertEquals(true, repo.lastWhatsappConfirmed)
        collectJob.cancel()
    }

    @Test
    fun `editingNumber_resetsConfirmed`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(
            repo = repo,
            existingUser = userWith(whatsapp = "+2348031234567", confirmed = true),
        )
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        // Confirm is initially true after load
        assertTrue(vm.state.value.whatsappConfirm.confirmed)

        // Editing the number should reset confirmed
        vm.onAction(EditProfileAction.OnWhatsappChange("8031234568"))
        runCurrent()

        assertFalse(vm.state.value.whatsappConfirm.confirmed)
        collectJob.cancel()
    }

    @Test
    fun `unifiedValidation_rejectsNumberOldCheckAccepted`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(repo = repo, existingUser = null)
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        // "2000000000" — 10 digits, old digit-count check would accept (7..15),
        // but the unified Nigerian mobile E.164 validator rejects it (leading pair must be [789][01])
        vm.onAction(EditProfileAction.OnWhatsappChange("2000000000"))
        vm.onAction(EditProfileAction.OnWhatsappBlur)
        runCurrent()

        assertNotNull(vm.state.value.whatsappError)
        collectJob.cancel()
    }
}
