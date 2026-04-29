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
        EmvTagInfo(
            tag = Tag.fromHex("9F26"),
            name = "Application Cryptogram",
            format = EmvTagFormat.B,
            length = EmvTagLength.Fixed(8),
            sensitivity = TagSensitivity.PCI,
        ),
    )

    private val byTag: Map<Tag, EmvTagInfo> = all.associateBy { it.tag }
}
