package io.github.a7asoft.nfcemv.brand.internal

/**
 * A predicate over the leading digits of a PAN string. Used by the brand
 * resolver's BIN-fallback layer when the AID directory misses.
 *
 * Two variants:
 * - [Prefix] matches when the PAN starts with a fixed digit prefix.
 * - [DigitRange] matches when the leading `length` digits, parsed as
 *   `Int`, fall in the closed range `lo..hi`.
 *
 * `BinMatcher` is `internal` to the `brand` module; the public surface
 * is `BrandResolver.resolveBrand`.
 */
internal sealed interface BinMatcher {

    /** True when [panDigits] should be classified by this matcher. */
    fun matches(panDigits: String): Boolean

    /** Match when the PAN starts with a fixed digit prefix. */
    data class Prefix(val prefix: String) : BinMatcher {
        init {
            require(prefix.isNotEmpty()) { "BIN prefix must not be empty" }
            require(prefix.all { it in '0'..'9' }) { "BIN prefix must be ASCII digits only" }
        }

        override fun matches(panDigits: String): Boolean =
            panDigits.startsWith(prefix)
    }

    /**
     * Match when the leading [length] digits fall in `lo..hi` (inclusive).
     *
     * `lo`/`hi` are interpreted as integers of [length] digits, so
     * `DigitRange(length = 4, lo = 2221, hi = 2720)` matches PANs whose
     * first four digits decode to a value in `2221..2720`.
     */
    data class DigitRange(val length: Int, val lo: Int, val hi: Int) : BinMatcher {
        init {
            require(length > 0) { "DigitRange length must be positive, was $length" }
            require(lo <= hi) { "DigitRange lo ($lo) must be <= hi ($hi)" }
        }

        override fun matches(panDigits: String): Boolean {
            if (panDigits.length < length) return false
            val n = panDigits.substring(0, length).toIntOrNull() ?: return false
            return n in lo..hi
        }
    }
}
