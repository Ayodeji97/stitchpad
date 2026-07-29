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
    @Test fun unknownTargetIsNull() = assertNull(PushTargetParser.parse(mapOf("target" to "wat")))
}
