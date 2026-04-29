package io.github.a7asoft.nfcemv.extract.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class BcdNibblesTest {

    @Test
    fun `nibbleCount is twice the byte count`() {
        assertEquals(0, byteArrayOf().nibbleCount())
        assertEquals(2, byteArrayOf(0x12).nibbleCount())
        assertEquals(8, byteArrayOf(0x12, 0x34, 0x56, 0x78).nibbleCount())
    }

    @Test
    fun `nibbleAt returns the high nibble at even index`() {
        assertEquals(0x4, byteArrayOf(0x41, 0x11).nibbleAt(0))
        assertEquals(0x1, byteArrayOf(0x41, 0x11).nibbleAt(2))
    }

    @Test
    fun `nibbleAt returns the low nibble at odd index`() {
        assertEquals(0x1, byteArrayOf(0x41, 0x11).nibbleAt(1))
        assertEquals(0x1, byteArrayOf(0x41, 0x11).nibbleAt(3))
    }

    @Test
    fun `nibbleAt extracts D and F nibbles`() {
        val bytes = byteArrayOf(0xD2.toByte(), 0x81.toByte())
        assertEquals(0xD, bytes.nibbleAt(0))
        assertEquals(0x2, bytes.nibbleAt(1))
        assertEquals(0x8, bytes.nibbleAt(2))
        assertEquals(0x1, bytes.nibbleAt(3))
    }

    @Test
    fun `nibbleAt throws IndexOutOfBoundsException on a negative index`() {
        val bytes = byteArrayOf(0x12, 0x34)
        kotlin.test.assertFailsWith<IndexOutOfBoundsException> { bytes.nibbleAt(-1) }
    }

    @Test
    fun `nibbleAt throws IndexOutOfBoundsException on an out-of-range index`() {
        val bytes = byteArrayOf(0x12, 0x34) // nibbleCount = 4
        kotlin.test.assertFailsWith<IndexOutOfBoundsException> { bytes.nibbleAt(4) }
    }

    @Test
    fun `nibbleAt throws IndexOutOfBoundsException on an empty array`() {
        kotlin.test.assertFailsWith<IndexOutOfBoundsException> { byteArrayOf().nibbleAt(0) }
    }
}
