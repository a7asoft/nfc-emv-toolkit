package io.github.a7asoft.nfcemv.emv

import io.github.a7asoft.nfcemv.tlv.Tag

/**
 * Static dictionary mapping `Tag` values to their EMV metadata.
 *
 * Entries are sourced from EMV Book 3 / Book 4 / contactless kernel
 * specifications C-2 through C-7. Descriptions are paraphrased; no
 * third-party listing has been copied verbatim.
 *
 * Lookup is O(1) via an internal `Map<Tag, EmvTagInfo>` precomputed at
 * class-init. [all] returns the entries in source order for callers
 * that need to enumerate.
 *
 * Unknown tags return `null` from [lookup]. Callers that need an
 * augmented dictionary (e.g. issuer-specific extensions) should
 * maintain their own map and consult it before falling back to this
 * one — the toolkit does not provide a plug-in / merge mechanism in
 * this milestone.
 */
public object EmvTags {

    /** Returns the metadata for [tag], or `null` if no entry is registered. */
    public fun lookup(tag: Tag): EmvTagInfo? = byTag[tag]

    /** All registered entries, in source order. */
    public val all: List<EmvTagInfo> = listOf(
        EmvTagInfo(Tag.fromHex("4F"),   "Application Identifier (AID)",                EmvTagFormat.B,  EmvTagLength.Variable(16),  TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("50"),   "Application Label",                           EmvTagFormat.AN, EmvTagLength.Variable(16),  TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("57"),   "Track 2 Equivalent Data",                     EmvTagFormat.B,  EmvTagLength.Variable(19),  TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("5A"),   "Application Primary Account Number (PAN)",    EmvTagFormat.CN, EmvTagLength.Variable(10),  TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("5F20"), "Cardholder Name",                             EmvTagFormat.AN, EmvTagLength.Variable(26),  TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("5F24"), "Application Expiration Date (YYMMDD)",        EmvTagFormat.N,  EmvTagLength.Fixed(3),      TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("5F25"), "Application Effective Date (YYMMDD)",         EmvTagFormat.N,  EmvTagLength.Fixed(3),      TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("5F28"), "Issuer Country Code (ISO 3166-1 numeric)",    EmvTagFormat.N,  EmvTagLength.Fixed(2),      TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("5F2A"), "Transaction Currency Code (ISO 4217 numeric)",EmvTagFormat.N,  EmvTagLength.Fixed(2),      TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("5F2D"), "Language Preference (ISO 639-1 codes)",       EmvTagFormat.AN, EmvTagLength.Variable(8),   TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("5F34"), "Application PAN Sequence Number",             EmvTagFormat.N,  EmvTagLength.Fixed(1),      TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("82"),   "Application Interchange Profile (AIP)",       EmvTagFormat.B,  EmvTagLength.Fixed(2),      TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("84"),   "Dedicated File (DF) Name",                    EmvTagFormat.B,  EmvTagLength.Variable(16),  TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("8C"),   "CDOL1 (Card Risk Management DOL 1)",          EmvTagFormat.B,  EmvTagLength.Variable(252), TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("8D"),   "CDOL2 (Card Risk Management DOL 2)",          EmvTagFormat.B,  EmvTagLength.Variable(252), TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("94"),   "Application File Locator (AFL)",              EmvTagFormat.B,  EmvTagLength.Variable(252), TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("9F02"), "Amount, Authorised (Numeric)",                EmvTagFormat.N,  EmvTagLength.Fixed(6),      TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("9F10"), "Issuer Application Data (IAD)",               EmvTagFormat.B,  EmvTagLength.Variable(32),  TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("9F12"), "Application Preferred Name",                  EmvTagFormat.AN, EmvTagLength.Variable(16),  TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("9F26"), "Application Cryptogram",                      EmvTagFormat.B,  EmvTagLength.Fixed(8),      TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("9F27"), "Cryptogram Information Data (CID)",           EmvTagFormat.B,  EmvTagLength.Fixed(1),      TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("9F36"), "Application Transaction Counter (ATC)",       EmvTagFormat.B,  EmvTagLength.Fixed(2),      TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("9F37"), "Unpredictable Number",                        EmvTagFormat.B,  EmvTagLength.Fixed(4),      TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("9F4B"), "Signed Dynamic Application Data",             EmvTagFormat.B,  EmvTagLength.Variable(248), TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("9F6B"), "Track 2 Data (Mastercard, kernel C-2)",       EmvTagFormat.B,  EmvTagLength.Variable(19),  TagSensitivity.PCI),
        EmvTagInfo(Tag.fromHex("9F6C"), "Card Transaction Qualifiers (CTQ)",           EmvTagFormat.B,  EmvTagLength.Fixed(2),      TagSensitivity.PUBLIC),
        EmvTagInfo(Tag.fromHex("BF0C"), "FCI Issuer Discretionary Data",               EmvTagFormat.B,  EmvTagLength.Variable(222), TagSensitivity.PUBLIC),
    )

    private val byTag: Map<Tag, EmvTagInfo> = all.associateBy { it.tag }
}
