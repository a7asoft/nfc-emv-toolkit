package io.github.a7asoft.nfcemv.tlv.internal

private const val PAD_BYTE: Byte = 0

/**
 * Advance the reader past consecutive `0x00` bytes, stopping at [endExclusive].
 *
 * Used inside a constructed value's body to honor EMV Specification Update
 * Bulletin 69, which permits cards to pad between children with `0x00` octets.
 */
internal fun skipZeroPaddingUpTo(reader: TlvReader, endExclusive: Int) {
    while (reader.pos < endExclusive && reader.peek() == PAD_BYTE) {
        reader.read()
    }
}

/**
 * Advance the reader past consecutive `0x00` bytes until end-of-input.
 *
 * Used at the top level to absorb trailing or interleaved padding when
 * [io.github.a7asoft.nfcemv.tlv.TlvOptions.paddingPolicy] is set to
 * [io.github.a7asoft.nfcemv.tlv.PaddingPolicy.Tolerated].
 */
internal fun skipZeroPaddingToEof(reader: TlvReader) {
    while (!reader.isEof && reader.peek() == PAD_BYTE) {
        reader.read()
    }
}
