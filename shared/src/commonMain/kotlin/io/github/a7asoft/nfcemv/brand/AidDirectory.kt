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

    /** All registered entries in source order. */
    public val all: List<AidEntry> = listOf(
        // Visa
        AidEntry(Aid.fromHex("A0000000031010"), EmvBrand.VISA),     // Visa Credit / Debit
        AidEntry(Aid.fromHex("A0000000032010"), EmvBrand.VISA),     // Visa Electron
        AidEntry(Aid.fromHex("A0000000033010"), EmvBrand.VISA),     // Visa Interlink
        AidEntry(Aid.fromHex("A0000000038010"), EmvBrand.VISA),     // Visa Plus
        AidEntry(Aid.fromHex("A0000000039010"), EmvBrand.VISA),     // Visa V Pay
        // Mastercard
        AidEntry(Aid.fromHex("A0000000041010"), EmvBrand.MASTERCARD),  // Mastercard Credit / Debit
        AidEntry(Aid.fromHex("A0000000043060"), EmvBrand.MAESTRO),     // Maestro
        AidEntry(Aid.fromHex("A0000000046000"), EmvBrand.MASTERCARD),  // Cirrus
        // (dropped: A0000000049999 — Mastercard internal test AID, not a production identifier)
        // American Express
        // (dropped: A00000002501 — 6-byte truncated stub; EMVCo Amex AIDs are 8 bytes and listed below)
        AidEntry(Aid.fromHex("A000000025010402"), EmvBrand.AMERICAN_EXPRESS),  // ALIS
        AidEntry(Aid.fromHex("A000000025010701"), EmvBrand.AMERICAN_EXPRESS),  // ExpressPay
        AidEntry(Aid.fromHex("A000000025010801"), EmvBrand.AMERICAN_EXPRESS),  // Centurion
        // Discover
        AidEntry(Aid.fromHex("A0000003241010"), EmvBrand.DISCOVER),        // Discover Common Debit
        AidEntry(Aid.fromHex("A0000003242010"), EmvBrand.DISCOVER),        // Discover D-PAS
        // Diners Club (uses Diners' own RID A000000152, not Discover's RID)
        AidEntry(Aid.fromHex("A0000001523010"), EmvBrand.DINERS_CLUB),
        AidEntry(Aid.fromHex("A0000001524010"), EmvBrand.DINERS_CLUB),
        // JCB
        AidEntry(Aid.fromHex("A0000000651010"), EmvBrand.JCB),
        // UnionPay
        AidEntry(Aid.fromHex("A000000333010101"), EmvBrand.UNIONPAY),  // Credit
        AidEntry(Aid.fromHex("A000000333010102"), EmvBrand.UNIONPAY),  // Debit
        AidEntry(Aid.fromHex("A000000333010103"), EmvBrand.UNIONPAY),  // Quasi-credit
        AidEntry(Aid.fromHex("A000000333010106"), EmvBrand.UNIONPAY),  // Electronic Cash
        // Interac
        AidEntry(Aid.fromHex("A0000002771010"), EmvBrand.INTERAC),
    )

    private val byAid: Map<Aid, EmvBrand> = all.associate { it.aid to it.brand }
}
