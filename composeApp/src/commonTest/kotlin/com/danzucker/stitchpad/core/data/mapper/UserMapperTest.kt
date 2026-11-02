package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.UserDto
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            whatsappNumber = "+2348064816695",
            avatarColorIndex = 3
        )

        val user = dto.toUser()

        assertEquals("uid-123", user.id)
        assertEquals("ade@gmail.com", user.email)
        assertEquals("Ade Fashions", user.displayName)
        assertEquals("Ade Tailoring", user.businessName)
        assertEquals("+2348012345678", user.phoneNumber)
        assertEquals("+2348064816695", user.whatsappNumber)
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
            whatsappNumber = null,
            avatarColorIndex = 0
        )

        val user = dto.toUser()

        assertNull(user.businessName)
        assertNull(user.phoneNumber)
        assertNull(user.whatsappNumber)
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
            whatsappNumber = "+2348012345678",
            avatarColorIndex = 5
        )

        val dto = user.toUserDto()

        assertEquals("uid-456", dto.id)
        assertEquals("tailor@gmail.com", dto.email)
        assertEquals("Tailor Shop", dto.displayName)
        assertEquals("Best Tailors", dto.businessName)
        assertEquals("+2349087654321", dto.phoneNumber)
        assertEquals("+2348012345678", dto.whatsappNumber)
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
            whatsappNumber = null,
            avatarColorIndex = 0
        )

        val dto = user.toUserDto()

        assertNull(dto.businessName)
        assertNull(dto.phoneNumber)
        assertNull(dto.whatsappNumber)
    }

    // --- Round-trip ---

    @Test
    fun roundTripDtoToUserToDto() {
        // dailyPushEnabled and announcementsPushEnabled are nullable in the DTO; absent (null)
        // maps to User's resolved Boolean, then toUserDto() writes it back as an explicit value —
        // so round-trip produces explicit true (both inherit dailyDigestEmailEnabled=true) rather
        // than null. We assert the resolved values are correct rather than strict DTO equality.
        val original = UserDto(
            id = "uid-789",
            email = "test@test.com",
            displayName = "Test User",
            businessName = "Test Biz",
            phoneNumber = "+1234567890",
            whatsappNumber = "+2348012345678",
            avatarColorIndex = 2
        )

        val roundTripped = original.toUser().toUserDto()

        // Legacy docs without country fields also come back with the resolved NG defaults.
        assertEquals(
            original.copy(
                dailyPushEnabled = true,
                announcementsPushEnabled = true,
                country = "NG",
                currency = "NGN",
                timeZone = "Africa/Lagos",
            ),
            roundTripped,
        )
    }

    @Test
    fun roundTripUserToDtoToUser() {
        val original = User(
            id = "uid-101",
            email = "round@trip.com",
            displayName = "Round Trip",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 7
        )

        val roundTripped = original.toUserDto().toUser()

        assertEquals(original, roundTripped)
    }

    @Test
    fun whatsappConfirmed_roundTripsBothDirections() {
        val dto = UserDto(id = "u1", whatsappNumber = "+2348031234567", whatsappConfirmed = true)
        val user = dto.toUser()
        assertTrue(user.whatsappConfirmed)

        val backToDto = user.toUserDto()
        assertTrue(backToDto.whatsappConfirmed)
    }

    @Test
    fun whatsappConfirmed_defaultsFalseWhenAbsent() {
        assertFalse(UserDto(id = "u2").toUser().whatsappConfirmed)
    }

    @Test
    fun dailyPushEnabled_defaultsTrue_andRoundTrips() {
        assertTrue(UserDto(id = "u1").toUser().dailyPushEnabled)          // absent push, absent email → both default true → true
        assertFalse(UserDto(id = "u1", dailyPushEnabled = false).toUser().dailyPushEnabled) // explicit push=false wins
        // inherit: absent push inherits digest opt-out (same as backend productionDigestIO)
        assertFalse(UserDto(id = "u1", dailyDigestEmailEnabled = false).toUser().dailyPushEnabled)
        // explicit push wins over digest value
        assertTrue(UserDto(id = "u1", dailyPushEnabled = true, dailyDigestEmailEnabled = false).toUser().dailyPushEnabled)
    }

    @Test
    fun announcementsPushEnabled_inheritsThroughBothOlderFlags() {
        // Absent everywhere → default on.
        assertTrue(UserDto(id = "u1").toUser().announcementsPushEnabled)
        // An explicit announcements value always wins.
        assertFalse(UserDto(id = "u1", announcementsPushEnabled = false).toUser().announcementsPushEnabled)
        // No migration needed: a tailor who already opted out of daily push must not be
        // silently opted into tips. Mirrors resolveAnnouncementsEnabled in engagementPush.ts.
        assertFalse(UserDto(id = "u1", dailyPushEnabled = false).toUser().announcementsPushEnabled)
        // Falls all the way through to the email digest preference.
        assertFalse(UserDto(id = "u1", dailyDigestEmailEnabled = false).toUser().announcementsPushEnabled)
        // Explicit announcements=true beats both older opt-outs.
        assertTrue(
            UserDto(
                id = "u1",
                announcementsPushEnabled = true,
                dailyPushEnabled = false,
                dailyDigestEmailEnabled = false,
            ).toUser().announcementsPushEnabled,
        )
        // Muting tips must NOT mute the daily deadline reminder — the whole point of
        // giving announcements their own field.
        assertTrue(UserDto(id = "u1", announcementsPushEnabled = false).toUser().dailyPushEnabled)
    }

    @Test
    fun referralCode_mapsFromDtoToDomain() {
        val dto = UserDto(id = "u1", email = "a@b.com", displayName = "Ada", referralCode = "CODE0")
        val user = dto.toUser()
        assertEquals("CODE0", user.referralCode)
    }

    @Test
    fun referralCode_isNullWhenAbsent() {
        val dto = UserDto(id = "u1", email = "a@b.com", displayName = "Ada")
        assertNull(dto.toUser().referralCode)
    }

    // --- Country fields ---

    @Test
    fun legacyDocWithoutCountryFallsBackToNigeria() {
        val user = UserDto(id = "u").toUser()
        assertEquals(Country.NG, user.country)
        assertEquals(Currency.NGN, user.currency)
        assertEquals("Africa/Lagos", user.timeZone)
        assertNull(user.locale)
    }

    @Test
    fun ghanaDocMaps() {
        val user = UserDto(country = "GH", currency = "GHS", timeZone = "Africa/Accra").toUser()
        assertEquals(Country.GH, user.country)
        assertEquals(Currency.GHS, user.currency)
        assertEquals("Africa/Accra", user.timeZone)
    }

    @Test
    fun unknownOrLowercaseCodes() {
        assertEquals(Country.NG, UserDto(country = "zz").toUser().country)
        assertEquals(Country.GH, UserDto(country = "gh").toUser().country)
        assertEquals(Currency.GHS, UserDto(country = "GH", currency = null).toUser().currency)
    }

    @Test
    fun missingTimeZoneUsesTheCountryDefault() {
        assertEquals("Africa/Nairobi", UserDto(country = "KE", timeZone = null).toUser().timeZone)
    }

    @Test
    fun countryFieldsRoundTrip() {
        val user = User(
            id = "u",
            email = "a@b.com",
            displayName = "Ada",
            businessName = null,
            phoneNumber = null,
            whatsappNumber = null,
            avatarColorIndex = 0,
            country = Country.CD,
            currency = Country.CD.currency,
            timeZone = "Africa/Lubumbashi",
            locale = "fr-CD",
        )
        assertEquals(user, user.toUserDto().toUser())
    }
}
