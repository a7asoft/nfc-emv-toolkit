package io.github.a7asoft.nfcemv.extract.internal

import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TlvSearchTest {

    @Test
    fun `findFirst returns a top-level primitive matching the tag`() {
        val target = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x41, 0x11))
        val tlvs = listOf(
            Tlv.Primitive(Tag.fromHex("4F"), byteArrayOf(0x01)),
            target,
        )
        assertEquals(target, findFirst(tlvs, Tag.fromHex("5A")))
    }

    @Test
    fun `findFirst recurses into a Constructed node`() {
        val nested = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x41, 0x11))
        val tlvs = listOf(
            Tlv.Constructed(Tag.fromHex("70"), listOf(nested)),
        )
        assertEquals(nested, findFirst(tlvs, Tag.fromHex("5A")))
    }

    @Test
    fun `findFirst returns null when the tag is absent`() {
        val tlvs = listOf(
            Tlv.Primitive(Tag.fromHex("4F"), byteArrayOf(0x01)),
        )
        assertNull(findFirst(tlvs, Tag.fromHex("5A")))
    }

    @Test
    fun `findFirst returns the first match in DFS order across siblings`() {
        val first = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x41))
        val second = Tlv.Primitive(Tag.fromHex("5A"), byteArrayOf(0x42))
        val tlvs = listOf(
            Tlv.Constructed(Tag.fromHex("70"), listOf(first)),
            second,
        )
        assertEquals(first, findFirst(tlvs, Tag.fromHex("5A")))
    }

    @Test
    fun `findFirst returns null on an empty list`() {
        assertNull(findFirst(emptyList(), Tag.fromHex("5A")))
    }

    @Test
    fun `findFirst on a Constructed match returns the Constructed node itself`() {
        val target = Tlv.Constructed(Tag.fromHex("70"), emptyList())
        val tlvs = listOf<Tlv>(target)
        val result = assertIs<Tlv.Constructed>(findFirst(tlvs, Tag.fromHex("70")))
        assertEquals(target, result)
    }
}
