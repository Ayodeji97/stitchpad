package com.danzucker.stitchpad.core.data.staff

import com.danzucker.stitchpad.core.data.dto.TeamMemberDto
import com.danzucker.stitchpad.core.domain.staff.TeamMemberKind
import com.danzucker.stitchpad.core.domain.staff.TeamMemberStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamMemberMapperTest {

    @Test
    fun toTeamMember_carriesTheDocumentId_notADtoField() {
        val dto = TeamMemberDto(name = "Chidi", kind = "named", colorSeed = 3, status = "active")

        val member = dto.toTeamMember("doc-1")

        assertEquals("doc-1", member.id)
    }

    @Test
    fun toTeamMember_mapsEnumsCaseInsensitively() {
        val staffDto = TeamMemberDto(name = "Chidi", kind = "STAFF", status = "ACTIVE")
        val namedDto = TeamMemberDto(name = "Ngozi", kind = "Named", status = "Archived")

        val staffMember = staffDto.toTeamMember("doc-1")
        val namedMember = namedDto.toTeamMember("doc-2")

        assertEquals(TeamMemberKind.STAFF, staffMember.kind)
        assertEquals(TeamMemberStatus.ACTIVE, staffMember.status)
        assertEquals(TeamMemberKind.NAMED, namedMember.kind)
        assertEquals(TeamMemberStatus.ARCHIVED, namedMember.status)
    }

    @Test
    fun toTeamMember_unknownKind_defaultsToNamed() {
        val dto = TeamMemberDto(name = "Chidi", kind = "robot")

        val member = dto.toTeamMember("doc-1")

        assertEquals(TeamMemberKind.NAMED, member.kind)
    }

    @Test
    fun toTeamMember_unknownStatus_defaultsToActive() {
        val dto = TeamMemberDto(name = "Chidi", status = "on-leave")

        val member = dto.toTeamMember("doc-1")

        assertEquals(TeamMemberStatus.ACTIVE, member.status)
    }

    @Test
    fun toTeamMember_blankName_fallsBackToTheDocId() {
        val dto = TeamMemberDto(name = "   ")

        val member = dto.toTeamMember("doc-1")

        assertEquals("doc-1", member.name)
    }

    @Test
    fun toTeamMember_carriesColorSeedThrough() {
        val dto = TeamMemberDto(name = "Chidi", colorSeed = 7)

        val member = dto.toTeamMember("doc-1")

        assertEquals(7, member.colorSeed)
    }
}
