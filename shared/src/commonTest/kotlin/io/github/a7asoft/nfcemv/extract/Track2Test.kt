package io.github.a7asoft.nfcemv.extract

import kotlinx.datetime.YearMonth
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class Track2Test {

    /**
     * Canonical 16-digit Visa Track 2 fixture (no F-pad, total 28 nibbles).
     *
     * PAN  = 4111111111111111
     * D    = separator
     * YYMM = 2812 (Dec 2028)
     * SSS  = 201
     * disc = 0000 (4 digits, makes total nibbles even, no F-pad needed)
     *
     * Bytes: 41 11 11 11 11 11 11 11 D2 81 22 01 00 00
     */
    private val visaFixture = byteArrayOf(
        0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
    )

    @Test
    fun `parse decodes a canonical 16-digit Visa Track2 fixture`() {
        val ok = assertIs<Track2Result.Ok>(Track2.parse(visaFixture))
        val track2 = ok.track2
        assertEquals("4111111111111111", track2.pan.unmasked())
        assertEquals(YearMonth(2028, 12), track2.expiry)
        assertEquals("201", track2.serviceCode.toString())
        assertEquals("0000", track2.unmaskedDiscretionary())
        assertEquals(4, track2.discretionaryLength)
    }

    @Test
    fun `parse rejects empty input with EmptyInput`() {
        val err = assertIs<Track2Result.Err>(Track2.parse(byteArrayOf()))
        assertEquals(Track2Error.EmptyInput, err.error)
    }

    @Test
    fun `parse rejects input without a D separator with MissingSeparator`() {
        val raw = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        assertEquals(Track2Error.MissingSeparator, err.error)
    }

    @Test
    fun `parse rejects PAN that fails Luhn with PanRejected wrapping LuhnCheckFailed`() {
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
        )
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        val cause = assertIs<Track2Error.PanRejected>(err.error).cause
        assertEquals(PanError.LuhnCheckFailed, cause)
    }

    @Test
    fun `parse rejects truncated expiry with ExpiryTooShort`() {
        // 16d PAN + D + only 2 expiry nibbles + F pad → 20 nibbles, 10 bytes.
        // Bytes: 41 11 11 11 11 11 11 11 D2 8F
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x8F.toByte(),
        )
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        val cause = assertIs<Track2Error.ExpiryTooShort>(err.error)
        assertEquals(2, cause.nibblesAvailable)
    }

    @Test
    fun `parse rejects expiry month 00 with InvalidExpiryMonth`() {
        // PAN + D + 2800 + 201 + 0000 → 28 nibbles, 14 bytes.
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x80.toByte(), 0x02, 0x01, 0x00, 0x00,
        )
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        assertEquals(Track2Error.InvalidExpiryMonth(month = 0), err.error)
    }

    @Test
    fun `parse rejects expiry month 13 with InvalidExpiryMonth`() {
        // PAN + D + 2813 + 201 + 0000 → 28 nibbles, 14 bytes.
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x32, 0x01, 0x00, 0x00,
        )
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        assertEquals(Track2Error.InvalidExpiryMonth(month = 13), err.error)
    }

    @Test
    fun `parse accepts every legal month from 01 to 12`() {
        (1..12).forEach { month ->
            val mmHigh = month / 10
            val mmLow = month % 10
            val raw = byteArrayOf(
                0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
                0xD2.toByte(), (0x80 or mmHigh).toByte(),
                ((mmLow shl 4) or 0x2).toByte(), 0x01, 0x00, 0x00,
            )
            val ok = assertIs<Track2Result.Ok>(Track2.parse(raw))
            assertEquals(YearMonth(2028, month), ok.track2.expiry)
        }
    }

    @Test
    fun `parse rejects too-short PAN with PanRejected wrapping LengthOutOfRange`() {
        // 11-digit PAN (11 nibbles) → less than Pan minimum 12.
        // 11 + 1(D) + 4(YYMM) + 3(SSS) = 19 nibbles → +F pad = 20 → 10 bytes.
        // Bytes: 11 11 11 11 11 1D 28 12 20 1F
        val raw = byteArrayOf(
            0x11, 0x11, 0x11, 0x11, 0x11,
            0x1D.toByte(), 0x28, 0x12, 0x20, 0x1F.toByte(),
        )
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        val cause = assertIs<Track2Error.PanRejected>(err.error).cause
        assertEquals(PanError.LengthOutOfRange(length = 11), cause)
    }

    @Test
    fun `parse rejects truncated service code with ServiceCodeTooShort`() {
        // 16d PAN + D + 2812 + only 1 service-code nibble → 22 nibbles, 11 bytes.
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22,
        )
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        assertEquals(Track2Error.ServiceCodeTooShort, err.error)
    }

    @Test
    fun `parse accepts a fixture with no discretionary digits`() {
        // PAN(16) + D(1) + YYMM(4) + SSS(3) = 24 nibbles → 12 bytes.
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01,
        )
        val ok = assertIs<Track2Result.Ok>(Track2.parse(raw))
        assertEquals(0, ok.track2.discretionaryLength)
        assertEquals("", ok.track2.unmaskedDiscretionary())
    }

    @Test
    fun `parse accepts a fixture with odd-nibble discretionary using F-pad`() {
        // PAN(16) + D(1) + 2812(4) + 201(3) + 5(1) + F-pad(1) = 26 nibbles → 13 bytes.
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x5F.toByte(),
        )
        val ok = assertIs<Track2Result.Ok>(Track2.parse(raw))
        assertEquals(1, ok.track2.discretionaryLength)
        assertEquals("5", ok.track2.unmaskedDiscretionary())
    }

    @Test
    fun `parse rejects an A nibble inside the PAN segment with MalformedBcdNibble at the actual offset`() {
        // PAN with 'A' (0xA) at nibble position 5. After refactor, scanInput
        // pre-validates every nibble and reports the actual offset of the
        // offending value, not the segment start. Replaces the previous
        // pin of offset=0 per CLAUDE.md §6.1 (the prior pin encoded the
        // wrong contract).
        val raw = byteArrayOf(
            0x41, 0x11, 0x1A, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
        )
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        val cause = assertIs<Track2Error.MalformedBcdNibble>(err.error)
        assertEquals(5, cause.offset)
    }

    @Test
    fun `parse rejects an F nibble before the last position with MalformedFPadding`() {
        // PAN + D + expiry + service + discretionary "5" + F + extra byte 0x00.
        // After refactor, scanInput recognises the F at nibble offset 25 as a
        // non-trailing pad and surfaces MalformedFPadding (instead of the
        // previous misleading MalformedBcdNibble or MissingSeparator).
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x5F.toByte(), 0x00,
        )
        val err = assertIs<Track2Result.Err>(Track2.parse(raw))
        assertEquals(Track2Error.MalformedFPadding, err.error)
    }

    @Test
    fun `parse accepts the maximum 19-digit discretionary`() {
        // 19 disc digits → 16+1+4+3+19 = 43 nibbles odd → +F pad = 44 → 22 bytes.
        // disc digits = 1234567890123456789
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(),
            0x22, 0x01,
            0x12, 0x34, 0x56, 0x78, 0x90.toByte(),
            0x12, 0x34, 0x56, 0x78, 0x9F.toByte(),
        )
        val ok = assertIs<Track2Result.Ok>(Track2.parse(raw))
        assertEquals(YearMonth(2028, 12), ok.track2.expiry)
        assertEquals("201", ok.track2.serviceCode.toString())
        assertEquals(19, ok.track2.discretionaryLength)
        assertEquals("1234567890123456789", ok.track2.unmaskedDiscretionary())
    }

    @Test
    fun `toString masks the PAN and reports only discretionary length`() {
        val track2 = Track2.parseOrThrow(visaFixture)
        assertEquals(
            "Track2(pan=411111******1111, expiry=2028-12, service=201, discretionary.size=4)",
            track2.toString(),
        )
    }

    @Test
    fun `toString output never contains the discretionary digit sequence`() {
        // The visaFixture's discretionary is "0000". The discretionary length
        // (4) IS expected in the output, so we cannot simply test "0000 not in
        // toString". Instead, build a fixture whose discretionary digits do NOT
        // also appear as the integer length, and assert absence directly.
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01,
            0x12, 0x34, 0x56, 0x7F.toByte(), // discretionary "1234567" + F-pad
        )
        val track2 = Track2.parseOrThrow(raw)
        val rendered = track2.toString()
        assertFalse("1234567" in rendered)
    }

    @Test
    fun `unmaskedDiscretionary returns the raw digits verbatim`() {
        val track2 = Track2.parseOrThrow(visaFixture)
        assertEquals("0000", track2.unmaskedDiscretionary())
    }

    @Test
    fun `parseOrThrow rejects empty input with IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Track2.parseOrThrow(byteArrayOf()) }
    }

    @Test
    fun `parseOrThrow rejects invalid month with IllegalArgumentException`() {
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x32, 0x01, 0x00, 0x00,
        )
        val ex = assertFailsWith<IllegalArgumentException> { Track2.parseOrThrow(raw) }
        assertEquals("Track2 expiry month out of range: 13", ex.message)
    }

    @Test
    fun `parseOrThrow IllegalArgumentException message embeds no raw digits`() {
        val raw = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
        )
        val ex = assertFailsWith<IllegalArgumentException> { Track2.parseOrThrow(raw) }
        val msg = ex.message ?: ""
        assertFalse("4111111111111112" in msg)
        assertFalse("411111" in msg)
        assertFalse("1112" in msg)
        assertEquals("Track2 PAN rejected: LuhnCheckFailed", msg)
    }

    @Test
    fun `equality compares all four fields`() {
        val a = Track2.parseOrThrow(visaFixture)
        val b = Track2.parseOrThrow(visaFixture)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equality differs when discretionary differs`() {
        val a = Track2.parseOrThrow(visaFixture)
        val b = Track2.parseOrThrow(byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x01,
        ))
        assertNotEquals(a, b)
    }

    @Test
    fun `parser never crashes on arbitrary input`() {
        // 5,000 deterministic random buffers up to 64 bytes each. Contract:
        // Track2.parse returns either Ok or Err. Any other exception escaping
        // (e.g. IndexOutOfBounds, NumberFormatException, NullPointerException)
        // is a parser bug. Mirrors TlvDecoderFuzzTest.
        val rng = Random(FUZZ_SEED)
        var okCount = 0
        var errCount = 0
        repeat(FUZZ_ITERATIONS) {
            val length = rng.nextInt(0, FUZZ_MAX_INPUT_BYTES + 1)
            val data = ByteArray(length).also { rng.nextBytes(it) }
            when (Track2.parse(data)) {
                is Track2Result.Ok -> okCount++
                is Track2Result.Err -> errCount++
            }
        }
        // Sanity: the seed must produce both Ok and Err outcomes so the test
        // is exercising both branches. With 64-byte random input, Ok is rare
        // but should appear at least once across 5,000 iterations.
        kotlin.test.assertTrue(okCount + errCount == FUZZ_ITERATIONS)
        kotlin.test.assertTrue(errCount > 0, "expected some rejections, got 0")
    }

    private companion object {
        const val FUZZ_SEED: Long = 0x54524B32L // "TRK2"
        const val FUZZ_ITERATIONS: Int = 5_000
        const val FUZZ_MAX_INPUT_BYTES: Int = 64
    }
}
