package io.github.a7asoft.nfcemv.emv

import io.github.a7asoft.nfcemv.tlv.Tag

/**
 * Metadata for a single EMV BER-TLV tag.
 *
 * Looked up via [EmvTags.lookup]. Entries are hand-curated from EMV
 * Book 3 / Book 4 / contactless kernels C-2..C-7; descriptions are
 * paraphrased, not copied from any third-party listing.
 */
public data class EmvTagInfo(
    val tag: Tag,
    val name: String,
    val format: EmvTagFormat,
    val length: EmvTagLength,
    val sensitivity: TagSensitivity,
)
