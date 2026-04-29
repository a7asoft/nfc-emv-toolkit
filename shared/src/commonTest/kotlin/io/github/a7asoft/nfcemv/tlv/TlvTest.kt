package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame

class TlvTest {

    @Test
    fun `primitive equality compares value bytes by content`() {
        val a = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        val b = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `primitive inequality on differing values`() {
        val a = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10))
        val b = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x11))
        assertNotEquals(a, b)
    }

    @Test
    fun `primitive inequality on differing tags`() {
        val a = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10))
        val b = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x10))
        assertNotEquals(a, b)
    }

    @Test
    fun `constructed equality is structural`() {
        val a = Tlv.Constructed(Tag.fromHex("70"), listOf(Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10))))
        val b = Tlv.Constructed(Tag.fromHex("70"), listOf(Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10))))
        assertEquals(a, b)
    }

    @Test
    fun `primitive toString reports tag and length only - exact form`() {
        val tlv = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x41, 0x11, 0x11, 0x11))
        assertEquals("Primitive(tag=5A, length=4)", tlv.toString())
    }

    @Test
    fun `primitive copyValue returns a defensive copy each call`() {
        val tlv = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x41, 0x11, 0x11, 0x11))
        val first = tlv.copyValue()
        val second = tlv.copyValue()
        assertContentEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    fun `primitive constructor copies input array so caller mutations do not leak in`() {
        val source = byteArrayOf(0x41, 0x11, 0x11, 0x11)
        val tlv = Tlv.Primitive(Tag.fromHex("5A"), source)
        source[0] = 0x00
        assertEquals(0x41.toByte(), tlv.copyValue()[0])
    }

    @Test
    fun `primitive copyValue mutation does not leak out into the node`() {
        val tlv = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x41, 0x11, 0x11, 0x11))
        tlv.copyValue()[0] = 0x00
        assertEquals(0x41.toByte(), tlv.copyValue()[0])
    }

    @Test
    fun `Primitive writeValueInto copies bytes directly into the destination`() {
        val node = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x10, 0x20, 0x30))
        val dst = ByteArray(5)
        dst[0] = 0xAA.toByte()
        dst[4] = 0xBB.toByte()
        val end = node.writeValueInto(dst, offset = 1)
        assertEquals(4, end)
        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0x10, 0x20, 0x30, 0xBB.toByte()),
            dst,
        )
    }

    @Test
    fun `Primitive writeValueInto does not expose the stored array`() {
        val node = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x10, 0x20, 0x30))
        val dst = ByteArray(3)
        node.writeValueInto(dst, offset = 0)
        dst[0] = 0x00
        assertEquals(0x10.toByte(), node.copyValue()[0])
    }

    @Test
    fun `constructed toString reports child count`() {
        val tlv = Tlv.Constructed(
            Tag.fromHex("70"),
            listOf(
                Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10)),
                Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x20)),
            ),
        )
        assertEquals("Constructed(tag=70, children=2)", tlv.toString())
    }
}
