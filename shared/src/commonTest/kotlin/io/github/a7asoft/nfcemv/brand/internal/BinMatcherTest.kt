package io.github.a7asoft.nfcemv.brand.internal

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinMatcherTest {

    @Test
    fun `Prefix matches a PAN whose digits start with the prefix`() {
        assertTrue(BinMatcher.Prefix("4").matches("4111111111111111"))
        assertTrue(BinMatcher.Prefix("51").matches("5105105105105100"))
    }

    @Test
    fun `Prefix does not match a PAN with a different leading digit`() {
        assertFalse(BinMatcher.Prefix("5").matches("4111111111111111"))
    }

    @Test
    fun `Prefix does not match a PAN shorter than the prefix`() {
        assertFalse(BinMatcher.Prefix("411").matches("41"))
    }

    @Test
    fun `Prefix rejects a non-digit prefix at construction`() {
        assertFailsWith<IllegalArgumentException> { BinMatcher.Prefix("4A") }
    }

    @Test
    fun `Prefix rejects an empty prefix at construction`() {
        assertFailsWith<IllegalArgumentException> { BinMatcher.Prefix("") }
    }

    @Test
    fun `DigitRange matches when the leading digits fall inside the range`() {
        val mc2Series = BinMatcher.DigitRange(length = 4, lo = 2221, hi = 2720)
        assertTrue(mc2Series.matches("2221111111111111"))
        assertTrue(mc2Series.matches("2500000000000000"))
        assertTrue(mc2Series.matches("2720999999999999"))
    }

    @Test
    fun `DigitRange does not match outside the range`() {
        val mc2Series = BinMatcher.DigitRange(length = 4, lo = 2221, hi = 2720)
        assertFalse(mc2Series.matches("2220111111111111"))
        assertFalse(mc2Series.matches("2721000000000000"))
    }

    @Test
    fun `DigitRange does not match when the PAN is shorter than length`() {
        val mc2Series = BinMatcher.DigitRange(length = 4, lo = 2221, hi = 2720)
        assertFalse(mc2Series.matches("222"))
    }

    @Test
    fun `DigitRange handles 6-digit Discover sub-range`() {
        val discover = BinMatcher.DigitRange(length = 6, lo = 622126, hi = 622925)
        assertTrue(discover.matches("6221260000000000"))
        assertTrue(discover.matches("6225000000000000"))
        assertTrue(discover.matches("6229250000000000"))
        assertFalse(discover.matches("6221250000000000"))
        assertFalse(discover.matches("6229260000000000"))
    }

    @Test
    fun `DigitRange rejects non-positive length at construction`() {
        assertFailsWith<IllegalArgumentException> {
            BinMatcher.DigitRange(length = 0, lo = 1, hi = 9)
        }
    }

    @Test
    fun `DigitRange rejects lo greater than hi at construction`() {
        assertFailsWith<IllegalArgumentException> {
            BinMatcher.DigitRange(length = 2, lo = 99, hi = 10)
        }
    }
}
