package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TlvOptionsTest {

    @Test
    fun `defaults are strict and tolerate zero padding`() {
        val opts = TlvOptions()
        assertEquals(Strictness.Strict, opts.strictness)
        assertEquals(PaddingPolicy.Tolerated, opts.paddingPolicy)
        assertEquals(4, opts.maxTagBytes)
        assertEquals(16, opts.maxDepth)
    }

    @Test
    fun `accepts maxTagBytes at lower boundary 1`() {
        val opts = TlvOptions(maxTagBytes = 1)
        assertEquals(1, opts.maxTagBytes)
    }

    @Test
    fun `accepts maxTagBytes at upper boundary 4`() {
        val opts = TlvOptions(maxTagBytes = 4)
        assertEquals(4, opts.maxTagBytes)
    }

    @Test
    fun `accepts maxDepth at lower boundary 1`() {
        val opts = TlvOptions(maxDepth = 1)
        assertEquals(1, opts.maxDepth)
    }

    @Test
    fun `accepts maxDepth at upper boundary 64`() {
        val opts = TlvOptions(maxDepth = 64)
        assertEquals(64, opts.maxDepth)
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
