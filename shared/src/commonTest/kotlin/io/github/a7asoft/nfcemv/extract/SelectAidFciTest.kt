package io.github.a7asoft.nfcemv.extract

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectAidFciTest {

    /**
     * Visa-style FCI containing 9F38. Outer 6F covers 84 (AID) + A5
     * proprietary template (50 label + 9F38 PDOL).
     *
     * Length math (verified by hand):
     * - 84 07 [aid:7]                       = 9 bytes
     * - 50 04 V I S A                       = 6 bytes
     * - PDOL value = 9F66 04 + 9F02 06 + 9F37 04 = 9 bytes
     * - 9F38 09 [pdol:9]                    = 12 bytes
     * - A5 inner = 6 + 12                   = 18 bytes → A5 12
     * - A5 outer (header+inner)             = 20 bytes
     * - 6F inner = 9 + 20                   = 29 bytes → 6F 1D
     */
    private val visaFciWithPdol: ByteArray = byteArrayOf(
        0x6F, 0x1D,
        0x84.toByte(), 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        0xA5.toByte(), 0x12,
        0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        0x9F.toByte(), 0x38, 0x09,
        0x9F.toByte(), 0x66, 0x04,
        0x9F.toByte(), 0x02, 0x06,
        0x9F.toByte(), 0x37, 0x04,
    )

    /**
     * Mastercard-style FCI without 9F38.
     *
     * 6F 0E
     *   84 07 [aid]
     *   A5 03 50 01 4D
     */
    private val mastercardFciWithoutPdol: ByteArray = byteArrayOf(
        0x6F, 0x0E,
        0x84.toByte(), 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10,
        0xA5.toByte(), 0x03, 0x50, 0x01, 0x4D,
    )

    @Test
    fun `parse rejects empty input with EmptyInput`() {
        val err = assertIs<SelectAidFciResult.Err>(SelectAidFci.parse(byteArrayOf()))
        assertEquals(SelectAidFciError.EmptyInput, err.error)
    }

    @Test
    fun `parse extracts the PDOL bytes from a Visa style FCI`() {
        val ok = assertIs<SelectAidFciResult.Ok>(SelectAidFci.parse(visaFciWithPdol))
        val expected = byteArrayOf(
            0x9F.toByte(), 0x66, 0x04,
            0x9F.toByte(), 0x02, 0x06,
            0x9F.toByte(), 0x37, 0x04,
        )
        val actual = assertNotNull(ok.fci.pdolBytes)
        assertContentEquals(expected, actual)
        assertEquals(9, actual.size)
    }

    @Test
    fun `parse returns null PDOL bytes for a Mastercard style FCI without 9F38`() {
        val ok = assertIs<SelectAidFciResult.Ok>(SelectAidFci.parse(mastercardFciWithoutPdol))
        assertNull(ok.fci.pdolBytes)
    }

    @Test
    fun `parse returns TlvDecodeFailed on malformed TLV`() {
        // why: 6F 0A claims 10 bytes but only 1 follows.
        val err = assertIs<SelectAidFciResult.Err>(
            SelectAidFci.parse(byteArrayOf(0x6F, 0x0A, 0x00)),
        )
        assertIs<SelectAidFciError.TlvDecodeFailed>(err.error)
    }

    @Test
    fun `parse returns MissingFciTemplate when 6F is absent`() {
        // why: valid TLV (70 02 00 00) but wrong outer tag.
        val err = assertIs<SelectAidFciResult.Err>(
            SelectAidFci.parse(byteArrayOf(0x70, 0x02, 0x00, 0x00)),
        )
        assertEquals(SelectAidFciError.MissingFciTemplate, err.error)
    }

    @Test
    fun `parseOrThrow returns the FCI on a valid input`() {
        val fci = SelectAidFci.parseOrThrow(mastercardFciWithoutPdol)
        assertNull(fci.pdolBytes)
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on empty input`() {
        assertFailsWith<IllegalArgumentException> { SelectAidFci.parseOrThrow(byteArrayOf()) }
    }

    @Test
    fun `equals compares pdolBytes by content`() {
        val a = SelectAidFci.parseOrThrow(visaFciWithPdol)
        val b = SelectAidFci.parseOrThrow(visaFciWithPdol)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equals returns true for two FCIs with null pdolBytes`() {
        val a = SelectAidFci.parseOrThrow(mastercardFciWithoutPdol)
        val b = SelectAidFci.parseOrThrow(mastercardFciWithoutPdol)
        assertEquals(a, b)
    }

    @Test
    fun `toString reports byte count without leaking values`() {
        val fci = SelectAidFci.parseOrThrow(visaFciWithPdol)
        assertEquals("SelectAidFci(pdol=9 bytes)", fci.toString())
    }

    @Test
    fun `parse never throws on random bytes property fuzz`() {
        val rng = Random(seed = 0xDEADBEEFL)
        repeat(500) {
            val size = rng.nextInt(0, 96)
            val bytes = ByteArray(size).also { rng.nextBytes(it) }
            val result = SelectAidFci.parse(bytes)
            assertTrue(result is SelectAidFciResult.Ok || result is SelectAidFciResult.Err)
        }
    }
}
