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
        Aid.fromHex("A0000000031010") to EmvBrand.VISA,
    )

    private val byAid: Map<Aid, EmvBrand> = all.associate { it }
}
