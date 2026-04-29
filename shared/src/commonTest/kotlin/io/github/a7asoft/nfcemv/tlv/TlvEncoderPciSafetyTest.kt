package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * The encoder MUST emit exactly the bytes the spec requires (otherwise it
 * is broken), but it must NOT introduce any new surface that turns a
 * sensitive `Tlv.Primitive` into a stringified value byte. The Primitive's
 * `toString()` already masks (PR #13); this suite pins that the encoder
 * does not regress that contract through any side channel.
 *
 * If any of these tests start to fail, do NOT relax them. Find the leak
 * (per CLAUDE.md §6.1).
 */
class TlvEncoderPciSafetyTest {

    @Test
    fun `encoding tag 5A produces correct bytes and the source Primitive still masks toString`() {
        // 5A 08 41 11 11 11 11 11 11 11 — fake test PAN
        val pan = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val node = Tlv.Primitive(Tag.fromHex("5A"), pan)
        val out = TlvEncoder.encode(node)
        assertContentEquals(byteArrayOf(0x5A, 0x08) + pan, out)
        // Source node toString remains masked even after encoding.
        assertEquals("Primitive(tag=5A, length=8)", node.toString())
    }

    @Test
    fun `encoder IllegalStateException message does not embed value bytes`() {
        // Build a depth-exhausted tree wrapping a fake PAN leaf.
        val pan = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        var node: Tlv = Tlv.Primitive(Tag.fromHex("5A"), pan)
        repeat(70) {
            node = Tlv.Constructed(Tag.fromHex("70"), listOf(node))
        }
        val ex = assertFailsWith<IllegalStateException> { TlvEncoder.encode(node) }
        val msg = ex.message ?: ""
        assertFalse("0x41" in msg, "byte leaked: $msg")
        assertFalse("0x11" in msg, "byte leaked: $msg")
        assertFalse("4111" in msg, "concatenated bytes leaked: $msg")
    }

    @Test
    fun `encoder does not retain a reference to the input value bytes`() {
        // Mutating the original array after construction must not affect
        // the encoded output (defensive copy contract on Tlv.Primitive).
        val source = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val node = Tlv.Primitive(Tag.fromHex("5A"), source)
        source[0] = 0x00
        val out = TlvEncoder.encode(node)
        assertEquals(0x41.toByte(), out[2])
    }
}
