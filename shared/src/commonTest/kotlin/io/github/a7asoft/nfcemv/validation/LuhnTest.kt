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
}
