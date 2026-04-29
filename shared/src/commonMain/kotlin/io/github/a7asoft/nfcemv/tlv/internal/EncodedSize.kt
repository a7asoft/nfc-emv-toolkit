package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tlv

/**
 * Compute the total number of bytes [node] will occupy when serialized as a
 * BER-TLV stream in DER-canonical (minimal length) form. Pure recursive
 * function; first pass of the encoder's two-pass strategy.
 */
internal fun encodedSize(node: Tlv): Int = when (node) {
    is Tlv.Primitive -> sizePrimitive(node)
    is Tlv.Constructed -> sizeConstructed(node)
}

private fun sizePrimitive(node: Tlv.Primitive): Int =
    node.tag.byteCount + lengthOctets(node.length) + node.length

private fun sizeConstructed(node: Tlv.Constructed): Int {
    val childrenSize = node.children.sumOf { encodedSize(it) }
    return node.tag.byteCount + lengthOctets(childrenSize) + childrenSize
}
