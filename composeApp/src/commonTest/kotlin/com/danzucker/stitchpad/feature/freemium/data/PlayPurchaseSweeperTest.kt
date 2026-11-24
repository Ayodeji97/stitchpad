package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.User
import com.danzucker.stitchpad.core.domain.session.FakeCurrentCountryProvider
import com.danzucker.stitchpad.feature.auth.data.FakeAuthRepository
import com.danzucker.stitchpad.feature.freemium.domain.StoreProductIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayPurchaseSweeperTest {

    private val client = FakeNativePlayBillingClient()
    private val verifier = FakePlayPurchaseVerifier()
    private val auth = FakeAuthRepository().apply {
        currentUser = User(
            id = "uid-1",
            email = "ama@example.com",
            displayName = "Ama",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
        )
    }
    private val repo = PlayBillingPaymentRepository(client, verifier, auth)

    private fun sweeper(country: Country) = PlayPurchaseSweeper(
        repository = repo,
        client = client,
        countryProvider = FakeCurrentCountryProvider(country),
        authRepository = auth,
    )

    private fun purchased(token: String, acknowledged: Boolean = false) = PlayPurchase(
        productIds = listOf(StoreProductIds.PRO_MONTHLY),
        purchaseToken = token,
        state = PlayPurchaseState.PURCHASED,
        isAcknowledged = acknowledged,
    )

    @Test
    fun sweep_verifiesPurchasedUnacknowledged() = runTest {
        client.activePurchases = Result.Success(listOf(purchased("tok-new"), purchased("tok-old", acknowledged = true)))
        sweeper(Country.GH).sweep()
        assertEquals(listOf("tok-new"), verifier.verifiedTokens)
    }

    @Test
    fun sweep_skipsWhenProviderNotPlay() = runTest {
        client.activePurchases = Result.Success(listOf(purchased("tok-new")))
        sweeper(Country.NG).sweep()
        assertTrue(verifier.verifiedTokens.isEmpty())
    }

    @Test
    fun sweep_skipsWhenSignedOut() = runTest {
        client.activePurchases = Result.Success(listOf(purchased("tok-new")))
        auth.currentUser = null
        sweeper(Country.GH).sweep()
        assertTrue(verifier.verifiedTokens.isEmpty())
    }

    @Test
    fun sweep_onPurchaseUpdate_verifies() = runTest(UnconfinedTestDispatcher()) {
        sweeper(Country.GH).observePurchaseUpdates(backgroundScope)
        runCurrent()

        // A cash payment clears while the app is open: Play pushes the update and
        // queryPurchases now lists it as PURCHASED but not acknowledged.
        client.activePurchases = Result.Success(listOf(purchased("tok-cleared")))
        client.updates.emit(purchased("tok-cleared"))
        runCurrent()

        assertEquals(listOf("tok-cleared"), verifier.verifiedTokens)
    }
}
