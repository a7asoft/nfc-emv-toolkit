package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.brand.EmvBrand
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EmvParserTest {

    /**
     * Canonical 60-byte EMV record fixture wrapping all six tags in a
     * `70` template. Mirrors the structure documented in the plan.
     */
    private val canonicalRecord: ByteArray = byteArrayOf(
        0x70, 0x3B,
        // 4F AID = A0000000031010
        0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        // 5A PAN = 4111111111111111
        0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        // 5F24 expiry = 28 12 31 (Dec 2028)
        0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        // 5F20 cardholder = "VISA TEST"
        0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
        // 50 label = "VISA"
        0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        // 57 Track 2 (14 bytes BCD)
        0x57, 0x0E,
        0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
    )

    @Test
    fun `parse extracts the PAN from the canonical fixture`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals("4111111111111111", ok.card.pan.unmasked())
    }

    @Test
    fun `parse extracts the expiry from the canonical fixture`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals(YearMonth(2028, 12), ok.card.expiry)
    }

    @Test
    fun `parse extracts the AID from the canonical fixture`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals(Aid.fromHex("A0000000031010"), ok.card.aid)
    }

    @Test
    fun `parse resolves the brand to VISA from the canonical fixture`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals(EmvBrand.VISA, ok.card.brand)
    }

    @Test
    fun `parse extracts the cardholder name from the canonical fixture`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals("VISA TEST", ok.card.cardholderName)
    }

    @Test
    fun `parse extracts the application label from the canonical fixture`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals("VISA", ok.card.applicationLabel)
    }

    @Test
    fun `parse extracts the Track 2 PAN when tag 57 is present`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        val track2 = assertNotNull(ok.card.track2)
        assertEquals("4111111111111111", track2.pan.unmasked())
    }

    @Test
    fun `parse extracts the Track 2 expiry when tag 57 is present`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        val track2 = assertNotNull(ok.card.track2)
        assertEquals(YearMonth(2028, 12), track2.expiry)
    }

    @Test
    fun `parse extracts the Track 2 service code when tag 57 is present`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        val track2 = assertNotNull(ok.card.track2)
        assertEquals("201", track2.serviceCode.toString())
    }

    @Test
    fun `parse returns track2 null when tag 57 is absent`() {
        val without57 = byteArrayOf(
            0x70, 0x2B,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
            0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
            0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        )
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(without57)))
        assertNull(ok.card.track2)
    }

    @Test
    fun `parse rejects empty input with EmptyInput`() {
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(emptyList()))
        assertEquals(EmvCardError.EmptyInput, err.error)
    }

    @Test
    fun `parse surfaces TlvDecodeFailed with InvalidLengthOctet on a 0xFF length byte`() {
        // 0xFF is reserved per ISO 8825-1 §8.1.3.5c.
        val malformed = byteArrayOf(0x4F, 0xFF.toByte(), 0x00)
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(malformed)))
        val tlvErr = assertIs<EmvCardError.TlvDecodeFailed>(err.error)
        assertIs<io.github.a7asoft.nfcemv.tlv.TlvError.InvalidLengthOctet>(tlvErr.cause)
    }

    @Test
    fun `parse surfaces MissingRequiredTag 4F when AID is absent`() {
        val raw = byteArrayOf(
            0x70, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        assertEquals(EmvCardError.MissingRequiredTag(tagHex = "4F"), err.error)
    }

    @Test
    fun `parse surfaces MissingRequiredTag 5A when PAN is absent`() {
        val raw = byteArrayOf(
            0x70, 0x0F,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        assertEquals(EmvCardError.MissingRequiredTag(tagHex = "5A"), err.error)
    }

    @Test
    fun `parse surfaces MissingRequiredTag 5F24 when expiry is absent`() {
        val raw = byteArrayOf(
            0x70, 0x13,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        assertEquals(EmvCardError.MissingRequiredTag(tagHex = "5F24"), err.error)
    }

    @Test
    fun `parse surfaces PanRejected when PAN fails Luhn`() {
        val raw = byteArrayOf(
            0x70, 0x19,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        val panErr = assertIs<EmvCardError.PanRejected>(err.error)
        assertEquals(PanError.LuhnCheckFailed, panErr.cause)
    }

    @Test
    fun `parse surfaces InvalidExpiryMonth when month is 13`() {
        val raw = byteArrayOf(
            0x70, 0x19,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x13, 0x31,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        assertEquals(EmvCardError.InvalidExpiryMonth(month = 13), err.error)
    }

    @Test
    fun `parseOrThrow returns the EmvCard for the canonical fixture`() {
        val card = EmvParser.parseOrThrow(listOf(canonicalRecord))
        assertEquals("4111111111111111", card.pan.unmasked())
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on EmptyInput with PCI-safe message`() {
        val ex = kotlin.test.assertFailsWith<IllegalArgumentException> {
            EmvParser.parseOrThrow(emptyList())
        }
        assertEquals("EmvCard input is empty", ex.message)
    }

    @Test
    fun `parseOrThrow IAE on Luhn fail does not embed the raw PAN`() {
        val raw = byteArrayOf(
            0x70, 0x19,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        )
        val ex = kotlin.test.assertFailsWith<IllegalArgumentException> {
            EmvParser.parseOrThrow(listOf(raw))
        }
        val msg = ex.message ?: ""
        kotlin.test.assertFalse("4111111111111112" in msg)
        kotlin.test.assertFalse("411111" in msg)
        kotlin.test.assertFalse("1112" in msg)
        assertEquals("EmvCard PAN rejected: LuhnCheckFailed", msg)
    }

    @Test
    fun `EmvCard toString from a parsed canonical fixture never embeds the raw PAN`() {
        val card = EmvParser.parseOrThrow(listOf(canonicalRecord))
        kotlin.test.assertFalse("4111111111111111" in card.toString())
    }

    @Test
    fun `EmvCard toString from a parsed canonical fixture never embeds the raw cardholder name`() {
        val card = EmvParser.parseOrThrow(listOf(canonicalRecord))
        kotlin.test.assertFalse("VISA TEST" in card.toString())
    }

    @Test
    fun `EmvCard toString from a parsed canonical fixture reports cardholder name as 9 chars placeholder`() {
        val card = EmvParser.parseOrThrow(listOf(canonicalRecord))
        kotlin.test.assertTrue("<9 chars>" in card.toString())
    }

    @Test
    fun `parser never crashes on arbitrary List of ByteArray input`() {
        // 1,000 deterministic random buffers of varying sizes and counts.
        // Contract: parse returns either Ok or Err — no other exception
        // (IndexOutOfBounds, NumberFormatException, IllegalStateException
        // from internal invariants) escapes. Mirrors TlvDecoderFuzzTest
        // and Track2 fuzz patterns.
        val rng = kotlin.random.Random(FUZZ_SEED)
        var okCount = 0
        var errCount = 0
        repeat(FUZZ_ITERATIONS) {
            val responseCount = rng.nextInt(0, FUZZ_MAX_RESPONSES + 1)
            val responses = List(responseCount) {
                val len = rng.nextInt(0, FUZZ_MAX_INPUT_BYTES + 1)
                ByteArray(len).also { rng.nextBytes(it) }
            }
            when (EmvParser.parse(responses)) {
                is EmvCardResult.Ok -> okCount++
                is EmvCardResult.Err -> errCount++
            }
        }
        kotlin.test.assertTrue(okCount + errCount == FUZZ_ITERATIONS)
        // Sanity: random bytes almost never produce a valid EmvCard but
        // they MUST resolve to typed Err. Pin that errCount > 0 to confirm
        // the test exercises the Err branch.
        kotlin.test.assertTrue(errCount > 0, "expected some rejections, got 0")
    }

    @Test
    fun `parse handles a List of multiple APDU response ByteArrays`() {
        // Split the canonical fixture into TWO ByteArrays: one carrying
        // the 4F + 5A + 5F24 + 5F20 + 50 wrapper, the other carrying the
        // 57 Track 2 entry. Both must merge into a single EmvCard.
        //
        // First template inner: 4F (9) + 5A (10) + 5F24 (6) +
        //   5F20 (12: 2-byte tag + 1 length + 9 value) + 50 (6) = 43 bytes.
        //   Outer: 70 2B.
        // Second template (16 bytes inner): 57 (16). Outer: 70 10.
        val first = byteArrayOf(
            0x70, 0x2B,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
            0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
            0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        )
        val second = byteArrayOf(
            0x70, 0x10,
            0x57, 0x0E,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
        )
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(first, second)))
        val card = ok.card
        assertEquals("4111111111111111", card.pan.unmasked())
        assertEquals(YearMonth(2028, 12), card.expiry)
        assertEquals("VISA TEST", card.cardholderName)
        assertEquals("VISA", card.applicationLabel)
        assertNotNull(card.track2)
    }

    @Test
    fun `parse decodes a Latin-1 cardholder name across high bytes`() {
        // Replace the canonical fixture's 5F20 entry with "MÜLLER" (Latin-1)
        // M=0x4D, Ü=0xDC, L=0x4C, L=0x4C, E=0x45, R=0x52.
        // 5F 20 06 4D DC 4C 4C 45 52 — 9 bytes total
        // Outer template inner = 4F (9) + 5A (10) + 5F24 (6) + 5F20 (9) +
        //   50 (6) + 57 (16) = 56 bytes. Outer 70 38.
        val raw = byteArrayOf(
            0x70, 0x38,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
            0x5F, 0x20, 0x06, 0x4D, 0xDC.toByte(), 0x4C, 0x4C, 0x45, 0x52,
            0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
            0x57, 0x0E,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
        )
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(raw)))
        assertEquals("MÜLLER", ok.card.cardholderName)
    }

    @Test
    fun `parse surfaces InvalidAid when the 4F entry has too few bytes`() {
        // 4F entry with 4-byte value (below the 5-byte AID minimum).
        // 5A and 5F24 present and valid.
        // 4F entry: 4F 04 A0 00 00 00 = 6 bytes
        // Inner: 6 + 10 + 6 = 22 bytes. Outer 70 16.
        val raw = byteArrayOf(
            0x70, 0x16,
            0x4F, 0x04, 0xA0.toByte(), 0x00, 0x00, 0x00,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        assertEquals(EmvCardError.InvalidAid(byteCount = 4), err.error)
    }

    @Test
    fun `parse surfaces Track2Rejected when tag 57 is present but malformed`() {
        // Tag 57 with no D separator — Track2.parse rejects with
        // MissingSeparator, surfaces as EmvCardError.Track2Rejected.
        // 57 entry: 57 08 41 11 11 11 11 11 11 11 = 10 bytes (no D in PAN).
        // Inner: 9 + 10 + 6 + 10 = 35 bytes. Outer 70 23.
        val raw = byteArrayOf(
            0x70, 0x23,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
            0x57, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        val t2err = assertIs<EmvCardError.Track2Rejected>(err.error)
        assertEquals(io.github.a7asoft.nfcemv.extract.Track2Error.MissingSeparator, t2err.cause)
    }

    @Test
    fun `parse surfaces InvalidExpiryFormat when 5F24 is 2 bytes`() {
        // 5F24 entry: 5F 24 02 28 12 = 5 bytes (2-byte value, expected 3).
        // Inner: 9 + 10 + 5 = 24 bytes. Outer 70 18.
        val raw = byteArrayOf(
            0x70, 0x18,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x02, 0x28, 0x12,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        assertEquals(EmvCardError.InvalidExpiryFormat(nibbleCount = 4), err.error)
    }

    @Test
    fun `parse surfaces MalformedPanNibble when 5A contains a non-digit nibble`() {
        // 5A entry with 0xA at nibble position 3 (second byte's high
        // nibble). Mirror the unit-level test from Task 2.
        // 5A entry: 5A 08 41 1A 11 11 11 11 11 11 = 10 bytes.
        // Inner: 9 + 10 + 6 = 25 bytes. Outer 70 19.
        val raw = byteArrayOf(
            0x70, 0x19,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x1A, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        )
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(raw)))
        val malformed = assertIs<EmvCardError.MalformedPanNibble>(err.error)
        assertEquals(3, malformed.offset)
    }

    /**
     * Real-card record: 4F removed, outer 70 length recomputed.
     * Original canonical inner = 0x3B = 59 bytes. Removing the 9-byte
     * 4F entry → inner = 50 bytes → 70 32.
     */
    private val recordWithoutAid: ByteArray = byteArrayOf(
        0x70, 0x32,
        0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
        0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        0x57, 0x0E,
        0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
    )

    @Test
    fun `parse with aid uses the injected aid even when records lack 4F`() {
        val aid = Aid.fromHex("A0000000031010")
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(aid, listOf(recordWithoutAid)))
        assertEquals(aid, ok.card.aid)
        assertEquals(EmvBrand.VISA, ok.card.brand)
    }

    @Test
    fun `parse with aid still extracts PAN from records`() {
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(Aid.fromHex("A0000000031010"), listOf(recordWithoutAid)),
        )
        assertEquals("4111111111111111", ok.card.pan.unmasked())
    }

    @Test
    fun `parse 1-arg overload preserves existing fixture behavior`() {
        // why: pinning back-compat — the canonical fixture (with 4F inline)
        // continues to parse via the 1-arg overload.
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals(Aid.fromHex("A0000000031010"), ok.card.aid)
    }

    @Test
    fun `parse with aid prefers the injected aid over any 4F in records`() {
        // why: documented contract — when both are available the injected aid wins.
        val mastercardAid = Aid.fromHex("A0000000041010")
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(mastercardAid, listOf(canonicalRecord)),
        )
        assertEquals(mastercardAid, ok.card.aid)
    }

    @Test
    fun `parseOrThrow with aid returns the EmvCard on a valid input`() {
        val aid = Aid.fromHex("A0000000031010")
        val card = EmvParser.parseOrThrow(aid, listOf(recordWithoutAid))
        assertEquals(aid, card.aid)
    }

    /**
     * Visa-style record: `4F` AID + `5F24` expiry + `5F20` cardholder
     * + `50` label + `57` Track 2. NO standalone `5A` PAN. Mirrors
     * the Visa Chase Debit failure observed in real-card QA (#59).
     *
     * Length math (entry size = tag + length + value):
     *   4F (1+1+7=9) + 5F24 (2+1+3=6) + 5F20 (2+1+9=12)
     *   + 50 (1+1+4=6) + 57 (1+1+14=16) = 49 bytes inner → 70 31.
     * (The plan's documented 0x2F undercounted the two-byte tag headers
     * on 5F24 and 5F20 — recomputed here.)
     */
    private val recordWithoutPanTagButWithTrack2: ByteArray = byteArrayOf(
        0x70, 0x31,
        0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
        0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        0x57, 0x0E,
        0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
    )

    /**
     * Same as [recordWithoutPanTagButWithTrack2] minus the `57` entry.
     * Both `5A` and `57` are absent — must fail MissingRequiredTag(5A).
     *
     * Length math: 49 - 16 (57 entry) = 33 bytes inner → 70 21.
     */
    private val recordWithoutPanTagAndWithoutTrack2: ByteArray = byteArrayOf(
        0x70, 0x21,
        0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
        0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
    )

    @Test
    fun `parse falls back to Track2 PAN when tag 5A is absent and tag 57 is present`() {
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(listOf(recordWithoutPanTagButWithTrack2)),
        )
        assertEquals("4111111111111111", ok.card.pan.unmasked())
        assertEquals(EmvBrand.VISA, ok.card.brand)
    }

    @Test
    fun `parse uses tag 5A directly when both 5A and 57 are present`() {
        // why: the canonical fixture has both; assert PAN stays sourced
        // from 5A semantics (same digits in this fixture, so we instead
        // assert the success path doesn't regress).
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals("4111111111111111", ok.card.pan.unmasked())
        assertNotNull(ok.card.track2)
    }

    @Test
    fun `parse fails MissingRequiredTag 5A when both 5A and 57 are absent`() {
        val err = assertIs<EmvCardResult.Err>(
            EmvParser.parse(listOf(recordWithoutPanTagAndWithoutTrack2)),
        )
        val cause = assertIs<EmvCardError.MissingRequiredTag>(err.error)
        assertEquals("5A", cause.tagHex)
    }

    @Test
    fun `parse with aid and TLV nodes resolves PAN from inline tag 57 alone`() {
        // why: simulates the union of (GPO format-2 body inline tags) + 0
        // record TLV — the MSD-only flow where Track 2 is the ONLY
        // PAN-bearing tag (no 5A, no records). Mirrors Visa Credit Chase
        // (#59) at the parser level.
        val track2Bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
        )
        val expiryBytes = byteArrayOf(0x28, 0x12, 0x31)
        val nodes = listOf(
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("57"), track2Bytes,
            ),
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5F24"), expiryBytes,
            ),
        )
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(Aid.fromHex("A0000000031010"), nodes),
        )
        assertEquals("4111111111111111", ok.card.pan.unmasked())
    }

    @Test
    fun `parse with aid and TLV nodes returns EmptyInput when nodes list is empty`() {
        val err = assertIs<EmvCardResult.Err>(
            EmvParser.parse(
                Aid.fromHex("A0000000031010"),
                emptyList<io.github.a7asoft.nfcemv.tlv.Tlv>(),
            ),
        )
        assertEquals(EmvCardError.EmptyInput, err.error)
    }

    @Test
    fun `parse falls back to Track2 expiry when tag 5F24 is absent and tag 57 is present`() {
        // why: real-card observation (#59) — Visa Chase cards omit
        // standalone tag 5F24 and carry expiry only in Track 2 (positions
        // YYMM after the D separator). ISO 7813 + EMV Book 3 treat the
        // Track 2 expiry as canonical when 5F24 is absent.
        // Track2: PAN 4111111111111111, separator D, expiry 3005 (May 2030),
        // service 220, discretionary 1000.
        val track2Bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD3.toByte(), 0x00, 0x52, 0x20, 0x10, 0x00,
        )
        val nodes = listOf(
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("57"), track2Bytes,
            ),
        )
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(Aid.fromHex("A0000000031010"), nodes),
        )
        assertEquals(kotlinx.datetime.YearMonth(2030, 5), ok.card.expiry)
    }

    @Test
    fun `parse uses tag 5F24 directly when both 5F24 and 57 are present`() {
        // why: pin 5F24 precedence over Track2 expiry — they CAN diverge
        // (parser does not cross-validate per EmvParser KDoc).
        val track2Bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD3.toByte(), 0x00, 0x52, 0x20, 0x10, 0x00,
        )
        val expiryBytes = byteArrayOf(0x28, 0x12, 0x31)  // YYMMDD 28-12-31 → Dec 2028
        val nodes = listOf(
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("57"), track2Bytes,
            ),
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5F24"), expiryBytes,
            ),
        )
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(Aid.fromHex("A0000000031010"), nodes),
        )
        // 5F24 wins (Dec 2028), not Track2 expiry (May 2030).
        assertEquals(kotlinx.datetime.YearMonth(2028, 12), ok.card.expiry)
    }

    @Test
    fun `parse fails MissingRequiredTag 5F24 when both 5F24 and 57 are absent`() {
        // why: when neither source provides expiry, fail with the
        // canonical missing-tag error. Mirrors the PAN three-cell matrix.
        val nodes = listOf(
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5A"),
                byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11),
            ),
        )
        val err = assertIs<EmvCardResult.Err>(
            EmvParser.parse(Aid.fromHex("A0000000031010"), nodes),
        )
        val cause = assertIs<EmvCardError.MissingRequiredTag>(err.error)
        assertEquals("5F24", cause.tagHex)
    }

    @Test
    fun `parse uses 9F12 Application Preferred Name when both 9F12 and 50 are present`() {
        // why: EMV Book 1 §12.2.2 — preferred name wins over label when
        // both are present. Real-card observation (#59) — Capital One MC
        // ships 50="MASTERCARD" + 9F12="CAPITAL ONE" in the FCI A5
        // template; UI should show "CAPITAL ONE".
        val track2Bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD3.toByte(), 0x00, 0x52, 0x20, 0x10, 0x00,
        )
        val nodes = listOf(
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("57"), track2Bytes,
            ),
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("50"),
                "MASTERCARD".encodeToByteArray(),
            ),
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("9F12"),
                "CAPITAL ONE".encodeToByteArray(),
            ),
        )
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(Aid.fromHex("A0000000041010"), nodes),
        )
        assertEquals("CAPITAL ONE", ok.card.applicationLabel)
    }

    @Test
    fun `parse falls back to tag 50 when 9F12 is absent`() {
        val track2Bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD3.toByte(), 0x00, 0x52, 0x20, 0x10, 0x00,
        )
        val nodes = listOf(
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("57"), track2Bytes,
            ),
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("50"),
                "VISA CREDIT".encodeToByteArray(),
            ),
        )
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(Aid.fromHex("A0000000031010"), nodes),
        )
        assertEquals("VISA CREDIT", ok.card.applicationLabel)
    }

    @Test
    fun `parse returns null applicationLabel when both 9F12 and 50 are absent`() {
        val track2Bytes = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD3.toByte(), 0x00, 0x52, 0x20, 0x10, 0x00,
        )
        val nodes = listOf(
            io.github.a7asoft.nfcemv.tlv.Tlv.Primitive(
                io.github.a7asoft.nfcemv.tlv.Tag.fromHex("57"), track2Bytes,
            ),
        )
        val ok = assertIs<EmvCardResult.Ok>(
            EmvParser.parse(Aid.fromHex("A0000000031010"), nodes),
        )
        assertEquals(null, ok.card.applicationLabel)
    }

    private companion object {
        const val FUZZ_SEED: Long = 0x454D5643L // "EMVC"
        const val FUZZ_ITERATIONS: Int = 1_000
        const val FUZZ_MAX_RESPONSES: Int = 4
        const val FUZZ_MAX_INPUT_BYTES: Int = 64
    }
}
