package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.session.workshopUidOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirebaseActiveWorkshopProviderTest {

    @Test
    fun signed_in_uid_resolves_to_owner_of_self() = runTest {
        val uids = MutableStateFlow<String?>("user-9")
        val provider = FirebaseActiveWorkshopProvider(uids, backgroundScope)

        val session = provider.awaitHydrated()

        assertEquals("user-9", session.workshopUid)
        assertTrue(session.isOwner)
    }

    @Test
    fun signed_out_resolves_immediately_and_does_not_hang() = runTest {
        // Regression for the codex P2: an unhydrated signed-out state made
        // awaitHydrated()/workshopUidOrNull() suspend forever. The signed-out
        // session is resolved, so it must return null promptly instead.
        val uids = MutableStateFlow<String?>(null)
        val provider = FirebaseActiveWorkshopProvider(uids, backgroundScope)

        val uid = withTimeout(1_000) { provider.workshopUidOrNull() }

        assertNull(uid)
    }

    @Test
    fun sign_out_after_sign_in_still_resolves_without_hanging() = runTest {
        val uids = MutableStateFlow<String?>("user-9")
        val provider = FirebaseActiveWorkshopProvider(uids, backgroundScope)
        assertEquals("user-9", provider.awaitHydrated().workshopUid)

        uids.value = null
        runCurrent()

        val uid = withTimeout(1_000) { provider.workshopUidOrNull() }
        assertNull(uid)
    }
}
