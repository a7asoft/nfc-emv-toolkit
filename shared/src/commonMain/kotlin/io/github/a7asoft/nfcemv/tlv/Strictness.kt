package io.github.a7asoft.nfcemv.tlv

/**
 * Decoder strictness toward non-minimal BER-TLV encodings.
 *
 * Strict mode rejects encodings that have an equivalent shorter representation
 * (e.g. a long-form length used where short form would suffice, or a multi-byte
 * tag continuation that begins with `0x80`). Lenient mode accepts these.
 *
 * Strict mode does NOT enforce the X.690 §8.1.2.4 short-form-mandatory rule
 * for tag numbers ≤ 30, because EMV intentionally violates it (see
 * `TagReader` KDoc).
 */
public sealed interface Strictness {
    public data object Strict : Strictness
    public data object Lenient : Strictness
}
