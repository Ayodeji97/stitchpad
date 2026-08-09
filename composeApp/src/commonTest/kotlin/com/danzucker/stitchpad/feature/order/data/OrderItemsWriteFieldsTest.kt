package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Guards [orderItemsWriteFields] — the write payload for
 * [FirebaseOrderRepository.updateItems] (Phase 2b, Task 3) — so the items-only base
 * write can never (re)introduce `price` into the base doc, structurally enforcing the
 * same guarantee the Firestore rules already assert for the staff work-fields branch.
 */
class OrderItemsWriteFieldsTest {

    private val item = OrderItem(
        id = "i1",
        garmentType = GarmentType.SHIRT,
        description = "desc",
        price = 9_999.0, // domain still carries price — the write must not
        quantity = 2,
        fabricName = "Ankara",
        styleImages = listOf(
            StyleImageRef(source = StyleImageSource.UPLOADED, photoUrl = "su", photoStoragePath = "sp"),
        ),
        fabricImages = listOf(
            FabricImageRef(photoUrl = "fu", photoStoragePath = "fp", syncState = ImageSyncState.PENDING),
        ),
    )

    @Test
    fun payload_isItemsAndUpdatedAtOnly() {
        val fields = orderItemsWriteFields(listOf(item), now = 42L)
        assertEquals(setOf("items", "updatedAt"), fields.keys)
        assertEquals(42L, fields["updatedAt"])
    }

    /**
     * EXACT key set, not just "no price": an item map that grew a key the base DTO
     * doesn't have would ride into the staff work-fields write and could re-introduce
     * money (or any owner-only field) under `items`. Pinning the whole set — all 14
     * [com.danzucker.stitchpad.core.data.dto.OrderItemBaseDto] fields, nothing more —
     * makes any drift in [OrderItemBaseDto.toFirestoreMap] a test failure rather than
     * a silent wire-shape change. (Together with the Firestore rules payload-shape
     * tests in `functions/src/__tests__/firestore.rules.test.ts` this is the net that
     * catches an outbox/repository write shape drifting off the staff whitelist.)
     */
    @Test
    fun itemMap_hasExactlyTheBaseDtoKeys() {
        val fields = orderItemsWriteFields(listOf(item), now = 42L)
        @Suppress("UNCHECKED_CAST")
        val itemMap = (fields["items"] as List<Map<String, Any?>>).single()
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
            itemMap.keys,
        )
    }

    @Test
    fun itemMaps_carryNoPriceAnywhere() {
        val fields = orderItemsWriteFields(listOf(item), now = 42L)
        @Suppress("UNCHECKED_CAST")
        val itemMap = (fields["items"] as List<Map<String, Any?>>).single()
        assertFalse(itemMap.containsKey("price"))
        assertEquals("i1", itemMap["id"])
        assertEquals("SHIRT", itemMap["garmentType"])
        assertEquals(2, itemMap["quantity"])
        assertEquals("Ankara", itemMap["fabricName"])
        @Suppress("UNCHECKED_CAST")
        val fabric = (itemMap["fabricImages"] as List<Map<String, Any?>>).single()
        assertEquals("fp", fabric["photoStoragePath"])
        assertEquals("PENDING", fabric["syncState"])
        @Suppress("UNCHECKED_CAST")
        val style = (itemMap["styleImages"] as List<Map<String, Any?>>).single()
        assertEquals("UPLOADED", style["source"])
        assertEquals("sp", style["photoStoragePath"])
    }
}
