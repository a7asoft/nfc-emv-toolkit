package io.github.a7asoft.nfcemv.brand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AidTest {

    @Test
    fun `fromHex accepts a 5-byte AID at the lower length boundary`() {
        val aid = Aid.fromHex("A000000003")
        assertEquals(5, aid.byteCount)
        assertEquals("A000000003", aid.toString())
    }

    @Test
    fun `fromHex accepts a 16-byte AID at the upper length boundary`() {
        val aid = Aid.fromHex("A0000000030000000000000000000000")
        assertEquals(16, aid.byteCount)
    }

    @Test
    fun `fromHex normalises lowercase to uppercase`() {
        assertEquals("A0000000031010", Aid.fromHex("a0000000031010").toString())
    }

    @Test
    fun `fromHex rejects an empty string`() {
        assertFailsWith<IllegalArgumentException> { Aid.fromHex("") }
    }

    @Test
    fun `fromHex rejects an odd-length hex string`() {
        assertFailsWith<IllegalArgumentException> { Aid.fromHex("A00000000") }
    }

    @Test
    fun `fromHex rejects a 4-byte AID below the lower bound`() {
        assertFailsWith<IllegalArgumentException> { Aid.fromHex("A0000000") }
    }

    @Test
    fun `fromHex rejects a 17-byte AID above the upper bound`() {
        assertFailsWith<IllegalArgumentException> {
            Aid.fromHex("A000000003000000000000000000000000")
        }
    }

    @Test
    fun `fromHex rejects non-hex characters`() {
        assertFailsWith<IllegalArgumentException> { Aid.fromHex("A00000000G10") }
    }

    @Test
    fun `fromBytes accepts a 5-byte array`() {
        val bytes = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03)
        assertEquals("A000000003", Aid.fromBytes(bytes).toString())
    }

    @Test
    fun `fromBytes rejects an empty array`() {
        assertFailsWith<IllegalArgumentException> { Aid.fromBytes(byteArrayOf()) }
    }

    @Test
    fun `fromBytes rejects an array longer than 16 bytes`() {
        assertFailsWith<IllegalArgumentException> {
            Aid.fromBytes(ByteArray(17))
        }
    }

    @Test
    fun `rid returns the first 5 bytes hex`() {
        val aid = Aid.fromHex("A0000000031010")
        assertEquals("A000000003", aid.rid)
    }

    @Test
    fun `pix returns the bytes after the RID`() {
        val aid = Aid.fromHex("A0000000031010")
        assertEquals("1010", aid.pix)
    }

    @Test
    fun `pix is empty for a 5-byte AID`() {
        val aid = Aid.fromHex("A000000003")
        assertEquals("", aid.pix)
    }

    @Test
    fun `equality is based on the normalised hex form`() {
        assertEquals(Aid.fromHex("A0000000031010"), Aid.fromHex("a0000000031010"))
    }

    @Test
    fun `inequality on differing AIDs`() {
        assertNotEquals(Aid.fromHex("A0000000031010"), Aid.fromHex("A0000000041010"))
    }

    @Test
    fun `equal Aids share the same hashCode`() {
        assertEquals(
            Aid.fromHex("A0000000031010").hashCode(),
            Aid.fromHex("a0000000031010").hashCode(),
        )
    }

    @Test
    fun `fromHex toString round-trips uppercase hex across 5 to 16 byte lengths`() {
        val fixtures = listOf(
            "A000000003",
            "A0000000031010",
            "A000000025010402",
            "A000000333010101",
            "A00000003200000000000000",
            "A0000000030000000000000000000000",
        )
        val mismatches = fixtures.filter { hex ->
            Aid.fromHex(hex).toString() != hex
        }
        assertEquals(emptyList(), mismatches, "round-trip failed for the listed AIDs")
    }

    @Test
    fun `fromHex normalises lowercase round-trips to uppercase`() {
        val mismatches = listOf("a0000000031010", "a000000025010402", "a000000333010101").filter { hex ->
            Aid.fromHex(hex).toString() != hex.uppercase()
        }
        assertEquals(emptyList(), mismatches, "lowercase round-trip failed for the listed AIDs")
    }

    @Test
    fun `fromBytes toString fromHex round-trip preserves the canonical hex form`() {
        val bytes = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        val fromBytes = Aid.fromBytes(bytes)
        val fromHex = Aid.fromHex(fromBytes.toString())
        assertEquals(fromBytes, fromHex)
    }

    @Test
    fun `fromBytes rejects an array of length 4 just below the minimum`() {
        assertFailsWith<IllegalArgumentException> { Aid.fromBytes(ByteArray(4)) }
    }
}
