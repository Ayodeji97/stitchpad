package com.danzucker.stitchpad.core.config.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityInviteTest {

    @Test
    fun validWhatsAppUrl_returnsTrue() {
        assertTrue(isUsableCommunityInviteUrl("https://chat.whatsapp.com/abc123"))
    }

    @Test
    fun nullUrl_returnsFalse() {
        assertFalse(isUsableCommunityInviteUrl(null))
    }

    @Test
    fun blankUrl_returnsFalse() {
        assertFalse(isUsableCommunityInviteUrl(""))
    }

    @Test
    fun httpScheme_returnsFalse() {
        assertFalse(isUsableCommunityInviteUrl("http://chat.whatsapp.com/abc"))
    }

    @Test
    fun otherHttpsHost_returnsFalse() {
        assertFalse(isUsableCommunityInviteUrl("https://evil.com/x"))
    }

    @Test
    fun missingScheme_returnsFalse() {
        assertFalse(isUsableCommunityInviteUrl("chat.whatsapp.com/x"))
    }

    @Test
    fun barePrefixWithoutCode_returnsFalse() {
        assertFalse(isUsableCommunityInviteUrl("https://chat.whatsapp.com/"))
    }

    @Test
    fun prefixWithBlankCode_returnsFalse() {
        assertFalse(isUsableCommunityInviteUrl("https://chat.whatsapp.com/   "))
    }

    @Test
    fun surroundingWhitespace_isTrimmedAndAccepted() {
        assertTrue(isUsableCommunityInviteUrl("  https://chat.whatsapp.com/abc123  "))
    }
}
