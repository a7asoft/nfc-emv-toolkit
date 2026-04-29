package io.github.a7asoft.nfcemv.extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceCodeTest {

    @Test
    fun `accepts a 3-digit ISO 7813 service code`() {
        assertEquals("201", ServiceCode("201").toString())
    }

    @Test
    fun `accepts other valid ISO 7813 codes and round-trips them through toString`() {
        listOf("101", "502", "000", "999").forEach { code ->
            kotlin.test.assertEquals(code, ServiceCode(code).toString())
        }
    }

    @Test
    fun `rejects strings shorter than 3 characters`() {
        assertFailsWith<IllegalArgumentException> { ServiceCode("12") }
        assertFailsWith<IllegalArgumentException> { ServiceCode("") }
    }

    @Test
    fun `rejects strings longer than 3 characters`() {
        assertFailsWith<IllegalArgumentException> { ServiceCode("2010") }
    }

    @Test
    fun `rejects non-digit characters`() {
        assertFailsWith<IllegalArgumentException> { ServiceCode("20A") }
        assertFailsWith<IllegalArgumentException> { ServiceCode(" 20") }
    }
}
