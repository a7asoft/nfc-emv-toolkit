package io.github.a7asoft.nfcemv.tlv

/**
 * Configures decoder strictness and bounds.
 *
 * Defaults are chosen to surface caller bugs early while accepting the leniency
 * EMV cards exhibit in practice.
 *
 * @property strict When true, reject non-minimal multi-byte tag and long-form
 *   length encodings (e.g. a leading-zero continuation byte in a tag, or a
 *   `0x81 05` length where `0x05` would suffice).
 * @property tolerateZeroPadding When true, skip `0x00` bytes that appear between
 *   top-level or constructed children. Per EMV Specification Update Bulletin 69,
 *   such padding is canonical EMV; left enabled by default.
 * @property maxTagBytes Hard cap on multi-byte tag length. ISO/IEC 8825-1 admits
 *   arbitrary length; EMV uses at most 2. Bounded to prevent decoder DoS.
 * @property maxDepth Hard cap on constructed-tag nesting. Bounded to prevent
 *   stack-overflow attacks via crafted input.
 * @property rejectTrailingBytes When true, fail if input contains bytes after
 *   the last decoded TLV. The most common cause is a caller forgetting to strip
 *   the SW1 SW2 status word from an APDU response — strict default surfaces this.
 */
public data class TlvOptions(
    val strict: Boolean = true,
    val tolerateZeroPadding: Boolean = true,
    val maxTagBytes: Int = 4,
    val maxDepth: Int = 16,
    val rejectTrailingBytes: Boolean = true,
) {
    init {
        require(maxTagBytes in 1..4) { "maxTagBytes out of range: $maxTagBytes" }
        require(maxDepth in 1..64) { "maxDepth out of range: $maxDepth" }
    }
}
