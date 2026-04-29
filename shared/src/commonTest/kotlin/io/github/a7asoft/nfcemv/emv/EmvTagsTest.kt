package io.github.a7asoft.nfcemv.emv

import io.github.a7asoft.nfcemv.tlv.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmvTagsTest {

    // ---- EmvTagFormat ----

    @Test
    fun `EmvTagFormat enumerates the four EMV format codes`() {
        assertEquals(
            setOf(EmvTagFormat.N, EmvTagFormat.AN, EmvTagFormat.B, EmvTagFormat.CN),
            EmvTagFormat.entries.toSet(),
        )
    }

    // ---- EmvTagLength ----

    @Test
    fun `EmvTagLength Fixed exposes the byte count`() {
        assertEquals(8, EmvTagLength.Fixed(8).bytes)
    }

    @Test
    fun `EmvTagLength Variable exposes the maximum byte count`() {
        assertEquals(252, EmvTagLength.Variable(252).maxBytes)
    }

    @Test
    fun `EmvTagLength Fixed rejects zero byte count`() {
        assertFailsWith<IllegalArgumentException> { EmvTagLength.Fixed(0) }
    }

    @Test
    fun `EmvTagLength Fixed rejects negative byte count`() {
        assertFailsWith<IllegalArgumentException> { EmvTagLength.Fixed(-1) }
    }

    @Test
    fun `EmvTagLength Variable rejects zero maximum`() {
        assertFailsWith<IllegalArgumentException> { EmvTagLength.Variable(0) }
    }

    @Test
    fun `EmvTagLength Variable rejects negative maximum`() {
        assertFailsWith<IllegalArgumentException> { EmvTagLength.Variable(-5) }
    }

    // ---- TagSensitivity ----

    @Test
    fun `TagSensitivity has exactly two values`() {
        assertEquals(setOf(TagSensitivity.PCI, TagSensitivity.PUBLIC), TagSensitivity.entries.toSet())
    }

    // ---- EmvTagInfo construction ----

    @Test
    fun `EmvTagInfo carries the supplied tag`() {
        val info = sampleInfo()
        assertEquals(Tag.fromHex("9F26"), info.tag)
    }

    @Test
    fun `EmvTagInfo carries the supplied name`() {
        assertEquals("Application Cryptogram", sampleInfo().name)
    }

    @Test
    fun `EmvTagInfo carries the supplied format`() {
        assertEquals(EmvTagFormat.B, sampleInfo().format)
    }

    @Test
    fun `EmvTagInfo carries the supplied length`() {
        assertEquals(EmvTagLength.Fixed(8), sampleInfo().length)
    }

    @Test
    fun `EmvTagInfo carries the supplied sensitivity`() {
        assertEquals(TagSensitivity.PCI, sampleInfo().sensitivity)
    }

    // ---- EmvTags lookup ----

    @Test
    fun `lookup returns null for an unknown tag`() {
        assertNull(EmvTags.lookup(Tag.fromHex("99")))
    }

    @Test
    fun `lookup returns null for unknown 0xFF tag`() {
        assertNull(EmvTags.lookup(Tag.fromHex("FF")))
    }

    @Test
    fun `lookup returns null for unknown 0x9F99 tag`() {
        assertNull(EmvTags.lookup(Tag.fromHex("9F99")))
    }

    @Test
    fun `lookup returns null for unknown 0xBFAA tag`() {
        assertNull(EmvTags.lookup(Tag.fromHex("BFAA")))
    }

    @Test
    fun `lookup returns the ARQC entry for tag 9F26`() {
        val info = assertNotNull(EmvTags.lookup(Tag.fromHex("9F26")))
        assertEquals("Application Cryptogram", info.name)
        assertEquals(EmvTagFormat.B, info.format)
        assertEquals(EmvTagLength.Fixed(8), info.length)
        assertEquals(TagSensitivity.PCI, info.sensitivity)
    }

    // ---- Dictionary structural invariants ----

    @Test
    fun `dictionary contains exactly 27 entries`() {
        assertEquals(27, EmvTags.all.size)
    }

    @Test
    fun `dictionary has no duplicate tag entries`() {
        val tags = EmvTags.all.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun `every dictionary entry has a non-blank name`() {
        val blanks = EmvTags.all.filter { it.name.isBlank() }
        assertEquals(emptyList(), blanks, "found entries with blank names")
    }

    @Test
    fun `lookup resolves every registered entry by its tag`() {
        val mismatches = EmvTags.all.filter { entry -> EmvTags.lookup(entry.tag) != entry }
        assertEquals(emptyList(), mismatches, "lookup failed for the listed entries")
    }

    // ---- Spot checks (one entry-shape per test, three field assertions per test) ----
    // Bundled-three-field assertions are treated as one "the entry's shape" concept;
    // if any field is wrong, the entire entry needs revision against EMV Book 3 Annex A.

    @Test
    fun `tag 5A is the PAN with CN format Variable 10 and PCI sensitivity`() {
        val pan = assertNotNull(EmvTags.lookup(Tag.fromHex("5A")))
        assertEquals(EmvTagFormat.CN, pan.format)
        assertEquals(EmvTagLength.Variable(10), pan.length)
        assertEquals(TagSensitivity.PCI, pan.sensitivity)
    }

    @Test
    fun `tag 4F is the AID with B format Variable 16 and PUBLIC sensitivity`() {
        val aid = assertNotNull(EmvTags.lookup(Tag.fromHex("4F")))
        assertEquals(EmvTagFormat.B, aid.format)
        assertEquals(EmvTagLength.Variable(16), aid.length)
        assertEquals(TagSensitivity.PUBLIC, aid.sensitivity)
    }

    @Test
    fun `tag 9F02 amount has N format Fixed 6 and PUBLIC sensitivity`() {
        val amount = assertNotNull(EmvTags.lookup(Tag.fromHex("9F02")))
        assertEquals(EmvTagFormat.N, amount.format)
        assertEquals(EmvTagLength.Fixed(6), amount.length)
        assertEquals(TagSensitivity.PUBLIC, amount.sensitivity)
    }

    @Test
    fun `tag 50 application label has AN format`() {
        val label = assertNotNull(EmvTags.lookup(Tag.fromHex("50")))
        assertEquals(EmvTagFormat.AN, label.format)
    }

    // ---- Distribution invariants ----

    @Test
    fun `at least one entry uses EmvTagFormat N`() {
        assertTrue(EmvTags.all.any { it.format == EmvTagFormat.N }, "no N entry")
    }

    @Test
    fun `at least one entry uses EmvTagFormat AN`() {
        assertTrue(EmvTags.all.any { it.format == EmvTagFormat.AN }, "no AN entry")
    }

    @Test
    fun `at least one entry uses EmvTagFormat B`() {
        assertTrue(EmvTags.all.any { it.format == EmvTagFormat.B }, "no B entry")
    }

    @Test
    fun `at least one entry uses EmvTagFormat CN`() {
        assertTrue(EmvTags.all.any { it.format == EmvTagFormat.CN }, "no CN entry")
    }

    @Test
    fun `at least one entry is flagged TagSensitivity PCI`() {
        assertTrue(EmvTags.all.any { it.sensitivity == TagSensitivity.PCI }, "no PCI entry")
    }

    @Test
    fun `at least one entry is flagged TagSensitivity PUBLIC`() {
        assertTrue(EmvTags.all.any { it.sensitivity == TagSensitivity.PUBLIC }, "no PUBLIC entry")
    }

    @Test
    fun `at least one entry uses EmvTagLength Fixed`() {
        assertTrue(EmvTags.all.any { it.length is EmvTagLength.Fixed }, "no Fixed-length entry")
    }

    @Test
    fun `at least one entry uses EmvTagLength Variable`() {
        assertTrue(EmvTags.all.any { it.length is EmvTagLength.Variable }, "no Variable-length entry")
    }

    // ---- PCI bucket coverage (one test per anchor tag) ----

    @Test
    fun `tag 5A PAN is in the PCI bucket`() {
        val pan = assertNotNull(EmvTags.lookup(Tag.fromHex("5A")))
        assertEquals(TagSensitivity.PCI, pan.sensitivity)
    }

    @Test
    fun `tag 57 Track 2 is in the PCI bucket`() {
        val t2 = assertNotNull(EmvTags.lookup(Tag.fromHex("57")))
        assertEquals(TagSensitivity.PCI, t2.sensitivity)
    }

    @Test
    fun `tag 9F26 ARQC is in the PCI bucket`() {
        val arqc = assertNotNull(EmvTags.lookup(Tag.fromHex("9F26")))
        assertEquals(TagSensitivity.PCI, arqc.sensitivity)
    }

    @Test
    fun `tag 9F4B Signed Dynamic Application Data is in the PCI bucket`() {
        val sdad = assertNotNull(EmvTags.lookup(Tag.fromHex("9F4B")))
        assertEquals(TagSensitivity.PCI, sdad.sensitivity)
    }

    @Test
    fun `tag 9F6B Track 2 Mastercard kernel C-2 is in the PCI bucket`() {
        val t2mc = assertNotNull(EmvTags.lookup(Tag.fromHex("9F6B")))
        assertEquals(TagSensitivity.PCI, t2mc.sensitivity)
    }

    // ---- PUBLIC bucket coverage (one test per anchor tag) ----

    @Test
    fun `tag 4F AID is in the PUBLIC bucket`() {
        val aid = assertNotNull(EmvTags.lookup(Tag.fromHex("4F")))
        assertEquals(TagSensitivity.PUBLIC, aid.sensitivity)
    }

    @Test
    fun `tag 82 AIP is in the PUBLIC bucket`() {
        val aip = assertNotNull(EmvTags.lookup(Tag.fromHex("82")))
        assertEquals(TagSensitivity.PUBLIC, aip.sensitivity)
    }

    @Test
    fun `tag 94 AFL is in the PUBLIC bucket`() {
        val afl = assertNotNull(EmvTags.lookup(Tag.fromHex("94")))
        assertEquals(TagSensitivity.PUBLIC, afl.sensitivity)
    }

    private fun sampleInfo(): EmvTagInfo = EmvTagInfo(
        tag = Tag.fromHex("9F26"),
        name = "Application Cryptogram",
        format = EmvTagFormat.B,
        length = EmvTagLength.Fixed(8),
        sensitivity = TagSensitivity.PCI,
    )
}
