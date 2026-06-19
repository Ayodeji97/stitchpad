package com.danzucker.stitchpad.feature.gift.presentation.redeem

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.freemium.domain.BillingCadence
import com.danzucker.stitchpad.feature.gift.domain.GiftError
import com.danzucker.stitchpad.feature.gift.domain.GiftLink
import com.danzucker.stitchpad.feature.gift.domain.GiftRepository
import com.danzucker.stitchpad.feature.gift.domain.RedeemedGift
import com.danzucker.stitchpad.navigation.PendingDeepLinkHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
class RedeemGiftViewModelTest {

    private lateinit var gift: FakeGiftRepository
    private lateinit var auth: FakeAuthRepository
    private lateinit var pendingDeepLink: PendingDeepLinkHolder

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        gift = FakeGiftRepository()
        auth = FakeAuthRepository()
        pendingDeepLink = PendingDeepLinkHolder()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deepLink_code_prefills_and_opens_accept_sheet() = runTest {
        pendingDeepLink.setClaimGift("ABC234")
        val vm = newVm()
        runCurrent()
        assertEquals("ABC234", vm.state.value.code)
        assertTrue(vm.state.value.showAcceptSheet)
        // Consumed once — a second screen open does not re-trigger.
        assertFalse(newVm().state.value.showAcceptSheet)
    }

    @Test
    fun account_email_is_loaded_for_the_accept_sheet() = runTest {
        auth.currentUser = userWithEmail("ada@example.com")
        val vm = newVm()
        runCurrent()
        assertEquals("ada@example.com", vm.state.value.accountEmail)
    }

    @Test
    fun redeemClick_opens_sheet_only_with_a_code() = runTest {
        val vm = newVm()
        vm.onAction(RedeemGiftAction.OnRedeemClick)
        runCurrent()
        assertFalse(vm.state.value.showAcceptSheet)

        vm.onAction(RedeemGiftAction.OnCodeChange("xyz"))
        vm.onAction(RedeemGiftAction.OnRedeemClick)
        runCurrent()
        assertTrue(vm.state.value.showAcceptSheet)
    }

    @Test
    fun confirmAccept_success_emits_redeemed_and_normalizes_code() = runTest {
        gift.result = Result.Success(RedeemedGift(SubscriptionTier.PRO, BillingCadence.MONTHLY))
        val vm = newVm()
        vm.onAction(RedeemGiftAction.OnCodeChange("abc 234"))
        val eventDeferred = async { vm.events.first() }

        vm.onAction(RedeemGiftAction.OnConfirmAccept)
        runCurrent()

        assertIs<RedeemGiftEvent.Redeemed>(eventDeferred.await())
        assertEquals("ABC234", gift.lastCode) // uppercased + whitespace stripped
        assertFalse(vm.state.value.showAcceptSheet)
        assertFalse(vm.state.value.isRedeeming)
    }

    @Test
    fun confirmAccept_error_emits_snackbar() = runTest {
        gift.result = Result.Error(GiftError.ALREADY_CLAIMED)
        val vm = newVm()
        vm.onAction(RedeemGiftAction.OnCodeChange("CODE"))
        val eventDeferred = async { vm.events.first() }

        vm.onAction(RedeemGiftAction.OnConfirmAccept)
        runCurrent()

        assertIs<RedeemGiftEvent.ShowSnackbar>(eventDeferred.await())
        assertFalse(vm.state.value.isRedeeming)
    }

    @Test
    fun back_emits_navigateBack() = runTest {
        val vm = newVm()
        val eventDeferred = async { vm.events.first() }
        vm.onAction(RedeemGiftAction.OnBack)
        runCurrent()
        assertIs<RedeemGiftEvent.NavigateBack>(eventDeferred.await())
    }

    private fun newVm(): RedeemGiftViewModel = RedeemGiftViewModel(
        giftRepository = gift,
        authRepository = auth,
        pendingDeepLink = pendingDeepLink,
    )

    private fun userWithEmail(email: String) = User(
        id = "uid",
        email = email,
        displayName = "Ada",
        businessName = null,
        phoneNumber = null,
        whatsappNumber = null,
        avatarColorIndex = 0,
    )

    private class FakeGiftRepository : GiftRepository {
        var result: Result<RedeemedGift, GiftError> =
            Result.Success(RedeemedGift(SubscriptionTier.PRO, BillingCadence.MONTHLY))
        var lastCode: String? = null

        override suspend fun redeemGift(code: String): Result<RedeemedGift, GiftError> {
            lastCode = code
            return result
        }

        override suspend fun getOrCreateGiftLink(): Result<GiftLink, GiftError> =
            Result.Success(GiftLink(token = "TOK", url = "https://getstitchpad.com/gift/TOK"))
    }
}
