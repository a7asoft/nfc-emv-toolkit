package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.tlv.TlvError
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PpseTest {

    @Test
    fun `parse rejects empty input with EmptyInput`() {
        val err = assertIs<PpseResult.Err>(Ppse.parse(byteArrayOf()))
        assertEquals(PpseError.EmptyInput, err.error)
    }

    @Test
    fun `parse rejects malformed TLV with TlvDecodeFailed`() {
        val err = assertIs<PpseResult.Err>(Ppse.parse(byteArrayOf(0x6F, 0x05, 0x00)))
        assertIs<TlvError>(err.error.let { (it as PpseError.TlvDecodeFailed).cause })
    }

    @Test
    fun `parse rejects when outer template is not 6F with UnknownTemplate`() {
        // 70 01 00 - valid TLV, wrong outer tag
        val err = assertIs<PpseResult.Err>(Ppse.parse(byteArrayOf(0x70, 0x01, 0x00)))
        assertEquals(PpseError.UnknownTemplate, err.error)
    }

    @Test
    fun `parse rejects when no application templates are present with NoApplicationsFound`() {
        // BF 0C 00 (3 bytes) inside A5 03 (5 bytes) inside 6F 05 (7 bytes total)
        val err = assertIs<PpseResult.Err>(
            Ppse.parse(
                byteArrayOf(0x6F, 0x05, 0xA5.toByte(), 0x03, 0xBF.toByte(), 0x0C, 0x00),
            ),
        )
        assertEquals(PpseError.NoApplicationsFound, err.error)
    }

    @Test
    fun `parse rejects an AID with length below 5 bytes with InvalidAid`() {
        // 4F 04 (6 bytes) + 87 01 01 (3) = 9 inner; 61 09 (11). BF0C 0B (14). A5 0E (16). 6F 10 (18 total).
        val bytes = byteArrayOf(
            0x6F, 0x10,
            0xA5.toByte(), 0x0E,
            0xBF.toByte(), 0x0C, 0x0B,
            0x61, 0x09,
            0x4F, 0x04, 0xA0.toByte(), 0x00, 0x00, 0x03,
            0x87.toByte(), 0x01, 0x01,
        )
        val err = assertIs<PpseResult.Err>(Ppse.parse(bytes))
        assertEquals(PpseError.InvalidAid(byteCount = 4), err.error)
    }

    @Test
    fun `parse decodes a single Visa AID with priority 1`() {
        val ok = assertIs<PpseResult.Ok>(Ppse.parse(visaSingleAidPpse()))
        assertEquals(1, ok.ppse.applications.size)
        val entry = ok.ppse.applications.single()
        assertEquals(Aid.fromHex("A0000000031010"), entry.aid)
        assertEquals(1, entry.priority)
    }

    @Test
    fun `parse decodes multiple AIDs in source order`() {
        val ok = assertIs<PpseResult.Ok>(Ppse.parse(visaThenMastercardPpse()))
        assertEquals(2, ok.ppse.applications.size)
        assertEquals(Aid.fromHex("A0000000031010"), ok.ppse.applications[0].aid)
        assertEquals(1, ok.ppse.applications[0].priority)
        assertEquals(Aid.fromHex("A0000000041010"), ok.ppse.applications[1].aid)
        assertEquals(2, ok.ppse.applications[1].priority)
    }

    @Test
    fun `parse decodes an entry whose priority indicator is absent as null`() {
        // 4F 07 ... = 9 bytes inside 61. 61 09 (11). BF0C 0B (14). A5 0E (16). +84 0E (16) => 6F inner 32, 6F 20 (34 total).
        val bytes = byteArrayOf(
            0x6F, 0x20,
            0x84.toByte(), 0x0E,
            0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
            0xA5.toByte(), 0x0E,
            0xBF.toByte(), 0x0C, 0x0B,
            0x61, 0x09,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        )
        val ok = assertIs<PpseResult.Ok>(Ppse.parse(bytes))
        assertNull(ok.ppse.applications.single().priority)
    }

    @Test
    fun `parseOrThrow returns the Ppse on a valid input`() {
        val ppse = Ppse.parseOrThrow(visaSingleAidPpse())
        assertEquals(1, ppse.applications.size)
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on a malformed input`() {
        assertFailsWith<IllegalArgumentException> { Ppse.parseOrThrow(byteArrayOf()) }
    }

    @Test
    fun `parse decodes priority nibble from a card sending 0x81 cardholder confirmation flag with priority 1`() {
        val ok = assertIs<PpseResult.Ok>(Ppse.parse(visaSingleAidPpseWithPriority(0x81.toByte())))
        assertEquals(1, ok.ppse.applications.single().priority)
    }

    @Test
    fun `parse treats priority 0x00 as no priority null`() {
        val ok = assertIs<PpseResult.Ok>(Ppse.parse(visaSingleAidPpseWithPriority(0x00)))
        assertNull(ok.ppse.applications.single().priority)
    }

    @Test
    fun `parse decodes priority nibble from RFU bit set byte 0xF5 as priority 5`() {
        val ok = assertIs<PpseResult.Ok>(Ppse.parse(visaSingleAidPpseWithPriority(0xF5.toByte())))
        assertEquals(5, ok.ppse.applications.single().priority)
    }

    // why: fuzz test asserts multiple structural invariants on the Ok
    // branch; CC counts each `assertTrue` predicate plus the when arm.
    @Suppress("CyclomaticComplexMethod")
    @Test
    fun `parse never crashes on random input and structural invariants hold`() {
        val rng = Random(seed = PPSE_FUZZ_SEED)
        repeat(times = FUZZ_ITERATIONS) {
            val size = rng.nextInt(0, MAX_FUZZ_SIZE)
            val bytes = ByteArray(size).also { rng.nextBytes(it) }
            when (val result = Ppse.parse(bytes)) {
                is PpseResult.Ok -> {
                    // Post Task 2: priority is null or in 1..15 (low nibble).
                    assertTrue(
                        result.ppse.applications.all { (it.priority ?: 1) in 1..15 },
                        "priority must be null or in 1..15; got ${result.ppse.applications.map { it.priority }}",
                    )
                    // Every entry has a valid AID per ISO 7816-5 (5..16 bytes).
                    assertTrue(
                        result.ppse.applications.all { it.aid.byteCount in 5..16 },
                        "aid byteCount out of 5..16",
                    )
                }
                is PpseResult.Err -> Unit
            }
        }
    }

    private fun visaSingleAidPpse(): ByteArray = byteArrayOf(
        // 6F 23 outer (2 + 35 = 37 bytes total)
        0x6F, 0x23,
        // 84 0E "2PAY.SYS.DDF01" (16 bytes)
        0x84.toByte(), 0x0E,
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
        // A5 11 FCI proprietary (19 bytes)
        0xA5.toByte(), 0x11,
        // BF 0C 0E FCI discretionary (17 bytes)
        0xBF.toByte(), 0x0C, 0x0E,
        // 61 0C application template (14 bytes)
        0x61, 0x0C,
        // 4F 07 Visa AID (9 bytes)
        0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        // 87 01 01 priority indicator (3 bytes)
        0x87.toByte(), 0x01, 0x01,
    )

    private fun visaSingleAidPpseWithPriority(priorityByte: Byte): ByteArray = byteArrayOf(
        // Same outer envelope as visaSingleAidPpse(); only the trailing priority byte varies.
        0x6F, 0x23,
        0x84.toByte(), 0x0E,
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
        0xA5.toByte(), 0x11,
        0xBF.toByte(), 0x0C, 0x0E,
        0x61, 0x0C,
        0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        0x87.toByte(), 0x01, priorityByte,
    )

    private fun visaThenMastercardPpse(): ByteArray = byteArrayOf(
        // Inner: 84 (16) + A5 (1+1 + BF0C inner)
        // Each 61 template = 14 bytes => 2 templates = 28 bytes
        // BF0C inner = 28 bytes => BF0C 1C => 31 bytes
        // A5 inner = 31 bytes => A5 1F => 33 bytes
        // 6F inner = 16 + 33 = 49 bytes => 6F 31 => 51 bytes total
        0x6F, 0x31,
        0x84.toByte(), 0x0E,
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
        0xA5.toByte(), 0x1F,
        0xBF.toByte(), 0x0C, 0x1C,
        // Visa template
        0x61, 0x0C,
        0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        0x87.toByte(), 0x01, 0x01,
        // Mastercard template
        0x61, 0x0C,
        0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10,
        0x87.toByte(), 0x01, 0x02,
    )

    private companion object {
        const val PPSE_FUZZ_SEED: Int = 0x9_FE
        const val FUZZ_ITERATIONS: Int = 1_000
        const val MAX_FUZZ_SIZE: Int = 96
    }
}
