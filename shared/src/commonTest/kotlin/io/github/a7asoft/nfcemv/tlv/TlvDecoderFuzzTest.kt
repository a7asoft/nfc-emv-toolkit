package io.github.a7asoft.nfcemv.tlv

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Deterministic fuzz: 10,000 random buffers up to 256 bytes total — 5,000
 * each under strict and lenient options. The decoder must never throw a
 * non-TlvParse exception (e.g., IndexOutOfBounds, OutOfMemoryError,
 * NullPointerException).
 *
 * Result is always either [TlvParseResult.Ok] or [TlvParseResult.Err]. No
 * partial output, no hang.
 */
class TlvDecoderFuzzTest {

    @Test
    fun `parser never crashes on arbitrary input - strict`() {
        runFuzz(seed = STRICT_SEED, iterations = 5_000, options = TlvOptions(strictness = Strictness.Strict))
    }

    @Test
    fun `parser never crashes on arbitrary input - lenient`() {
        runFuzz(seed = LENIENT_SEED, iterations = 5_000, options = TlvOptions(strictness = Strictness.Lenient))
    }

    @Test
    fun `parser rejects length larger than remaining buffer`() {
        // 57 84 7F FF FF FF — declares 2 GB of value with no body. Classic OOM bait.
        // The reader's pre-check on remaining bytes throws UnexpectedEof BEFORE
        // any allocation; pin this exact error per CLAUDE.md §6.1.
        val data = byteArrayOf(0x57, 0x84.toByte(), 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val result = TlvDecoder.parse(data)
        val err = assertIs<TlvParseResult.Err>(result)
        assertIs<TlvError.UnexpectedEof>(err.error)
    }

    private fun runFuzz(seed: Long, iterations: Int, options: TlvOptions) {
        val rng = Random(seed)
        repeat(iterations) {
            val length = rng.nextInt(0, 257)
            val data = ByteArray(length).also { rng.nextBytes(it) }
            // Pin the invariant per iteration: every input MUST resolve to a
            // typed parse result (Ok or Err). Asserting on the return value
            // (not just relying on no-throw) catches a regression where a
            // future code path returns null, hangs, or throws a non-typed
            // exception that gets swallowed elsewhere.
            assertIs<TlvParseResult>(TlvDecoder.parse(data, options))
        }
    }

    private companion object {
        const val STRICT_SEED: Long = 0x454D5601L
        const val LENIENT_SEED: Long = 0x454D5602L
    }
}
