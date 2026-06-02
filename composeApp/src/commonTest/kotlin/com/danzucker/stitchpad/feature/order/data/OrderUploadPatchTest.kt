package com.danzucker.stitchpad.feature.order.data

import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.StatusChange
import com.danzucker.stitchpad.core.domain.model.StyleImageRef
import com.danzucker.stitchpad.core.domain.model.StyleImageSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderUploadPatchTest {
    @Test
    fun applyCompletedOrderUploadPatches_preservesCompletedUrlsForStalePendingRefs() {
        val order = orderWithImages(
            stylePath = "users/u/orders/o/styles/item-1-a.jpg",
            fabricPath = "users/u/orders/o/fabrics/item-1-b.jpg",
        )

        val patched = applyCompletedOrderUploadPatches(order) { path ->
            when (path) {
                "users/u/orders/o/styles/item-1-a.jpg" -> "https://cdn/style.jpg"
                "users/u/orders/o/fabrics/item-1-b.jpg" -> "https://cdn/fabric.jpg"
                else -> null
            }
        }

        val item = patched.items.single()
        val style = item.styleImages.single()
        val fabric = item.fabricImages.single()
        assertEquals("https://cdn/style.jpg", style.photoUrl)
        assertEquals(ImageSyncState.SYNCED, style.syncState)
        assertNull(style.localPhotoPath)
        assertEquals("https://cdn/fabric.jpg", fabric.photoUrl)
        assertEquals(ImageSyncState.SYNCED, fabric.syncState)
        assertNull(fabric.localPhotoPath)
    }

    private fun orderWithImages(
        stylePath: String,
        fabricPath: String,
    ): Order = Order(
        id = "o",
        userId = "u",
        customerId = "c",
        customerName = "Customer",
        items = listOf(
            OrderItem(
                id = "item-1",
                garmentType = GarmentType.SHIRT,
                description = "Blue shirt",
                price = 1_000.0,
                styleImages = listOf(
                    StyleImageRef(
                        source = StyleImageSource.UPLOADED,
                        photoUrl = "",
                        photoStoragePath = stylePath,
                        syncState = ImageSyncState.PENDING,
                        localPhotoPath = "/tmp/style.jpg",
                    )
                ),
                fabricImages = listOf(
                    FabricImageRef(
                        photoUrl = "",
                        photoStoragePath = fabricPath,
                        syncState = ImageSyncState.PENDING,
                        localPhotoPath = "/tmp/fabric.jpg",
                    )
                ),
            )
        ),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = listOf(StatusChange(OrderStatus.PENDING, 1L)),
        totalPrice = 1_000.0,
        deadline = null,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
