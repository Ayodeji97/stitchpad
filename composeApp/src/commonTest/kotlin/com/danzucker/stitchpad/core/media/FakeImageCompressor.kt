package com.danzucker.stitchpad.core.media

/**
 * Test double for [ImageCompressor].
 *
 * Defaults to an identity (returns the input unchanged) so tests that aren't about
 * compression behave exactly as before. Pass [outputSize] to simulate shrinking a
 * pick to a fixed byte count, or [returnNull] to simulate a decode failure (caller
 * should fall back to the original bytes).
 */
class FakeImageCompressor(
    private val outputSize: Int? = null,
    private val returnNull: Boolean = false,
) : ImageCompressor {

    /** Sizes of the inputs passed to [compress], in call order. */
    val inputSizes = mutableListOf<Int>()

    override suspend fun compress(bytes: ByteArray, maxEdgePx: Int, jpegQuality: Int): ByteArray? {
        inputSizes += bytes.size
        return when {
            returnNull -> null
            outputSize != null -> ByteArray(outputSize)
            else -> bytes
        }
    }
}
