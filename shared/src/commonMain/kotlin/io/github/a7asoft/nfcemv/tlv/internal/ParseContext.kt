package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.TlvOptions

/**
 * Bundle of state threaded through the recursive node decoder.
 *
 * Reduces parameter counts on internal functions and makes depth bookkeeping
 * a single hop ([deeper]) at the recursion boundary.
 */
internal data class ParseContext(
    val reader: TlvReader,
    val options: TlvOptions,
    val depth: Int,
) {
    fun deeper(): ParseContext = copy(depth = depth + 1)
}
