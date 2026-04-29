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
}
