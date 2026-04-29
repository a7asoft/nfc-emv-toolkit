package io.github.a7asoft.nfcemv.tlv

/**
 * Configures decoder behavior. Defaults are tuned for real EMV cards.
 *
 * @property strictness Whether non-minimal BER encodings are rejected.
 *   See [Strictness].
 * @property paddingPolicy Whether `0x00` bytes between TLVs are skipped.
 *   See [PaddingPolicy].
 * @property maxTagBytes Hard cap on multi-byte tag length. ISO/IEC 8825-1
 *   admits arbitrary length; EMV uses at most 2. Bounded to prevent
 *   decoder DoS via crafted never-terminating continuations.
 * @property maxDepth Hard cap on constructed-tag nesting. Bounded to prevent
 *   stack-overflow attacks via crafted input.
 */
public data class TlvOptions(
    val strictness: Strictness = Strictness.Strict,
    val paddingPolicy: PaddingPolicy = PaddingPolicy.Tolerated,
    val maxTagBytes: Int = 4,
    val maxDepth: Int = 16,
) {
    init {
        require(maxTagBytes in 1..4) { "maxTagBytes out of range: $maxTagBytes" }
        require(maxDepth in 1..64) { "maxDepth out of range: $maxDepth" }
    }
}
