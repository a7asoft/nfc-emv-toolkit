package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import kotlin.test.Test
import kotlin.test.assertEquals

class EncodedSizeTest {

    @Test
    fun `primitive size is tag bytes plus length octets plus value bytes`() {
        // tag 5A (1 byte) + length 0x08 (1 byte) + 8 value bytes = 10
        val node = Tlv.Primitive(Tag.fromHex("5A"), ByteArray(8))
        assertEquals(10, encodedSize(node))
    }

    @Test
    fun `primitive size accounts for multi-byte tag`() {
        // tag 9F02 (2 bytes) + length 0x06 (1 byte) + 6 value bytes = 9
        val node = Tlv.Primitive(Tag.fromHex("9F02"), ByteArray(6))
        assertEquals(9, encodedSize(node))
    }

    @Test
    fun `primitive size accounts for long-form length`() {
        // tag 5A (1) + length 0x81 0x80 (2) + 128 value bytes = 131
        val node = Tlv.Primitive(Tag.fromHex("5A"), ByteArray(0x80))
        assertEquals(131, encodedSize(node))
    }

    @Test
    fun `empty constructed has size tag plus length 0`() {
        // tag 70 (1) + length 0x00 (1) + no children = 2
        val node = Tlv.Constructed(Tag.fromHex("70"), emptyList())
        assertEquals(2, encodedSize(node))
    }

    @Test
    fun `constructed with one primitive child sums correctly`() {
        // outer 70 (1) + length 0x04 (1) + (inner 57 (1) + length 0x02 (1) + 2 value bytes) = 6
        val inner = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        val outer = Tlv.Constructed(Tag.fromHex("70"), listOf(inner))
        assertEquals(6, encodedSize(outer))
    }

    @Test
    fun `nested constructed depth 3 is recursively summed`() {
        // L3 leaf: 57 (1) + len 0x02 (1) + 2 = 4
        // L2 cons: A6 (1) + len 0x04 (1) + 4 = 6
        // L1 cons: A5 (1) + len 0x06 (1) + 6 = 8
        // L0 cons: 70 (1) + len 0x08 (1) + 8 = 10
        val leaf = Tlv.Primitive(Tag.fromHex("57"), byteArrayOf(0x10, 0x20))
        val l2 = Tlv.Constructed(Tag.fromHex("A6"), listOf(leaf))
        val l1 = Tlv.Constructed(Tag.fromHex("A5"), listOf(l2))
        val l0 = Tlv.Constructed(Tag.fromHex("70"), listOf(l1))
        assertEquals(10, encodedSize(l0))
    }

    @Test
    fun `constructed body crossing 0x7F triggers long-form length`() {
        // 200 children of size 1 each (tag 5A + len 0 = 2 bytes) = 400 body bytes
        val children = List(200) { Tlv.Primitive(Tag.fromHex("5A"), ByteArray(0)) }
        // outer tag 70 (1) + length 0x82 0x01 0x90 (3) + 400 = 404
        val outer = Tlv.Constructed(Tag.fromHex("70"), children)
        assertEquals(404, encodedSize(outer))
    }
}
