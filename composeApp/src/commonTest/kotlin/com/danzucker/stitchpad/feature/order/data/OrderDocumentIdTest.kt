package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.data.dto.OrderDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The Firestore DOCUMENT id is authoritative over the DTO's `id` field. A doc whose
 * `id` field is missing/blank decoded as `id = ""`, and two such docs made the keyed
 * Orders LazyColumn throw `Key "" was already used` — crashing the whole screen.
 */
class OrderDocumentIdTest {

    @Test
    fun withDocumentId_blankIdField_takesTheDocumentId() {
        val decoded = OrderDto(id = "", customerName = "Ada")

        assertEquals("order-1", decoded.withDocumentId("order-1").id)
    }

    @Test
    fun withDocumentId_matchingId_returnsTheSameInstance() {
        val decoded = OrderDto(id = "order-1")

        assertSame(decoded, decoded.withDocumentId("order-1"))
    }

    @Test
    fun withDocumentId_staleIdField_documentIdWins() {
        val decoded = OrderDto(id = "stale-id", customerName = "Ada")

        val stamped = decoded.withDocumentId("order-1")

        assertEquals("order-1", stamped.id)
        assertEquals("Ada", stamped.customerName)
    }

    @Test
    fun withDocumentId_twoBlankIdDocs_produceDistinctKeys() {
        val first = OrderDto(id = "").withDocumentId("order-1")
        val second = OrderDto(id = "").withDocumentId("order-2")

        assertEquals(2, setOf(first.id, second.id).size)
    }
}
