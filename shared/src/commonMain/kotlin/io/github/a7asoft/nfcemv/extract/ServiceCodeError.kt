package io.github.a7asoft.nfcemv.extract

/**
 * A typed reason `ServiceCode.parse` rejected an input string.
 *
 * Variants carry only structural metadata (length, offset). They never
 * embed the raw input — keeping any potentially sensitive byte stream
 * out of error reporting even when the failure is logged or stringified.
 */
public sealed interface ServiceCodeError {

    /** Input was empty. */
    public data object EmptyInput : ServiceCodeError

    /**
     * Input had a length other than the required 3 characters.
     * The actual length is reported (digit count is not sensitive).
     */
    public data class WrongLength(val length: Int) : ServiceCodeError

    /**
     * Input contained a non-`'0'..'9'` codepoint at [offset].
     * Position is reported for developer diagnostics; the offending
     * character itself is intentionally NOT embedded.
     */
    public data class NonDigitCharacter(val offset: Int) : ServiceCodeError
}
