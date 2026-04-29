package io.github.a7asoft.nfcemv.tlv

/**
 * A typed reason a BER-TLV decode failed, with the byte offset where the
 * problem was detected.
 *
 * Errors never embed value bytes; only structural information (offsets, byte
 * counts, and the offending length octet where applicable). This keeps PCI
 * scope out of error reporting.
 */
public sealed interface TlvError {
    /** Byte offset into the input where the error was detected. */
    public val offset: Int

    /** Input ended before the parser could finish reading a tag, length, or value. */
    public data class UnexpectedEof(override val offset: Int) : TlvError

    /**
     * The length byte was `0x80` (indefinite form). Forbidden by EMV
     * Book 3 Annex B-2; only definite-length encoding is allowed.
     */
    public data class IndefiniteLengthForbidden(override val offset: Int) : TlvError

    /**
     * The length byte was reserved or unsupported (`0x85`–`0xFF`). EMV bounds
     * length to four octets at most.
     */
    public data class InvalidLengthOctet(val byte: Byte, override val offset: Int) : TlvError

    /** A multi-byte tag was started but not terminated before EOF. */
    public data class IncompleteTag(override val offset: Int) : TlvError

    /** A multi-byte tag exceeded [TlvOptions.maxTagBytes]. */
    public data class TagTooLong(override val offset: Int, val maxBytes: Int) : TlvError

    /**
     * Strict mode: a multi-byte tag continuation began with `0x80` (a leading
     * zero in the 7-bit tag-number representation). Rejected per
     * ISO/IEC 8825-1 §8.1.2.4.2 minimal-encoding rule.
     */
    public data class NonMinimalTagEncoding(override val offset: Int) : TlvError

    /**
     * Strict mode: a long-form length used more octets than necessary
     * (e.g. `0x81 05` instead of `0x05`). Rejected per X.690 §8.1.3.5
     * minimal-encoding requirement for DER-aligned readers.
     */
    public data class NonMinimalLengthEncoding(override val offset: Int) : TlvError

    /**
     * The children of a constructed tag did not consume exactly the declared
     * length. Either short (children incomplete) or long (length lies).
     */
    public data class ChildrenLengthMismatch(
        val declared: Int,
        val consumed: Int,
        override val offset: Int,
    ) : TlvError

    /** Constructed nesting exceeded [TlvOptions.maxDepth]. */
    public data class MaxDepthExceeded(override val offset: Int, val limit: Int) : TlvError

    /**
     * The input contained bytes after the last complete TLV.
     *
     * Common cause: caller forgot to strip the SW1 SW2 status word from an
     * APDU response before passing the data to the decoder.
     */
    public data class TrailingBytes(override val offset: Int, val remaining: Int) : TlvError
}
