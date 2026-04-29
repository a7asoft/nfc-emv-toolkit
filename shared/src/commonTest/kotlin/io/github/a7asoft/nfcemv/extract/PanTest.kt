package io.github.a7asoft.nfcemv.extract

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
