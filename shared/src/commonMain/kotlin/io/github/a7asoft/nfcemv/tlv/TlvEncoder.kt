package io.github.a7asoft.nfcemv.tlv

import io.github.a7asoft.nfcemv.tlv.internal.encodedSize
import io.github.a7asoft.nfcemv.tlv.internal.writeNode

/**
 * Public entry point for BER-TLV encoding.
 *
 * Output is always X.690 DER-canonical: definite length, minimal length
 * octets. The encoder takes no options — there is no legitimate reason to
 * emit non-minimal long-form length on a fresh stream. Tag bytes are
 * preserved verbatim from the [Tlv] tree, so EMV deviations like `9F02`
 * and `BF0C` round-trip byte-for-byte at the tag level.
 *
 * The encoder uses two passes over a pre-allocated [ByteArray]: first to
 * compute the total size, second to fill the buffer. No intermediate
 * growth, no temporary copies of value bytes beyond the one defensive copy
 * the [Tlv.Primitive] contract already requires.
 *
 * Defense in depth: a hardcoded `MAX_DEPTH = 64` guard mirrors the upper
 * bound of `TlvOptions.maxDepth`, protecting against caller-constructed
 * trees that bypassed the decoder.
 */
public object TlvEncoder {

    /** Encode a single [Tlv] tree. */
    public fun encode(node: Tlv): ByteArray {
        val size = encodedSize(node)
        val dst = ByteArray(size)
        val end = writeNode(node, dst, offset = 0, depth = 0)
        check(end == size) { "Encoder size mismatch: predicted=$size actual=$end" }
        return dst
    }

    /** Encode a list of top-level [Tlv] nodes as a concatenated stream. */
    public fun encode(nodes: List<Tlv>): ByteArray {
        val total = nodes.sumOf { encodedSize(it) }
        require(total >= 0) { "Total encoded size overflows Int" }
        val dst = ByteArray(total)
        val end = nodes.fold(0) { pos, node -> writeNode(node, dst, pos, depth = 0) }
        check(end == total) { "Encoder size mismatch: predicted=$total actual=$end" }
        return dst
    }
}
