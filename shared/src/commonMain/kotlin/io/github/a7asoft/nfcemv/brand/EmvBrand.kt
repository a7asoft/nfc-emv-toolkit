package io.github.a7asoft.nfcemv.brand

/**
 * The set of card brands the toolkit recognises via the AID directory and
 * BIN matchers.
 *
 * [UNKNOWN] is returned by [BrandResolver.resolveBrand] when neither AID
 * lookup nor BIN matching produces a hit; callers typically treat
 * `UNKNOWN` as "do not auto-route" rather than as a real brand.
 *
 * The list intentionally covers the EMVCo founding members plus widely
 * deployed regional brands (Maestro, Diners, Interac). Issuer-extension
 * brands and country-specific debit networks are out of scope for this
 * milestone — extend the directory in a future PR rather than here.
 */
public enum class EmvBrand(public val displayName: String) {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    MAESTRO("Maestro"),
    AMERICAN_EXPRESS("American Express"),
    DISCOVER("Discover"),
    DINERS_CLUB("Diners Club"),
    JCB("JCB"),
    UNIONPAY("UnionPay"),
    INTERAC("Interac"),
    UNKNOWN("Unknown"),
}
