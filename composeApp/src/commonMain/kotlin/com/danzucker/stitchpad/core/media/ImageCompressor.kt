package com.danzucker.stitchpad.core.media

/**
 * Downscales and re-encodes an image so uploads stay small and fast.
 *
 * Gallery picks arrive at full resolution — a modern phone photo is often several
 * MB — whereas camera captures already go through the same downscale on capture.
 * Compressing gallery picks to this shared ceiling keeps upload time, Firebase
 * Storage cost, and on-device decode memory low, and means the upload size guard
 * almost never fires.
 */
interface ImageCompressor {
    /**
     * Decodes [bytes], corrects EXIF orientation, downscales so the longest edge is
     * at most [maxEdgePx], and re-encodes as JPEG at [jpegQuality] (0..100). Runs off
     * the main thread.
     *
     * Returns null when the input cannot be decoded (corrupt/unsupported), so callers
     * can fall back to the original bytes and let the size guard decide.
     */
    suspend fun compress(
        bytes: ByteArray,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
        jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    ): ByteArray?

    companion object {
        const val DEFAULT_MAX_EDGE_PX: Int = 1920
        const val DEFAULT_JPEG_QUALITY: Int = 85
    }
}
