package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagTest {

    @Test
    fun `single byte tag preserves bytes`() {
        val tag = Tag.ofBytes(byteArrayOf(0x57))
        assertEquals(1, tag.byteCount)
        assertContentEquals(byteArrayOf(0x57), tag.bytes)
    }

    @Test
    fun `two byte tag preserves bytes`() {
        val tag = Tag.ofBytes(byteArrayOf(0x9F.toByte(), 0x02))
        assertEquals(2, tag.byteCount)
        assertContentEquals(byteArrayOf(0x9F.toByte(), 0x02), tag.bytes)
    }

    @Test
    fun `four byte tag preserves bytes`() {
        val raw = byteArrayOf(0x9F.toByte(), 0x01, 0x02, 0x03)
        val tag = Tag.ofBytes(raw)
        assertEquals(4, tag.byteCount)
        assertContentEquals(raw, tag.bytes)
    }

    @Test
    fun `fromHex parses uppercase`() {
        val tag = Tag.fromHex("9F02")
        assertContentEquals(byteArrayOf(0x9F.toByte(), 0x02), tag.bytes)
    }

    @Test
    fun `fromHex parses lowercase`() {
        val tag = Tag.fromHex("9f02")
        assertContentEquals(byteArrayOf(0x9F.toByte(), 0x02), tag.bytes)
    }

    @Test
    fun `toString returns uppercase hex`() {
        assertEquals("9F02", Tag.ofBytes(byteArrayOf(0x9F.toByte(), 0x02)).toString())
    }

    @Test
    fun `tagClass universal for low bits`() {
        assertEquals(TagClass.Universal, Tag.ofBytes(byteArrayOf(0x00)).tagClass)
    }

    @Test
    fun `tagClass application for 0x40`() {
        assertEquals(TagClass.Application, Tag.ofBytes(byteArrayOf(0x40)).tagClass)
    }

    @Test
    fun `tagClass context specific for 0x80`() {
        assertEquals(TagClass.ContextSpecific, Tag.ofBytes(byteArrayOf(0x80.toByte())).tagClass)
    }

    @Test
    fun `tagClass private for 0xC0`() {
        assertEquals(TagClass.Private, Tag.ofBytes(byteArrayOf(0xC0.toByte())).tagClass)
    }

    @Test
    fun `tagClass uses first byte for multi byte tag`() {
        val tag = Tag.ofBytes(byteArrayOf(0x9F.toByte(), 0x02))
        assertEquals(TagClass.ContextSpecific, tag.tagClass)
    }

    @Test
    fun `isConstructed true when bit 6 set`() {
        assertTrue(Tag.ofBytes(byteArrayOf(0x70)).isConstructed)
        assertFalse(Tag.ofBytes(byteArrayOf(0x70)).isPrimitive)
    }

    @Test
    fun `isPrimitive true when bit 6 clear`() {
        assertTrue(Tag.ofBytes(byteArrayOf(0x57)).isPrimitive)
        assertFalse(Tag.ofBytes(byteArrayOf(0x57)).isConstructed)
    }

    @Test
    fun `isConstructed reads first byte for multi byte tag`() {
        val tag = Tag.ofBytes(byteArrayOf(0xBF.toByte(), 0x0C))
        assertTrue(tag.isConstructed)
    }

    @Test
    fun `equality is structural and allocation free`() {
        val a = Tag.ofBytes(byteArrayOf(0x9F.toByte(), 0x02))
        val b = Tag.fromHex("9F02")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `inequality across different tags`() {
        val a = Tag.fromHex("57")
        val b = Tag.fromHex("9F02")
        assertTrue(a != b)
    }

    @Test
    fun `bytes returns a defensive copy`() {
        val tag = Tag.ofBytes(byteArrayOf(0x9F.toByte(), 0x02))
        val first = tag.bytes
        first[0] = 0
        assertContentEquals(byteArrayOf(0x9F.toByte(), 0x02), tag.bytes)
    }

    @Test
    fun `ofBytes rejects empty`() {
        assertFailsWith<IllegalArgumentException> { Tag.ofBytes(byteArrayOf()) }
    }

    @Test
    fun `ofBytes rejects more than four bytes`() {
        val tooLong = byteArrayOf(0x9F.toByte(), 0x01, 0x02, 0x03, 0x04)
        assertFailsWith<IllegalArgumentException> { Tag.ofBytes(tooLong) }
    }

    @Test
    fun `fromHex rejects empty`() {
        assertFailsWith<IllegalArgumentException> { Tag.fromHex("") }
    }

    @Test
    fun `fromHex rejects odd length`() {
        assertFailsWith<IllegalArgumentException> { Tag.fromHex("9F0") }
    }

    @Test
    fun `fromHex rejects invalid digit`() {
        assertFailsWith<IllegalArgumentException> { Tag.fromHex("9G") }
    }
}
