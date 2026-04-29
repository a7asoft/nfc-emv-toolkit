package io.github.a7asoft.nfcemv.tlv

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Deterministic fuzz: 5,000 random buffers up to 256 bytes, parsed under both
 * strict and lenient options. The decoder must never throw a non-TlvParse
 * exception (e.g., IndexOutOfBounds, OutOfMemoryError, NullPointerException).
 *
 * Result is always either [TlvParseResult.Ok] or [TlvParseResult.Err]. No
 * partial output, no hang.
 */
class TlvDecoderFuzzTest {

    @Test
    fun `parser never crashes on arbitrary input - strict`() {
        runFuzz(seed = STRICT_SEED, iterations = 5_000, options = TlvOptions(strict = true))
    }

    @Test
    fun `parser never crashes on arbitrary input - lenient`() {
        runFuzz(seed = LENIENT_SEED, iterations = 5_000, options = TlvOptions(strict = false))
    }

    @Test
    fun `parser rejects length larger than remaining buffer`() {
        // 57 84 7F FF FF FF — declares 2 GB of value with no body. Classic OOM bait.
        val data = byteArrayOf(0x57, 0x84.toByte(), 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val result = TlvDecoder.parse(data)
        assertTrue(result is TlvParseResult.Err, "Expected Err on absurd declared length")
    }

    private fun runFuzz(seed: Long, iterations: Int, options: TlvOptions) {
        val rng = Random(seed)
        repeat(iterations) {
            val length = rng.nextInt(0, 257)
            val data = ByteArray(length).also { rng.nextBytes(it) }
            // Either Ok or Err is acceptable; the contract is "no other exception escapes".
            TlvDecoder.parse(data, options)
        }
    }

    private companion object {
        const val STRICT_SEED: Long = 0x454D5601L
        const val LENIENT_SEED: Long = 0x454D5602L
    }
}
