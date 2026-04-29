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
    fun `encoding tag 5A produces correct bytes`() {
        // 5A 08 41 11 11 11 11 11 11 11 — fake test PAN
        val pan = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val node = Tlv.Primitive(Tag.fromHex("5A"), pan)
        val out = TlvEncoder.encode(node)
        assertContentEquals(byteArrayOf(0x5A, 0x08) + pan, out)
    }

    @Test
    fun `Primitive toString for tag 5A stays masked after encode`() {
        val pan = byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11)
        val node = Tlv.Primitive(Tag.fromHex("5A"), pan)
        TlvEncoder.encode(node)
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

    @Test
    fun `encoding tag 57 produces correct bytes`() {
        // 57 0A 41 11 11 11 11 11 11 11 D2 80 — fake Track2
        val track2 = byteArrayOf(
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0xD2.toByte(), 0x80.toByte(),
        )
        val node = Tlv.Primitive(Tag.fromHex("57"), track2)
        val out = TlvEncoder.encode(node)
        assertContentEquals(byteArrayOf(0x57, 0x0A) + track2, out)
    }

    @Test
    fun `Primitive toString for tag 57 stays masked after encode`() {
        val node = Tlv.Primitive(
            Tag.fromHex("57"),
            byteArrayOf(0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0xD2.toByte(), 0x80.toByte()),
        )
        TlvEncoder.encode(node)
        assertEquals("Primitive(tag=57, length=10)", node.toString())
    }

    @Test
    fun `encoding tag 9F26 produces correct bytes`() {
        // 9F 26 08 DE AD BE EF CA FE BA BE — fake ARQC
        val arqc = byteArrayOf(
            0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(),
            0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
        )
        val node = Tlv.Primitive(Tag.fromHex("9F26"), arqc)
        val out = TlvEncoder.encode(node)
        assertContentEquals(byteArrayOf(0x9F.toByte(), 0x26, 0x08) + arqc, out)
    }

    @Test
    fun `Primitive toString for tag 9F26 stays masked after encode`() {
        val node = Tlv.Primitive(
            Tag.fromHex("9F26"),
            byteArrayOf(
                0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(),
                0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
            ),
        )
        TlvEncoder.encode(node)
        assertEquals("Primitive(tag=9F26, length=8)", node.toString())
    }
}
