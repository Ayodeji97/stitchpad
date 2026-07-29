package com.danzucker.stitchpad.core.data.session

import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopClaims
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

    private fun owner(uid: String) = WorkshopClaims(authUid = uid, workshopUid = null, role = null)
    private fun staff(uid: String, workshop: String) =
        WorkshopClaims(authUid = uid, workshopUid = workshop, role = "staff")

    @Test
    fun a_user_with_no_claims_resolves_to_owner_of_self() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(owner("user-9"))
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)

        val session = provider.awaitHydrated()

        assertEquals("user-9", session.workshopUid)
        assertTrue(session.isOwner)
    }

    @Test
    fun a_staff_claim_resolves_to_active_staff_on_the_owners_tree() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(staff("staff-1", "owner-9"))
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)

        val session = provider.awaitHydrated()

        assertEquals("staff-1", session.authUid)
        assertEquals("owner-9", session.workshopUid)
        assertTrue(session.isActiveStaff)
    }

    @Test
    fun a_claim_change_re_resolves_the_session() = runTest {
        // Simulates the post-approval token refresh: owner-of-self → staff.
        val claims = MutableStateFlow<WorkshopClaims?>(owner("staff-1"))
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)
        assertTrue(provider.awaitHydrated().isOwner)

        claims.value = staff("staff-1", "owner-9")
        runCurrent()

        assertEquals(StaffRole.STAFF, provider.current().role)
        assertEquals("owner-9", provider.current().workshopUid)
    }

    @Test
    fun signed_out_resolves_immediately_and_does_not_hang() = runTest {
        // Regression: an unhydrated signed-out state made awaitHydrated()/
        // workshopUidOrNull() suspend forever. Signed-out is resolved → null.
        val claims = MutableStateFlow<WorkshopClaims?>(null)
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)

        val uid = withTimeout(1_000) { provider.workshopUidOrNull() }

        assertNull(uid)
    }

    @Test
    fun sign_out_after_sign_in_still_resolves_without_hanging() = runTest {
        val claims = MutableStateFlow<WorkshopClaims?>(owner("user-9"))
        val provider = FirebaseActiveWorkshopProvider(claims, backgroundScope)
        assertEquals("user-9", provider.awaitHydrated().workshopUid)

        claims.value = null
        runCurrent()

        val uid = withTimeout(1_000) { provider.workshopUidOrNull() }
        assertNull(uid)
    }
}
