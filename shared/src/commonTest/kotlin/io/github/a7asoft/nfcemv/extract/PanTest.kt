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
}
