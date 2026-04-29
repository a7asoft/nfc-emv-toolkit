package io.github.a7asoft.nfcemv.tlv.internal

private const val SHORT_FORM_MAX = 0x7F
private const val LONG_FORM_BASE = 0x80

/**
 * Number of octets required to encode [value] as a BER-TLV length field per
 * ISO/IEC 8825-1 §8.1.3 in the minimal (DER-canonical) form.
 *
 * Short form (1 octet) covers `0..127`; long form covers `128..Int.MAX_VALUE`
 * with `1 + n` octets, where `n` is the minimum number of big-endian octets
 * needed to represent the value.
 */
internal fun lengthOctets(value: Int): Int {
    require(value >= 0) { "Length must be non-negative: $value" }
    if (value <= SHORT_FORM_MAX) return 1
    return 1 + significantOctets(value)
}

/**
 * Write the BER-TLV length field for [value] into [dst] starting at [offset]
 * in DER-canonical minimal form. Returns the offset just past the last byte
 * written.
 */
internal fun writeLength(value: Int, dst: ByteArray, offset: Int): Int {
    require(value >= 0) { "Length must be non-negative: $value" }
    if (value <= SHORT_FORM_MAX) {
        dst[offset] = value.toByte()
        return offset + 1
    }
    val octets = significantOctets(value)
    dst[offset] = (LONG_FORM_BASE or octets).toByte()
    var shift = (octets - 1) * 8
    var pos = offset + 1
    repeat(octets) {
        dst[pos] = ((value ushr shift) and 0xFF).toByte()
        shift -= 8
        pos++
    }
    return pos
}

private fun significantOctets(value: Int): Int =
    (Int.SIZE_BITS - value.countLeadingZeroBits() + 7) / 8
