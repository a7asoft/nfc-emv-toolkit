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
}
