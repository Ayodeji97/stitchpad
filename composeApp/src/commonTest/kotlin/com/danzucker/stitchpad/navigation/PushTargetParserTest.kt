package com.danzucker.stitchpad.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushTargetParserTest {
    @Test fun parsesInbox() =
        assertEquals(PushTargetParser.Parsed(DeepLinkTarget.INBOX), PushTargetParser.parse(mapOf("target" to "inbox")))
    @Test fun parsesToCollect() =
        assertEquals(PushTargetParser.Parsed(DeepLinkTarget.TO_COLLECT), PushTargetParser.parse(mapOf("target" to "to_collect")))
    @Test fun parsesOrderWithId() =
        assertEquals(PushTargetParser.Parsed(DeepLinkTarget.ORDER, "o1"), PushTargetParser.parse(mapOf("target" to "order", "orderId" to "o1")))
    @Test fun orderWithoutIdIsNull() = assertNull(PushTargetParser.parse(mapOf("target" to "order")))
    @Test fun parsesDashboard() =
        assertEquals(PushTargetParser.Parsed(DeepLinkTarget.DASHBOARD), PushTargetParser.parse(mapOf("target" to "dashboard")))
    @Test fun parsesFoundingTailors() =
        assertEquals(
            PushTargetParser.Parsed(DeepLinkTarget.FOUNDING_TAILORS),
            PushTargetParser.parse(mapOf("target" to "founding_tailors")),
        )

    // An unrecognised target must stay null, never throw: that is what lets the
    // server introduce a new campaign target before every client has updated. The
    // notification still displays; the tap just opens the app normally.
    @Test fun unknownTargetIsNull() = assertNull(PushTargetParser.parse(mapOf("target" to "wat")))
    @Test fun missingTargetIsNull() = assertNull(PushTargetParser.parse(emptyMap()))
}
