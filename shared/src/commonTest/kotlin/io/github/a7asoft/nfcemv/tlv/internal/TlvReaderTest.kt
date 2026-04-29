package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TlvReaderTest {

    @Test
    fun `empty input is at eof`() {
        val r = TlvReader(byteArrayOf())
        assertTrue(r.isEof)
        assertEquals(0, r.remaining)
        assertEquals(0, r.pos)
    }

    @Test
    fun `peek does not advance position`() {
        val r = TlvReader(byteArrayOf(0x10, 0x20))
        assertEquals(0x10.toByte(), r.peek())
        assertEquals(0, r.pos)
        assertEquals(0x10.toByte(), r.peek())
        assertEquals(0, r.pos)
    }

    @Test
    fun `read advances and returns byte`() {
        val r = TlvReader(byteArrayOf(0x10, 0x20))
        assertEquals(0x10.toByte(), r.read())
        assertEquals(1, r.pos)
        assertEquals(0x20.toByte(), r.read())
        assertEquals(2, r.pos)
        assertTrue(r.isEof)
    }

    @Test
    fun `readBytes returns exact slice and advances`() {
        val r = TlvReader(byteArrayOf(0x10, 0x20, 0x30, 0x40))
        assertContentEquals(byteArrayOf(0x10, 0x20), r.readBytes(2))
        assertEquals(2, r.pos)
        assertContentEquals(byteArrayOf(0x30, 0x40), r.readBytes(2))
        assertTrue(r.isEof)
    }

    @Test
    fun `readBytes of zero length is allowed`() {
        val r = TlvReader(byteArrayOf(0x10))
        assertContentEquals(byteArrayOf(), r.readBytes(0))
        assertEquals(0, r.pos)
        assertFalse(r.isEof)
    }

    @Test
    fun `peek on eof throws UnexpectedEof at current offset`() {
        val r = TlvReader(byteArrayOf(0x10))
        r.read()
        val ex = assertFailsWith<TlvParseException> { r.peek() }
        assertEquals(TlvError.UnexpectedEof(1), ex.error)
    }

    @Test
    fun `read on eof throws UnexpectedEof`() {
        val r = TlvReader(byteArrayOf())
        val ex = assertFailsWith<TlvParseException> { r.read() }
        assertEquals(TlvError.UnexpectedEof(0), ex.error)
    }

    @Test
    fun `readBytes beyond remaining throws UnexpectedEof at start`() {
        val r = TlvReader(byteArrayOf(0x10, 0x20))
        val ex = assertFailsWith<TlvParseException> { r.readBytes(5) }
        assertEquals(TlvError.UnexpectedEof(0), ex.error)
        assertEquals(0, r.pos)
    }

    @Test
    fun `readBytes negative count throws IAE`() {
        val r = TlvReader(byteArrayOf(0x10))
        assertFailsWith<IllegalArgumentException> { r.readBytes(-1) }
    }

    @Test
    fun `initial position offsets all reads`() {
        val r = TlvReader(byteArrayOf(0x10, 0x20, 0x30), initialPos = 1)
        assertEquals(1, r.pos)
        assertEquals(0x20.toByte(), r.read())
        assertEquals(0x30.toByte(), r.read())
        assertTrue(r.isEof)
    }
}
