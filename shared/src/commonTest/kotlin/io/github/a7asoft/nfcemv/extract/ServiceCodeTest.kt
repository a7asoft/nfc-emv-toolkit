package io.github.a7asoft.nfcemv.extract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ServiceCodeTest {

    @Test
    fun `parse returns Ok for a valid 3-digit service code`() {
        val ok = assertIs<ServiceCodeResult.Ok>(ServiceCode.parse("201"))
        assertEquals("201", ok.serviceCode.toString())
    }

    @Test
    fun `parse returns Ok and round-trips other valid ISO 7813 codes through toString`() {
        listOf("101", "502", "000", "999").forEach { code ->
            val ok = assertIs<ServiceCodeResult.Ok>(ServiceCode.parse(code))
            assertEquals(code, ok.serviceCode.toString())
        }
    }

    @Test
    fun `parse returns Err EmptyInput when input is empty`() {
        val err = assertIs<ServiceCodeResult.Err>(ServiceCode.parse(""))
        assertEquals(ServiceCodeError.EmptyInput, err.error)
    }

    @Test
    fun `parse returns Err WrongLength when input is 2 characters`() {
        val err = assertIs<ServiceCodeResult.Err>(ServiceCode.parse("12"))
        assertEquals(ServiceCodeError.WrongLength(length = 2), err.error)
    }

    @Test
    fun `parse returns Err WrongLength when input is 4 characters`() {
        val err = assertIs<ServiceCodeResult.Err>(ServiceCode.parse("2010"))
        assertEquals(ServiceCodeError.WrongLength(length = 4), err.error)
    }

    @Test
    fun `parse returns Err NonDigitCharacter at the offending offset`() {
        val err = assertIs<ServiceCodeResult.Err>(ServiceCode.parse("2A1"))
        assertEquals(ServiceCodeError.NonDigitCharacter(offset = 1), err.error)
    }

    @Test
    fun `parse returns Err NonDigitCharacter when input begins with whitespace`() {
        val err = assertIs<ServiceCodeResult.Err>(ServiceCode.parse(" 20"))
        assertEquals(ServiceCodeError.NonDigitCharacter(offset = 0), err.error)
    }

    @Test
    fun `parseOrThrow returns the ServiceCode for a valid input`() {
        val sc = ServiceCode.parseOrThrow("201")
        assertEquals("201", sc.toString())
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on too-short input`() {
        val ex = assertFailsWith<IllegalArgumentException> { ServiceCode.parseOrThrow("12") }
        assertEquals("ServiceCode must be 3 digits, was 2", ex.message)
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on too-long input`() {
        val ex = assertFailsWith<IllegalArgumentException> { ServiceCode.parseOrThrow("2010") }
        assertEquals("ServiceCode must be 3 digits, was 4", ex.message)
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on non-digit input`() {
        val ex = assertFailsWith<IllegalArgumentException> { ServiceCode.parseOrThrow("20A") }
        assertEquals("ServiceCode contains a non-digit character at offset 2", ex.message)
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on empty input`() {
        val ex = assertFailsWith<IllegalArgumentException> { ServiceCode.parseOrThrow("") }
        assertEquals("ServiceCode input is empty", ex.message)
    }
}
