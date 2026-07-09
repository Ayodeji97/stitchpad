package com.danzucker.stitchpad.core.sharing

/**
 * Shares a single image (raw bytes) plus an optional caption via the platform
 * share sheet (Android ACTION_SEND chooser / iOS UIActivityViewController).
 * Feature-agnostic; used by style sharing and reusable elsewhere.
 */
expect class ImageSharer {
    suspend fun shareImage(bytes: ByteArray, caption: String?)
}
