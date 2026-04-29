package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseException

private const val SHORT_FORM_MAX = 0x7F
private const val INDEFINITE_FORM = 0x80
private const val LONG_FORM_BASE = 0x80
private const val LONG_FORM_MIN = 0x81
private const val LONG_FORM_MAX = 0x84

/**
 * Read a BER-TLV length field per ISO/IEC 8825-1 §8.1.3.
 *
 * Accepts short form (≤0x7F) and long form (0x81–0x84). Rejects indefinite
 * form (0x80) per EMV Book 3 Annex B-2 and reserved/oversized lengths
 * (≥0x85). In strict mode, also rejects non-minimal long-form encodings.
 */
internal fun readLength(reader: TlvReader, options: TlvOptions): Int {
    val startOffset = reader.pos
    val first = reader.read().toInt() and 0xFF
    return when {
        first <= SHORT_FORM_MAX -> first
        first == INDEFINITE_FORM -> throw indefinite(startOffset)
        first in LONG_FORM_MIN..LONG_FORM_MAX -> readLongForm(reader, first - LONG_FORM_BASE, startOffset, options)
        else -> throw invalidOctet(first.toByte(), startOffset)
    }
}

private fun readLongForm(reader: TlvReader, octets: Int, startOffset: Int, options: TlvOptions): Int {
    val value = readBigEndian(reader, octets)
    if (options.strict && minimalOctetsFor(value) < octets) {
        throw TlvParseException(TlvError.NonMinimalLengthEncoding(startOffset))
    }
    if (value > Int.MAX_VALUE) {
        throw invalidOctet((LONG_FORM_BASE or octets).toByte(), startOffset)
    }
    return value.toInt()
}

private fun readBigEndian(reader: TlvReader, octets: Int): Long {
    var value = 0L
    repeat(octets) {
        value = (value shl 8) or (reader.read().toLong() and 0xFFL)
    }
    return value
}

private fun minimalOctetsFor(value: Long): Int = when {
    value <= 0x7F -> 0
    value <= 0xFF -> 1
    value <= 0xFFFF -> 2
    value <= 0xFFFFFF -> 3
    else -> 4
}

private fun indefinite(offset: Int) = TlvParseException(TlvError.IndefiniteLengthForbidden(offset))

private fun invalidOctet(byte: Byte, offset: Int) = TlvParseException(TlvError.InvalidLengthOctet(byte, offset))
