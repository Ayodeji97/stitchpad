package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.FabricImageRef
import com.danzucker.stitchpad.core.domain.model.ImageSyncState
import com.danzucker.stitchpad.feature.order.presentation.detail.components.fabricReferenceImages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FabricReferenceImagesTest {

    private fun ref(
        photoUrl: String,
        localPhotoPath: String? = null,
        syncState: ImageSyncState = ImageSyncState.SYNCED,
    ) = FabricImageRef(
        photoUrl = photoUrl,
        photoStoragePath = "users/u/orders/o/fabrics/i.jpg",
        syncState = syncState,
        localPhotoPath = localPhotoPath,
    )

    @Test
    fun blankPendingRef_isDroppedSoTheAddCtaCanReturn() {
        // The bug: a PENDING upload comes back as localPhotoPath=null + photoUrl=""
        // once the outbox entry is gone. It must NOT render as a stuck blank tile.
        val refs = fabricReferenceImages(
            fabricImages = listOf(ref(photoUrl = "", syncState = ImageSyncState.PENDING)),
            legacyUrl = null,
        )
        assertTrue(refs.isEmpty(), "A ref with no usable source must be dropped")
    }

    @Test
    fun localPhotoPath_resolves() {
        val refs = fabricReferenceImages(
            fabricImages = listOf(ref(photoUrl = "", localPhotoPath = "/cache/abc.jpg")),
            legacyUrl = null,
        )
        assertEquals(1, refs.size)
        assertEquals("/cache/abc.jpg", refs[0].url)
        assertEquals(0, refs[0].sourceIndex)
    }

    @Test
    fun remoteUrl_resolves() {
        val refs = fabricReferenceImages(
            fabricImages = listOf(ref(photoUrl = "https://x/y.jpg")),
            legacyUrl = null,
        )
        assertEquals(1, refs.size)
        assertEquals("https://x/y.jpg", refs[0].url)
    }

    @Test
    fun mixedList_keepsValidAndPreservesSourceIndexForRemoval() {
        // A blank ref sits between two valid ones. The blank is dropped, but the
        // surviving refs must carry their ORIGINAL index so delete hits the right one.
        val refs = fabricReferenceImages(
            fabricImages = listOf(
                ref(photoUrl = "https://x/0.jpg"),
                ref(photoUrl = "", syncState = ImageSyncState.PENDING),
                ref(photoUrl = "https://x/2.jpg"),
            ),
            legacyUrl = null,
        )
        assertEquals(2, refs.size)
        assertEquals(0, refs[0].sourceIndex)
        assertEquals(2, refs[1].sourceIndex)
    }

    @Test
    fun emptyImages_fallsBackToLegacyUrl() {
        val refs = fabricReferenceImages(fabricImages = emptyList(), legacyUrl = "https://legacy/f.jpg")
        assertEquals(1, refs.size)
        assertEquals("https://legacy/f.jpg", refs[0].url)
    }

    @Test
    fun emptyImages_blankLegacyUrl_isEmpty() {
        assertTrue(fabricReferenceImages(fabricImages = emptyList(), legacyUrl = "").isEmpty())
        assertTrue(fabricReferenceImages(fabricImages = emptyList(), legacyUrl = null).isEmpty())
    }
}
