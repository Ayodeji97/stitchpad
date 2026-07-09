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
 * are available (offline with no cache, decode failure, or blank model) OR
 * when the share sheet could not be presented — so the ViewModels never show
 * "success" for a share the user never actually saw.
 *
 * [share] returns true when the sheet was presented, false otherwise.
 */
class ShareStyle(
    private val loader: StyleImageBytesLoader,
    private val entitlements: EntitlementsProvider,
    private val share: suspend (ByteArray, String?) -> Boolean,
) {
    constructor(
        loader: StyleImageBytesLoader,
        entitlements: EntitlementsProvider,
        sharer: ImageSharer,
    ) : this(loader, entitlements, share = { bytes, caption -> sharer.shareImage(bytes, caption) })

    suspend operator fun invoke(style: Style): EmptyResult<DataError> {
        val bytes = loadBytes(style) ?: return Result.Error(DataError.Local.UNKNOWN)
        val tier = entitlements.awaitHydrated().tier
        val presented = share(bytes, StyleShareFormatter.caption(style, tier))
        return if (presented) Result.Success(Unit) else Result.Error(DataError.Local.UNKNOWN)
    }

    /**
     * Prefers the local file (works offline / pre-upload), but if the local
     * path is stale — pruned after upload, deleted, or a stale form snapshot —
     * falls back to the remote URL rather than failing when the image is still
     * fetchable. Returns null only when neither source yields bytes.
     */
    @Suppress("ReturnCount")
    private suspend fun loadBytes(style: Style): ByteArray? {
        val local = style.localPhotoPath
        if (local != null) {
            loader.load(local)?.let { return it }
        }
        val url = style.photoUrl
        if (url.isNotBlank() && url != local) {
            return loader.load(url)
        }
        return null
    }
}
