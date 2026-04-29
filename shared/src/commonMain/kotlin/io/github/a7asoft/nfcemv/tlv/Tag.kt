package io.github.a7asoft.nfcemv.tlv

import io.github.a7asoft.nfcemv.tlv.internal.hexToBytes
import io.github.a7asoft.nfcemv.tlv.internal.toUpperHex
import kotlin.jvm.JvmInline

/**
 * A BER-TLV tag for use with EMV contactless cards.
 *
 * Tags are 1 to 4 bytes per ISO/IEC 8825-1 §8.1.2. EMV defines no tag longer than
 * two bytes, but this type accepts up to four bytes to remain spec-correct.
 *
 * `Tag` is a value class backed by a `Long`, so equality, hashing, and storage
 * are allocation-free.
 *
 * Construct via [ofBytes] or [fromHex]. The `Long` constructor is internal so
 * the encoding remains an implementation detail.
 *
 * Packing layout (high to low):
 * - bits 56–63: byte count (1–4)
 * - bits 24–55: up to four big-endian tag bytes, MSB-aligned at bit 55
 * - bits 0–23: unused, always zero
 */
@JvmInline
public value class Tag internal constructor(private val packed: Long) {

    /** Number of bytes used to encode this tag (1–4). */
    public val byteCount: Int
        get() = ((packed ushr LENGTH_SHIFT) and 0xFF).toInt()

    /** The raw tag bytes in big-endian order (most significant first). */
    public val bytes: ByteArray
        get() = unpackBytes(packed, byteCount)

    /** ISO/IEC 8825-1 §8.1.2.2 class bits, decoded from the first tag byte. */
    public val tagClass: TagClass
        get() = TagClass.fromBits((firstByteAsInt() ushr CLASS_SHIFT) and CLASS_MASK)

    /** True when ISO/IEC 8825-1 §8.1.2.3 P/C bit indicates a constructed encoding. */
    public val isConstructed: Boolean
        get() = (firstByteAsInt() and CONSTRUCTED_BIT) != 0

    /** Inverse of [isConstructed]. */
    public val isPrimitive: Boolean
        get() = !isConstructed

    /** Uppercase hexadecimal representation, e.g. `9F02`. */
    override fun toString(): String = bytes.toUpperHex()

    private fun firstByteAsInt(): Int {
        val shift = (byteCount - 1) * 8
        return ((packed ushr shift) and 0xFF).toInt()
    }

    public companion object {
        internal const val MAX_BYTES: Int = 4

        private const val LENGTH_SHIFT = 56
        private const val CLASS_SHIFT = 6
        private const val CLASS_MASK = 0x03
        private const val CONSTRUCTED_BIT = 0x20

        /**
         * Construct a [Tag] from raw bytes.
         *
         * @throws IllegalArgumentException if [bytes] is empty or longer than [MAX_BYTES].
         */
        public fun ofBytes(bytes: ByteArray): Tag {
            require(bytes.isNotEmpty()) { "Tag must have at least one byte" }
            require(bytes.size <= MAX_BYTES) { "Tag exceeds maximum of $MAX_BYTES bytes" }
            return Tag(packBytes(bytes))
        }

        /**
         * Construct a [Tag] from a hex string. Case insensitive.
         *
         * @throws IllegalArgumentException if [hex] is empty, has an odd length, or
         *   contains a non-hex character.
         */
        public fun fromHex(hex: String): Tag {
            require(hex.isNotEmpty()) { "Hex string must not be empty" }
            return ofBytes(hex.hexToBytes())
        }

        private fun packBytes(bytes: ByteArray): Long {
            var packed = bytes.size.toLong() shl LENGTH_SHIFT
            for (i in bytes.indices) {
                val shift = (bytes.size - 1 - i) * 8
                packed = packed or ((bytes[i].toLong() and 0xFF) shl shift)
            }
            return packed
        }

        private fun unpackBytes(packed: Long, count: Int): ByteArray {
            val out = ByteArray(count)
            for (i in 0 until count) {
                val shift = (count - 1 - i) * 8
                out[i] = ((packed ushr shift) and 0xFF).toByte()
            }
            return out
        }
    }
}
