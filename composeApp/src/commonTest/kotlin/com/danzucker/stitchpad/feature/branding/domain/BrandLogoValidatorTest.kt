package com.danzucker.stitchpad.feature.branding.domain

import com.danzucker.stitchpad.core.domain.error.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandLogoValidatorTest {

    private val validator = BrandLogoValidator()

    @Test
    fun `rejects bytes larger than 2MB`() {
        // 2MB + 1 byte of valid PNG magic followed by junk
        val tooBig = ByteArray(2 * 1024 * 1024 + 1).apply {
            this[0] = 0x89.toByte(); this[1] = 0x50; this[2] = 0x4E; this[3] = 0x47
        }
        val result = validator.validate(tooBig)
        assertEquals(Result.Error(BrandLogoError.TooLarge), result)
    }

    @Test
    fun `rejects bytes with no PNG or JPG magic`() {
        // GIF magic: 47 49 46 38
        val gif = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        val result = validator.validate(gif)
        assertEquals(Result.Error(BrandLogoError.UnsupportedFormat), result)
    }

    @Test
    fun `accepts valid PNG bytes`() {
        // PNG magic: 89 50 4E 47
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            ByteArray(100)
        val result = validator.validate(png)
        assertTrue(result is Result.Success<*>)
    }

    @Test
    fun `accepts valid JPG bytes`() {
        // JPG magic: FF D8 FF
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(100)
        val result = validator.validate(jpg)
        assertTrue(result is Result.Success<*>)
    }

    @Test
    fun `rejects empty bytes`() {
        val result = validator.validate(ByteArray(0))
        assertEquals(Result.Error(BrandLogoError.UnsupportedFormat), result)
    }
}
