package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.brand.EmvBrand
import kotlinx.datetime.YearMonth

/**
 * Top-level EMV card snapshot composed from APDU responses by
 * [EmvParser.parse].
 *
 * Required fields (`pan`, `expiry`, `aid`) are non-null; optional
 * fields (`cardholderName`, `applicationLabel`, `track2`) are nullable
 * and reflect whether the corresponding TLV tag was present in the
 * input. `brand` is computed via `BrandResolver.resolveBrand(aid, pan)`
 * and is therefore always one of the registered [EmvBrand] values
 * including [EmvBrand.UNKNOWN].
 *
 * The class is a `data class` (per the issue spec) for `componentN` /
 * `copy` ergonomics, but `toString` is overridden to mask sensitive
 * fields:
 * - `pan` is rendered through [Pan.toString], which masks per PCI DSS
 *   Req 3.4 first-6 + last-4 (delegated).
 * - `cardholderName` is rendered as `<N chars>` — the length only,
 *   never the value, because cardholder name is PCI Cardholder Data.
 * - `track2` is rendered through [Track2.toString], which masks both
 *   the embedded PAN and the discretionary bytes (delegated).
 * - `applicationLabel`, `brand`, `expiry`, `aid` are rendered raw —
 *   non-PCI operational metadata.
 *
 * IMPORTANT: the [cardholderName] property accessor returns the raw
 * String. Direct field access bypasses the `toString` mask. Callers
 * MUST NOT pass an `EmvCard` instance to a generic logger that calls
 * `toString` recursively over all fields, and MUST NOT log
 * `card.cardholderName` directly. A future `CardholderName` value-class
 * wrapper will harden this surface (tracked separately).
 */
public data class EmvCard internal constructor(
    public val pan: Pan,
    public val expiry: YearMonth,
    public val cardholderName: String?,
    public val brand: EmvBrand,
    public val applicationLabel: String?,
    public val track2: Track2?,
    public val aid: Aid,
) {
    override fun toString(): String =
        "EmvCard(pan=$pan, expiry=$expiry, cardholderName=${maskName(cardholderName)}, " +
            "brand=$brand, applicationLabel=$applicationLabel, track2=$track2, aid=$aid)"

    private companion object {
        private fun maskName(name: String?): String =
            if (name == null) "null" else "<${name.length} chars>"
    }
}
