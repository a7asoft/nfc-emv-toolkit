package io.github.a7asoft.nfcemv.extract.internal

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCardError
import io.github.a7asoft.nfcemv.extract.PanError
import io.github.a7asoft.nfcemv.tlv.Tlv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmvFieldExtractorsTest {

    // ---- AID ----

    @Test
    fun `extractAid returns the Aid for a valid 7-byte 4F primitive`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("4F"),
            byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10),
        )
        val result = extractAid(node)
        assertIs<ExtractResult.Ok<Aid>>(result)
        assertEquals("A0000000031010", result.value.toString())
    }

    @Test
    fun `extractAid surfaces InvalidAid when the byte count is below 5`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("4F"),
            byteArrayOf(0xA0.toByte(), 0x00, 0x00),
        )
        val result = extractAid(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidAid(byteCount = 3), err.error)
    }

    @Test
    fun `extractAid surfaces InvalidAid when the byte count is above 16`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("4F"),
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
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5A"),
            byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11),
        )
        val result = extractPan(node)
        val ok = assertIs<ExtractResult.Ok<io.github.a7asoft.nfcemv.extract.Pan>>(result)
        assertEquals("4111111111111111", ok.value.unmasked())
    }

    @Test
    fun `extractPan strips a single trailing F-pad nibble`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5A"),
            byteArrayOf(0x42, 0x22, 0x22, 0x22, 0x22, 0x22, 0x2F),
        )
        val result = extractPan(node)
        val ok = assertIs<ExtractResult.Ok<io.github.a7asoft.nfcemv.extract.Pan>>(result)
        assertEquals("4222222222222", ok.value.unmasked())
    }

    @Test
    fun `extractPan surfaces PanRejected when Pan parse fails Luhn`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5A"),
            byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12),
        )
        val result = extractPan(node)
        val err = assertIs<ExtractResult.Err>(result)
        val panErr = assertIs<EmvCardError.PanRejected>(err.error)
        assertEquals(PanError.LuhnCheckFailed, panErr.cause)
    }

    @Test
    fun `extractPan surfaces PanRejected on a malformed nibble`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5A"),
            byteArrayOf(0x41, 0x1A, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11),
        )
        val result = extractPan(node)
        val err = assertIs<ExtractResult.Err>(result)
        val panErr = assertIs<EmvCardError.PanRejected>(err.error)
        assertEquals(PanError.NonDigitCharacters, panErr.cause)
    }

    // ---- Expiry ----

    @Test
    fun `extractExpiry returns YearMonth 2028-12 for 281231`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x12, 0x31),
        )
        val result = extractExpiry(node)
        val ok = assertIs<ExtractResult.Ok<kotlinx.datetime.YearMonth>>(result)
        assertEquals(kotlinx.datetime.YearMonth(2028, 12), ok.value)
    }

    @Test
    fun `extractExpiry surfaces InvalidExpiryFormat for a 2-byte value`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x12),
        )
        val result = extractExpiry(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidExpiryFormat(nibbleCount = 4), err.error)
    }

    @Test
    fun `extractExpiry surfaces InvalidExpiryFormat for a 4-byte value`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x12, 0x31, 0x00),
        )
        val result = extractExpiry(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidExpiryFormat(nibbleCount = 8), err.error)
    }

    @Test
    fun `extractExpiry surfaces InvalidExpiryMonth for month 00`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x00, 0x31),
        )
        val result = extractExpiry(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidExpiryMonth(month = 0), err.error)
    }

    @Test
    fun `extractExpiry surfaces InvalidExpiryMonth for month 13`() {
        val node = Tlv.Primitive(
            io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5F24"),
            byteArrayOf(0x28, 0x13, 0x31),
        )
        val result = extractExpiry(node)
        val err = assertIs<ExtractResult.Err>(result)
        assertEquals(EmvCardError.InvalidExpiryMonth(month = 13), err.error)
    }
}
