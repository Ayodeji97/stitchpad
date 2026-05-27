package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.branding.presentation.LogoUploadState
import com.danzucker.stitchpad.feature.onboarding.data.FakeOnboardingPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkshopSetupViewModelLogoTest {

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
    ): WorkshopSetupViewModel {
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
        return WorkshopSetupViewModel(
            userRepository = repo,
            authRepository = authRepo,
            onboardingPreferences = FakeOnboardingPreferences(),
            // Identity compressor: the real one would hit Android BitmapFactory,
            // which throws "Stub!" in non-Robolectric JVM unit tests. We're not
            // testing image decoding here — just the VM's compress→upload wiring.
            compressLogo = { it },
        )
    }

    @Test
    fun `picking valid logo transitions Empty -- Uploading -- Uploaded`() = runTest {
        val repo = FakeUserRepository().apply {
            uploadLogoResult = Result.Success("https://x/logo.jpg" to "users/u1/branding/logo.jpg")
        }
        val vm = buildVm(repo)

        vm.state.test {
            assertEquals(LogoUploadState.Empty, awaitItem().logo)
            vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
            val uploading = awaitItem().logo
            assertIs<LogoUploadState.Uploading>(uploading)
            val uploaded = awaitItem().logo
            assertIs<LogoUploadState.Uploaded>(uploaded)
            assertEquals("https://x/logo.jpg", uploaded.url)
        }
    }

    @Test
    fun `picking oversize bytes emits snackbar event and stays Empty`() = runTest {
        val repo = FakeUserRepository()
        val vm = buildVm(repo)

        vm.events.test {
            // Bigger than the validator's 5MB cap so this trips TooLarge before
            // hitting the compressor. Anything ≤ 5MB now passes validation and
            // would be exercised by the compress-path tests instead.
            vm.onAction(WorkshopSetupAction.OnLogoPicked(ByteArray(6 * 1024 * 1024)))
            val event = awaitItem()
            assertIs<WorkshopSetupEvent.ShowSnackbar>(event)
        }
        assertEquals(LogoUploadState.Empty, vm.state.value.logo)
    }

    @Test
    fun `upload failure transitions to Failed`() = runTest {
        val repo = FakeUserRepository().apply {
            uploadLogoResult = Result.Error(DataError.Network.UNKNOWN)
        }
        val vm = buildVm(repo)

        vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
        assertIs<LogoUploadState.Failed>(vm.state.value.logo)
    }

    @Test
    fun `Skip after Uploaded deletes the Storage object`() = runTest {
        val repo = FakeUserRepository().apply {
            uploadLogoResult = Result.Success("u" to "users/u1/branding/logo.jpg")
        }
        val vm = buildVm(repo)

        vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
        vm.onAction(WorkshopSetupAction.OnSkipClick)

        assertTrue("users/u1/branding/logo.jpg" in repo.deletedLogoPaths)
    }

    @Test
    fun `Continue while Uploaded writes URL via updateBrandLogo`() = runTest {
        val repo = FakeUserRepository().apply {
            uploadLogoResult = Result.Success("https://x" to "users/u1/branding/logo.jpg")
        }
        val vm = buildVm(repo)

        vm.onAction(WorkshopSetupAction.OnBusinessNameChange("Esther"))
        vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
        vm.onAction(WorkshopSetupAction.OnContinueClick)

        assertEquals("https://x" to "users/u1/branding/logo.jpg", repo.lastBrandLogoUpdate)
    }

    @Test
    fun `Skip while Continue awaits upload cancels Continue and persists nothing`() = runTest {
        // Gate the compressor so the upload sits in flight indefinitely. This lets
        // Continue enter its pending.join() phase (isAwaitingLogo=true) before
        // Skip runs — exactly the race the bugfix targets.
        val gate = CompletableDeferred<Unit>()
        val repo = FakeUserRepository().apply {
            uploadLogoResult = Result.Success("https://x" to "users/u1/branding/logo.jpg")
        }
        val authRepo = FakeAuthRepository().apply {
            currentUser = User(
                id = "u1",
                email = "u@x",
                displayName = "U",
                businessName = null,
                phoneNumber = null,
                whatsappNumber = null,
                avatarColorIndex = 0,
            )
        }
        val vm = WorkshopSetupViewModel(
            userRepository = repo,
            authRepository = authRepo,
            onboardingPreferences = FakeOnboardingPreferences(),
            compressLogo = { gate.await(); it },
        )

        vm.onAction(WorkshopSetupAction.OnBusinessNameChange("Esther"))
        vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
        vm.onAction(WorkshopSetupAction.OnContinueClick)
        assertTrue(vm.state.value.isAwaitingLogo, "Continue should be awaiting the gated upload")

        vm.onAction(WorkshopSetupAction.OnSkipClick)

        // Continue was cancelled mid-await; no profile write, no logo URL persisted.
        assertNull(repo.lastUserId, "createUserProfile must not run after Skip")
        assertNull(repo.lastBrandLogoUpdate, "updateBrandLogo must not run after Skip")
        assertEquals(false, vm.state.value.isAwaitingLogo)
    }

    @Test
    fun `picking a new logo while Uploaded re-uploads via the same flow`() = runTest {
        val repo = FakeUserRepository().apply {
            uploadLogoResult = Result.Success("https://x/v1.jpg" to "users/u1/branding/logo.jpg")
        }
        val vm = buildVm(repo)

        vm.state.test {
            assertEquals(LogoUploadState.Empty, awaitItem().logo)

            vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
            assertIs<LogoUploadState.Uploading>(awaitItem().logo)
            assertIs<LogoUploadState.Uploaded>(awaitItem().logo)

            vm.onAction(WorkshopSetupAction.OnLogoPicked(validPngBytes()))
            assertIs<LogoUploadState.Uploading>(awaitItem().logo)
            assertIs<LogoUploadState.Uploaded>(awaitItem().logo)
        }
    }
}
