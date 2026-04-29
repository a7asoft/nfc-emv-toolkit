package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseException

/**
 * Read a single BER-TLV node, dispatching to primitive or constructed
 * decoding based on the tag's P/C bit.
 *
 * [depth] is the current constructed-nesting depth (0 at top level). The
 * decoder rejects depth ≥ [TlvOptions.maxDepth] to prevent stack-overflow
 * via crafted nested input.
 */
internal fun readNode(reader: TlvReader, options: TlvOptions, depth: Int): Tlv {
    val nodeOffset = reader.pos
    enforceDepth(depth, options.maxDepth, nodeOffset)
    val tag = readTag(reader, options)
    val length = readLength(reader, options)
    return if (tag.isPrimitive) {
        readPrimitive(reader, tag, length)
    } else {
        readConstructed(reader, tag, length, options, depth)
    }
}

private fun enforceDepth(depth: Int, max: Int, offset: Int) {
    if (depth >= max) throw TlvParseException(TlvError.MaxDepthExceeded(offset, max))
}

private fun readPrimitive(reader: TlvReader, tag: Tag, length: Int): Tlv.Primitive {
    val value = reader.readBytes(length)
    return Tlv.Primitive(tag, value)
}

private fun readConstructed(
    reader: TlvReader,
    tag: Tag,
    length: Int,
    options: TlvOptions,
    depth: Int,
): Tlv.Constructed {
    val bodyStart = reader.pos
    val end = bodyStart + length
    val children = readChildren(reader, end, options, depth + 1)
    detectChildrenMismatch(reader.pos, bodyStart, length)
    return Tlv.Constructed(tag, children)
}

private fun readChildren(
    reader: TlvReader,
    end: Int,
    options: TlvOptions,
    depth: Int,
): List<Tlv> {
    val out = mutableListOf<Tlv>()
    while (reader.pos < end) {
        if (options.tolerateZeroPadding) skipZeroPaddingUpTo(reader, end)
        if (reader.pos >= end) break
        out.add(readNode(reader, options, depth))
    }
    return out
}

private fun detectChildrenMismatch(currentPos: Int, bodyStart: Int, declared: Int) {
    if (currentPos == bodyStart + declared) return
    val consumed = currentPos - bodyStart
    throw TlvParseException(TlvError.ChildrenLengthMismatch(declared, consumed, bodyStart))
}
