package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LengthReaderTest {

    private val strict = TlvOptions(strict = true)
    private val lenient = TlvOptions(strict = false)

    @Test
    fun `short form zero`() {
        val r = TlvReader(byteArrayOf(0x00))
        assertEquals(0, readLength(r, strict))
        assertEquals(1, r.pos)
    }

    @Test
    fun `short form max`() {
        val r = TlvReader(byteArrayOf(0x7F))
        assertEquals(127, readLength(r, strict))
    }

    @Test
    fun `long form 0x81 with minimal value`() {
        val r = TlvReader(byteArrayOf(0x81.toByte(), 0x80.toByte()))
        assertEquals(128, readLength(r, strict))
        assertEquals(2, r.pos)
    }

    @Test
    fun `long form 0x82 two bytes`() {
        val r = TlvReader(byteArrayOf(0x82.toByte(), 0x01, 0x00))
        assertEquals(256, readLength(r, strict))
        assertEquals(3, r.pos)
    }

    @Test
    fun `long form 0x83 three bytes`() {
        val r = TlvReader(byteArrayOf(0x83.toByte(), 0x01, 0x00, 0x00))
        assertEquals(0x010000, readLength(r, strict))
    }

    @Test
    fun `long form 0x84 four bytes within int range`() {
        val r = TlvReader(byteArrayOf(0x84.toByte(), 0x01, 0x00, 0x00, 0x00))
        assertEquals(0x01000000, readLength(r, strict))
    }

    @Test
    fun `indefinite form 0x80 rejected`() {
        val r = TlvReader(byteArrayOf(0x80.toByte()))
        val ex = assertFailsWith<TlvParseException> { readLength(r, strict) }
        assertEquals(TlvError.IndefiniteLengthForbidden(0), ex.error)
    }

    @Test
    fun `reserved 0xFF rejected`() {
        val r = TlvReader(byteArrayOf(0xFF.toByte()))
        val ex = assertFailsWith<TlvParseException> { readLength(r, strict) }
        val err = ex.error
        assertIs<TlvError.InvalidLengthOctet>(err)
        assertEquals(0, err.offset)
        assertEquals(0xFF.toByte(), err.byte)
    }

    @Test
    fun `0x85 rejected as oversized`() {
        val r = TlvReader(byteArrayOf(0x85.toByte()))
        val ex = assertFailsWith<TlvParseException> { readLength(r, strict) }
        assertIs<TlvError.InvalidLengthOctet>(ex.error)
    }

    @Test
    fun `strict mode rejects non-minimal 0x81 0x05`() {
        val r = TlvReader(byteArrayOf(0x81.toByte(), 0x05))
        val ex = assertFailsWith<TlvParseException> { readLength(r, strict) }
        val err = ex.error
        assertIs<TlvError.NonMinimalLengthEncoding>(err)
        assertEquals(0, err.offset)
    }

    @Test
    fun `lenient mode accepts non-minimal 0x81 0x05`() {
        val r = TlvReader(byteArrayOf(0x81.toByte(), 0x05))
        assertEquals(5, readLength(r, lenient))
    }

    @Test
    fun `strict mode rejects 0x82 with value fitting in 1 octet`() {
        val r = TlvReader(byteArrayOf(0x82.toByte(), 0x00, 0x80.toByte()))
        assertFailsWith<TlvParseException> { readLength(r, strict) }
    }

    @Test
    fun `strict mode rejects 0x84 with value fitting in 1 octet`() {
        val r = TlvReader(byteArrayOf(0x84.toByte(), 0x00, 0x00, 0x00, 0x42))
        assertFailsWith<TlvParseException> { readLength(r, strict) }
    }

    @Test
    fun `lenient mode accepts 0x84 padded value`() {
        val r = TlvReader(byteArrayOf(0x84.toByte(), 0x00, 0x00, 0x00, 0x42))
        assertEquals(0x42, readLength(r, lenient))
    }

    @Test
    fun `eof on empty input throws UnexpectedEof`() {
        val r = TlvReader(byteArrayOf())
        val ex = assertFailsWith<TlvParseException> { readLength(r, strict) }
        assertEquals(TlvError.UnexpectedEof(0), ex.error)
    }

    @Test
    fun `eof mid long form throws UnexpectedEof`() {
        val r = TlvReader(byteArrayOf(0x82.toByte(), 0x01))
        val ex = assertFailsWith<TlvParseException> { readLength(r, strict) }
        assertIs<TlvError.UnexpectedEof>(ex.error)
    }
}
