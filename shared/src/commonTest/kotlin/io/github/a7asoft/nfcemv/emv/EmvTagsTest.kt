package io.github.a7asoft.nfcemv.emv

import kotlin.test.Test
import kotlin.test.assertEquals

class EmvTagsTest {

    @Test
    fun `EmvTagFormat enumerates the four EMV format codes`() {
        assertEquals(
            setOf(EmvTagFormat.N, EmvTagFormat.AN, EmvTagFormat.B, EmvTagFormat.CN),
            EmvTagFormat.entries.toSet(),
        )
    }

    @Test
    fun `EmvTagLength Fixed exposes the byte count`() {
        val length = EmvTagLength.Fixed(8)
        assertEquals(8, length.bytes)
    }

    @Test
    fun `EmvTagLength Variable exposes the maximum byte count`() {
        val length = EmvTagLength.Variable(252)
        assertEquals(252, length.maxBytes)
    }

    @Test
    fun `EmvTagLength Fixed rejects non-positive byte count`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { EmvTagLength.Fixed(0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { EmvTagLength.Fixed(-1) }
    }

    @Test
    fun `EmvTagLength Variable rejects non-positive maximum`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { EmvTagLength.Variable(0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { EmvTagLength.Variable(-5) }
    }

    @Test
    fun `TagSensitivity has exactly two values`() {
        assertEquals(
            setOf(TagSensitivity.PCI, TagSensitivity.PUBLIC),
            TagSensitivity.entries.toSet(),
        )
    }

    @Test
    fun `EmvTagInfo carries every field exposed by the spec`() {
        val info = EmvTagInfo(
            tag = io.github.a7asoft.nfcemv.tlv.Tag.fromHex("9F26"),
            name = "Application Cryptogram",
            format = EmvTagFormat.B,
            length = EmvTagLength.Fixed(8),
            sensitivity = TagSensitivity.PCI,
        )
        assertEquals("9F26", info.tag.toString())
        assertEquals("Application Cryptogram", info.name)
        assertEquals(EmvTagFormat.B, info.format)
        assertEquals(EmvTagLength.Fixed(8), info.length)
        assertEquals(TagSensitivity.PCI, info.sensitivity)
    }

    @Test
    fun `lookup returns null for an unknown tag`() {
        kotlin.test.assertNull(EmvTags.lookup(io.github.a7asoft.nfcemv.tlv.Tag.fromHex("99")))
    }

    @Test
    fun `lookup returns the entry for tag 9F26 ARQC`() {
        val info = EmvTags.lookup(io.github.a7asoft.nfcemv.tlv.Tag.fromHex("9F26"))
        kotlin.test.assertNotNull(info)
        assertEquals("Application Cryptogram", info!!.name)
        assertEquals(EmvTagFormat.B, info.format)
        assertEquals(EmvTagLength.Fixed(8), info.length)
        assertEquals(TagSensitivity.PCI, info.sensitivity)
    }

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
    fun `every entry's name is non-blank`() {
        EmvTags.all.forEach { entry ->
            kotlin.test.assertTrue(entry.name.isNotBlank(), "blank name for tag ${entry.tag}")
        }
    }

    @Test
    fun `lookup resolves every registered entry by its tag`() {
        EmvTags.all.forEach { entry ->
            assertEquals(entry, EmvTags.lookup(entry.tag), "lookup mismatch for tag ${entry.tag}")
        }
    }

    @Test
    fun `lookup returns null for several unregistered tags`() {
        listOf("99", "FF", "9F99", "BFAA").forEach { hex ->
            kotlin.test.assertNull(
                EmvTags.lookup(io.github.a7asoft.nfcemv.tlv.Tag.fromHex(hex)),
                "expected null for unregistered tag $hex",
            )
        }
    }

    @Test
    fun `tag 5A is the PAN with CN format and PCI sensitivity`() {
        val pan = EmvTags.lookup(io.github.a7asoft.nfcemv.tlv.Tag.fromHex("5A"))!!
        assertEquals(EmvTagFormat.CN, pan.format)
        assertEquals(EmvTagLength.Variable(10), pan.length)
        assertEquals(TagSensitivity.PCI, pan.sensitivity)
    }

    @Test
    fun `tag 4F is the AID with B format and PUBLIC sensitivity`() {
        val aid = EmvTags.lookup(io.github.a7asoft.nfcemv.tlv.Tag.fromHex("4F"))!!
        assertEquals(EmvTagFormat.B, aid.format)
        assertEquals(EmvTagLength.Variable(16), aid.length)
        assertEquals(TagSensitivity.PUBLIC, aid.sensitivity)
    }

    @Test
    fun `tag 9F02 amount has N format and 6-byte fixed length`() {
        val amount = EmvTags.lookup(io.github.a7asoft.nfcemv.tlv.Tag.fromHex("9F02"))!!
        assertEquals(EmvTagFormat.N, amount.format)
        assertEquals(EmvTagLength.Fixed(6), amount.length)
        assertEquals(TagSensitivity.PUBLIC, amount.sensitivity)
    }

    @Test
    fun `tag 50 application label has AN format`() {
        val label = EmvTags.lookup(io.github.a7asoft.nfcemv.tlv.Tag.fromHex("50"))!!
        assertEquals(EmvTagFormat.AN, label.format)
    }

    @Test
    fun `every EmvTagFormat value has at least one entry`() {
        EmvTagFormat.entries.forEach { format ->
            kotlin.test.assertTrue(
                EmvTags.all.any { it.format == format },
                "no entry uses format $format",
            )
        }
    }

    @Test
    fun `every TagSensitivity value has at least one entry`() {
        TagSensitivity.entries.forEach { sensitivity ->
            kotlin.test.assertTrue(
                EmvTags.all.any { it.sensitivity == sensitivity },
                "no entry uses sensitivity $sensitivity",
            )
        }
    }

    @Test
    fun `dictionary uses both Fixed and Variable length variants`() {
        kotlin.test.assertTrue(EmvTags.all.any { it.length is EmvTagLength.Fixed }, "no Fixed entry")
        kotlin.test.assertTrue(EmvTags.all.any { it.length is EmvTagLength.Variable }, "no Variable entry")
    }

    @Test
    fun `the PCI bucket covers PAN and Track 2 and ARQC and signed dynamic data`() {
        val pciTags = EmvTags.all.filter { it.sensitivity == TagSensitivity.PCI }.map { it.tag.toString() }
        listOf("5A", "57", "9F26", "9F4B", "9F6B").forEach { expected ->
            kotlin.test.assertTrue(expected in pciTags, "expected $expected in PCI bucket")
        }
    }

    @Test
    fun `the PUBLIC bucket covers AID and AIP and AFL`() {
        val publicTags = EmvTags.all.filter { it.sensitivity == TagSensitivity.PUBLIC }.map { it.tag.toString() }
        listOf("4F", "82", "94").forEach { expected ->
            kotlin.test.assertTrue(expected in publicTags, "expected $expected in PUBLIC bucket")
        }
    }
}
