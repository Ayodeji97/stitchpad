package com.danzucker.stitchpad.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserStaffInviteTest {

    @Test
    fun parses_the_https_universal_link() {
        assertEquals(
            "K7QP3RM9",
            DeepLinkParser.parseStaffInvite("https://link.getstitchpad.com/join?code=K7QP3RM9"),
        )
    }

    @Test
    fun parses_the_custom_scheme() {
        assertEquals("K7QP3RM9", DeepLinkParser.parseStaffInvite("stitchpad://join?code=K7QP3RM9"))
    }

    @Test
    fun normalises_lowercase_and_separators() {
        assertEquals(
            "K7QP3RM9",
            DeepLinkParser.parseStaffInvite("https://link.getstitchpad.com/join?code=k7qp-3rm9"),
        )
    }

    @Test
    fun returns_null_for_a_non_join_link() {
        assertNull(DeepLinkParser.parseStaffInvite("https://link.getstitchpad.com/claim?code=K7QP3RM9"))
        assertNull(DeepLinkParser.parseStaffInvite("https://link.getstitchpad.com/joinzzz?code=K7QP3RM9"))
    }

    @Test
    fun returns_null_when_code_is_missing_or_empty() {
        assertNull(DeepLinkParser.parseStaffInvite("https://link.getstitchpad.com/join"))
        assertNull(DeepLinkParser.parseStaffInvite("https://link.getstitchpad.com/join?code="))
    }

    @Test
    fun returns_null_for_a_null_url() {
        assertNull(DeepLinkParser.parseStaffInvite(null))
    }
}
