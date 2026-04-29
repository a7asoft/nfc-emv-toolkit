package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * The TLV layer is one boundary where sensitive bytes (PAN inside tag 5A,
 * Track2 inside tag 57, ARQC inside 9F26) enter the system. Even though the
 * layer doesn't interpret these tags semantically, it MUST NOT make their
 * raw bytes appear in `toString()`, error messages, or exception messages.
 *
 * If any of these tests start to fail, do NOT relax them. Find the leak
 * (per CLAUDE.md §6.1).
 */
class TlvDecoderPciSafetyTest {

    @Test
    fun `Tlv Primitive toString from tag 5A omits all PAN bytes - exact form`() {
        // 5A 08 41 11 11 11 11 11 11 11 — fake test PAN
        val data = byteArrayOf(0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        assertEquals("Primitive(tag=5A, length=8)", ok.tlvs.single().toString())
    }

    @Test
    fun `Tlv Primitive toString from tag 57 omits all Track2 bytes - exact form`() {
        // 57 0A 41 11 11 11 11 11 11 11 D2 80 — fake Track2 (PAN + 'D' separator + expiry start)
        val data = byteArrayOf(
            0x57, 0x0A,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0xD2.toByte(), 0x80.toByte(),
        )
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        assertEquals("Primitive(tag=57, length=10)", ok.tlvs.single().toString())
    }

    @Test
    fun `Tlv Primitive toString from tag 9F26 omits all ARQC bytes - exact form`() {
        // 9F 26 08 DE AD BE EF CA FE BA BE — fake 8-byte cryptogram
        val data = byteArrayOf(
            0x9F.toByte(), 0x26, 0x08,
            0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(),
            0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
        )
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        assertEquals("Primitive(tag=9F26, length=8)", ok.tlvs.single().toString())
    }

    @Test
    fun `Tlv Constructed toString omits all child value bytes - exact form`() {
        // 70 0A | 5A 08 41 11 11 11 11 11 11 11
        val data = byteArrayOf(0x70, 0x0A, 0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        assertEquals("Constructed(tag=70, children=1)", ok.tlvs.single().toString())
    }

    @Test
    fun `TlvError on truncated PAN does not embed value bytes`() {
        // 5A 08 41 11 — declares 8 bytes, only 2 follow
        val data = byteArrayOf(0x5A, 0x08, 0x41, 0x11)
        val err = assertIs<TlvParseResult.Err>(TlvDecoder.parse(data))
        val text = err.error.toString()
        assertFalse("0x41" in text, "byte leaked: $text")
        assertFalse("0x11" in text, "byte leaked: $text")
        assertFalse("4111" in text, "concatenated bytes leaked: $text")
        assertFalse("65, 17" in text, "decimal-form bytes leaked: $text")
        assertFalse("41 11" in text, "space-separated hex leaked: $text")
    }

    @Test
    fun `TlvParseException message on truncated PAN does not embed value bytes`() {
        val data = byteArrayOf(0x5A, 0x08, 0x41, 0x11)
        try {
            TlvDecoder.parseOrThrow(data)
            error("Expected exception")
        } catch (e: TlvParseException) {
            val msg = e.message ?: ""
            assertFalse("0x41" in msg)
            assertFalse("0x11" in msg)
            assertFalse("4111" in msg)
            assertFalse("65, 17" in msg)
            assertFalse("41 11" in msg)
        }
    }

    @Test
    fun `Tag toString returns hex-only output`() {
        val tag = Tag.fromHex("5A")
        val text = tag.toString()
        assertEquals("5A", text)
    }
}
