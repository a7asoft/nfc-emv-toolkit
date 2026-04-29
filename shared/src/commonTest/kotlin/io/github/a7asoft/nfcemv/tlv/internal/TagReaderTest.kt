package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TagReaderTest {

    private val strict = TlvOptions(strict = true)
    private val lenient = TlvOptions(strict = false)

    @Test
    fun `single byte primitive tag`() {
        val r = TlvReader(byteArrayOf(0x57))
        assertEquals(Tag.fromHex("57"), readTag(r, strict))
        assertEquals(1, r.pos)
    }

    @Test
    fun `single byte universal class`() {
        val r = TlvReader(byteArrayOf(0x10))
        val tag = readTag(r, strict)
        assertEquals(Tag.fromHex("10"), tag)
    }

    @Test
    fun `single byte private class`() {
        val r = TlvReader(byteArrayOf(0xC1.toByte()))
        val tag = readTag(r, strict)
        assertEquals(Tag.fromHex("C1"), tag)
    }

    @Test
    fun `two byte tag 9F02`() {
        val r = TlvReader(byteArrayOf(0x9F.toByte(), 0x02))
        assertEquals(Tag.fromHex("9F02"), readTag(r, strict))
        assertEquals(2, r.pos)
    }

    @Test
    fun `two byte tag BF0C`() {
        val r = TlvReader(byteArrayOf(0xBF.toByte(), 0x0C))
        assertEquals(Tag.fromHex("BF0C"), readTag(r, strict))
    }

    @Test
    fun `three byte tag with two continuations`() {
        // first 0x1F escape; then 0x81 (more, 7 bits = 1); then 0x02 (last, 7 bits = 2)
        // tag number = (1 << 7) | 2 = 130, well above 30
        val r = TlvReader(byteArrayOf(0x1F, 0x81.toByte(), 0x02))
        val tag = readTag(r, strict)
        assertEquals(3, tag.byteCount)
    }

    @Test
    fun `multi byte tag preserves all raw bytes`() {
        val r = TlvReader(byteArrayOf(0x9F.toByte(), 0x02))
        val tag = readTag(r, strict)
        assertEquals("9F02", tag.toString())
    }

    @Test
    fun `eof on empty input throws UnexpectedEof`() {
        val r = TlvReader(byteArrayOf())
        val ex = assertFailsWith<TlvParseException> { readTag(r, strict) }
        assertEquals(TlvError.UnexpectedEof(0), ex.error)
    }

    @Test
    fun `multi byte tag with eof after escape throws IncompleteTag`() {
        val r = TlvReader(byteArrayOf(0x9F.toByte()))
        val ex = assertFailsWith<TlvParseException> { readTag(r, strict) }
        assertEquals(TlvError.IncompleteTag(0), ex.error)
    }

    @Test
    fun `multi byte tag never terminating throws TagTooLong`() {
        // 0x9F + four continuation bytes all with bit 8 set; default maxTagBytes = 4
        val r = TlvReader(byteArrayOf(0x9F.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x01))
        val ex = assertFailsWith<TlvParseException> { readTag(r, strict) }
        val err = ex.error
        assertIs<TlvError.TagTooLong>(err)
        assertEquals(0, err.offset)
        assertEquals(4, err.maxBytes)
    }

    @Test
    fun `strict mode rejects first continuation 0x80`() {
        val r = TlvReader(byteArrayOf(0x9F.toByte(), 0x80.toByte(), 0x01))
        val ex = assertFailsWith<TlvParseException> { readTag(r, strict) }
        assertEquals(TlvError.NonMinimalTagEncoding(0), ex.error)
    }

    @Test
    fun `lenient mode accepts first continuation 0x80`() {
        val r = TlvReader(byteArrayOf(0x9F.toByte(), 0x80.toByte(), 0x01))
        val tag = readTag(r, lenient)
        assertEquals(3, tag.byteCount)
    }

    /**
     * Regression: X.690 §8.1.2.4 mandates short-form for tag numbers ≤ 30, but
     * EMVCo allocates real-world tags like 9F02 (Amount, Authorised Numeric)
     * and BF0C (FCI Issuer Discretionary Data) in long-form even though their
     * decoded tag numbers are 2 and 12 respectively.
     *
     * Strict mode in this library follows EMV practice: it does NOT enforce
     * the X.690 short-form-mandatory rule. If you expect this to fail, you
     * are reading the wrong spec.
     */
    @Test
    fun `strict mode accepts EMV tag 9F02 even though tag number is below 31`() {
        val r = TlvReader(byteArrayOf(0x9F.toByte(), 0x02))
        val tag = readTag(r, strict)
        assertEquals(Tag.fromHex("9F02"), tag)
    }

    @Test
    fun `strict mode accepts EMV tag BF0C with tag number 12 in long form`() {
        val r = TlvReader(byteArrayOf(0xBF.toByte(), 0x0C))
        val tag = readTag(r, strict)
        assertEquals(Tag.fromHex("BF0C"), tag)
    }

    @Test
    fun `strict mode still rejects leading zero in first continuation`() {
        // Even with the EMV deviation above, the X.690 §8.1.2.4 leading-zero rule
        // (first continuation byte != 0x80) IS enforced.
        val r = TlvReader(byteArrayOf(0x9F.toByte(), 0x80.toByte(), 0x02))
        val ex = assertFailsWith<TlvParseException> { readTag(r, strict) }
        assertEquals(TlvError.NonMinimalTagEncoding(0), ex.error)
    }

    @Test
    fun `tag bytes 1F 1F decodes class universal`() {
        val r = TlvReader(byteArrayOf(0x1F, 0x1F))
        val tag = readTag(r, strict)
        assertEquals(io.github.a7asoft.nfcemv.tlv.TagClass.Universal, tag.tagClass)
    }
}
