package com.danzucker.stitchpad.feature.staff.presentation.team

import com.danzucker.stitchpad.core.domain.staff.TeamMemberKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [rosterRowShowsMenu] (Task 6): only a NAMED roster row is rename/archivable — a STAFF
 * row's lifecycle is revoke (not archive), and an OWNER row is a fixed, pinned-first
 * member the owner can never rename or archive out of their own roster.
 */
class RosterRowMenuTest {

    @Test
    fun `a named member shows the rename-archive menu`() {
        assertTrue(rosterRowShowsMenu(TeamMemberKind.NAMED))
    }

    @Test
    fun `the owner row exposes no rename-archive menu`() {
        assertFalse(rosterRowShowsMenu(TeamMemberKind.OWNER))
    }

    @Test
    fun `a staff row exposes no rename-archive menu`() {
        assertFalse(rosterRowShowsMenu(TeamMemberKind.STAFF))
    }
}
