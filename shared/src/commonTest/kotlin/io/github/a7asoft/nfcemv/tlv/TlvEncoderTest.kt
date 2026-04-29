package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TlvEncoderTest {

    @Test
    fun `encodes a single primitive`() {
        // 57 02 10 20
        val node = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        val out = TlvEncoder.encode(node)
        assertContentEquals(byteArrayOf(0x57, 0x02, 0x10, 0x20), out)
    }

    @Test
    fun `encodes an empty primitive`() {
        // 5A 00
        val node = Tlv.Primitive(Tag.fromHex("5A"), ByteArray(0))
        val out = TlvEncoder.encode(node)
        assertContentEquals(byteArrayOf(0x5A, 0x00), out)
    }

    @Test
    fun `encodes a constructed node with a single child`() {
        // 70 04 57 02 10 20
        val inner = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        val outer = Tlv.Constructed(Tag.fromHex("70"), listOf(inner))
        val out = TlvEncoder.encode(outer)
        assertContentEquals(byteArrayOf(0x70, 0x04, 0x57, 0x02, 0x10, 0x20), out)
    }

    @Test
    fun `encodes EMV multi-byte tag 9F02 preserving raw bytes`() {
        val node = Tlv.Primitive(Tag.fromHex("9F02"), byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x00))
        val out = TlvEncoder.encode(node)
        assertEquals(0x9F.toByte(), out[0])
        assertEquals(0x02.toByte(), out[1])
        assertEquals(0x06.toByte(), out[2])
    }

    @Test
    fun `encodes EMV multi-byte tag BF0C as a constructed node preserving raw tag bytes`() {
        // BF0C is a 2-byte tag (class 10 application, P/C 1 constructed, tag number 12 in long form).
        // Round-trip a small constructed tree with one primitive child.
        // Expected wire bytes: BF 0C 03 | 9F 36 00
        // (BF0C constructed, length 3, child = 9F36 ATC primitive, length 0)
        val child = Tlv.Primitive(Tag.fromHex("9F36"), ByteArray(0))
        val node = Tlv.Constructed(Tag.fromHex("BF0C"), listOf(child))
        val out = TlvEncoder.encode(node)
        assertContentEquals(
            byteArrayOf(0xBF.toByte(), 0x0C, 0x03, 0x9F.toByte(), 0x36, 0x00),
            out,
        )
    }

    @Test
    fun `rejects depth-exhausted trees with IllegalStateException`() {
        var node: Tlv = Tlv.Primitive(Tag.fromHex("57"), ByteArray(0))
        repeat(70) {
            node = Tlv.Constructed(Tag.fromHex("70"), listOf(node))
        }
        assertFailsWith<IllegalStateException> { TlvEncoder.encode(node) }
    }

    @Test
    fun `encodes an empty list as an empty ByteArray`() {
        val out = TlvEncoder.encode(emptyList())
        assertEquals(0, out.size)
    }

    @Test
    fun `encodes multiple top-level primitives in source order`() {
        val a = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10))
        val b = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x20))
        val out = TlvEncoder.encode(listOf(a, b))
        assertContentEquals(
            byteArrayOf(0x57, 0x01, 0x10, 0x5A, 0x01, 0x20),
            out,
        )
    }

    @Test
    fun `single-element list overload matches single-node overload`() {
        val node = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        assertContentEquals(TlvEncoder.encode(node), TlvEncoder.encode(listOf(node)))
    }
}
