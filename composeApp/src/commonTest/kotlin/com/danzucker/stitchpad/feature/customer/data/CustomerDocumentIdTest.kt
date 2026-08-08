package com.danzucker.stitchpad.feature.customer.data

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Customer twin of [com.danzucker.stitchpad.feature.order.data.OrderDocumentIdTest]:
 * the Firestore document id is authoritative, so blank/stale `id` fields can never
 * collide as LazyColumn keys in the Customers list.
 */
class CustomerDocumentIdTest {

    @Test
    fun withDocumentId_blankIdField_takesTheDocumentId() {
        val decoded = CustomerDto(id = "", name = "Ada")

        assertEquals("cust-1", decoded.withDocumentId("cust-1").id)
    }

    @Test
    fun withDocumentId_matchingId_returnsTheSameInstance() {
        val decoded = CustomerDto(id = "cust-1")

        assertSame(decoded, decoded.withDocumentId("cust-1"))
    }

    @Test
    fun withDocumentId_staleIdField_documentIdWins() {
        val decoded = CustomerDto(id = "stale-id", name = "Ada")

        val stamped = decoded.withDocumentId("cust-1")

        assertEquals("cust-1", stamped.id)
        assertEquals("Ada", stamped.name)
    }

    @Test
    fun withDocumentId_twoBlankIdDocs_produceDistinctKeys() {
        val first = CustomerDto(id = "").withDocumentId("cust-1")
        val second = CustomerDto(id = "").withDocumentId("cust-2")

        assertEquals(2, setOf(first.id, second.id).size)
    }
}
