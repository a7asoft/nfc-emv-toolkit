package io.github.a7asoft.nfcemv.tlv

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Property: for every byte buffer the decoder accepts, encoding the
 * resulting tree and decoding the encoded output MUST yield an equal tree.
 *
 * Strategy: run 20,000 deterministic random buffers through the decoder; for
 * each buffer that produces an `Ok`, assert the round-trip property. Inputs
 * that produce an `Err` are skipped — they are not the encoder's domain.
 */
class TlvEncoderFuzzTest {

    @Test
    fun `every decoder-accepted input round-trips through encode and decode`() {
        val rng = Random(SEED)
        var roundTripped = 0
        repeat(ITERATIONS) {
            val length = rng.nextInt(0, MAX_INPUT_BYTES + 1)
            val data = ByteArray(length).also { rng.nextBytes(it) }
            val parsed = TlvDecoder.parse(data)
            if (parsed is TlvParseResult.Ok) {
                val encoded = TlvEncoder.encode(parsed.tlvs)
                val reparsed = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(encoded))
                assertEquals(parsed.tlvs, reparsed.tlvs)
                roundTripped++
            }
        }
        // Sanity check that the seed actually produces some accepted inputs;
        // otherwise this test would silently pass while exercising nothing.
        kotlin.test.assertTrue(
            roundTripped >= MIN_ROUND_TRIPS,
            "expected at least $MIN_ROUND_TRIPS round-trips, exercised $roundTripped",
        )
    }

    private companion object {
        const val SEED: Long = 0x454E5201L
        const val ITERATIONS: Int = 20_000
        const val MAX_INPUT_BYTES: Int = 256
        const val MIN_ROUND_TRIPS: Int = 50
    }
}
