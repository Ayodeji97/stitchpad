package com.danzucker.stitchpad.core.media

import kotlinx.coroutines.CompletableDeferred

/**
 * Test double for [ImageCompressor].
 *
 * Defaults to an identity (returns the input unchanged) so tests that aren't about
 * compression behave exactly as before. Pass [outputSize] to simulate shrinking a
 * pick to a fixed byte count, or [returnNull] to simulate a decode failure (caller
 * should fall back to the original bytes). Pass [gate] to hold compression open until
 * the test completes it — used to exercise the "pick still compressing when Save is
 * tapped" race.
 */
class FakeImageCompressor(
    private val outputSize: Int? = null,
    private val returnNull: Boolean = false,
    private val gate: CompletableDeferred<Unit>? = null,
) : ImageCompressor {

    /** Sizes of the inputs passed to [compress], in call order. */
    val inputSizes = mutableListOf<Int>()

    override suspend fun compress(bytes: ByteArray, maxEdgePx: Int, jpegQuality: Int): ByteArray? {
        inputSizes += bytes.size
        gate?.await()
        return when {
            returnNull -> null
            outputSize != null -> ByteArray(outputSize)
            else -> bytes
        }
    }
}
