package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.tlv.TlvError

/**
 * A typed reason `EmvParser.parse` rejected an APDU response stream.
 *
 * Variants carry only structural metadata (offsets, counts, sub-error
 * references). They never embed raw nibbles, PAN digits, cardholder
 * name, or discretionary bytes — `MissingRequiredTag.tagHex` is the
 * static tag identifier (e.g. `"5A"`), not card-derived data.
 */
public sealed interface EmvCardError {

    /** Caller passed an empty `List<ByteArray>` to [EmvParser.parse]. */
    public data object EmptyInput : EmvCardError

    /**
     * One of the input byte arrays failed BER-TLV decoding. The wrapped
     * [TlvError] carries the structural offset; no value bytes are
     * embedded.
     */
    public data class TlvDecodeFailed(val cause: TlvError) : EmvCardError

    /**
     * A tag the EmvCard contract requires (`4F` AID, `5A` PAN, or
     * `5F24` expiry) was not present in any of the decoded TLV trees.
     * [tagHex] is the static tag identifier as uppercase hex.
     */
    public data class MissingRequiredTag(val tagHex: String) : EmvCardError

    /** Tag `5A` PAN failed `Pan.parse`. The wrapped [PanError] explains why. */
    public data class PanRejected(val cause: PanError) : EmvCardError

    /**
     * Tag `57` Track 2 was present but failed `Track2.parse`. The
     * wrapped [Track2Error] explains why.
     */
    public data class Track2Rejected(val cause: Track2Error) : EmvCardError

    /**
     * Tag `5F24` expiry value did not have the expected 6-nibble
     * `YYMMDD` shape.
     */
    public data class InvalidExpiryFormat(val nibbleCount: Int) : EmvCardError

    /**
     * Tag `5F24` expiry parsed to an out-of-range month (must be 1..12).
     */
    public data class InvalidExpiryMonth(val month: Int) : EmvCardError

    /** Tag `4F` AID had a length outside the valid `5..16` byte range. */
    public data class InvalidAid(val byteCount: Int) : EmvCardError

    /**
     * Tag `5A` PAN BCD encoding contained a malformed nibble at the
     * given offset (any of `0xA..0xE` in any position, or `0xF` in a
     * non-trailing position). EMV Book 3 §A.1 mandates BCD digits
     * `0..9` with at most one trailing `F` pad nibble.
     *
     * Distinct from [PanRejected] (`NonDigitCharacters`): this variant
     * fires BEFORE the unpacked digit string reaches `Pan.parse`, so
     * the offset reported is the actual offending nibble position
     * rather than a derived character index.
     */
    public data class MalformedPanNibble(val offset: Int) : EmvCardError
}
