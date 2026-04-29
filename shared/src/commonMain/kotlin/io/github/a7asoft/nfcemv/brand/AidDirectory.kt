package io.github.a7asoft.nfcemv.brand

/**
 * Static lookup mapping EMV Application Identifiers to [EmvBrand].
 *
 * Entries are paraphrased from EMVCo published listings and ISO/IEC
 * 7816-5 RID assignments; no third-party listing has been copied
 * verbatim. Lookup is O(1) via an internal `Map<Aid, EmvBrand>`
 * precomputed at object init.
 *
 * Returns `null` for unknown AIDs. Callers needing a layered AID-then-BIN
 * resolution should use [BrandResolver.resolveBrand].
 */
public object AidDirectory {

    /** Returns the brand for [aid], or `null` if no entry is registered. */
    public fun lookup(aid: Aid): EmvBrand? = byAid[aid]

    /** All registered (AID, brand) pairs in source order. */
    public val all: List<Pair<Aid, EmvBrand>> = listOf(
        // Visa
        Aid.fromHex("A0000000031010") to EmvBrand.VISA,
        Aid.fromHex("A0000000032010") to EmvBrand.VISA,
        Aid.fromHex("A0000000033010") to EmvBrand.VISA,
        Aid.fromHex("A0000000038010") to EmvBrand.VISA,
        Aid.fromHex("A0000000039010") to EmvBrand.VISA,
        // Mastercard
        Aid.fromHex("A0000000041010") to EmvBrand.MASTERCARD,
        Aid.fromHex("A0000000043060") to EmvBrand.MAESTRO,
        Aid.fromHex("A0000000046000") to EmvBrand.MASTERCARD,
        Aid.fromHex("A0000000049999") to EmvBrand.MASTERCARD,
        // American Express
        Aid.fromHex("A00000002501") to EmvBrand.AMERICAN_EXPRESS,
        Aid.fromHex("A000000025010402") to EmvBrand.AMERICAN_EXPRESS,
        Aid.fromHex("A000000025010701") to EmvBrand.AMERICAN_EXPRESS,
        Aid.fromHex("A000000025010801") to EmvBrand.AMERICAN_EXPRESS,
        // Discover
        Aid.fromHex("A0000003241010") to EmvBrand.DISCOVER,
        Aid.fromHex("A0000003242010") to EmvBrand.DISCOVER,
        // Diners Club
        Aid.fromHex("A0000001523010") to EmvBrand.DINERS_CLUB,
        Aid.fromHex("A0000001524010") to EmvBrand.DINERS_CLUB,
        // JCB
        Aid.fromHex("A0000000651010") to EmvBrand.JCB,
        // UnionPay
        Aid.fromHex("A000000333010101") to EmvBrand.UNIONPAY,
        Aid.fromHex("A000000333010102") to EmvBrand.UNIONPAY,
        Aid.fromHex("A000000333010103") to EmvBrand.UNIONPAY,
        Aid.fromHex("A000000333010106") to EmvBrand.UNIONPAY,
        // Interac
        Aid.fromHex("A0000002771010") to EmvBrand.INTERAC,
    )

    private val byAid: Map<Aid, EmvBrand> = all.associate { it }
}
