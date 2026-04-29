package io.github.a7asoft.nfcemv.brand

import kotlin.test.Test
import kotlin.test.assertEquals

class AidDirectoryTest {

    @Test
    fun `EmvBrand has 10 distinct variants`() {
        assertEquals(10, EmvBrand.entries.size)
    }

    @Test
    fun `EmvBrand UNKNOWN is the catch-all`() {
        assertEquals("Unknown", EmvBrand.UNKNOWN.displayName)
    }

    @Test
    fun `EmvBrand displayName is human-readable for VISA`() {
        assertEquals("Visa", EmvBrand.VISA.displayName)
    }

    @Test
    fun `EmvBrand displayName is human-readable for AMERICAN_EXPRESS`() {
        assertEquals("American Express", EmvBrand.AMERICAN_EXPRESS.displayName)
    }
}
