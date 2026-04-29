package io.github.a7asoft.nfcemv.tlv.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaddingSkipperTest {

    @Test
    fun `skipUpTo advances past leading zeros`() {
        val r = TlvReader(byteArrayOf(0x00, 0x00, 0x57, 0x10))
        skipZeroPaddingUpTo(r, endExclusive = 4)
        assertEquals(2, r.pos)
        assertEquals(0x57.toByte(), r.peek())
    }

    @Test
    fun `skipUpTo stops at endExclusive`() {
        val r = TlvReader(byteArrayOf(0x00, 0x00, 0x00))
        skipZeroPaddingUpTo(r, endExclusive = 1)
        assertEquals(1, r.pos)
    }

    @Test
    fun `skipUpTo no-op on non zero first byte`() {
        val r = TlvReader(byteArrayOf(0x57, 0x00))
        skipZeroPaddingUpTo(r, endExclusive = 2)
        assertEquals(0, r.pos)
    }

    @Test
    fun `skipUpTo no-op on empty range`() {
        val r = TlvReader(byteArrayOf(0x00, 0x00))
        skipZeroPaddingUpTo(r, endExclusive = 0)
        assertEquals(0, r.pos)
    }

    @Test
    fun `skipToEof consumes all trailing zeros`() {
        val r = TlvReader(byteArrayOf(0x00, 0x00, 0x00))
        skipZeroPaddingToEof(r)
        assertTrue(r.isEof)
    }

    @Test
    fun `skipToEof stops at first non-zero`() {
        val r = TlvReader(byteArrayOf(0x00, 0x57, 0x00))
        skipZeroPaddingToEof(r)
        assertEquals(1, r.pos)
        assertEquals(0x57.toByte(), r.peek())
    }
}
