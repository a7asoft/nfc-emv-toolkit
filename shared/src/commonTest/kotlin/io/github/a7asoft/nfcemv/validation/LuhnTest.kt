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

    @Test
    fun `8-digit minimum PAN length validates`() {
        assertTrue("00000000".isValidLuhn())
    }

    @Test
    fun `13-digit legacy Visa PAN validates`() {
        assertTrue("4222222222222".isValidLuhn())
    }

    @Test
    fun `19-digit max-length PAN validates`() {
        // Leading zeros do not affect Luhn (per ISO/IEC 7812-1 Annex B); use
        // a 16-digit Visa test PAN with three leading zeros to reach 19.
        assertTrue("0004111111111111111".isValidLuhn())
    }

    @Test
    fun `leading zeros do not affect validity`() {
        assertTrue("4111111111111111".isValidLuhn())
        assertTrue("04111111111111111".isValidLuhn())
        assertTrue("00004111111111111111".isValidLuhn())
        assertFalse("4111111111111112".isValidLuhn())
        assertFalse("00004111111111111112".isValidLuhn())
    }

    @Test
    fun `all-zero strings of arbitrary length validate`() {
        assertTrue("0".isValidLuhn())
        assertTrue("00".isValidLuhn())
        assertTrue("000".isValidLuhn())
        assertTrue("0000000000000000".isValidLuhn())
    }

    @Test
    fun `agrees with a reference Luhn implementation on 1000 random digit strings`() {
        val rng = kotlin.random.Random(SEED)
        repeat(ITERATIONS) {
            val len = rng.nextInt(1, MAX_LENGTH + 1)
            val s = buildString(len) {
                repeat(len) { append(rng.nextInt(10)) }
            }
            kotlin.test.assertEquals(
                referenceLuhn(s),
                s.isValidLuhn(),
                "mismatch for input $s",
            )
        }
    }

    private fun referenceLuhn(s: String): Boolean {
        if (s.isEmpty()) return false
        val digits = s.reversed().map { it - '0' }
        val sum = digits.withIndex().sumOf { (i, d) ->
            if (i % 2 == 1) {
                val doubled = d * 2
                if (doubled > 9) doubled - 9 else doubled
            } else {
                d
            }
        }
        return sum % 10 == 0
    }

    private companion object {
        const val SEED: Long = 0x4C55484EL
        const val ITERATIONS: Int = 1_000
        const val MAX_LENGTH: Int = 24
    }
}
