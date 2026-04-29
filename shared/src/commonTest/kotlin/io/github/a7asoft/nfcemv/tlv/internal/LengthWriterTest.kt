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
}
