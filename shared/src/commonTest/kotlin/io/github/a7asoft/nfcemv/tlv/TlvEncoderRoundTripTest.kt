package io.github.a7asoft.nfcemv.tlv

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Round-trip invariant per issue #2: for every input the decoder accepts
 * (and produces a `Tlv` tree from), encoding that tree and decoding the
 * result MUST yield an equal tree. Byte-equality of the encoded form vs the
 * original input is NOT guaranteed (the decoder accepts non-minimal length
 * in lenient mode; the encoder always emits minimal length), but semantic
 * equality of the `Tlv` tree is.
 */
class TlvEncoderRoundTripTest {

    @Test
    fun `FCI Visa response round-trips through encode and decode`() {
        // 6F 16
        //   84 07 A0 00 00 00 03 10 10
        //   A5 0B
        //     50 06 56 49 53 41 30 31
        //     87 01 01
        val input = byteArrayOf(
            0x6F, 0x16,
            0x84.toByte(), 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0xA5.toByte(), 0x0B,
            0x50, 0x06, 0x56, 0x49, 0x53, 0x41, 0x30, 0x31,
            0x87.toByte(), 0x01, 0x01,
        )
        roundTrip(input)
    }

    @Test
    fun `Track2 response round-trips`() {
        val input = byteArrayOf(
            0x57, 0x0A,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0xD2.toByte(), 0x80.toByte(),
        )
        roundTrip(input)
    }

    @Test
    fun `ARQC response round-trips`() {
        val input = byteArrayOf(
            0x9F.toByte(), 0x26, 0x08,
            0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(),
            0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
        )
        roundTrip(input)
    }

    @Test
    fun `nested constructed depth 3 round-trips`() {
        val input = byteArrayOf(
            0x70, 0x08,
            0xA5.toByte(), 0x06,
            0xA6.toByte(), 0x04,
            0x57, 0x02, 0x10, 0x20,
        )
        roundTrip(input)
    }

    @Test
    fun `multiple top-level primitives round-trip as a list`() {
        val input = byteArrayOf(0x57, 0x01, 0x10, 0x5A, 0x01, 0x20)
        roundTrip(input)
    }

    @Test
    fun `encoded output of a minimal-length input is byte-equal to the input`() {
        // When the input is already DER-canonical (which all the fixtures
        // above are), encode(decode(x)) MUST equal x byte-for-byte.
        val input = byteArrayOf(
            0x6F, 0x16,
            0x84.toByte(), 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0xA5.toByte(), 0x0B,
            0x50, 0x06, 0x56, 0x49, 0x53, 0x41, 0x30, 0x31,
            0x87.toByte(), 0x01, 0x01,
        )
        val tree = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(input)).tlvs.single()
        val encoded = TlvEncoder.encode(tree)
        assertContentEquals(input, encoded)
    }

    private fun roundTrip(input: ByteArray) {
        val firstParse = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(input))
        val encoded = TlvEncoder.encode(firstParse.tlvs)
        val secondParse = assertIs<TlvParseResult.Ok>(TlvDecoder.parse(encoded))
        assertEquals(firstParse.tlvs, secondParse.tlvs)
    }
}
