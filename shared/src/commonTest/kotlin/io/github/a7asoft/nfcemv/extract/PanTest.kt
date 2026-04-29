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
}
