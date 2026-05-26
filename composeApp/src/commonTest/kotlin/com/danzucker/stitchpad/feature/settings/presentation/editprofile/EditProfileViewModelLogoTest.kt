package com.danzucker.stitchpad.feature.settings.presentation.editprofile

import androidx.lifecycle.SavedStateHandle
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelLogoTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun validPngBytes() =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(100)

    private fun buildVm(
        repo: FakeUserRepository,
        userId: String = "u1",
    ): EditProfileViewModel {
        val authRepo = FakeAuthRepository().apply {
            currentUser = User(
                id = userId,
                email = "u@x",
                displayName = "U",
                businessName = null,
                phoneNumber = null,
                whatsappNumber = null,
                avatarColorIndex = 0,
            )
        }
        return EditProfileViewModel(
            authRepository = authRepo,
            userRepository = repo,
            savedStateHandle = SavedStateHandle(),
        )
    }

    @Test
    fun `picking valid logo invokes updateBrandLogo with new url and path`() = runTest {
        val repo = FakeUserRepository().apply {
            uploadLogoResult = Result.Success("https://x/logo.jpg" to "users/u1/branding/logo.jpg")
        }
        val vm = buildVm(repo)
        // Activate the WhileSubscribed stateIn so onStart fires and state updates propagate
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        vm.onAction(EditProfileAction.OnLogoPicked(validPngBytes()))
        runCurrent()

        assertEquals("https://x/logo.jpg" to "users/u1/branding/logo.jpg", repo.lastBrandLogoUpdate)
        assertIs<LogoUploadState.Uploaded>(vm.state.value.logo)
        collectJob.cancel()
    }

    @Test
    fun `OnLogoRemoveClick opens confirmation dialog without touching logo state`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(repo)
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        vm.onAction(EditProfileAction.OnLogoRemoveClick)
        runCurrent()

        assertTrue(vm.state.value.showRemoveLogoDialog)
        assertEquals(null, repo.lastBrandLogoUpdate)
        assertTrue(repo.deletedLogoPaths.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `OnLogoRemoveDismiss closes dialog without clearing the logo`() = runTest {
        val repo = FakeUserRepository().apply {
            uploadLogoResult = Result.Success("https://x/logo.jpg" to "users/u1/branding/logo.jpg")
        }
        val vm = buildVm(repo)
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        // Put the logo into Uploaded state
        vm.onAction(EditProfileAction.OnLogoPicked(validPngBytes()))
        runCurrent()

        vm.onAction(EditProfileAction.OnLogoRemoveClick)
        runCurrent()
        assertTrue(vm.state.value.showRemoveLogoDialog)

        vm.onAction(EditProfileAction.OnLogoRemoveDismiss)
        runCurrent()

        assertFalse(vm.state.value.showRemoveLogoDialog)
        assertIs<LogoUploadState.Uploaded>(vm.state.value.logo)
        collectJob.cancel()
    }

    @Test
    fun `OnLogoRemoveConfirm clears logo state and deletes storage object`() = runTest {
        // Seed the userFlow with a User that already has a logo before building the VM
        val repo = FakeUserRepository()
        repo.userFlow.value = User(
            id = "u1",
            email = "u@x",
            displayName = "U",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
            businessLogoUrl = "https://x/logo.jpg",
            businessLogoStoragePath = "users/u1/branding/logo.jpg",
        )
        val vm = buildVm(repo)
        // Activate state flow and allow initial load to populate originalLogoStoragePath
        val collectJob = backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        vm.onAction(EditProfileAction.OnLogoRemoveClick)
        runCurrent()
        vm.onAction(EditProfileAction.OnLogoRemoveConfirm)
        runCurrent()

        assertEquals(null to null, repo.lastBrandLogoUpdate)
        assertTrue("users/u1/branding/logo.jpg" in repo.deletedLogoPaths)
        assertIs<LogoUploadState.Empty>(vm.state.value.logo)
        assertEquals(null, vm.state.value.originalLogoUrl)
        assertEquals(null, vm.state.value.originalLogoStoragePath)
        collectJob.cancel()
    }
}
