package com.danzucker.stitchpad.core.domain.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkshopUidHelperTest {

    @Test
    fun returns_workshop_uid_for_an_owner() = runTest {
        val provider = FakeActiveWorkshopProvider(WorkshopSession.ownerOfSelf("owner-1"))

        assertEquals("owner-1", provider.workshopUidOrNull())
    }

    @Test
    fun returns_owners_uid_for_active_staff() = runTest {
        val provider = FakeActiveWorkshopProvider(
            WorkshopSession("staff-1", "owner-9", StaffRole.STAFF, MembershipStatus.ACTIVE),
        )

        assertEquals("owner-9", provider.workshopUidOrNull())
    }

    @Test
    fun returns_null_when_signed_out() = runTest {
        val provider = FakeActiveWorkshopProvider(WorkshopSession.signedOut())

        assertNull(provider.workshopUidOrNull())
    }
}
