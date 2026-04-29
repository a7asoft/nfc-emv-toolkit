package io.github.a7asoft.nfcemv.brand

import io.github.a7asoft.nfcemv.brand.internal.matchBrandByBin
import io.github.a7asoft.nfcemv.extract.Pan

/**
 * Layered card-brand resolution.
 *
 * 1. If [aid] is non-null and [AidDirectory] knows it, return that brand.
 * 2. Otherwise, if [pan] is non-null and one of the registered BIN
 *    matchers accepts its leading digits, return that brand.
 * 3. Otherwise, return [EmvBrand.UNKNOWN].
 *
 * AID match takes precedence because a card-asserted AID is more
 * authoritative than legacy BIN heuristics. BIN matching is a fallback
 * for mag-stripe-derived data and for chips whose AIDs the toolkit does
 * not yet register.
 *
 * The PAN's raw digits are accessed via `pan.unmasked()`. The string is
 * consumed inside this call frame and never escapes; the result is an
 * [EmvBrand] enum, not card data.
 */
public object BrandResolver {

    /**
     * Resolve the best-effort [EmvBrand] for the given [aid] / [pan]
     * inputs per the layered rules above.
     */
    public fun resolveBrand(aid: Aid?, pan: Pan?): EmvBrand {
        if (aid != null) {
            val byAid = AidDirectory.lookup(aid)
            if (byAid != null) return byAid
        }
        if (pan != null) {
            val byBin = matchBrandByBin(pan.unmasked())
            if (byBin != null) return byBin
        }
        return EmvBrand.UNKNOWN
    }
}
