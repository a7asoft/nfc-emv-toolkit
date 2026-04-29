package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The TLV layer is one boundary where sensitive bytes (PAN inside tag 5A,
 * Track2 inside tag 57, ARQC inside 9F26) enter the system. Even though the
 * layer doesn't interpret these tags semantically, it MUST NOT make their
 * raw bytes appear in `toString()`, error messages, or exception messages.
 *
 * If any of these tests start to fail, do NOT relax them. Find the leak.
 */
class TlvDecoderPciSafetyTest {

    @Test
    fun `Tlv Primitive toString does not embed PAN bytes`() {
        // 5A 08 41 11 11 11 11 11 11 11 — fake test PAN
        val data = byteArrayOf(
            0x5A, 0x08,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        )
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        val text = ok.tlvs.single().toString()
        assertFalse("4111" in text, "PAN leaked in toString: $text")
        assertFalse("0x41" in text, "PAN leaked in toString: $text")
        assertTrue("length=8" in text)
    }

    @Test
    fun `Tlv Constructed toString does not embed any child value bytes`() {
        // 70 0A 5A 08 41 11 11 11 11 11 11 11
        val data = byteArrayOf(
            0x70, 0x0A,
            0x5A, 0x08,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        )
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        val text = ok.tlvs.single().toString()
        assertFalse("4111" in text)
        assertFalse("0x41" in text)
    }

    @Test
    fun `TlvError on bad input does not embed value bytes`() {
        // Truncated PAN-bearing field: 5A 08 41 11 (declares 8 bytes, only 2 follow)
        val data = byteArrayOf(0x5A, 0x08, 0x41, 0x11)
        val err = assertIs<TlvParseResult.Err>(TlvDecoder.parse(data))
        val text = err.error.toString()
        assertFalse("0x41" in text)
        assertFalse("0x11" in text)
        assertFalse("4111" in text)
    }

    @Test
    fun `TlvParseException message does not embed value bytes`() {
        val data = byteArrayOf(0x5A, 0x08, 0x41, 0x11)
        try {
            TlvDecoder.parseOrThrow(data)
            error("Expected exception")
        } catch (e: TlvParseException) {
            val msg = e.message ?: ""
            assertFalse("0x41" in msg)
            assertFalse("0x11" in msg)
            assertFalse("4111" in msg)
        }
    }

    @Test
    fun `Tag toString returns only hex without value-bytes context`() {
        // Tag bytes are themselves public information; only verify no extras appear.
        val tag = Tag.fromHex("5A")
        val text = tag.toString()
        assertTrue(text.matches(Regex("^[0-9A-F]+$")))
    }
}
