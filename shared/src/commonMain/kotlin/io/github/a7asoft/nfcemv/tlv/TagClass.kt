package io.github.a7asoft.nfcemv.tlv

/**
 * BER-TLV tag class as defined in ISO/IEC 8825-1 §8.1.2.2.
 *
 * Encoded in the two most-significant bits of the first tag byte.
 */
public enum class TagClass(public val bits: Int) {
    Universal(0b00),
    Application(0b01),
    ContextSpecific(0b10),
    Private(0b11),
    ;

    public companion object {
        /**
         * Resolve a [TagClass] from the 2-bit class field of a tag's first byte.
         *
         * @throws IllegalArgumentException if [bits] is outside the range `0..3`.
         */
        public fun fromBits(bits: Int): TagClass {
            require(bits in 0..3) { "Class bits out of range: $bits" }
            return entries[bits]
        }
    }
}
