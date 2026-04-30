package io.github.a7asoft.nfcemv.extract

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AflTest {

    @Test
    fun `parse rejects empty input with EmptyInput`() {
        val err = assertIs<AflResult.Err>(Afl.parse(byteArrayOf()))
        assertEquals(AflError.EmptyInput, err.error)
    }

    @Test
    fun `parse rejects byte length not divisible by 4 with InvalidLength`() {
        val err = assertIs<AflResult.Err>(Afl.parse(byteArrayOf(0x08, 0x01, 0x01)))
        assertEquals(AflError.InvalidLength(byteCount = 3), err.error)
    }

    @Test
    fun `parse rejects SFI 0 with InvalidSfi`() {
        val err = assertIs<AflResult.Err>(Afl.parse(byteArrayOf(0x00, 0x01, 0x01, 0x00)))
        assertEquals(AflError.InvalidSfi(offset = 0, sfi = 0), err.error)
    }

    @Test
    fun `parse rejects SFI 31 with InvalidSfi`() {
        val firstByte = ((31 shl 3) and 0xFF).toByte()
        val err = assertIs<AflResult.Err>(Afl.parse(byteArrayOf(firstByte, 0x01, 0x01, 0x00)))
        assertEquals(AflError.InvalidSfi(offset = 0, sfi = 31), err.error)
    }

    @Test
    fun `parse rejects firstRecord 0 with InvalidRecordRange`() {
        val err = assertIs<AflResult.Err>(Afl.parse(byteArrayOf(0x08, 0x00, 0x01, 0x00)))
        assertEquals(AflError.InvalidRecordRange(offset = 0, first = 0, last = 1), err.error)
    }

    @Test
    fun `parse rejects firstRecord greater than lastRecord with InvalidRecordRange`() {
        val err = assertIs<AflResult.Err>(Afl.parse(byteArrayOf(0x08, 0x05, 0x02, 0x00)))
        assertEquals(AflError.InvalidRecordRange(offset = 0, first = 5, last = 2), err.error)
    }

    @Test
    fun `parse decodes a single AFL entry covering SFI 1 record 1`() {
        val ok = assertIs<AflResult.Ok>(Afl.parse(byteArrayOf(0x08, 0x01, 0x01, 0x00)))
        assertEquals(listOf(AflEntry(sfi = 1, firstRecord = 1, lastRecord = 1, odaCount = 0)), ok.afl.entries)
    }

    @Test
    fun `parse decodes multiple AFL entries in source order`() {
        val ok = assertIs<AflResult.Ok>(
            Afl.parse(byteArrayOf(0x08, 0x01, 0x02, 0x01, 0x10, 0x01, 0x03, 0x00)),
        )
        assertEquals(
            listOf(
                AflEntry(sfi = 1, firstRecord = 1, lastRecord = 2, odaCount = 1),
                AflEntry(sfi = 2, firstRecord = 1, lastRecord = 3, odaCount = 0),
            ),
            ok.afl.entries,
        )
    }

    @Test
    fun `parse decodes the high SFI 30 boundary`() {
        val firstByte = ((30 shl 3) and 0xFF).toByte()
        val ok = assertIs<AflResult.Ok>(Afl.parse(byteArrayOf(firstByte, 0x01, 0x01, 0x00)))
        assertEquals(30, ok.afl.entries.single().sfi)
    }

    @Test
    fun `parseOrThrow returns the Afl on a valid input`() {
        val afl = Afl.parseOrThrow(byteArrayOf(0x08, 0x01, 0x01, 0x00))
        assertEquals(1, afl.entries.size)
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on a malformed input`() {
        assertFailsWith<IllegalArgumentException> { Afl.parseOrThrow(byteArrayOf()) }
    }

    @Test
    fun `parse never crashes on random input and always returns a typed result`() {
        val rng = Random(seed = AFL_FUZZ_SEED)
        repeat(times = FUZZ_ITERATIONS) {
            val size = rng.nextInt(0, MAX_FUZZ_SIZE)
            val bytes = ByteArray(size).also { rng.nextBytes(it) }
            val result = Afl.parse(bytes)
            assertTrue(result is AflResult.Ok || result is AflResult.Err)
        }
    }

    private companion object {
        const val AFL_FUZZ_SEED: Int = 0x4A_F1
        const val FUZZ_ITERATIONS: Int = 1_000
        const val MAX_FUZZ_SIZE: Int = 64
    }
}
