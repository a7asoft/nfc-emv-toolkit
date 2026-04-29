package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tlv

/**
 * Write [node] into [dst] starting at [offset] in DER-canonical BER-TLV form.
 * Returns the offset just past the last byte written.
 *
 * Two preconditions on the input tree (defense in depth — the decoder
 * already enforces both on parsed inputs, but caller-built trees can bypass
 * the decoder entirely):
 *
 * - [depth] ≤ [MAX_DEPTH]: rejects pathologically nested trees as
 *   [IllegalStateException] before they can stack-overflow the recursion.
 * - The node's tag P/C bit must match the encoding form ([Tlv.Primitive] ⇒
 *   primitive bit; [Tlv.Constructed] ⇒ constructed bit) per ISO/IEC 8825-1
 *   §8.1.2.5; mismatches surface as [IllegalArgumentException].
 */
internal fun writeNode(node: Tlv, dst: ByteArray, offset: Int, depth: Int): Int {
    check(depth <= MAX_DEPTH) { "TLV encode depth exceeds $MAX_DEPTH" }
    require(node.matchesPCBit()) {
        "Tlv ${node::class.simpleName} tag P/C bit contradicts encoding form (ISO/IEC 8825-1 §8.1.2.5)"
    }
    val tagBytes = node.tag.bytes
    tagBytes.copyInto(dst, offset)
    val afterTag = offset + tagBytes.size
    return when (node) {
        is Tlv.Primitive -> writePrimitiveBody(node, dst, afterTag)
        is Tlv.Constructed -> writeConstructedBody(node, dst, afterTag, depth)
    }
}

private fun Tlv.matchesPCBit(): Boolean = when (this) {
    is Tlv.Primitive -> tag.isPrimitive
    is Tlv.Constructed -> tag.isConstructed
}

private fun writePrimitiveBody(node: Tlv.Primitive, dst: ByteArray, offset: Int): Int {
    val afterLength = writeLength(node.length, dst, offset)
    return node.writeValueInto(dst, afterLength)
}

private fun writeConstructedBody(node: Tlv.Constructed, dst: ByteArray, offset: Int, depth: Int): Int {
    val childrenSize = node.children.sumOf { encodedSize(it) }
    val afterLength = writeLength(childrenSize, dst, offset)
    return node.children.fold(afterLength) { pos, child ->
        writeNode(child, dst, pos, depth + 1)
    }
}
