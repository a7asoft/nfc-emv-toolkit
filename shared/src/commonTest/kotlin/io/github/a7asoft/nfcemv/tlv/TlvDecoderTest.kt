package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TlvDecoderTest {

    private val opts = TlvOptions()
    private val noPadding = opts.copy(paddingPolicy = PaddingPolicy.Rejected)

    @Test
    fun `empty input returns empty list`() {
        val result = TlvDecoder.parse(byteArrayOf())
        val ok = assertIs<TlvParseResult.Ok>(result)
        assertTrue(ok.tlvs.isEmpty())
    }

    @Test
    fun `single primitive at top level`() {
        val result = TlvDecoder.parse(byteArrayOf(0x57, 0x02, 0x10, 0x20))
        val ok = assertIs<TlvParseResult.Ok>(result)
        assertEquals(1, ok.tlvs.size)
        val prim = assertIs<Tlv.Primitive>(ok.tlvs.single())
        assertEquals(Tag.fromHex("57"), prim.tag)
        assertContentEquals(byteArrayOf(0x10, 0x20), prim.copyValue())
    }

    @Test
    fun `multiple top level primitives`() {
        val data = byteArrayOf(
            0x57, 0x01, 0x10,
            0x5A, 0x01, 0x20,
            0x5F.toByte(), 0x24, 0x03, 0x28, 0x12, 0x31,
        )
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        assertEquals(3, ok.tlvs.size)
        assertEquals(Tag.fromHex("5F24"), ok.tlvs[2].tag)
    }

    @Test
    fun `leading zero padding skipped when tolerated`() {
        val data = byteArrayOf(0x00, 0x00, 0x57, 0x01, 0x10)
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        assertEquals(1, ok.tlvs.size)
    }

    @Test
    fun `trailing zero padding skipped when tolerated`() {
        val data = byteArrayOf(0x57, 0x01, 0x10, 0x00, 0x00)
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        assertEquals(1, ok.tlvs.size)
    }

    @Test
    fun `non-tolerant mode parses 00 00 as empty primitive`() {
        val data = byteArrayOf(0x00, 0x00)
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data, noPadding))
        assertEquals(1, ok.tlvs.size)
        assertEquals(Tag.fromHex("00"), ok.tlvs.single().tag)
    }

    @Test
    fun `realistic constructed FCI template`() {
        // 6F 16
        //   84 07 A0 00 00 00 03 10 10                  (DF Name = Visa AID, 7 bytes)
        //   A5 0B                                       (FCI Proprietary Template, 11 bytes)
        //     50 06 56 49 53 41 30 31                   (App Label "VISA01")
        //     87 01 01                                  (App Priority Indicator)
        // outer body total = 9 (84..) + 13 (A5..) = 22 = 0x16
        val data = byteArrayOf(
            0x6F, 0x16,
            0x84.toByte(), 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0xA5.toByte(), 0x0B,
            0x50, 0x06, 0x56, 0x49, 0x53, 0x41, 0x30, 0x31,
            0x87.toByte(), 0x01, 0x01,
        )
        val ok = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(data))
        val outer = assertIs<Tlv.Constructed>(ok.tlvs.single())
        assertEquals(Tag.fromHex("6F"), outer.tag)
        assertEquals(2, outer.children.size)
        assertEquals(Tag.fromHex("84"), outer.children[0].tag)
        val a5 = assertIs<Tlv.Constructed>(outer.children[1])
        assertEquals(Tag.fromHex("A5"), a5.tag)
        assertEquals(2, a5.children.size)
        assertEquals(Tag.fromHex("50"), a5.children[0].tag)
        assertEquals(Tag.fromHex("87"), a5.children[1].tag)
    }

    @Test
    fun `parse returns Err on bad length octet`() {
        val data = byteArrayOf(0x57, 0xFF.toByte())
        val err = assertIs<TlvParseResult.Err>(TlvDecoder.parse(data))
        assertIs<TlvError.InvalidLengthOctet>(err.error)
    }

    @Test
    fun `parse returns Err on incomplete tag`() {
        val data = byteArrayOf(0x9F.toByte())
        val err = assertIs<TlvParseResult.Err>(TlvDecoder.parse(data))
        assertIs<TlvError.IncompleteTag>(err.error)
    }

    @Test
    fun `parseOrThrow on success returns list`() {
        val list = TlvDecoder.parseOrThrow(byteArrayOf(0x57, 0x01, 0x10))
        assertEquals(1, list.size)
    }

    @Test
    fun `parseOrThrow on bad input throws TlvParseException with same error`() {
        val data = byteArrayOf(0x57, 0xFF.toByte())
        val ex = assertFailsWith<TlvParseException> { TlvDecoder.parseOrThrow(data) }
        assertIs<TlvError.InvalidLengthOctet>(ex.error)
    }

    @Test
    fun `parse and parseOrThrow agree on errors`() {
        val data = byteArrayOf(0x80.toByte())
        val viaResult = TlvDecoder.parse(data)
        val errResult = assertIs<TlvParseResult.Err>(viaResult)
        val errThrown = assertFailsWith<TlvParseException> { TlvDecoder.parseOrThrow(data) }
        assertEquals(errResult.error, errThrown.error)
    }
}
