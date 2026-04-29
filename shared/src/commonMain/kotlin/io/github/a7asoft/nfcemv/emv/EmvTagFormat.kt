package io.github.a7asoft.nfcemv.emv

/**
 * EMV data-element format codes per Book 3 §4.3 and Book 4 §A.5.
 *
 * - [N]  Numeric digits packed two per byte (BCD), high-nibble first; the
 *   field is right-padded with leading-zero nibbles when shorter than the
 *   declared length. Examples: tag 5F2A (currency), 9F02 (amount).
 * - [AN] Alphanumeric ASCII as defined by ISO/IEC 8859-1, one character
 *   per byte. Examples: tag 50 (label), 5F20 (cardholder name).
 * - [B]  Raw binary bytes, opaque to the dictionary. Examples: tag 4F
 *   (AID), 82 (AIP), 9F26 (ARQC).
 * - [CN] Compressed numeric: BCD nibbles like [N] but with `'F'` (`0x0F`)
 *   nibble padding when the value's nibble count is odd. Examples:
 *   tag 5A (PAN).
 *
 * EMV's full notation also includes `ans` and `var.` variants. This
 * dictionary collapses them to [AN] and either [B] or `Variable` length
 * respectively; that's enough for the lookup-only use case.
 */
public enum class EmvTagFormat { N, AN, B, CN }
