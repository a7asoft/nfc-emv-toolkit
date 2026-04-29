package io.github.a7asoft.nfcemv.extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class PanTest {

    @Test
    fun `rejects strings shorter than 12 digits with IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Pan("11111111111") } // 11 digits, length guard fires before Luhn check
        assertFailsWith<IllegalArgumentException> { Pan("0") }
        assertFailsWith<IllegalArgumentException> { Pan("") }
    }

    @Test
    fun `rejects strings longer than 19 digits with IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Pan("12345678901234567890") } // 20d
        assertFailsWith<IllegalArgumentException> { Pan("0".repeat(25)) }
    }

    @Test
    fun `rejects Luhn-invalid strings even within length range`() {
        assertFailsWith<IllegalArgumentException> { Pan("4111111111111112") } // 16d, corrupted check
        assertFailsWith<IllegalArgumentException> { Pan("000000000001") }     // 12d, sum != mod10
    }

    @Test
    fun `accepts a 12-digit Luhn-valid PAN at the lower length boundary`() {
        Pan("000000000000")
    }

    @Test
    fun `accepts a 19-digit Luhn-valid PAN at the upper length boundary`() {
        Pan("0004111111111111111")
    }

    @Test
    fun `accepts a 16-digit Visa test PAN`() {
        Pan("4111111111111111")
    }

    @Test
    fun `accepts a 15-digit Amex test PAN`() {
        Pan("378282246310005")
    }

    @Test
    fun `toString masks 16-digit Visa PAN as 411111star6_1111`() {
        assertEquals("411111******1111", Pan("4111111111111111").toString())
    }

    @Test
    fun `toString masks 12-digit PAN as 6prefix_2stars_4suffix`() {
        assertEquals("000000**0000", Pan("000000000000").toString())
    }

    @Test
    fun `toString masks 13-digit legacy Visa PAN as 6prefix_3stars_4suffix`() {
        assertEquals("422222***2222", Pan("4222222222222").toString())
    }

    @Test
    fun `toString masks 15-digit Amex PAN as 6prefix_5stars_4suffix`() {
        assertEquals("378282*****0005", Pan("378282246310005").toString())
    }

    @Test
    fun `toString masks 19-digit PAN as 6prefix_9stars_4suffix`() {
        assertEquals("000411*********1111", Pan("0004111111111111111").toString())
    }

    @Test
    fun `toString never embeds the middle digits of a 16-digit PAN`() {
        val pan = Pan("4111111111111111")
        val rendered = pan.toString()
        assertFalse("111111" in rendered.removePrefix("411111").removeSuffix("1111"))
    }

    @Test
    fun `string interpolation produces the masked form`() {
        val pan = Pan("4111111111111111")
        assertEquals("411111******1111", "$pan")
    }

    @Test
    fun `string interpolation in a sentence includes only the masked form`() {
        val pan = Pan("4111111111111111")
        assertEquals(
            "Card 411111******1111 was authorised.",
            "Card $pan was authorised.",
        )
    }

    @Test
    fun `unmasked returns the raw digit string verbatim`() {
        val raw = "4111111111111111"
        assertEquals(raw, Pan(raw).unmasked())
    }

    @Test
    fun `unmasked round-trip preserves leading zeros`() {
        val raw = "0004111111111111111"
        assertEquals(raw, Pan(raw).unmasked())
    }

    @Test
    fun `two Pan instances with the same digits are equal`() {
        assertEquals(Pan("4111111111111111"), Pan("4111111111111111"))
    }

    @Test
    fun `two Pan instances with different digits are not equal`() {
        assertNotEquals(
            Pan("4111111111111111"),
            Pan("5555555555554444"),
        )
    }

    @Test
    fun `equal Pan instances share the same hashCode`() {
        assertEquals(
            Pan("4111111111111111").hashCode(),
            Pan("4111111111111111").hashCode(),
        )
    }

    @Test
    fun `Pan equality treats leading-zero-padded forms as distinct`() {
        assertNotEquals(
            Pan("4111111111111111"),
            Pan("0004111111111111111"),
        )
    }

    @Test
    fun `IllegalArgumentException for Luhn failure does not embed the raw value`() {
        val raw = "4111111111111112"
        val ex = assertFailsWith<IllegalArgumentException> { Pan(raw) }
        val msg = ex.message ?: ""
        assertFalse(raw in msg, "raw embedded in $msg")
        assertFalse(raw.take(6) in msg, "BIN embedded in $msg")
        assertFalse(raw.takeLast(4) in msg, "last4 embedded in $msg")
        assertEquals("PAN failed Luhn check", msg)
    }

    @Test
    fun `IllegalArgumentException for length violation embeds only the length integer`() {
        val raw = "4111"
        val ex = assertFailsWith<IllegalArgumentException> { Pan(raw) }
        val msg = ex.message ?: ""
        assertFalse(raw in msg, "raw embedded in $msg")
        assertEquals("PAN length must be 12 to 19 digits, was 4", msg)
    }

    @Test
    fun `toString of a freshly constructed Pan never contains the hidden middle digits`() {
        val zeroPans = (12..19).map { len -> Pan("0".repeat(len)) }
        zeroPans.forEach { pan ->
            val rendered = pan.toString()
            val starCount = rendered.count { it == '*' }
            assertEquals(pan.unmasked().length - 10, starCount)
            assertEquals("000000", rendered.take(6))
            assertEquals("0000", rendered.takeLast(4))
        }
    }

    @Test
    fun `toString format does not change between calls and is idempotent`() {
        val pan = Pan("4111111111111111")
        assertEquals(pan.toString(), pan.toString())
    }
}
