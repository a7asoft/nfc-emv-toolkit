package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.PaddingPolicy
import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvParseException

/**
 * Read a single BER-TLV node, dispatching to primitive or constructed
 * decoding based on the tag's P/C bit.
 *
 * Recursion depth is tracked via [ParseContext] and bounded by
 * [io.github.a7asoft.nfcemv.tlv.TlvOptions.maxDepth] to prevent
 * stack-overflow via crafted nested input.
 */
internal fun readNode(ctx: ParseContext): Tlv {
    val nodeOffset = ctx.reader.pos
    enforceDepth(ctx.depth, ctx.options.maxDepth, nodeOffset)
    val tag = readTag(ctx.reader, ctx.options)
    val length = readLength(ctx.reader, ctx.options)
    return if (tag.isPrimitive) {
        readPrimitive(ctx.reader, tag, length)
    } else {
        readConstructed(ctx, tag, length)
    }
}

private fun enforceDepth(depth: Int, max: Int, offset: Int) {
    if (depth >= max) throw TlvParseException(TlvError.MaxDepthExceeded(offset, max))
}

private fun readPrimitive(reader: TlvReader, tag: Tag, length: Int): Tlv.Primitive {
    val value = reader.readBytes(length)
    return Tlv.Primitive(tag, value)
}

private fun readConstructed(ctx: ParseContext, tag: Tag, length: Int): Tlv.Constructed {
    val bodyStart = ctx.reader.pos
    val end = bodyStart + length
    val children = readChildren(ctx.deeper(), end)
    detectChildrenMismatch(ctx.reader.pos, bodyStart, length)
    return Tlv.Constructed(tag, children)
}

private fun readChildren(ctx: ParseContext, end: Int): List<Tlv> {
    val out = mutableListOf<Tlv>()
    while (ctx.reader.pos < end) {
        if (ctx.options.paddingPolicy === PaddingPolicy.Tolerated) {
            skipZeroPaddingUpTo(ctx.reader, end)
        }
        if (ctx.reader.pos >= end) break
        out.add(readNode(ctx))
    }
    return out
}

private fun detectChildrenMismatch(currentPos: Int, bodyStart: Int, declared: Int) {
    if (currentPos == bodyStart + declared) return
    val consumed = currentPos - bodyStart
    throw TlvParseException(TlvError.ChildrenLengthMismatch(declared, consumed, currentPos))
}
