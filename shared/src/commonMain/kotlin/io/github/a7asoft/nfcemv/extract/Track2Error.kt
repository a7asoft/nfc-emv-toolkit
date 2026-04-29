package io.github.a7asoft.nfcemv.extract

/**
 * A typed reason `Track2.parse` rejected an input byte buffer.
 *
 * Variants carry only structural metadata (offsets, counts, sub-error
 * references). They never embed raw nibble values. The `MalformedBcdNibble`
 * variant carries an offset rather than the offending value to avoid
 * leaking PAN / discretionary bytes through error reporting.
 */
public sealed interface Track2Error {

    /** Empty input. */
    public data object EmptyInput : Track2Error

    /** No `D` separator nibble was found inside the PAN search range. */
    public data object MissingSeparator : Track2Error

    /**
     * The PAN segment (everything before the `D` separator) failed
     * `Pan.parse`. The wrapped [PanError] carries the original reason.
     */
    public data class PanRejected(val cause: PanError) : Track2Error

    /** Fewer than 4 nibbles available after the separator for YYMM. */
    public data class ExpiryTooShort(val nibblesAvailable: Int) : Track2Error

    /** Expiry month parsed but is not in `1..12`. */
    public data class InvalidExpiryMonth(val month: Int) : Track2Error

    /** Fewer than 3 nibbles available after the expiry for the service code. */
    public data object ServiceCodeTooShort : Track2Error

    /**
     * A BCD nibble outside `0x0..0x9`, `0xD`, `0xF` was found at the given
     * zero-based offset. Offset is reported as nibble position, not byte.
     */
    public data class MalformedBcdNibble(val offset: Int) : Track2Error

    /**
     * An `F` (`0x0F`) padding nibble appeared somewhere other than as the
     * single trailing nibble of the input.
     *
     * Reserved for future stricter F-pad detection; the current parser
     * surfaces non-trailing F as [MalformedBcdNibble] because `readDigits`
     * treats any non-`0..9` nibble uniformly.
     */
    public data object MalformedFPadding : Track2Error
}
