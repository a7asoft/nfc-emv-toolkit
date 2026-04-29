package io.github.a7asoft.nfcemv.extract

/**
 * A typed reason `Pan.parse` rejected an input string.
 *
 * Variants carry only structural metadata (length, position counts).
 * They never embed raw digits — keeping PCI scope out of error reporting
 * even when the failure is logged or stringified.
 */
public sealed interface PanError {

    /** Input length fell outside the supported `12..19` digit range. */
    public data class LengthOutOfRange(val length: Int) : PanError

    /**
     * Input contained at least one non-`'0'..'9'` codepoint. The position
     * is intentionally NOT reported: knowing where the offending character
     * appeared would let an attacker probe the structure of a corrupted
     * PAN buffer one byte at a time.
     */
    public data object NonDigitCharacters : PanError

    /** Input is structurally well-formed but failed the Luhn / mod-10 check. */
    public data object LuhnCheckFailed : PanError
}
