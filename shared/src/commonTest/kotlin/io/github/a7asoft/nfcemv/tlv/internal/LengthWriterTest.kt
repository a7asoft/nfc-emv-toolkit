package io.github.a7asoft.nfcemv.tlv.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LengthWriterTest {

    @Test
    fun `lengthOctets is 1 for length 0`() {
        assertEquals(1, lengthOctets(0))
    }

    @Test
    fun `writeLength encodes 0 as a single 0x00 byte`() {
        val dst = ByteArray(1)
        val end = writeLength(0, dst, 0)
        assertEquals(1, end)
        assertContentEquals(byteArrayOf(0x00), dst)
    }

    @Test
    fun `writeLength encodes 0x7F as a single 0x7F byte`() {
        val dst = ByteArray(1)
        val end = writeLength(0x7F, dst, 0)
        assertEquals(1, end)
        assertContentEquals(byteArrayOf(0x7F), dst)
    }

    @Test
    fun `lengthOctets is 1 for length 0x7F`() {
        assertEquals(1, lengthOctets(0x7F))
    }

    @Test
    fun `writeLength encodes 0x80 as 0x81 0x80`() {
        val dst = ByteArray(2)
        val end = writeLength(0x80, dst, 0)
        assertEquals(2, end)
        assertContentEquals(byteArrayOf(0x81.toByte(), 0x80.toByte()), dst)
    }

    @Test
    fun `writeLength encodes 0xFF as 0x81 0xFF`() {
        val dst = ByteArray(2)
        val end = writeLength(0xFF, dst, 0)
        assertEquals(2, end)
        assertContentEquals(byteArrayOf(0x81.toByte(), 0xFF.toByte()), dst)
    }

    @Test
    fun `lengthOctets is 2 for length 0x80`() {
        assertEquals(2, lengthOctets(0x80))
    }

    @Test
    fun `lengthOctets is 2 for length 0xFF`() {
        assertEquals(2, lengthOctets(0xFF))
    }

    @Test
    fun `writeLength encodes 0x100 as 0x82 0x01 0x00`() {
        val dst = ByteArray(3)
        val end = writeLength(0x100, dst, 0)
        assertEquals(3, end)
        assertContentEquals(byteArrayOf(0x82.toByte(), 0x01, 0x00), dst)
    }

    @Test
    fun `writeLength encodes 0xFFFF as 0x82 0xFF 0xFF`() {
        val dst = ByteArray(3)
        val end = writeLength(0xFFFF, dst, 0)
        assertEquals(3, end)
        assertContentEquals(byteArrayOf(0x82.toByte(), 0xFF.toByte(), 0xFF.toByte()), dst)
    }

    @Test
    fun `lengthOctets is 3 for length 0x100`() {
        assertEquals(3, lengthOctets(0x100))
    }

    @Test
    fun `lengthOctets is 3 for length 0xFFFF`() {
        assertEquals(3, lengthOctets(0xFFFF))
    }
}
