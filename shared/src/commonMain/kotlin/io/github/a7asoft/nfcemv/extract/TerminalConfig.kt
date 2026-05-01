package io.github.a7asoft.nfcemv.extract

/**
 * Terminal-side data the reader supplies in the GPO PDOL response.
 *
 * Defaults aim at a "read-only contactless terminal that does not
 * commit to a transaction": TTQ flags qVSDC support without forcing a
 * specific cryptogram path; amounts are zero; country and currency
 * default to US/USD. Transaction date and unpredictable number are
 * computed at READ time (not stored on this struct) so a long-lived
 * [TerminalConfig] does not ship stale values.
 *
 * Default-value choices are documented inline with EMV / ISO citations;
 * see [Companion.default] for the canonical "standard payment terminal"
 * shape. Callers override individual fields via `data class` `copy`.
 *
 * @see PdolResponseBuilder
 */
public data class TerminalConfig(
    /** Tag `9F66` — Terminal Transaction Qualifiers, 4 bytes. */
    public val terminalTransactionQualifiers: ByteArray,
    /** Tag `9F1A` — Terminal Country Code (ISO 3166-1 numeric), 2 bytes. */
    public val terminalCountryCode: ByteArray,
    /** Tag `5F2A` — Transaction Currency Code (ISO 4217 numeric), 2 bytes. */
    public val transactionCurrencyCode: ByteArray,
    /** Tag `9F02` — Amount, Authorised (n12 BCD), 6 bytes. */
    public val amountAuthorised: ByteArray,
    /** Tag `9F03` — Amount, Other (n12 BCD), 6 bytes. */
    public val amountOther: ByteArray,
    /** Tag `95` — Terminal Verification Results, 5 bytes. */
    public val terminalVerificationResults: ByteArray,
    /** Tag `9C` — Transaction Type, 1 byte. */
    public val transactionType: Byte,
    /** Tag `9F35` — Terminal Type, 1 byte. */
    public val terminalType: Byte,
    /** Tag `9F33` — Terminal Capabilities, 3 bytes. */
    public val terminalCapabilities: ByteArray,
    /** Tag `9F40` — Additional Terminal Capabilities, 5 bytes. */
    public val additionalTerminalCapabilities: ByteArray,
    /** Tag `9F09` — Application Version Number (Terminal), 2 bytes. */
    public val applicationVersionNumber: ByteArray,
) {

    @Suppress("CyclomaticComplexMethod")
    // why: structural data-class equals across 11 ByteArray fields. Auto-equals
    // would compare ByteArray references, breaking value semantics.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalConfig) return false
        if (!terminalTransactionQualifiers.contentEquals(other.terminalTransactionQualifiers)) return false
        if (!terminalCountryCode.contentEquals(other.terminalCountryCode)) return false
        if (!transactionCurrencyCode.contentEquals(other.transactionCurrencyCode)) return false
        if (!amountAuthorised.contentEquals(other.amountAuthorised)) return false
        if (!amountOther.contentEquals(other.amountOther)) return false
        if (!terminalVerificationResults.contentEquals(other.terminalVerificationResults)) return false
        if (transactionType != other.transactionType) return false
        if (terminalType != other.terminalType) return false
        if (!terminalCapabilities.contentEquals(other.terminalCapabilities)) return false
        if (!additionalTerminalCapabilities.contentEquals(other.additionalTerminalCapabilities)) return false
        if (!applicationVersionNumber.contentEquals(other.applicationVersionNumber)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = terminalTransactionQualifiers.contentHashCode()
        result = 31 * result + terminalCountryCode.contentHashCode()
        result = 31 * result + transactionCurrencyCode.contentHashCode()
        result = 31 * result + amountAuthorised.contentHashCode()
        result = 31 * result + amountOther.contentHashCode()
        result = 31 * result + terminalVerificationResults.contentHashCode()
        result = 31 * result + transactionType
        result = 31 * result + terminalType
        result = 31 * result + terminalCapabilities.contentHashCode()
        result = 31 * result + additionalTerminalCapabilities.contentHashCode()
        result = 31 * result + applicationVersionNumber.contentHashCode()
        return result
    }

    public companion object {
        /**
         * "Read-only contactless terminal" defaults. TTQ value
         * `36 00 00 00` matches what a passive reader (no online
         * authorization, no GENERATE AC, no cryptogram verification)
         * SHOULD signal:
         *
         * - Byte 1 `0x36` — qVSDC + Contact EMV + Online PIN + Signature.
         * - Byte 2 `0x00` — **online-cryptogram-required bit cleared**.
         *   Setting this bit (`0x80`) tells the Visa kernel "I will go
         *   online", which on some issuers (observed: Chase) causes the
         *   kernel to skip AFL emission in the GPO response and rely on
         *   the issuer host for static data. A read-only reader has no
         *   issuer host, so it MUST keep this bit clear or it will
         *   receive `90 00` with no `94` tag. See issue #59.
         * - Bytes 3–4 `0x00 0x00` — reserved / RFU.
         *
         * Country / currency default to US/USD. Amounts zero. Type
         * purchase. Callers needing a different TTQ (e.g. to test
         * issuer behavior under online-only flows) override via
         * `TerminalConfig.default().copy(terminalTransactionQualifiers = ...)`.
         *
         * Returns a fresh instance per call so callers cannot mutate the
         * shared default arrays.
         */
        @Suppress("MagicNumber")
        // why: every byte here is a spec-mandated value (TTQ flags per
        // EMV qVSDC kernel, ISO 3166-1 numeric 0840, ISO 4217 numeric
        // 0840, EMV terminal-type / capability bit patterns). Naming
        // each constant separately would obscure the wire shape.
        public fun default(): TerminalConfig = TerminalConfig(
            terminalTransactionQualifiers = byteArrayOf(0x36, 0x00, 0x00, 0x00),
            terminalCountryCode = byteArrayOf(0x08, 0x40),
            transactionCurrencyCode = byteArrayOf(0x08, 0x40),
            amountAuthorised = byteArrayOf(0, 0, 0, 0, 0, 0),
            amountOther = byteArrayOf(0, 0, 0, 0, 0, 0),
            terminalVerificationResults = byteArrayOf(0, 0, 0, 0, 0),
            transactionType = 0x00,
            terminalType = 0x22,
            terminalCapabilities = byteArrayOf(0x60, 0x08, 0x08),
            additionalTerminalCapabilities = byteArrayOf(0, 0, 0, 0, 0),
            applicationVersionNumber = byteArrayOf(0x00, 0x8C.toByte()),
        )
    }
}
