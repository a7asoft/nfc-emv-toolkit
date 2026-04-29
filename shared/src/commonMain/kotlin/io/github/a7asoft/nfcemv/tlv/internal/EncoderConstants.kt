package io.github.a7asoft.nfcemv.tlv.internal

/**
 * Hard cap on TLV nesting depth honored by both encoder passes
 * ([encodedSize] and [writeNode]).
 *
 * Mirrors the upper bound of `TlvOptions.maxDepth`, so any tree the decoder
 * accepts can also be re-emitted; caller-built trees that exceed this surface
 * as `IllegalStateException` instead of `StackOverflowError` from either pass.
 */
internal const val MAX_DEPTH: Int = 64
