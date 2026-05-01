package io.github.a7asoft.nfcemv.extract.internal

import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv

/**
 * Depth-first search across a forest of [Tlv] roots and their constructed
 * children, returning the first node whose tag equals [target].
 *
 * Used by `EmvParser` to locate required and optional tags inside the
 * concatenated TLV trees decoded from APDU responses.
 *
 * Recursion is bounded by the decoder's `TlvOptions.maxDepth`; this
 * search adds no further bound because the input was already validated.
 */
internal fun findFirst(tlvs: List<Tlv>, target: Tag): Tlv? {
    for (node in tlvs) {
        if (node.tag == target) return node
        if (node is Tlv.Constructed) {
            val child = findFirst(node.children, target)
            if (child != null) return child
        }
    }
    return null
}

/**
 * Return the first direct child of [parent] whose tag matches [tag],
 * or null if absent. Shallow search (does NOT recurse into nested
 * constructed children). Use [findFirst] when DFS recursion is needed.
 *
 * Matches the search semantics needed by FCI / GPO format-2 template
 * lookups where the desired child sits one level deep inside a known
 * constructed wrapper (`6F`, `A5`, `77`).
 */
internal fun firstChildByTag(parent: Tlv.Constructed, tag: Tag): Tlv? {
    for (child in parent.children) {
        if (child.tag == tag) return child
    }
    return null
}
