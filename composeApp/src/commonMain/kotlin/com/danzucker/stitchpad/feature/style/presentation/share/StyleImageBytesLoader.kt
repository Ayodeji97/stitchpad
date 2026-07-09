package com.danzucker.stitchpad.feature.style.presentation.share

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.danzucker.stitchpad.core.sharing.toPngBytes

/**
 * Resolves an image model (a local file path or remote URL) to PNG-encoded
 * bytes for sharing. Behind a fun interface so ShareStyle stays unit-testable
 * without Coil (which is unconstructible in commonTest).
 */
fun interface StyleImageBytesLoader {
    suspend fun load(model: String): ByteArray?
}

/**
 * Real loader: runs the model through Coil (served from disk cache when the
 * image is already on screen — no re-download) and PNG-encodes the result.
 * Returns null on blank input, load failure, or unsupported image type.
 */
class CoilStyleImageBytesLoader(
    private val imageLoader: ImageLoader,
    private val platformContext: PlatformContext,
) : StyleImageBytesLoader {

    // Three guard-style early returns (blank model / non-success / decode
    // failure) read clearer than nesting; same pattern OrderDetailViewModel
    // .fetchLogoBytes suppresses for.
    @Suppress("ReturnCount")
    override suspend fun load(model: String): ByteArray? {
        if (model.isBlank()) return null
        val request = ImageRequest.Builder(platformContext).data(model).build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return null
        return result.image.toPngBytes()
    }
}
