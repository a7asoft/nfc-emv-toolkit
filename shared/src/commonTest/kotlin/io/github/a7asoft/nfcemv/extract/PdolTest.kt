package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.tlv.Tag
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PdolTest {

    @Test
    fun `parse rejects empty input with EmptyInput`() {
        val err = assertIs<PdolResult.Err>(Pdol.parse(byteArrayOf()))
        assertEquals(PdolError.EmptyInput, err.error)
    }

    @Test
    fun `parse decodes a single 9F66 04 entry`() {
        val ok = assertIs<PdolResult.Ok>(Pdol.parse(byteArrayOf(0x9F.toByte(), 0x66, 0x04)))
        assertEquals(listOf(PdolEntry(Tag.fromHex("9F66"), 4)), ok.pdol.entries)
    }

    @Test
    fun `parse decodes a single 1-byte tag entry`() {
        val ok = assertIs<PdolResult.Ok>(Pdol.parse(byteArrayOf(0x95.toByte(), 0x05)))
        assertEquals(listOf(PdolEntry(Tag.fromHex("95"), 5)), ok.pdol.entries)
    }

    @Test
    fun `parse decodes the standard Visa qVSDC PDOL`() {
        val bytes = byteArrayOf(
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
        val ok = assertIs<PdolResult.Ok>(Pdol.parse(bytes))
        assertEquals(9, ok.pdol.entries.size)
        assertEquals(Tag.fromHex("9F66"), ok.pdol.entries[0].tag)
        assertEquals(4, ok.pdol.entries[0].length)
        assertEquals(Tag.fromHex("9F37"), ok.pdol.entries.last().tag)
        assertEquals(4, ok.pdol.entries.last().length)
        assertEquals(33, ok.pdol.entries.sumOf { it.length })
    }

    @Test
    fun `parse decodes a multi-byte tag entry`() {
        // why: 9F38 02 — explicit two-byte BER-TLV tag.
        val ok = assertIs<PdolResult.Ok>(Pdol.parse(byteArrayOf(0x9F.toByte(), 0x38, 0x02)))
        assertEquals(listOf(PdolEntry(Tag.fromHex("9F38"), 2)), ok.pdol.entries)
    }

    @Test
    fun `parse returns IncompleteTag when continuation byte is missing`() {
        // why: 9F is the start of a 2-byte tag (low 5 bits = 0x1F) but stream ends.
        val err = assertIs<PdolResult.Err>(Pdol.parse(byteArrayOf(0x9F.toByte())))
        assertEquals(PdolError.IncompleteTag(0), err.error)
    }

    @Test
    fun `parse returns IncompleteLength when no length byte follows the tag`() {
        val err = assertIs<PdolResult.Err>(Pdol.parse(byteArrayOf(0x9F.toByte(), 0x66)))
        assertEquals(PdolError.IncompleteLength(2), err.error)
    }

    @Test
    fun `parse returns IncompleteLength when no length byte follows a 1-byte tag`() {
        val err = assertIs<PdolResult.Err>(Pdol.parse(byteArrayOf(0x95.toByte())))
        assertEquals(PdolError.IncompleteLength(1), err.error)
    }

    @Test
    fun `parse returns InvalidLength when length byte exceeds 0x7F`() {
        // why: PDOL only uses short-form length per EMV Book 3 §5.4.
        val err = assertIs<PdolResult.Err>(Pdol.parse(byteArrayOf(0x95.toByte(), 0x80.toByte())))
        assertEquals(PdolError.InvalidLength(1, 0x80.toByte()), err.error)
    }

    @Test
    fun `parseOrThrow returns the Pdol on a valid input`() {
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x95.toByte(), 0x05))
        assertEquals(1, pdol.entries.size)
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on empty input`() {
        assertFailsWith<IllegalArgumentException> { Pdol.parseOrThrow(byteArrayOf()) }
    }

    @Test
    fun `parseOrThrow throws IllegalArgumentException on truncated input`() {
        assertFailsWith<IllegalArgumentException> { Pdol.parseOrThrow(byteArrayOf(0x9F.toByte())) }
    }

    @Test
    fun `equals and hashCode are structural over entries`() {
        val a = Pdol.parseOrThrow(byteArrayOf(0x95.toByte(), 0x05))
        val b = Pdol.parseOrThrow(byteArrayOf(0x95.toByte(), 0x05))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `toString reports entry count without leaking values`() {
        val pdol = Pdol.parseOrThrow(byteArrayOf(0x95.toByte(), 0x05))
        assertEquals("Pdol(entries=1)", pdol.toString())
    }

    @Test
    fun `parse never throws on random bytes property fuzz`() {
        // why: typed result discipline — random inputs must funnel through
        // PdolResult and never escape as an unchecked exception.
        val rng = Random(seed = 0xCAFEBABEL)
        repeat(1000) {
            val size = rng.nextInt(0, 64)
            val bytes = ByteArray(size).also { rng.nextBytes(it) }
            // Either Ok or Err — we just want no uncaught throwable.
            val result = Pdol.parse(bytes)
            assertTrue(result is PdolResult.Ok || result is PdolResult.Err)
        }
    }
}
