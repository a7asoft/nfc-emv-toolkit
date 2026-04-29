package io.github.a7asoft.nfcemv.tlv

/**
 * Behavior toward `0x00` bytes that appear between TLVs.
 *
 * EMV Specification Update Bulletin 69 (2009) authorizes such padding inside
 * constructed templates. This decoder applies the same tolerance at the top
 * level for symmetry with how callers receive concatenated record data.
 *
 * Use [Rejected] only for diagnostic dumps where every byte must be parsed
 * literally; in that mode `0x00 0x00` decodes as a valid empty primitive
 * (`tag=0x00, value=[]`).
 */
public sealed interface PaddingPolicy {
    public data object Tolerated : PaddingPolicy
    public data object Rejected : PaddingPolicy
}
