package io.github.a7asoft.nfcemv.extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

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
}
