package com.danzucker.stitchpad.feature.branding.domain

/**
 * Downscales and re-encodes a picked logo before upload so phone-camera images
 * (often 3–8 MB) land in Storage at a few hundred KB. The logo only ever renders
 * at ~64 dp on screen and ~40 px in the receipt header, so a 1024 px max edge
 * has zero perceptible quality cost while making receipt-bytes prefetch + the
 * dashboard cold-load cache cheap.
 *
 * Implementations decode `bytes`, scale so the longest edge is at most [maxEdgePx]
 * (preserving aspect ratio), then re-encode as JPEG at [jpegQuality] (0–100).
 * Returns the encoded bytes, or null if the input can't be decoded.
 *
 * Pure (same input → same output). Implementations run on a background dispatcher
 * so the picker callback returns immediately.
 */
/** 1024 px max edge: a logo never renders larger than ~64 dp / ~40 px on receipts. */
const val DEFAULT_LOGO_MAX_EDGE_PX: Int = 1024

/** JPEG quality 85: visually lossless for a logo, ~5× smaller than q100. */
const val DEFAULT_LOGO_JPEG_QUALITY: Int = 85

expect class BrandLogoCompressor() {
    suspend fun compress(
        bytes: ByteArray,
        maxEdgePx: Int = DEFAULT_LOGO_MAX_EDGE_PX,
        jpegQuality: Int = DEFAULT_LOGO_JPEG_QUALITY,
    ): ByteArray?
}

/**
 * Default suspend-function adapter used as the ViewModels' constructor default.
 * Routes through a fresh [BrandLogoCompressor] each call (the compressor is
 * stateless, so this is cheap).
 *
 * VMs take a `suspend (ByteArray) -> ByteArray?` rather than the class directly
 * so JVM unit tests can substitute an identity lambda without invoking Android
 * `BitmapFactory.decodeByteArray` (which throws "Stub!" in the non-Robolectric
 * test environment). Mirrors the `nowMillis: () -> Long` injection pattern
 * already used by `DashboardViewModel`.
 */
suspend fun defaultCompressLogo(bytes: ByteArray): ByteArray? =
    BrandLogoCompressor().compress(bytes)
