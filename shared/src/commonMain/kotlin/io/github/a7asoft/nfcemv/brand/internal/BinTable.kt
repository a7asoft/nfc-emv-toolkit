package io.github.a7asoft.nfcemv.brand.internal

import io.github.a7asoft.nfcemv.brand.EmvBrand

/**
 * Ordered list of `(BinMatcher, EmvBrand)` pairs evaluated top-to-bottom
 * by [matchBrandByBin].
 *
 * Order matters: more specific matchers come BEFORE broader ones. The
 * canonical example is the Discover sub-range `622126..622925` which
 * sits ahead of UnionPay's `62` prefix; without that ordering, Discover
 * cards in the overlap would be misclassified as UnionPay.
 */
internal val BIN_TABLE: List<Pair<BinMatcher, EmvBrand>> = listOf(
    // American Express
    BinMatcher.Prefix("34") to EmvBrand.AMERICAN_EXPRESS,
    BinMatcher.Prefix("37") to EmvBrand.AMERICAN_EXPRESS,
    // JCB (numeric range 3528..3589)
    BinMatcher.DigitRange(length = 4, lo = 3528, hi = 3589) to EmvBrand.JCB,
    // Discover (Discover sub-ranges before UnionPay's broader 62 prefix)
    BinMatcher.Prefix("6011") to EmvBrand.DISCOVER,
    BinMatcher.Prefix("65") to EmvBrand.DISCOVER,
    BinMatcher.DigitRange(length = 3, lo = 644, hi = 649) to EmvBrand.DISCOVER,
    BinMatcher.DigitRange(length = 6, lo = 622126, hi = 622925) to EmvBrand.DISCOVER,
    // Diners Club
    BinMatcher.DigitRange(length = 3, lo = 300, hi = 305) to EmvBrand.DINERS_CLUB,
    BinMatcher.Prefix("309") to EmvBrand.DINERS_CLUB,
    BinMatcher.Prefix("36") to EmvBrand.DINERS_CLUB,
    BinMatcher.Prefix("38") to EmvBrand.DINERS_CLUB,
    // Mastercard (legacy 51..55 plus 2 series 2221..2720)
    BinMatcher.Prefix("51") to EmvBrand.MASTERCARD,
    BinMatcher.Prefix("52") to EmvBrand.MASTERCARD,
    BinMatcher.Prefix("53") to EmvBrand.MASTERCARD,
    BinMatcher.Prefix("54") to EmvBrand.MASTERCARD,
    BinMatcher.Prefix("55") to EmvBrand.MASTERCARD,
    BinMatcher.DigitRange(length = 4, lo = 2221, hi = 2720) to EmvBrand.MASTERCARD,
    // Maestro
    BinMatcher.Prefix("50") to EmvBrand.MAESTRO,
    BinMatcher.Prefix("56") to EmvBrand.MAESTRO,
    BinMatcher.Prefix("57") to EmvBrand.MAESTRO,
    BinMatcher.Prefix("58") to EmvBrand.MAESTRO,
    // Visa
    BinMatcher.Prefix("4") to EmvBrand.VISA,
    // UnionPay (catch-all after Discover sub-ranges)
    BinMatcher.Prefix("62") to EmvBrand.UNIONPAY,
)

/**
 * Returns the first brand whose matcher accepts [panDigits], or `null`
 * if no entry in [BIN_TABLE] matches.
 */
internal fun matchBrandByBin(panDigits: String): EmvBrand? =
    BIN_TABLE.firstOrNull { it.first.matches(panDigits) }?.second
