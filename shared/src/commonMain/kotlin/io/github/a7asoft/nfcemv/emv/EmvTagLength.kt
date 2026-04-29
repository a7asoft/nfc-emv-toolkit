package io.github.a7asoft.nfcemv.emv

/**
 * Expected length of a tag's value field, in bytes.
 *
 * - [Fixed] when the spec mandates an exact byte count (e.g. tag 9F26
 *   ARQC = 8 bytes, tag 82 AIP = 2 bytes).
 * - [Variable] when the spec gives an upper bound and the actual length
 *   varies card-by-card (e.g. tag 50 label up to 16 bytes, tag 84 DF
 *   name up to 16 bytes, tag 8C CDOL1 up to 252 bytes).
 *
 * Both variants enforce a positive byte count at construction; a zero
 * or negative spec'd length is a programming error and surfaces as
 * [IllegalArgumentException].
 */
public sealed interface EmvTagLength {

    public data class Fixed(val bytes: Int) : EmvTagLength {
        init {
            require(bytes > 0) { "Fixed tag length must be positive, was $bytes" }
        }
    }

    public data class Variable(val maxBytes: Int) : EmvTagLength {
        init {
            require(maxBytes > 0) { "Variable tag max length must be positive, was $maxBytes" }
        }
    }
}
