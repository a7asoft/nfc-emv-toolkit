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
}
