package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseException

private const val TAG_NUMBER_MASK = 0x1F
private const val MULTI_BYTE_ESCAPE = 0x1F
private const val CONTINUATION_BIT = 0x80
private const val NON_MINIMAL_FIRST_CONTINUATION = 0x80

/**
 * Read a BER-TLV tag per ISO/IEC 8825-1 §8.1.2.
 *
 * Single-byte tags are returned directly. Multi-byte tags use the
 * "bits 5-1 = 0x1F escape" form with continuation bytes; bit 8 of each
 * continuation indicates whether another byte follows.
 *
 * Strict mode rejects a first continuation byte of `0x80`, which would
 * encode a leading zero in the 7-bit tag-number representation.
 *
 * Note on EMV deviation: X.690 §8.1.2.4 specifies that tag numbers ≤ 30
 * MUST use the short form. EMV violates this by design (e.g. tag `9F02`
 * encodes tag number 2 in long form for namespace separation across card
 * schemes). This decoder follows EMV practice and does not enforce that
 * particular X.690 rule even in strict mode.
 */
internal fun readTag(reader: TlvReader, options: TlvOptions): Tag {
    val startOffset = reader.pos
    val first = reader.read().toInt() and 0xFF
    return if (isShortFormTag(first)) {
        Tag.ofBytes(byteArrayOf(first.toByte()))
    } else {
        readMultiByteTag(reader, first.toByte(), startOffset, options)
    }
}

private fun isShortFormTag(firstByte: Int): Boolean = (firstByte and TAG_NUMBER_MASK) != MULTI_BYTE_ESCAPE

private fun readMultiByteTag(
    reader: TlvReader,
    firstByte: Byte,
    startOffset: Int,
    options: TlvOptions,
): Tag {
    val bytes = mutableListOf(firstByte)
    do {
        ensureRoomForContinuation(bytes.size, options.maxTagBytes, startOffset)
        val next = readContinuation(reader, startOffset)
        validateLeadingZero(bytes.size, next, options.strict, startOffset)
        bytes.add(next)
    } while (hasMoreContinuations(next))
    return Tag.ofBytes(bytes.toByteArray())
}

private fun ensureRoomForContinuation(currentSize: Int, maxBytes: Int, startOffset: Int) {
    if (currentSize >= maxBytes) {
        throw TlvParseException(TlvError.TagTooLong(startOffset, maxBytes))
    }
}

private fun readContinuation(reader: TlvReader, startOffset: Int): Byte {
    if (reader.isEof) throw TlvParseException(TlvError.IncompleteTag(startOffset))
    return reader.read()
}

private fun validateLeadingZero(currentSize: Int, next: Byte, strict: Boolean, startOffset: Int) {
    if (!strict) return
    if (currentSize != 1) return
    if ((next.toInt() and 0xFF) == NON_MINIMAL_FIRST_CONTINUATION) {
        throw TlvParseException(TlvError.NonMinimalTagEncoding(startOffset))
    }
}

private fun hasMoreContinuations(next: Byte): Boolean = (next.toInt() and CONTINUATION_BIT) != 0
