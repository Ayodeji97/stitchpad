package com.danzucker.stitchpad.feature.branding.domain

import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result

/**
 * Validates that a picked image is small enough to upload and is one of the formats
 * we render natively on both Android and iOS (PNG + JPG). SVG is intentionally rejected
 * in V1; coil-svg + an iOS SVG decoder would be required to render it on the receipt.
 */
class BrandLogoValidator(
    private val maxBytes: Int = MAX_BYTES,
) {
    fun validate(bytes: ByteArray): EmptyResult<BrandLogoError> {
        if (bytes.size > maxBytes) return Result.Error(BrandLogoError.TooLarge)
        if (!hasSupportedMagic(bytes)) return Result.Error(BrandLogoError.UnsupportedFormat)
        return Result.Success(Unit)
    }

    private fun hasSupportedMagic(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_MAGIC_BYTES) return false
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        val isPng = bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        // JPG: FF D8 FF
        val isJpg = bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        return isPng || isJpg
    }

    companion object {
        const val MAX_BYTES: Int = 2 * 1024 * 1024
        private const val MIN_MAGIC_BYTES = 4
    }
}
