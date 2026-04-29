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
}
