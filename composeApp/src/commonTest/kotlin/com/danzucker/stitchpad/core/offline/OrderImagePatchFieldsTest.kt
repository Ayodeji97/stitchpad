package com.danzucker.stitchpad.core.offline

import com.danzucker.stitchpad.core.data.dto.FabricImageRefDto
import com.danzucker.stitchpad.core.data.dto.OrderItemDto
import com.danzucker.stitchpad.core.data.dto.StyleImageRefDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards [orderImagePatchFields] — the Firestore payload the upload outbox writes
 * after an order style/fabric photo finishes uploading.
 *
 * Why this test exists (whole-branch review, Critical): the patch used to write a
 * whole `OrderBaseDto` with `set(merge = true)`. GitLive encodes defaults, so EVERY
 * base key rode on the wire; on a pre-Phase-2a order doc (no `assignedMemberId` /
 * `assignedMemberName`, older ones also no `subStatus` / `archivedAt`) the merge ADDED
 * those keys, pushing `diff().affectedKeys()` outside the staff work-fields whitelist
 * (`status`, `subStatus`, `statusHistory`, `updatedAt`, `items`, `notes`) — so a staff
 * member's photo upload was permanently permission-denied with no visible error.
 *
 * The payload must therefore be items+updatedAt ONLY, in the exact money-free shape
 * [com.danzucker.stitchpad.feature.order.data.orderItemsWriteFields] uses. The
 * matching wire-shape assertions live in the rules tests
 * (`functions/src/__tests__/firestore.rules.test.ts`, "staff outbox photo patch").
 */
class OrderImagePatchFieldsTest {

    private val fabricRef = FabricImageRefDto(
        photoUrl = "",
        photoStoragePath = "orders/o1/fabric-1.jpg",
        syncState = "PENDING",
    )
    private val styleRef = StyleImageRefDto(
        source = "UPLOADED",
        photoUrl = null,
        photoStoragePath = "orders/o1/style-1.jpg",
        syncState = "PENDING",
    )
    private val item = OrderItemDto(
        id = "i1",
        garmentType = "SHIRT",
        description = "desc",
        price = 9_999.0, // the read DTO still carries price — the write must not
        quantity = 2,
        fabricName = "Ankara",
        styleImages = listOf(styleRef),
        fabricImages = listOf(fabricRef),
        stylePhotoStoragePath = styleRef.photoStoragePath,
        fabricPhotoStoragePath = fabricRef.photoStoragePath,
    )
    private val otherItem = OrderItemDto(id = "i2", garmentType = "TROUSERS")

    private fun fabricPatch(
        items: List<OrderItemDto> = listOf(item, otherItem),
        itemId: String? = "i1",
        storagePath: String = fabricRef.photoStoragePath,
    ) = orderImagePatchFields(
        items = items,
        itemId = itemId,
        storagePath = storagePath,
        downloadUrl = "https://cdn/fabric.jpg",
        isFabric = true,
        now = 42L,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.itemMaps(): List<Map<String, Any?>> =
        this["items"] as List<Map<String, Any?>>

    @Test
    fun payload_isItemsAndUpdatedAtOnly() {
        val fields = requireNotNull(fabricPatch())
        assertEquals(setOf("items", "updatedAt"), fields.keys)
        assertEquals(42L, fields["updatedAt"])
    }

    @Test
    fun itemMaps_carryNoPriceAndExactlyTheBaseDtoKeys() {
        val fields = requireNotNull(fabricPatch())
        fields.itemMaps().forEach { map ->
            assertFalse(map.containsKey("price"), "outbox patch leaked price: $map")
            assertEquals(
                setOf(
                    "id",
                    "garmentType",
                    "customGarmentName",
                    "description",
                    "quantity",
                    "measurementId",
                    "fabricName",
                    "styleImages",
                    "fabricImages",
                    "styleId",
                    "stylePhotoUrl",
                    "stylePhotoStoragePath",
                    "fabricPhotoUrl",
                    "fabricPhotoStoragePath",
                ),
                map.keys,
            )
        }
    }

    @Test
    fun fabricRef_isMarkedSyncedWithTheDownloadUrl_andLegacyFieldFollows() {
        val fields = requireNotNull(fabricPatch())
        val patched = fields.itemMaps().first { it["id"] == "i1" }
        @Suppress("UNCHECKED_CAST")
        val ref = (patched["fabricImages"] as List<Map<String, Any?>>).single()
        assertEquals("https://cdn/fabric.jpg", ref["photoUrl"])
        assertEquals("SYNCED", ref["syncState"])
        assertEquals("https://cdn/fabric.jpg", patched["fabricPhotoUrl"])
    }

    @Test
    fun untargetedItems_ridePayloadUnchanged() {
        val fields = requireNotNull(fabricPatch())
        val untouched = fields.itemMaps().first { it["id"] == "i2" }
        assertEquals("TROUSERS", untouched["garmentType"])
        @Suppress("UNCHECKED_CAST")
        assertTrue((untouched["fabricImages"] as List<Map<String, Any?>>).isEmpty())
    }

    @Test
    fun styleRef_isMarkedSyncedWithTheDownloadUrl_andLegacyFieldFollows() {
        val fields = requireNotNull(
            orderImagePatchFields(
                items = listOf(item),
                itemId = "i1",
                storagePath = styleRef.photoStoragePath!!,
                downloadUrl = "https://cdn/style.jpg",
                isFabric = false,
                now = 7L,
            ),
        )
        val patched = fields.itemMaps().single()
        @Suppress("UNCHECKED_CAST")
        val ref = (patched["styleImages"] as List<Map<String, Any?>>).single()
        assertEquals("https://cdn/style.jpg", ref["photoUrl"])
        assertEquals("SYNCED", ref["syncState"])
        assertEquals("https://cdn/style.jpg", patched["stylePhotoUrl"])
    }

    // --- "no matching pending ref" cases: null, so the caller aborts + retries ---

    @Test
    fun unknownStoragePath_yieldsNoPayload() {
        assertNull(fabricPatch(storagePath = "orders/o1/gone.jpg"))
    }

    @Test
    fun unknownItemId_yieldsNoPayload() {
        assertNull(fabricPatch(itemId = "nope"))
        assertNull(fabricPatch(itemId = null))
    }

    @Test
    fun legacySingleFieldMatchAlone_yieldsNoPayload() {
        // Only the legacy `fabricPhotoStoragePath` matches, no entry in `fabricImages`
        // — the pre-fix transaction treated this as "pending ref unavailable" too.
        val legacyOnly = item.copy(fabricImages = emptyList())
        assertNull(fabricPatch(items = listOf(legacyOnly)))
    }
}
