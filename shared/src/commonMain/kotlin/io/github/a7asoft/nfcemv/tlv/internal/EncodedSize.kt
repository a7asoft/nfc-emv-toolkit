package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tlv

/**
 * Compute the total number of bytes [node] will occupy when serialized as a
 * BER-TLV stream in DER-canonical (minimal length) form.
 *
 * First pass of the encoder's two-pass strategy. Recursion is bounded by
 * [MAX_DEPTH]; trees deeper than that surface as [IllegalStateException]
 * mirroring the second-pass guard in [writeNode].
 */
internal fun encodedSize(node: Tlv): Int = encodedSizeAt(node, depth = 0)

private fun encodedSizeAt(node: Tlv, depth: Int): Int {
    check(depth <= MAX_DEPTH) { "TLV encode depth exceeds $MAX_DEPTH" }
    return when (node) {
        is Tlv.Primitive -> sizePrimitive(node)
        is Tlv.Constructed -> sizeConstructed(node, depth)
    }
}

private fun sizePrimitive(node: Tlv.Primitive): Int =
    node.tag.byteCount + lengthOctets(node.length) + node.length

private fun sizeConstructed(node: Tlv.Constructed, depth: Int): Int {
    val childrenSize = node.children.sumOf { encodedSizeAt(it, depth + 1) }
    require(childrenSize >= 0) { "Constructed children size overflows Int" }
    return node.tag.byteCount + lengthOctets(childrenSize) + childrenSize
}
