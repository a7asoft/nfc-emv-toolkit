package io.github.a7asoft.nfcemv.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuhnTest {

    @Test
    fun `empty string is not a valid Luhn number`() {
        assertFalse("".isValidLuhn())
    }

    @Test
    fun `single zero is a valid Luhn number`() {
        assertTrue("0".isValidLuhn())
    }

    @Test
    fun `single non-zero digit is not a valid Luhn number unless it is zero`() {
        assertFalse("1".isValidLuhn())
        assertFalse("9".isValidLuhn())
    }

    @Test
    fun `whitespace inside string is rejected`() {
        assertFalse("4111 1111 1111 1111".isValidLuhn())
        assertFalse(" 4111111111111111".isValidLuhn())
        assertFalse("4111111111111111 ".isValidLuhn())
    }

    @Test
    fun `letters anywhere in string are rejected`() {
        assertFalse("a".isValidLuhn())
        assertFalse("411a111111111111".isValidLuhn())
        assertFalse("4111111111111111a".isValidLuhn())
        assertFalse("a4111111111111111".isValidLuhn())
    }

    @Test
    fun `punctuation and unicode are rejected`() {
        assertFalse("4111-1111-1111-1111".isValidLuhn())
        assertFalse("4111 1111".isValidLuhn()) // non-breaking space
        assertFalse("4111　1111".isValidLuhn()) // ideographic space
    }

    @Test
    fun `Visa test PAN 4111111111111111 is valid`() {
        assertTrue("4111111111111111".isValidLuhn())
    }

    @Test
    fun `Mastercard test PAN 5555555555554444 is valid`() {
        assertTrue("5555555555554444".isValidLuhn())
    }

    @Test
    fun `Amex 15-digit test PAN 378282246310005 is valid`() {
        assertTrue("378282246310005".isValidLuhn())
    }

    @Test
    fun `Discover test PAN 6011111111111117 is valid`() {
        assertTrue("6011111111111117".isValidLuhn())
    }

    @Test
    fun `JCB test PAN 3530111333300000 is valid`() {
        assertTrue("3530111333300000".isValidLuhn())
    }

    @Test
    fun `Diners test PAN 30569309025904 is valid`() {
        assertTrue("30569309025904".isValidLuhn())
    }

    @Test
    fun `Visa test PAN with corrupted check digit is invalid`() {
        assertFalse("4111111111111112".isValidLuhn())
    }

    @Test
    fun `Visa test PAN with one mid-string digit changed is invalid`() {
        assertFalse("4111111111121111".isValidLuhn())
    }

    @Test
    fun `Mastercard test PAN with adjacent transposition is invalid`() {
        // 5555555555554444 (valid) → 5555555555545444 (swap indices 11 and 12)
        // Confirms adjacent-digit transposition is detected (Luhn catches
        // most but not 09↔90 / 22↔55 / 33↔66 / 44↔77 — see KDoc).
        assertFalse("5555555555545444".isValidLuhn())
    }
}
