package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NodeWriterTest {

    @Test
    fun `writes a primitive with single-byte tag and short-form length`() {
        // 57 02 10 20
        val node = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        val dst = ByteArray(encodedSize(node))
        val end = writeNode(node, dst, offset = 0, depth = 0)
        assertEquals(4, end)
        assertContentEquals(byteArrayOf(0x57, 0x02, 0x10, 0x20), dst)
    }

    @Test
    fun `writes a primitive with multi-byte tag`() {
        // 9F 02 03 11 22 33
        val node = Tlv.Primitive(Tag.fromHex("9F02"), byteArrayOf(0x11, 0x22, 0x33))
        val dst = ByteArray(encodedSize(node))
        val end = writeNode(node, dst, offset = 0, depth = 0)
        assertEquals(6, end)
        assertContentEquals(
            byteArrayOf(0x9F.toByte(), 0x02, 0x03, 0x11, 0x22, 0x33),
            dst,
        )
    }

    @Test
    fun `writes a primitive with long-form length`() {
        // 5A 81 80 00 00 ... (128 zero bytes)
        val node = Tlv.Primitive(Tag.fromHex("5A"), ByteArray(0x80))
        val dst = ByteArray(encodedSize(node))
        val end = writeNode(node, dst, offset = 0, depth = 0)
        assertEquals(131, end)
        assertEquals(0x5A.toByte(), dst[0])
        assertEquals(0x81.toByte(), dst[1])
        assertEquals(0x80.toByte(), dst[2])
        assertEquals(0x00.toByte(), dst[3])
        assertEquals(0x00.toByte(), dst[130])
    }

    @Test
    fun `writes a constructed node with one child`() {
        // 70 04 | 57 02 10 20
        val inner = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        val node = Tlv.Constructed(Tag.fromHex("70"), listOf(inner))
        val dst = ByteArray(encodedSize(node))
        val end = writeNode(node, dst, offset = 0, depth = 0)
        assertEquals(6, end)
        assertContentEquals(
            byteArrayOf(0x70, 0x04, 0x57, 0x02, 0x10, 0x20),
            dst,
        )
    }

    @Test
    fun `writes a constructed node with multiple children in source order`() {
        // 70 06 | 57 01 10 | 5A 01 20
        val a = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10))
        val b = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x20))
        val node = Tlv.Constructed(Tag.fromHex("70"), listOf(a, b))
        val dst = ByteArray(encodedSize(node))
        val end = writeNode(node, dst, offset = 0, depth = 0)
        assertEquals(8, end)
        assertContentEquals(
            byteArrayOf(0x70, 0x06, 0x57, 0x01, 0x10, 0x5A, 0x01, 0x20),
            dst,
        )
    }

    @Test
    fun `writes nested constructed depth 3 in pre-order`() {
        // 70 08 | A5 06 | A6 04 | 57 02 10 20
        val leaf = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        val l2 = Tlv.Constructed(Tag.fromHex("A6"), listOf(leaf))
        val l1 = Tlv.Constructed(Tag.fromHex("A5"), listOf(l2))
        val l0 = Tlv.Constructed(Tag.fromHex("70"), listOf(l1))
        val dst = ByteArray(encodedSize(l0))
        val end = writeNode(l0, dst, offset = 0, depth = 0)
        assertEquals(10, end)
        assertContentEquals(
            byteArrayOf(
                0x70, 0x08,
                0xA5.toByte(), 0x06,
                0xA6.toByte(), 0x04,
                0x57, 0x02, 0x10, 0x20,
            ),
            dst,
        )
    }

    @Test
    fun `rejects trees deeper than MAX_DEPTH`() {
        // Build a chain of MAX_DEPTH + 2 constructed nodes wrapping a leaf so
        // the recursion definitely exceeds the bound on the way down.
        var node: Tlv = Tlv.Primitive(Tag.fromHex("57"), ByteArray(0))
        repeat(MAX_DEPTH + 2) {
            node = Tlv.Constructed(Tag.fromHex("70"), listOf(node))
        }
        val dst = ByteArray(encodedSize(node))
        assertFailsWith<IllegalStateException> {
            writeNode(node, dst, offset = 0, depth = 0)
        }
    }
}
