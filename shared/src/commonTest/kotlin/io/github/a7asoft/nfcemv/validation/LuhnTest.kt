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
}
