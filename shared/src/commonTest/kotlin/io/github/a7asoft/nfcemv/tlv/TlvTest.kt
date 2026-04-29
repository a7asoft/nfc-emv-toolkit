package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
