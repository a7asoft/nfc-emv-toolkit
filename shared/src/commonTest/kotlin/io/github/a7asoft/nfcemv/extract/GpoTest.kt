package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.tlv.TlvError
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class GpoTest {

    @Test
    fun `parse rejects empty input with EmptyInput`() {
        val err = assertIs<GpoResult.Err>(Gpo.parse(byteArrayOf()))
        assertEquals(GpoError.EmptyInput, err.error)
    }

    @Test
    fun `parse rejects malformed TLV with TlvDecodeFailed`() {
        val err = assertIs<GpoResult.Err>(Gpo.parse(byteArrayOf(0x80.toByte(), 0x05, 0x00)))
        assertIs<TlvError>(err.error.let { (it as GpoError.TlvDecodeFailed).cause })
    }

    @Test
    fun `parse rejects an unknown outer template tag with UnknownTemplate`() {
        // 70 02 00 00 - valid TLV, wrong outer
        val err = assertIs<GpoResult.Err>(
            Gpo.parse(byteArrayOf(0x70, 0x02, 0x00, 0x00)),
        )
        assertEquals(GpoError.UnknownTemplate, err.error)
    }

    @Test
    fun `parse rejects format-1 with payload shorter than 2 bytes with InvalidAipLength`() {
        // 80 01 00 - 1-byte body
        val err = assertIs<GpoResult.Err>(
            Gpo.parse(byteArrayOf(0x80.toByte(), 0x01, 0x00)),
        )
        assertEquals(GpoError.InvalidAipLength(byteCount = 1), err.error)
    }

    @Test
    fun `parse rejects format-2 missing AIP with MissingAip`() {
        // 77 06 94 04 08 01 01 00
        val err = assertIs<GpoResult.Err>(
            Gpo.parse(
                byteArrayOf(
                    0x77, 0x06, 0x94.toByte(), 0x04, 0x08, 0x01, 0x01, 0x00,
                ),
            ),
        )
        assertEquals(GpoError.MissingAip, err.error)
    }

    @Test
    fun `parse rejects format-2 missing AFL with MissingAfl`() {
        // 77 04 82 02 00 80
        val err = assertIs<GpoResult.Err>(
            Gpo.parse(
                byteArrayOf(0x77, 0x04, 0x82.toByte(), 0x02, 0x00, 0x80.toByte()),
            ),
        )
        assertEquals(GpoError.MissingAfl, err.error)
    }

    @Test
    fun `parse rejects format-2 with AIP length not equal to 2 bytes with InvalidAipLength`() {
        // 77 0B 82 03 00 80 00 94 04 08 01 01 00
        val err = assertIs<GpoResult.Err>(
            Gpo.parse(
                byteArrayOf(
                    0x77, 0x0B, 0x82.toByte(), 0x03, 0x00, 0x80.toByte(), 0x00,
                    0x94.toByte(), 0x04, 0x08, 0x01, 0x01, 0x00,
                ),
            ),
        )
        assertEquals(GpoError.InvalidAipLength(byteCount = 3), err.error)
    }

    @Test
    fun `parse rejects format-1 when the embedded AFL is malformed with AflRejected`() {
        // 80 05 00 80 08 01 - AFL slice is 3 bytes (not multiple of 4)
        val err = assertIs<GpoResult.Err>(
            Gpo.parse(byteArrayOf(0x80.toByte(), 0x05, 0x00, 0x80.toByte(), 0x08, 0x01, 0x01)),
        )
        assertIs<AflError.InvalidLength>((err.error as GpoError.AflRejected).cause)
    }

    @Test
    fun `parse decodes a format-1 GPO with one AFL entry`() {
        // 80 06 00 80 08 01 01 00
        val ok = assertIs<GpoResult.Ok>(
            Gpo.parse(byteArrayOf(0x80.toByte(), 0x06, 0x00, 0x80.toByte(), 0x08, 0x01, 0x01, 0x00)),
        )
        assertContentEquals(byteArrayOf(0x00, 0x80.toByte()), ok.gpo.applicationInterchangeProfile)
        assertEquals(
            listOf(AflEntry(sfi = 1, firstRecord = 1, lastRecord = 1, odaCount = 0)),
            ok.gpo.afl.entries,
        )
    }

    @Test
    fun `parse decodes a format-2 GPO with AIP and AFL children`() {
        // 77 0A 82 02 00 80 94 04 08 01 01 00
        val ok = assertIs<GpoResult.Ok>(
            Gpo.parse(
                byteArrayOf(
                    0x77, 0x0A,
                    0x82.toByte(), 0x02, 0x00, 0x80.toByte(),
                    0x94.toByte(), 0x04, 0x08, 0x01, 0x01, 0x00,
                ),
            ),
        )
        assertContentEquals(byteArrayOf(0x00, 0x80.toByte()), ok.gpo.applicationInterchangeProfile)
        assertEquals(1, ok.gpo.afl.entries.size)
    }

    @Test
    fun `parseOrThrow returns the Gpo on a valid input`() {
        val gpo = Gpo.parseOrThrow(
            byteArrayOf(0x80.toByte(), 0x06, 0x00, 0x80.toByte(), 0x08, 0x01, 0x01, 0x00),
        )
        assertEquals(1, gpo.afl.entries.size)
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on a malformed input`() {
        assertFailsWith<IllegalArgumentException> { Gpo.parseOrThrow(byteArrayOf()) }
    }

    @Test
    fun `applicationInterchangeProfile returns a fresh array per access`() {
        val gpo = Gpo.parseOrThrow(
            byteArrayOf(0x80.toByte(), 0x06, 0x00, 0x80.toByte(), 0x08, 0x01, 0x01, 0x00),
        )
        val first = gpo.applicationInterchangeProfile
        val second = gpo.applicationInterchangeProfile
        first[0] = 0xFF.toByte()
        assertEquals(0x00.toByte(), second[0])
    }

    @Test
    fun `Gpo toString reports AIP byte count not raw bytes`() {
        val gpo = Gpo.parseOrThrow(
            byteArrayOf(0x80.toByte(), 0x06, 0x00, 0x80.toByte(), 0x08, 0x01, 0x01, 0x00),
        )
        val rendered = gpo.toString()
        assertContains(rendered, "aip=2 bytes")
        assertFalse(
            "0x80" in rendered,
            "AIP raw byte 0x80 must not appear in toString; was: $rendered",
        )
    }

    // why: fuzz test combines a `when` arm with a structural invariant
    // assertion; CC counts both. Splitting would obscure the round-trip.
    @Suppress("CyclomaticComplexMethod")
    @Test
    fun `parse never crashes on random input and structural invariants hold`() {
        val rng = Random(seed = GPO_FUZZ_SEED)
        repeat(times = FUZZ_ITERATIONS) {
            val size = rng.nextInt(0, MAX_FUZZ_SIZE)
            val bytes = ByteArray(size).also { rng.nextBytes(it) }
            when (val result = Gpo.parse(bytes)) {
                is GpoResult.Ok -> {
                    // Spec: AIP is exactly 2 bytes per EMV Book 3 Annex C1.
                    assertEquals(2, result.gpo.applicationInterchangeProfile.size)
                }
                is GpoResult.Err -> Unit
            }
        }
    }

    @Test
    fun `parse format-2 populates inlineTlv with non-AIP non-AFL children`() {
        // why: real-card observation (#59) shows format-2 GPO bodies often
        // carry application data (5F20 cardholder, 57 Track 2, 5F34 PAN
        // sequence, 9F26 cryptogram, ...) inline alongside AIP/AFL.
        // 77 0F 82 02 00 80 94 04 08 01 01 00 5F 20 02 41 42
        val ok = assertIs<GpoResult.Ok>(
            Gpo.parse(
                byteArrayOf(
                    0x77, 0x0F,
                    0x82.toByte(), 0x02, 0x00, 0x80.toByte(),
                    0x94.toByte(), 0x04, 0x08, 0x01, 0x01, 0x00,
                    0x5F, 0x20, 0x02, 0x41, 0x42,
                ),
            ),
        )
        assertEquals(1, ok.gpo.inlineTlv.size)
        val first = assertIs<io.github.a7asoft.nfcemv.tlv.Tlv.Primitive>(ok.gpo.inlineTlv[0])
        assertEquals(io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5F20"), first.tag)
    }

    @Test
    fun `parse format-1 returns inlineTlv as empty list`() {
        // why: format-1 (tag 80) is a fixed-layout payload with no
        // embedded TLV — only AIP + AFL bytes packed in sequence.
        // 80 06 00 80 08 01 01 00
        val ok = assertIs<GpoResult.Ok>(
            Gpo.parse(byteArrayOf(0x80.toByte(), 0x06, 0x00, 0x80.toByte(), 0x08, 0x01, 0x01, 0x00)),
        )
        assertEquals(emptyList(), ok.gpo.inlineTlv)
    }

    private companion object {
        const val GPO_FUZZ_SEED: Int = 0x6_F0
        const val FUZZ_ITERATIONS: Int = 1_000
        const val MAX_FUZZ_SIZE: Int = 64
    }
}
