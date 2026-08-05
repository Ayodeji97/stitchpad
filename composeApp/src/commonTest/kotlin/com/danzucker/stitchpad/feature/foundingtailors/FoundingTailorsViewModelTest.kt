package com.danzucker.stitchpad.feature.foundingtailors

import com.danzucker.stitchpad.core.data.repository.FakeUserRepository
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.foundingtailors.presentation.FoundingTailorsAction
import com.danzucker.stitchpad.feature.foundingtailors.presentation.FoundingTailorsEvent
import com.danzucker.stitchpad.feature.foundingtailors.presentation.FoundingTailorsViewModel
import com.danzucker.stitchpad.feature.referral.data.FakeReferralRepository
import com.danzucker.stitchpad.feature.referral.domain.FoundingTailorsStanding
import com.danzucker.stitchpad.feature.referral.domain.ReferralLink
import app.cash.turbine.test
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Fixed message resolver stand-in for the production `getString(Res.string.
 * founding_tailors_share_message, url)` resolver. Real `getString()` calls throw
 * "Method getSystem in android.content.res.Resources not mocked" under plain-JVM
 * ViewModel unit tests (no Robolectric in this module) — see
 * MeasurementDetailViewModelTest's `fakeShareLabels` for the established precedent.
 */
private fun fakeShareMessageResolver(url: String): String = "Join StitchPad Founding Tailors: $url"

@OptIn(ExperimentalCoroutinesApi::class)
class FoundingTailorsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun userWith(referralCode: String?): FakeUserRepository = FakeUserRepository().apply {
        userFlow.value = User(
            id = "u1",
            email = "dan@example.com",
            displayName = "Dan",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
            referralCode = referralCode,
        )
    }

    private fun authWithUid(uid: String): FakeAuthRepository = FakeAuthRepository().apply {
        currentUser = User(
            id = uid,
            email = "dan@example.com",
            displayName = "Dan",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }

    private fun viewModel(
        referralRepository: FakeReferralRepository = FakeReferralRepository(),
        userRepository: FakeUserRepository = userWith("CODE0"),
        authRepository: FakeAuthRepository = authWithUid("u1"),
    ) = FoundingTailorsViewModel(
        referralRepository = referralRepository,
        userRepository = userRepository,
        authRepository = authRepository,
        shareMessageResolver = ::fakeShareMessageResolver,
    )

    @Test
    fun `LoadLink uses existing referral code without minting`() = runTest {
        val repo = FakeReferralRepository()
        val vm = viewModel(referralRepository = repo, userRepository = userWith("CODE0"))

        vm.onAction(FoundingTailorsAction.LoadLink)

        assertEquals("https://link.getstitchpad.com/r/CODE0", vm.state.value.referralUrl)
        assertEquals(0, repo.referralLinkCallCount)
        assertEquals(false, vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `LoadLink with null code mints a new referral link`() = runTest {
        val repo = FakeReferralRepository()
        repo.referralLinkResult = Result.Success(
            ReferralLink(
                code = "MINTED1",
                url = "https://link.getstitchpad.com/r/MINTED1",
                playUrl = "https://play.google.com/store/apps/details?id=com.danzucker.stitchpad&referrer=ref%3DMINTED1",
            ),
        )
        val vm = viewModel(referralRepository = repo, userRepository = userWith(null))

        vm.onAction(FoundingTailorsAction.LoadLink)

        assertEquals(1, repo.referralLinkCallCount)
        assertEquals("https://link.getstitchpad.com/r/MINTED1", vm.state.value.referralUrl)
    }

    @Test
    fun `LoadLink maps a mint failure to an error UiText`() = runTest {
        val repo = FakeReferralRepository()
        repo.referralLinkResult = Result.Error(DataError.Network.NO_INTERNET)
        val vm = viewModel(referralRepository = repo, userRepository = userWith(null))

        vm.onAction(FoundingTailorsAction.LoadLink)

        assertNull(vm.state.value.referralUrl)
        assertNotNull(vm.state.value.error)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `OpenLeaderboard emits OpenUrl carrying the code`() = runTest {
        val vm = viewModel(userRepository = userWith("CODE0"))
        vm.onAction(FoundingTailorsAction.LoadLink)

        vm.events.test {
            vm.onAction(FoundingTailorsAction.OpenLeaderboard)
            assertEquals(
                FoundingTailorsEvent.OpenUrl("https://getstitchpad.com/founding-tailors?code=CODE0"),
                awaitItem(),
            )
        }
    }

    @Test
    fun `LoadLink populates standing from the repository`() = runTest {
        val repo = FakeReferralRepository()
        repo.standingResult = Result.Success(
            FoundingTailorsStanding(monthPoints = 2, monthRank = 1, allTimePoints = 9, allTimeRank = 3),
        )
        val vm = viewModel(referralRepository = repo, userRepository = userWith("CODE0"))

        vm.onAction(FoundingTailorsAction.LoadLink)

        assertEquals(1, repo.standingCallCount)
        assertEquals("CODE0", repo.lastStandingCode)
        assertEquals(
            FoundingTailorsStanding(monthPoints = 2, monthRank = 1, allTimePoints = 9, allTimeRank = 3),
            vm.state.value.standing,
        )
    }

    @Test
    fun `LoadLink leaves standing null and no error when the standing fetch fails`() = runTest {
        val repo = FakeReferralRepository()
        repo.standingResult = Result.Error(DataError.Network.UNKNOWN)
        val vm = viewModel(referralRepository = repo, userRepository = userWith("CODE0"))

        vm.onAction(FoundingTailorsAction.LoadLink)

        assertNull(vm.state.value.standing)
        assertNull(vm.state.value.error) // a failed standing fetch must NOT surface an error
        assertEquals("https://link.getstitchpad.com/r/CODE0", vm.state.value.referralUrl)
    }

    @Test
    fun `ShareLink emits ShareText with the link substituted`() = runTest {
        val vm = viewModel(userRepository = userWith("CODE0"))
        vm.onAction(FoundingTailorsAction.LoadLink)

        vm.events.test {
            vm.onAction(FoundingTailorsAction.ShareLink)
            assertEquals(
                FoundingTailorsEvent.ShareText(
                    "Join StitchPad Founding Tailors: https://link.getstitchpad.com/r/CODE0",
                ),
                awaitItem(),
            )
        }
    }
}
