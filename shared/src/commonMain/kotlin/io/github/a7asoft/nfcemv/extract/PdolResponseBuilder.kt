package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.tlv.Tag

/**
 * Pure builder for the GPO PDOL response data field.
 *
 * For each entry in the [Pdol], looks up the value in [TerminalConfig]
 * (or per-read parameters [build]'s `transactionDate` and
 * `unpredictableNumber`) and concatenates them in the order the card
 * requested. Unknown tags are zero-padded to the requested length.
 *
 * Length handling per EMV Book 3 §5.4: the terminal MUST emit exactly
 * `length` bytes per entry. If a terminal value is shorter than the
 * requested length, right-pad with zeros; if longer, truncate to the
 * requested length.
 *
 * Pure function: no I/O, no clock or random injection inside the
 * builder. Caller computes [build]'s `transactionDate` and
 * `unpredictableNumber` at READ time. Trivially testable.
 */
public object PdolResponseBuilder {

    private val TAG_TTQ = Tag.fromHex("9F66")
    private val TAG_COUNTRY = Tag.fromHex("9F1A")
    private val TAG_CURRENCY = Tag.fromHex("5F2A")
    private val TAG_AMOUNT_AUTH = Tag.fromHex("9F02")
    private val TAG_AMOUNT_OTHER = Tag.fromHex("9F03")
    private val TAG_TVR = Tag.fromHex("95")
    private val TAG_TX_TYPE = Tag.fromHex("9C")
    private val TAG_TERMINAL_TYPE = Tag.fromHex("9F35")
    private val TAG_TERMINAL_CAPS = Tag.fromHex("9F33")
    private val TAG_ADDL_TERMINAL_CAPS = Tag.fromHex("9F40")
    private val TAG_APP_VERSION = Tag.fromHex("9F09")
    private val TAG_TX_DATE = Tag.fromHex("9A")
    private val TAG_UN = Tag.fromHex("9F37")

    /**
     * Build the PDOL response bytes per EMV Book 3 §5.4.
     *
     * @param pdol The card's parsed PDOL.
     * @param config Terminal-side defaults.
     * @param transactionDate Tag `9A` value (3 bytes BCD YYMMDD). Caller
     *   computes from the current clock — NOT stored on [TerminalConfig]
     *   so a long-lived config does not ship a stale date.
     * @param unpredictableNumber Tag `9F37` value (4 bytes random).
     *   Caller generates fresh per read.
     */
    @Suppress("LongParameterList")
    // why: pdol + config + per-read inputs (transactionDate, unpredictableNumber)
    // are all genuinely independent. Bundling them into a wrapper struct just
    // hides the four required inputs the caller must supply.
    public fun build(
        pdol: Pdol,
        config: TerminalConfig,
        transactionDate: ByteArray,
        unpredictableNumber: ByteArray,
    ): ByteArray {
        val response = mutableListOf<Byte>()
        for (entry in pdol.entries) {
            val value = valueFor(entry.tag, config, transactionDate, unpredictableNumber)
            response.addAll(value.padOrTruncate(entry.length).toList())
        }
        return response.toByteArray()
    }

    @Suppress("CyclomaticComplexMethod", "LongParameterList")
    // why: 1:1 dispatch table over the standard kernel-PDOL tag set
    // (per PR #34/#45/#49 precedent for sealed-style dispatch). CC reflects
    // the spec breadth. LongParameterList is justified for the same reason
    // as `build`.
    private fun valueFor(
        tag: Tag,
        config: TerminalConfig,
        transactionDate: ByteArray,
        unpredictableNumber: ByteArray,
    ): ByteArray = when (tag) {
        TAG_TTQ -> config.terminalTransactionQualifiers
        TAG_COUNTRY -> config.terminalCountryCode
        TAG_CURRENCY -> config.transactionCurrencyCode
        TAG_AMOUNT_AUTH -> config.amountAuthorised
        TAG_AMOUNT_OTHER -> config.amountOther
        TAG_TVR -> config.terminalVerificationResults
        TAG_TX_TYPE -> byteArrayOf(config.transactionType)
        TAG_TERMINAL_TYPE -> byteArrayOf(config.terminalType)
        TAG_TERMINAL_CAPS -> config.terminalCapabilities
        TAG_ADDL_TERMINAL_CAPS -> config.additionalTerminalCapabilities
        TAG_APP_VERSION -> config.applicationVersionNumber
        TAG_TX_DATE -> transactionDate
        TAG_UN -> unpredictableNumber
        else -> ByteArray(0)
    }

    private fun ByteArray.padOrTruncate(targetLength: Int): ByteArray {
        if (size == targetLength) return this
        val out = ByteArray(targetLength)
        copyInto(out, endIndex = minOf(size, targetLength))
        return out
    }
}
