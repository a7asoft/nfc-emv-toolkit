package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tlv

/**
 * Write [node] into [dst] starting at [offset] in DER-canonical BER-TLV form.
 * Returns the offset just past the last byte written.
 *
 * @throws IllegalStateException if recursion exceeds [MAX_DEPTH]. Defense in
 *   depth: the decoder's `TlvOptions.maxDepth` already bounds parsed inputs,
 *   but caller-built `Tlv` trees can bypass that path entirely.
 */
internal fun writeNode(node: Tlv, dst: ByteArray, offset: Int, depth: Int): Int {
    check(depth <= MAX_DEPTH) { "TLV encode depth exceeds $MAX_DEPTH" }
    val tagBytes = node.tag.bytes
    tagBytes.copyInto(dst, offset)
    val afterTag = offset + tagBytes.size
    return when (node) {
        is Tlv.Primitive -> writePrimitiveBody(node, dst, afterTag)
        is Tlv.Constructed -> writeConstructedBody(node, dst, afterTag, depth)
    }
}

private fun writePrimitiveBody(node: Tlv.Primitive, dst: ByteArray, offset: Int): Int {
    val afterLength = writeLength(node.length, dst, offset)
    val value = node.copyValue()
    value.copyInto(dst, afterLength)
    return afterLength + value.size
}

private fun writeConstructedBody(node: Tlv.Constructed, dst: ByteArray, offset: Int, depth: Int): Int {
    val childrenSize = node.children.sumOf { encodedSize(it) }
    val afterLength = writeLength(childrenSize, dst, offset)
    return node.children.fold(afterLength) { pos, child ->
        writeNode(child, dst, pos, depth + 1)
    }
}
