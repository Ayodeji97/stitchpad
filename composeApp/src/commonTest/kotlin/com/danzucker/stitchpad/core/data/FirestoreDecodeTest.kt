package com.danzucker.stitchpad.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class FirestoreDecodeTest {

    @Test
    fun `decodeDocOrLog returns the decoded value on success`() {
        val result = decodeDocOrLog(tag = "Test", docId = "doc-1") { 42 }

        assertEquals(42, result)
    }

    @Test
    fun `decodeDocOrLog returns null when decode throws`() {
        val result = decodeDocOrLog<Int>(tag = "Test", docId = "doc-1") {
            error("corrupt document")
        }

        assertNull(result)
    }

    @Test
    fun `docIdHash is stable and does not expose the raw id`() {
        val userDerivedId = "agbada-with-embroidery"

        assertEquals(docIdHash(userDerivedId), docIdHash(userDerivedId))
        assertNotEquals(userDerivedId, docIdHash(userDerivedId))
    }
}
