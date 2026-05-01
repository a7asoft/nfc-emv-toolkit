package io.github.a7asoft.nfcemv.extract

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PdolResponseBuilderTest {

    private val txDate: ByteArray = byteArrayOf(0x26, 0x04, 0x29)
    private val un: ByteArray = byteArrayOf(0x11, 0x22, 0x33, 0x44)

    @Test
    fun `build returns the 4 TTQ bytes for a 9F66 04 entry from default config`() {
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x9F.toByte(), 0x66, 0x04))
        val response = PdolResponseBuilder.build(pdol, TerminalConfig.default(), txDate, un)
        assertContentEquals(byteArrayOf(0x36, 0x00, 0x00, 0x00), response)
    }

    @Suppress("LongMethod")
    // why: the test exhaustively pins every byte of the canonical Visa
    // qVSDC PDOL response (33 bytes across 9 entries). Splitting hides
    // the byte-by-byte spec citation that makes the assertion auditable.
    @Test
    fun `build emits the full Visa qVSDC PDOL response from the standard PDOL`() {
        val pdolBytes = byteArrayOf(
            0x9F.toByte(), 0x66, 0x04,
            0x9F.toByte(), 0x02, 0x06,
            0x9F.toByte(), 0x03, 0x06,
            0x9F.toByte(), 0x1A, 0x02,
            0x95.toByte(), 0x05,
            0x5F, 0x2A, 0x02,
            0x9A.toByte(), 0x03,
            0x9C.toByte(), 0x01,
            0x9F.toByte(), 0x37, 0x04,
        )
        val pdol = Pdol.parseOrThrow(pdolBytes)
        val response = PdolResponseBuilder.build(pdol, TerminalConfig.default(), txDate, un)
        val expected = byteArrayOf(
            // 9F66 (4) — TTQ
            0x36, 0x00, 0x00, 0x00,
            // 9F02 (6) — amount auth
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // 9F03 (6) — amount other
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            // 9F1A (2) — country
            0x08, 0x40,
            // 95 (5) — TVR
            0x00, 0x00, 0x00, 0x00, 0x00,
            // 5F2A (2) — currency
            0x08, 0x40,
            // 9A (3) — date
            0x26, 0x04, 0x29,
            // 9C (1) — purchase
            0x00,
            // 9F37 (4) — UN
            0x11, 0x22, 0x33, 0x44,
        )
        assertContentEquals(expected, response)
        assertEquals(33, response.size)
    }

    @Test
    fun `build pads an unknown tag with zeros to the requested length`() {
        // why: 9F4E is not in the standard config dispatch; expect 16 zero bytes.
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x9F.toByte(), 0x4E, 0x10))
        val response = PdolResponseBuilder.build(pdol, TerminalConfig.default(), txDate, un)
        assertContentEquals(ByteArray(16), response)
    }

    @Test
    fun `build truncates a value that is longer than the requested length`() {
        // why: country defaults to 2 bytes; request 1 byte → first byte only.
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x9F.toByte(), 0x1A, 0x01))
        val response = PdolResponseBuilder.build(pdol, TerminalConfig.default(), txDate, un)
        assertContentEquals(byteArrayOf(0x08), response)
    }

    @Test
    fun `build pads a value that is shorter than the requested length`() {
        // why: country defaults to 2 bytes; request 4 → 2 bytes + 2 zero pad.
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x9F.toByte(), 0x1A, 0x04))
        val response = PdolResponseBuilder.build(pdol, TerminalConfig.default(), txDate, un)
        assertContentEquals(byteArrayOf(0x08, 0x40, 0x00, 0x00), response)
    }

    @Test
    fun `build returns an empty byte array when the PDOL has zero entries`() {
        // why: a Pdol with zero entries cannot be parsed (EmptyInput) but
        // can still be constructed via the internal API for this guarantee.
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x95.toByte(), 0x00))
        val response = PdolResponseBuilder.build(pdol, TerminalConfig.default(), txDate, un)
        assertContentEquals(byteArrayOf(), response)
    }

    @Test
    fun `build uses the provided transactionDate for tag 9A`() {
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x9A.toByte(), 0x03))
        val response = PdolResponseBuilder.build(
            pdol, TerminalConfig.default(),
            byteArrayOf(0x99.toByte(), 0x12, 0x31),
            un,
        )
        assertContentEquals(byteArrayOf(0x99.toByte(), 0x12, 0x31), response)
    }

    @Test
    fun `build uses the provided unpredictableNumber for tag 9F37`() {
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x9F.toByte(), 0x37, 0x04))
        val response = PdolResponseBuilder.build(
            pdol, TerminalConfig.default(),
            txDate,
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()),
        )
        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()),
            response,
        )
    }

    @Test
    fun `build emits 1 byte for transaction type 9C`() {
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x9C.toByte(), 0x01))
        val response = PdolResponseBuilder.build(pdol, TerminalConfig.default(), txDate, un)
        assertContentEquals(byteArrayOf(0x00), response)
    }

    @Test
    fun `build emits 1 byte for terminal type 9F35`() {
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x9F.toByte(), 0x35, 0x01))
        val response = PdolResponseBuilder.build(pdol, TerminalConfig.default(), txDate, un)
        assertContentEquals(byteArrayOf(0x22), response)
    }
}
