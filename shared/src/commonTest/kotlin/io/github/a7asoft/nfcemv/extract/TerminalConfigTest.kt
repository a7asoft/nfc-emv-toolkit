package io.github.a7asoft.nfcemv.extract

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame

class TerminalConfigTest {

    @Test
    fun `default returns TTQ 36 00 00 00 read-only contactless friendly`() {
        val config = TerminalConfig.default()
        assertContentEquals(
            byteArrayOf(0x36, 0x00, 0x00, 0x00),
            config.terminalTransactionQualifiers,
        )
    }

    @Test
    fun `default returns US country code 08 40`() {
        assertContentEquals(byteArrayOf(0x08, 0x40), TerminalConfig.default().terminalCountryCode)
    }

    @Test
    fun `default returns USD currency code 08 40`() {
        assertContentEquals(byteArrayOf(0x08, 0x40), TerminalConfig.default().transactionCurrencyCode)
    }

    @Test
    fun `default returns 6 zero bytes for amount authorised`() {
        assertContentEquals(ByteArray(6), TerminalConfig.default().amountAuthorised)
    }

    @Test
    fun `default returns 6 zero bytes for amount other`() {
        assertContentEquals(ByteArray(6), TerminalConfig.default().amountOther)
    }

    @Test
    fun `default returns 5 zero bytes for terminal verification results`() {
        assertContentEquals(ByteArray(5), TerminalConfig.default().terminalVerificationResults)
    }

    @Test
    fun `default returns 0x00 for transaction type purchase`() {
        assertEquals(0x00.toByte(), TerminalConfig.default().transactionType)
    }

    @Test
    fun `default returns 0x22 for terminal type attended`() {
        assertEquals(0x22.toByte(), TerminalConfig.default().terminalType)
    }

    @Test
    fun `default returns 60 08 08 for terminal capabilities`() {
        assertContentEquals(
            byteArrayOf(0x60, 0x08, 0x08),
            TerminalConfig.default().terminalCapabilities,
        )
    }

    @Test
    fun `default returns 5 zero bytes for additional terminal capabilities`() {
        assertContentEquals(ByteArray(5), TerminalConfig.default().additionalTerminalCapabilities)
    }

    @Test
    fun `default returns 00 8C for application version number`() {
        assertContentEquals(
            byteArrayOf(0x00, 0x8C.toByte()),
            TerminalConfig.default().applicationVersionNumber,
        )
    }

    @Test
    fun `default returns a fresh instance per call so callers cannot mutate the shared default`() {
        val a = TerminalConfig.default()
        val b = TerminalConfig.default()
        assertNotSame(a.terminalTransactionQualifiers, b.terminalTransactionQualifiers)
        a.terminalTransactionQualifiers[0] = 0x77
        assertEquals(0x36.toByte(), b.terminalTransactionQualifiers[0])
    }

    @Test
    fun `equals returns true for two structurally equal instances`() {
        assertEquals(TerminalConfig.default(), TerminalConfig.default())
    }

    @Test
    fun `hashCode is stable across two structurally equal instances`() {
        assertEquals(TerminalConfig.default().hashCode(), TerminalConfig.default().hashCode())
    }

    @Test
    fun `equals returns false when one ByteArray field differs`() {
        val a = TerminalConfig.default()
        val b = a.copy(terminalCountryCode = byteArrayOf(0x05, 0x40))
        assertNotEquals(a, b)
    }

    @Test
    fun `copy preserves untouched ByteArray fields by content equality`() {
        val a = TerminalConfig.default()
        val b = a.copy(transactionType = 0x09)
        assertContentEquals(a.terminalTransactionQualifiers, b.terminalTransactionQualifiers)
        assertEquals(0x09.toByte(), b.transactionType)
    }
}
