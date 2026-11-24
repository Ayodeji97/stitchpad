package com.danzucker.stitchpad.feature.freemium.data

/**
 * Lowercase hex SHA-256 of the UTF-8 bytes of [value]. Used as the Play
 * `obfuscatedAccountId` (sha256Hex(uid)); MUST equal playAccountId() in
 * functions/src/billing/playBilling.ts. Pure Kotlin because commonMain has no
 * crypto dependency and this runs once per purchase on a short string.
 */
@Suppress("MagicNumber")
fun sha256Hex(value: String): String {
    val message = value.encodeToByteArray()
    val bitLength = message.size.toLong() * 8
    val paddedLength = ((message.size + 8) / 64 + 1) * 64
    val padded = ByteArray(paddedLength)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[paddedLength - 1 - i] = (bitLength ushr (8 * i)).toByte()
    }
    val state = SHA256_INITIAL.copyOf()
    for (block in 0 until paddedLength / 64) {
        compressBlock(state, messageSchedule(padded, block * 64))
    }
    return state.joinToString("") { it.toUInt().toString(16).padStart(8, '0') }
}

@Suppress("MagicNumber")
private fun messageSchedule(padded: ByteArray, offset: Int): IntArray {
    val w = IntArray(64)
    for (t in 0 until 16) {
        val o = offset + t * 4
        w[t] = ((padded[o].toInt() and 0xff) shl 24) or
            ((padded[o + 1].toInt() and 0xff) shl 16) or
            ((padded[o + 2].toInt() and 0xff) shl 8) or
            (padded[o + 3].toInt() and 0xff)
    }
    for (t in 16 until 64) {
        val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
        val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
        w[t] = w[t - 16] + s0 + w[t - 7] + s1
    }
    return w
}

/** One SHA-256 compression round over the working variables v = a..h, folded into [state]. */
@Suppress("MagicNumber")
private fun compressBlock(state: IntArray, w: IntArray) {
    val v = state.copyOf()
    for (t in 0 until 64) {
        val e = v[4]
        val a = v[0]
        val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
        val ch = (e and v[5]) xor (e.inv() and v[6])
        val temp1 = v[7] + s1 + ch + SHA256_K[t] + w[t]
        val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
        val maj = (a and v[1]) xor (a and v[2]) xor (v[1] and v[2])
        // Shift h <- g <- f <- e <- d(+temp1) <- c <- b <- a <- temp1 + temp2.
        for (i in 7 downTo 1) v[i] = v[i - 1]
        v[4] += temp1
        v[0] = temp1 + s0 + maj
    }
    for (i in 0 until 8) state[i] += v[i]
}

private val SHA256_INITIAL = intArrayOf(
    0x6a09e667,
    -0x4498517b,
    0x3c6ef372,
    -0x5ab00ac6,
    0x510e527f,
    -0x64fa9774,
    0x1f83d9ab,
    0x5be0cd19,
)

private val SHA256_K = longArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
).map { it.toInt() }.toIntArray()
