package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserMapperTest {

    // --- UserDto to User ---

    @Test
    fun dtoToUserMapsAllFields() {
        val dto = UserDto(
            id = "uid-123",
            email = "ade@gmail.com",
            displayName = "Ade Fashions",
            businessName = "Ade Tailoring",
            phoneNumber = "+2348012345678",
            avatarColorIndex = 3
        )

        val user = dto.toUser()

        assertEquals("uid-123", user.id)
        assertEquals("ade@gmail.com", user.email)
        assertEquals("Ade Fashions", user.displayName)
        assertEquals("Ade Tailoring", user.businessName)
        assertEquals("+2348012345678", user.phoneNumber)
        assertEquals(3, user.avatarColorIndex)
    }

    @Test
    fun dtoToUserPreservesNullOptionalFields() {
        val dto = UserDto(
            id = "uid-123",
            email = "ade@gmail.com",
            displayName = "Ade",
            businessName = null,
            phoneNumber = null,
            avatarColorIndex = 0
        )

        val user = dto.toUser()

        assertNull(user.businessName)
        assertNull(user.phoneNumber)
    }

    // --- User to UserDto ---

    @Test
    fun userToDtoMapsAllFields() {
        val user = User(
            id = "uid-456",
            email = "tailor@gmail.com",
            displayName = "Tailor Shop",
            businessName = "Best Tailors",
            phoneNumber = "+2349087654321",
            avatarColorIndex = 5
        )

        val dto = user.toUserDto()

        assertEquals("uid-456", dto.id)
        assertEquals("tailor@gmail.com", dto.email)
        assertEquals("Tailor Shop", dto.displayName)
        assertEquals("Best Tailors", dto.businessName)
        assertEquals("+2349087654321", dto.phoneNumber)
        assertEquals(5, dto.avatarColorIndex)
    }

    @Test
    fun userToDtoPreservesNullOptionalFields() {
        val user = User(
            id = "uid-456",
            email = "tailor@gmail.com",
            displayName = "Tailor",
            businessName = null,
            phoneNumber = null,
            avatarColorIndex = 0
        )

        val dto = user.toUserDto()

        assertNull(dto.businessName)
        assertNull(dto.phoneNumber)
    }

    // --- Round-trip ---

    @Test
    fun roundTripDtoToUserToDto() {
        val original = UserDto(
            id = "uid-789",
            email = "test@test.com",
            displayName = "Test User",
            businessName = "Test Biz",
            phoneNumber = "+1234567890",
            avatarColorIndex = 2
        )

        val roundTripped = original.toUser().toUserDto()

        assertEquals(original, roundTripped)
    }

    @Test
    fun roundTripUserToDtoToUser() {
        val original = User(
            id = "uid-101",
            email = "round@trip.com",
            displayName = "Round Trip",
            businessName = null,
            phoneNumber = null,
            avatarColorIndex = 7
        )

        val roundTripped = original.toUserDto().toUser()

        assertEquals(original, roundTripped)
    }
}
