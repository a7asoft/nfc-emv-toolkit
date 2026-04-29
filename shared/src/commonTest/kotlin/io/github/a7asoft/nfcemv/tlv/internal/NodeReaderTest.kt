package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.PaddingPolicy
import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NodeReaderTest {

    private val opts = TlvOptions()
    private val noPadding = opts.copy(paddingPolicy = PaddingPolicy.Rejected)

    @Test
    fun `single primitive with short length`() {
        // 57 02 10 20
        val r = TlvReader(byteArrayOf(0x57, 0x02, 0x10, 0x20))
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val prim = assertIs<Tlv.Primitive>(tlv)
        assertEquals(Tag.fromHex("57"), prim.tag)
        assertContentEquals(byteArrayOf(0x10, 0x20), prim.value)
        assertTrue(r.isEof)
    }

    @Test
    fun `single primitive with empty value`() {
        // 57 00
        val r = TlvReader(byteArrayOf(0x57, 0x00))
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val prim = assertIs<Tlv.Primitive>(tlv)
        assertEquals(0, prim.value.size)
    }

    @Test
    fun `primitive with long form length`() {
        // 5A 81 80  (length 128)
        val data = byteArrayOf(0x5A, 0x81.toByte(), 0x80.toByte()) + ByteArray(128) { 0x42 }
        val r = TlvReader(data)
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val prim = assertIs<Tlv.Primitive>(tlv)
        assertEquals(128, prim.value.size)
        assertEquals(0x42.toByte(), prim.value[0])
    }

    @Test
    fun `constructed with one primitive child`() {
        // 70 04 57 02 10 20
        val r = TlvReader(byteArrayOf(0x70, 0x04, 0x57, 0x02, 0x10, 0x20))
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val cons = assertIs<Tlv.Constructed>(tlv)
        assertEquals(Tag.fromHex("70"), cons.tag)
        assertEquals(1, cons.children.size)
        val child = assertIs<Tlv.Primitive>(cons.children[0])
        assertEquals(Tag.fromHex("57"), child.tag)
    }

    @Test
    fun `constructed with multiple children`() {
        // 70 06 | 57 01 10 | 5A 01 20
        val r = TlvReader(byteArrayOf(0x70, 0x06, 0x57, 0x01, 0x10, 0x5A, 0x01, 0x20))
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val cons = assertIs<Tlv.Constructed>(tlv)
        assertEquals(2, cons.children.size)
    }

    @Test
    fun `nested constructed depth 2`() {
        // 70 06 | A5 04 | 57 02 10 20
        val r = TlvReader(byteArrayOf(0x70, 0x06, 0xA5.toByte(), 0x04, 0x57, 0x02, 0x10, 0x20))
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val outer = assertIs<Tlv.Constructed>(tlv)
        val inner = assertIs<Tlv.Constructed>(outer.children.single())
        val leaf = assertIs<Tlv.Primitive>(inner.children.single())
        assertEquals(Tag.fromHex("57"), leaf.tag)
    }

    @Test
    fun `nested constructed depth 3`() {
        // 70 08 | A5 06 | A6 04 | 57 02 10 20
        val r = TlvReader(byteArrayOf(
            0x70, 0x08,
            0xA5.toByte(), 0x06,
            0xA6.toByte(), 0x04,
            0x57, 0x02, 0x10, 0x20,
        ))
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val l1 = assertIs<Tlv.Constructed>(tlv)
        val l2 = assertIs<Tlv.Constructed>(l1.children.single())
        val l3 = assertIs<Tlv.Constructed>(l2.children.single())
        assertIs<Tlv.Primitive>(l3.children.single())
    }

    @Test
    fun `max depth exceeded throws MaxDepthExceeded`() {
        // depth limit = 2: a top-level constructed (depth 0 → child depth 1) holding
        // a constructed (depth 1 → child depth 2 — at the limit, rejected).
        val deep = byteArrayOf(
            0x70, 0x06,
            0xA5.toByte(), 0x04,
            0xA6.toByte(), 0x02,
            0x57, 0x00,
        )
        val r = TlvReader(deep)
        val ex = assertFailsWith<TlvParseException> { readNode(ParseContext(r, opts.copy(maxDepth = 2), depth = 0)) }
        assertIs<TlvError.MaxDepthExceeded>(ex.error)
    }

    @Test
    fun `constructed with zero padding between children when tolerated`() {
        // 70 08 | 57 01 10 | 00 00 | 5A 01 20
        val r = TlvReader(byteArrayOf(
            0x70, 0x08,
            0x57, 0x01, 0x10,
            0x00, 0x00,
            0x5A, 0x01, 0x20,
        ))
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val cons = assertIs<Tlv.Constructed>(tlv)
        assertEquals(2, cons.children.size)
    }

    @Test
    fun `constructed with zero padding parsed literally when not tolerated`() {
        // 70 04 | 57 00 | 00 00
        // With tolerateZeroPadding=false, the trailing 00 00 is parsed as
        // tag=0x00, length=0 -> a valid empty primitive.
        val r = TlvReader(byteArrayOf(0x70, 0x04, 0x57, 0x00, 0x00, 0x00))
        val tlv = readNode(ParseContext(r, noPadding, depth = 0))
        val cons = assertIs<Tlv.Constructed>(tlv)
        assertEquals(2, cons.children.size)
        assertEquals(Tag.fromHex("00"), cons.children[1].tag)
    }

    @Test
    fun `children sum exceeding declared length throws mismatch`() {
        // 70 04 | 57 01 10 | 57 03 11 12 13
        // Declared body length = 4 but children together consume 8.
        val r = TlvReader(byteArrayOf(0x70, 0x04, 0x57, 0x01, 0x10, 0x57, 0x03, 0x11, 0x12, 0x13))
        val ex = assertFailsWith<TlvParseException> { readNode(ParseContext(r, opts, depth = 0)) }
        val err = ex.error
        assertIs<TlvError.ChildrenLengthMismatch>(err)
        assertEquals(4, err.declared)
        assertTrue(err.consumed > 4)
    }

    @Test
    fun `constructed with declared length larger than buffer throws UnexpectedEof`() {
        // 70 10 | only 2 bytes follow. The inner read attempts to read the next
        // child header past EOF and surfaces UnexpectedEof — pin this exact
        // error per CLAUDE.md §6.1 (no disjunction asserts).
        val r = TlvReader(byteArrayOf(0x70, 0x10, 0x57, 0x00))
        val ex = assertFailsWith<TlvParseException> { readNode(ParseContext(r, opts, depth = 0)) }
        assertIs<TlvError.UnexpectedEof>(ex.error)
    }

    @Test
    fun `empty constructed value`() {
        // 70 00
        val r = TlvReader(byteArrayOf(0x70, 0x00))
        val tlv = readNode(ParseContext(r, opts, depth = 0))
        val cons = assertIs<Tlv.Constructed>(tlv)
        assertEquals(0, cons.children.size)
    }
}
