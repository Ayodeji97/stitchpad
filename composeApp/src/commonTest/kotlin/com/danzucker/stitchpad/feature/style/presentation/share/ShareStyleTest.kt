package com.danzucker.stitchpad.feature.style.presentation.share

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareStyleTest {

    private class FakeSharer {
        var sharedBytes: ByteArray? = null
        var sharedCaption: String? = null
        var callCount = 0
    }

    // ShareStyle's primary constructor takes a fun-interface loader + a suspend
    // `share` lambda, so tests need no platform ImageSharer. See Step 3.
    private fun entitlements(tier: SubscriptionTier) = object : EntitlementsProvider {
        private val flowState = MutableStateFlow(
            UserEntitlements(
                tier = tier,
                customerCap = if (tier == SubscriptionTier.FREE) 15 else Int.MAX_VALUE,
                smartCoinAllowance = if (tier == SubscriptionTier.FREE) 5 else 50,
                isInWelcomeWindow = false,
                welcomeEndsAt = null,
                isWithinWelcomeEndingWarning = false,
                welcomeDaysLeft = null,
                canUseCustomMeasurements = tier != SubscriptionTier.FREE,
            )
        )
        override val flow: StateFlow<UserEntitlements> = flowState
        override fun current() = flowState.value
        override suspend fun awaitHydrated() = flowState.value
    }

    private val style = Style(
        id = "s1", customerId = "c1", description = "Blue agbada",
        photoUrl = "https://example.com/p.jpg", photoStoragePath = "styles/s1.jpg",
        createdAt = 0L, updatedAt = 0L, localPhotoPath = "/local/s1.jpg",
    )

    @Test
    fun shares_local_path_bytes_and_caption() = runTest {
        val fake = FakeSharer()
        val shareStyle = ShareStyle(
            loader = { model -> if (model == "/local/s1.jpg") byteArrayOf(1, 2, 3) else null },
            share = { bytes, caption -> fake.callCount++; fake.sharedBytes = bytes; fake.sharedCaption = caption },
            entitlements = entitlements(SubscriptionTier.FREE),
        )

        val result = shareStyle(style)

        assertTrue(result is Result.Success)
        assertEquals(1, fake.callCount)
        assertEquals("Blue agbada\n\nShared via StitchPad · getstitchpad.com", fake.sharedCaption)
    }

    @Test
    fun falls_back_to_photoUrl_when_no_local_path() = runTest {
        var seenModel: String? = null
        val shareStyle = ShareStyle(
            loader = { model -> seenModel = model; byteArrayOf(9) },
            share = { _, _ -> },
            entitlements = entitlements(SubscriptionTier.PRO),
        )

        shareStyle(style.copy(localPhotoPath = null))

        assertEquals("https://example.com/p.jpg", seenModel)
    }

    @Test
    fun returns_error_and_does_not_share_when_bytes_null() = runTest {
        val fake = FakeSharer()
        val shareStyle = ShareStyle(
            loader = { null },
            share = { bytes, caption -> fake.callCount++; fake.sharedBytes = bytes; fake.sharedCaption = caption },
            entitlements = entitlements(SubscriptionTier.FREE),
        )

        val result = shareStyle(style)

        assertTrue(result is Result.Error)
        assertEquals(DataError.Local.UNKNOWN, result.error)
        assertEquals(0, fake.callCount)
        assertNull(fake.sharedBytes)
    }
}
