package io.github.a7asoft.nfcemv.extract.internal

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCardError
import io.github.a7asoft.nfcemv.extract.Pan
import io.github.a7asoft.nfcemv.extract.PanError
import io.github.a7asoft.nfcemv.extract.Track2
import io.github.a7asoft.nfcemv.extract.Track2Error
import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmvFieldExtractorsTest {

    // ---- AID ----

    @Test
    fun `extractAid returns the Aid for a valid 7-byte 4F primitive`() {
        val node = Tlv.Primitive(
            Tag.fromHex("4F"),
            byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10),
        )
        val result = extractAid(node)
        assertIs<ExtractResult.Ok<Aid>>(result)
        assertEquals("A0000000031010", result.value.toString())
    }

    @Test
    fun `extractAid surfaces InvalidAid when the byte count is below 5`() {
        val node = Tlv.Primitive(
            Tag.fromHex("4F"),
            byteArrayOf(0xA0.toByte(), 0x00, 0x00),
        )
        val result = extractAid(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidAid(byteCount = 3), err.error)
    }

    @Test
    fun `extractAid surfaces InvalidAid when the byte count is above 16`() {
        val node = Tlv.Primitive(
            Tag.fromHex("4F"),
            ByteArray(17),
        )
        val result = extractAid(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidAid(byteCount = 17), err.error)
    }

    // ---- PAN ----

    @Test
    fun `extractPan returns a Pan for a 16-digit BCD-packed 5A primitive`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5A"),
            byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11),
        )
        val result = extractPan(node)
        val ok = assertIs<ExtractResult.Ok<Pan>>(result)
        assertEquals("4111111111111111", ok.value.unmasked())
    }

    @Test
    fun `extractPan strips a single trailing F-pad nibble`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5A"),
            byteArrayOf(0x42, 0x22, 0x22, 0x22, 0x22, 0x22, 0x2F),
        )
        val result = extractPan(node)
        val ok = assertIs<ExtractResult.Ok<Pan>>(result)
        assertEquals("4222222222222", ok.value.unmasked())
    }

    @Test
    fun `extractPan surfaces PanRejected when Pan parse fails Luhn`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5A"),
            byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12),
        )
        val result = extractPan(node)
        val err = assertIs<ExtractResult.Err>(result)
        val panErr = assertIs<EmvCardError.PanRejected>(err.error)
        assertEquals(PanError.LuhnCheckFailed, panErr.cause)
    }

    @Test
    fun `extractPan surfaces MalformedPanNibble at the actual offending offset`() {
        // Inject 0xA at nibble position 3 (third nibble of the second byte).
        val node = Tlv.Primitive(
            Tag.fromHex("5A"),
            byteArrayOf(0x41, 0x1A, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11),
        )
        val result = extractPan(node)
        val err = assertIs<ExtractResult.Err>(result)
        val malformed = assertIs<EmvCardError.MalformedPanNibble>(err.error)
        assertEquals(3, malformed.offset)
    }

    @Test
    fun `extractPan surfaces MalformedPanNibble for non-trailing F at offset 4`() {
        // 0xF at nibble position 4 (mid-string), final nibble is 1 (not F).
        // Stripping logic only strips a trailing 0xF, so this F at offset 4
        // is correctly flagged as malformed.
        val node = Tlv.Primitive(
            Tag.fromHex("5A"),
            byteArrayOf(0x41, 0x11, 0xF1.toByte(), 0x11, 0x11, 0x11, 0x11, 0x11),
        )
        val result = extractPan(node)
        val err = assertIs<ExtractResult.Err>(result)
        val malformed = assertIs<EmvCardError.MalformedPanNibble>(err.error)
        assertEquals(4, malformed.offset)
    }

    // ---- Expiry ----

    @Test
    fun `extractExpiry returns YearMonth 2028-12 for 281231`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x12, 0x31),
        )
        val result = extractExpiry(node)
        val ok = assertIs<ExtractResult.Ok<YearMonth>>(result)
        assertEquals(YearMonth(2028, 12), ok.value)
    }

    @Test
    fun `extractExpiry surfaces InvalidExpiryFormat for a 2-byte value`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x12),
        )
        val result = extractExpiry(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidExpiryFormat(nibbleCount = 4), err.error)
    }

    @Test
    fun `extractExpiry surfaces InvalidExpiryFormat for a 4-byte value`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x12, 0x31, 0x00),
        )
        val result = extractExpiry(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidExpiryFormat(nibbleCount = 8), err.error)
    }

    @Test
    fun `extractExpiry surfaces InvalidExpiryMonth for month 00`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x00, 0x31),
        )
        val result = extractExpiry(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidExpiryMonth(month = 0), err.error)
    }

    @Test
    fun `extractExpiry surfaces InvalidExpiryMonth for month 13`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x13, 0x31),
        )
        val result = extractExpiry(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidExpiryMonth(month = 13), err.error)
    }

    // ---- Cardholder name + Application label ----

    @Test
    fun `extractCardholderName returns the trimmed ASCII string`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F20"),
            "VISA TEST".encodeToByteArray(),
        )
        assertEquals("VISA TEST", extractCardholderName(node))
    }

    @Test
    fun `extractCardholderName strips trailing 0x20 spaces`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F20"),
            "VISA TEST     ".encodeToByteArray(),
        )
        assertEquals("VISA TEST", extractCardholderName(node))
    }

    @Test
    fun `extractCardholderName returns null on an all-spaces value`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F20"),
            "          ".encodeToByteArray(),
        )
        assertEquals(null, extractCardholderName(node))
    }

    @Test
    fun `extractCardholderName returns null on an empty value`() {
        val node = Tlv.Primitive(
            Tag.fromHex("5F20"),
            byteArrayOf(),
        )
        assertEquals(null, extractCardholderName(node))
    }

    @Test
    fun `extractCardholderName decodes ISO-8859-1 high-byte characters correctly`() {
        // "MÜLLER" in ISO-8859-1: M=0x4D, Ü=0xDC, L=0x4C, L=0x4C, E=0x45, R=0x52
        val bytes = byteArrayOf(0x4D, 0xDC.toByte(), 0x4C, 0x4C, 0x45, 0x52)
        val node = Tlv.Primitive(Tag.fromHex("5F20"), bytes)
        assertEquals("MÜLLER", extractCardholderName(node))
    }

    @Test
    fun `extractCardholderName decodes 0xFF as the highest Latin-1 codepoint`() {
        val bytes = byteArrayOf(0xFF.toByte())
        val node = Tlv.Primitive(Tag.fromHex("5F20"), bytes)
        assertEquals("ÿ", extractCardholderName(node))
    }

    @Test
    fun `extractApplicationLabel returns the trimmed ASCII string`() {
        val node = Tlv.Primitive(
            Tag.fromHex("50"),
            "VISA".encodeToByteArray(),
        )
        assertEquals("VISA", extractApplicationLabel(node))
    }

    @Test
    fun `extractApplicationLabel returns null on an all-spaces value`() {
        val node = Tlv.Primitive(
            Tag.fromHex("50"),
            "    ".encodeToByteArray(),
        )
        assertEquals(null, extractApplicationLabel(node))
    }

    // ---- Track 2 ----

    @Test
    fun `extractTrack2 returns Track2 for a canonical Visa fixture`() {
        val node = Tlv.Primitive(
            Tag.fromHex("57"),
            byteArrayOf(
                0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
                0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
            ),
        )
        val result = extractTrack2(node)
        val ok = assertIs<ExtractResult.Ok<Track2>>(result)
        assertEquals("4111111111111111", ok.value.pan.unmasked())
    }

    @Test
    fun `extractTrack2 surfaces Track2Rejected on a missing separator`() {
        val node = Tlv.Primitive(
            Tag.fromHex("57"),
            byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11),
        )
        val result = extractTrack2(node)
        val err = assertIs<ExtractResult.Err>(result)
        val t2err = assertIs<EmvCardError.Track2Rejected>(err.error)
        assertEquals(Track2Error.MissingSeparator, t2err.cause)
    }
}
