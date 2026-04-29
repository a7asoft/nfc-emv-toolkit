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
    fun `encodes EMV multi-byte tags 9F02 and BF0C preserving raw bytes`() {
        val node = Tlv.Primitive(Tag.fromHex("9F02"), byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x00))
        val out = TlvEncoder.encode(node)
        assertEquals(0x9F.toByte(), out[0])
        assertEquals(0x02.toByte(), out[1])
        assertEquals(0x06.toByte(), out[2])
    }

    @Test
    fun `rejects depth-exhausted trees with IllegalStateException`() {
        var node: Tlv = Tlv.Primitive(Tag.fromHex("57"), ByteArray(0))
        repeat(70) {
            node = Tlv.Constructed(Tag.fromHex("70"), listOf(node))
        }
        assertFailsWith<IllegalStateException> { TlvEncoder.encode(node) }
    }
}
