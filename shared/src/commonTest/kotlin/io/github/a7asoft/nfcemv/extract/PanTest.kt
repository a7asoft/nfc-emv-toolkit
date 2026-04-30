package io.github.a7asoft.nfcemv.extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class PanTest {

    // ---- Construction: typed parse() error paths ----

    @Test
    fun `parse rejects strings shorter than 12 digits with LengthOutOfRange`() {
        val err = assertIs<PanResult.Err>(Pan.parse("11111111111"))
        assertEquals(PanError.LengthOutOfRange(length = 11), err.error)
    }

    @Test
    fun `parse rejects an empty string with LengthOutOfRange of length 0`() {
        val err = assertIs<PanResult.Err>(Pan.parse(""))
        assertEquals(PanError.LengthOutOfRange(length = 0), err.error)
    }

    @Test
    fun `parse rejects strings longer than 19 digits with LengthOutOfRange`() {
        val err = assertIs<PanResult.Err>(Pan.parse("12345678901234567890"))
        assertEquals(PanError.LengthOutOfRange(length = 20), err.error)
    }

    @Test
    fun `parse rejects non-digit characters within valid length range with NonDigitCharacters`() {
        val err = assertIs<PanResult.Err>(Pan.parse("411a11111111111X"))
        assertEquals(PanError.NonDigitCharacters, err.error)
    }

    @Test
    fun `parse rejects all-letters input within valid length range with NonDigitCharacters`() {
        val err = assertIs<PanResult.Err>(Pan.parse("abcdefghijkl"))
        assertEquals(PanError.NonDigitCharacters, err.error)
    }

    @Test
    fun `parse rejects whitespace-padded digits with NonDigitCharacters`() {
        val err = assertIs<PanResult.Err>(Pan.parse("4111 1111 1111 1111"))
        assertEquals(PanError.NonDigitCharacters, err.error)
    }

    @Test
    fun `parse rejects Luhn-invalid strings within valid length range with LuhnCheckFailed`() {
        val err = assertIs<PanResult.Err>(Pan.parse("4111111111111112"))
        assertEquals(PanError.LuhnCheckFailed, err.error)
    }

    // ---- Construction: parseOrThrow throws on the same inputs ----

    @Test
    fun `parseOrThrow rejects too-short inputs with IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Pan.parseOrThrow("11111111111") }
    }

    @Test
    fun `parseOrThrow rejects too-long inputs with IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Pan.parseOrThrow("12345678901234567890") }
    }

    @Test
    fun `parseOrThrow rejects non-digit input with IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Pan.parseOrThrow("abcdefghijkl") }
    }

    @Test
    fun `parseOrThrow rejects Luhn-invalid input with IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { Pan.parseOrThrow("4111111111111112") }
    }

    // ---- Construction: typed parse() happy paths ----

    @Test
    fun `parse accepts a 12-digit Luhn-valid PAN at the lower length boundary`() {
        assertIs<PanResult.Ok>(Pan.parse("000000000000"))
    }

    @Test
    fun `parse accepts a 19-digit Luhn-valid PAN at the upper length boundary`() {
        assertIs<PanResult.Ok>(Pan.parse("0004111111111111111"))
    }

    @Test
    fun `parse accepts a 16-digit Visa test PAN`() {
        assertIs<PanResult.Ok>(Pan.parse("4111111111111111"))
    }

    @Test
    fun `parse accepts a 15-digit Amex test PAN`() {
        assertIs<PanResult.Ok>(Pan.parse("378282246310005"))
    }

    // ---- Masking: exact-form snapshots across length classes ----

    @Test
    fun `toString masks 16-digit Visa PAN as 411111star6_1111`() {
        assertEquals("411111******1111", Pan.parseOrThrow("4111111111111111").toString())
    }

    @Test
    fun `toString masks 12-digit PAN as 6prefix_2stars_4suffix`() {
        assertEquals("000000**0000", Pan.parseOrThrow("000000000000").toString())
    }

    @Test
    fun `toString masks 13-digit legacy Visa PAN as 6prefix_3stars_4suffix`() {
        assertEquals("422222***2222", Pan.parseOrThrow("4222222222222").toString())
    }

    @Test
    fun `toString masks 15-digit Amex PAN as 6prefix_5stars_4suffix`() {
        assertEquals("378282*****0005", Pan.parseOrThrow("378282246310005").toString())
    }

    @Test
    fun `toString masks 19-digit PAN as 6prefix_9stars_4suffix`() {
        assertEquals("000411*********1111", Pan.parseOrThrow("0004111111111111111").toString())
    }

    @Test
    fun `toString never embeds the full raw PAN`() {
        val raw = "4111111111111111"
        val pan = Pan.parseOrThrow(raw)
        assertFalse(raw in pan.toString(), "raw embedded in $pan")
    }

    @Test
    fun `toString never embeds the middle digits of a 16-digit PAN`() {
        val pan = Pan.parseOrThrow("4111111111111111")
        val rendered = pan.toString()
        assertFalse("111111" in rendered.removePrefix("411111").removeSuffix("1111"))
    }

    // ---- Interpolation + unmasked round-trip ----

    @Test
    fun `string interpolation produces the masked form`() {
        val pan = Pan.parseOrThrow("4111111111111111")
        assertEquals("411111******1111", "$pan")
    }

    @Test
    fun `string interpolation in a sentence includes only the masked form`() {
        val pan = Pan.parseOrThrow("4111111111111111")
        assertEquals(
            "Card 411111******1111 was authorised.",
            "Card $pan was authorised.",
        )
    }

    @Test
    fun `unmasked returns the raw digit string verbatim`() {
        val raw = "4111111111111111"
        assertEquals(raw, Pan.parseOrThrow(raw).unmasked())
    }

    @Test
    fun `unmasked round-trip preserves leading zeros`() {
        val raw = "0004111111111111111"
        assertEquals(raw, Pan.parseOrThrow(raw).unmasked())
    }

    // ---- Equality / hashCode ----

    @Test
    fun `two Pan instances with the same digits are equal`() {
        assertEquals(Pan.parseOrThrow("4111111111111111"), Pan.parseOrThrow("4111111111111111"))
    }

    @Test
    fun `two Pan instances with different digits are not equal`() {
        assertNotEquals(
            Pan.parseOrThrow("4111111111111111"),
            Pan.parseOrThrow("5555555555554444"),
        )
    }

    @Test
    fun `equal Pan instances share the same hashCode`() {
        assertEquals(
            Pan.parseOrThrow("4111111111111111").hashCode(),
            Pan.parseOrThrow("4111111111111111").hashCode(),
        )
    }

    @Test
    fun `Pan equality treats leading-zero-padded forms as distinct`() {
        assertNotEquals(
            Pan.parseOrThrow("4111111111111111"),
            Pan.parseOrThrow("0004111111111111111"),
        )
    }

    // ---- PCI safety: error paths leak nothing, masking sweep ----

    @Test
    fun `parseOrThrow IllegalArgumentException on Luhn failure does not embed the raw value`() {
        val raw = "4111111111111112"
        val ex = assertFailsWith<IllegalArgumentException> { Pan.parseOrThrow(raw) }
        val msg = ex.message ?: ""
        assertFalse(raw in msg, "raw embedded in $msg")
        assertFalse(raw.take(6) in msg, "BIN embedded in $msg")
        assertFalse(raw.takeLast(4) in msg, "last4 embedded in $msg")
        assertEquals("PAN failed Luhn check", msg)
    }

    @Test
    fun `parseOrThrow IllegalArgumentException on length violation embeds only the length integer`() {
        val raw = "4111"
        val ex = assertFailsWith<IllegalArgumentException> { Pan.parseOrThrow(raw) }
        val msg = ex.message ?: ""
        assertFalse(raw in msg, "raw embedded in $msg")
        assertEquals("PAN length must be 12 to 19 digits, was 4", msg)
    }

    @Test
    fun `parseOrThrow IllegalArgumentException on non-digit input embeds only static text`() {
        val raw = "abcdefghijkl"
        val ex = assertFailsWith<IllegalArgumentException> { Pan.parseOrThrow(raw) }
        val msg = ex.message ?: ""
        assertFalse(raw in msg, "raw embedded in $msg")
        assertEquals("PAN must be ASCII digits only", msg)
    }

    @Test
    fun `toString of a freshly constructed Pan never contains the hidden middle digits`() {
        val zeroPans = (12..19).map { len -> Pan.parseOrThrow("0".repeat(len)) }
        zeroPans.forEach { pan ->
            val rendered = pan.toString()
            val starCount = rendered.count { it == '*' }
            assertEquals(pan.unmasked().length - 10, starCount)
            assertEquals("000000", rendered.take(6))
            assertEquals("0000", rendered.takeLast(4))
        }
    }
}
