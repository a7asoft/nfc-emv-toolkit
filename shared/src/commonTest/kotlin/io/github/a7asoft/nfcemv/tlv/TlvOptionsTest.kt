package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TlvOptionsTest {

    @Test
    fun `defaults are strict and tolerant of zero padding`() {
        val opts = TlvOptions()
        assertTrue(opts.strict)
        assertTrue(opts.tolerateZeroPadding)
        assertTrue(opts.rejectTrailingBytes)
        assertEquals(4, opts.maxTagBytes)
        assertEquals(16, opts.maxDepth)
    }

    @Test
    fun `rejects maxTagBytes below 1`() {
        assertFailsWith<IllegalArgumentException> { TlvOptions(maxTagBytes = 0) }
    }

    @Test
    fun `rejects maxTagBytes above 4`() {
        assertFailsWith<IllegalArgumentException> { TlvOptions(maxTagBytes = 5) }
    }

    @Test
    fun `rejects maxDepth below 1`() {
        assertFailsWith<IllegalArgumentException> { TlvOptions(maxDepth = 0) }
    }

    @Test
    fun `rejects maxDepth above 64`() {
        assertFailsWith<IllegalArgumentException> { TlvOptions(maxDepth = 65) }
    }
}
