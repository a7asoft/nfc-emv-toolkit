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
    fun `parse extracts every required field from the canonical fixture`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        val card = ok.card
        assertEquals("4111111111111111", card.pan.unmasked())
        assertEquals(YearMonth(2028, 12), card.expiry)
        assertEquals(Aid.fromHex("A0000000031010"), card.aid)
        assertEquals(EmvBrand.VISA, card.brand)
    }

    @Test
    fun `parse extracts cardholder name and application label`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals("VISA TEST", ok.card.cardholderName)
        assertEquals("VISA", ok.card.applicationLabel)
    }

    @Test
    fun `parse extracts Track 2 when tag 57 is present`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        val track2 = assertNotNull(ok.card.track2)
        assertEquals("4111111111111111", track2.pan.unmasked())
        assertEquals(YearMonth(2028, 12), track2.expiry)
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
    fun `parse surfaces TlvDecodeFailed on malformed TLV input`() {
        val malformed = byteArrayOf(0x4F, 0xFF.toByte(), 0x00)
        val err = assertIs<EmvCardResult.Err>(EmvParser.parse(listOf(malformed)))
        val tlvErr = assertIs<EmvCardError.TlvDecodeFailed>(err.error)
        assertNotNull(tlvErr.cause)
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

    private companion object {
        const val FUZZ_SEED: Long = 0x454D5643L // "EMVC"
        const val FUZZ_ITERATIONS: Int = 1_000
        const val FUZZ_MAX_RESPONSES: Int = 4
        const val FUZZ_MAX_INPUT_BYTES: Int = 64
    }
}
