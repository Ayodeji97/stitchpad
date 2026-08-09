package com.danzucker.stitchpad.core.domain.staff

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [rosterDisplayName] (Task 6): the owner's own roster row renders as "You" for the
 * signed-in viewer, everyone else's row renders their real name. Shared by the Team
 * screen's roster rows and the order-detail assign picker.
 */
class RosterDisplayTest {

    private fun member(id: String, name: String, kind: TeamMemberKind) = TeamMember(
        id = id,
        name = name,
        kind = kind,
        colorSeed = 0,
        status = TeamMemberStatus.ACTIVE,
    )

    @Test
    fun `owner viewing their own row sees You`() {
        val owner = member(id = "owner-1", name = "Adaeze Chukwu", kind = TeamMemberKind.OWNER)
        assertEquals(
            "You",
            rosterDisplayName(member = owner, currentAuthUid = "owner-1", youLabel = "You"),
        )
    }

    @Test
    fun `staff viewing the owner row sees the owner's real name`() {
        val owner = member(id = "owner-1", name = "Adaeze Chukwu", kind = TeamMemberKind.OWNER)
        assertEquals(
            "Adaeze Chukwu",
            rosterDisplayName(member = owner, currentAuthUid = "staff-uid", youLabel = "You"),
        )
    }

    @Test
    fun `a named member is unaffected when the viewer is someone else`() {
        // NAMED placeholders are account-less (Task 5), so their id can never equal a real
        // signed-in auth uid in practice — this exercises the ordinary "not me" path.
        val named = member(id = "fake-member-0", name = "Ngozi Eze", kind = TeamMemberKind.NAMED)
        assertEquals(
            "Ngozi Eze",
            rosterDisplayName(member = named, currentAuthUid = "owner-1", youLabel = "You"),
        )
    }

    @Test
    fun `a null viewer uid never matches any row`() {
        val owner = member(id = "owner-1", name = "Adaeze Chukwu", kind = TeamMemberKind.OWNER)
        assertEquals(
            "Adaeze Chukwu",
            rosterDisplayName(member = owner, currentAuthUid = null, youLabel = "You"),
        )
    }

    @Test
    fun `staff viewing their own row sees You`() {
        val staff = member(id = "staff-1", name = "Chidi Okafor", kind = TeamMemberKind.STAFF)
        assertEquals(
            "You",
            rosterDisplayName(member = staff, currentAuthUid = "staff-1", youLabel = "You"),
        )
    }
}
