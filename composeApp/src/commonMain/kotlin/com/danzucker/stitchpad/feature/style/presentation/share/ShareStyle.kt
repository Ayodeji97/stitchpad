package com.danzucker.stitchpad.feature.style.presentation.share

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.sharing.ImageSharer

/**
 * Orchestrates sharing a style's photo + caption. Resolves the image model
 * (local file first, remote URL fallback), loads bytes, builds the tier-keyed
 * caption, and fires the share sheet. Returns Error when no shareable bytes
 * are available (offline with no cache, decode failure, or blank model).
 */
class ShareStyle(
    private val loader: StyleImageBytesLoader,
    private val entitlements: EntitlementsProvider,
    private val share: suspend (ByteArray, String?) -> Unit,
) {
    constructor(
        loader: StyleImageBytesLoader,
        entitlements: EntitlementsProvider,
        sharer: ImageSharer,
    ) : this(loader, entitlements, share = { bytes, caption -> sharer.shareImage(bytes, caption) })

    suspend operator fun invoke(style: Style): EmptyResult<DataError> {
        val model = style.localPhotoPath ?: style.photoUrl
        val bytes = loader.load(model) ?: return Result.Error(DataError.Local.UNKNOWN)
        val tier = entitlements.awaitHydrated().tier
        share(bytes, StyleShareFormatter.caption(style, tier))
        return Result.Success(Unit)
    }
}
