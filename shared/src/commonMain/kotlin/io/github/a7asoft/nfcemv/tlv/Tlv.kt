package io.github.a7asoft.nfcemv.tlv

/**
 * A decoded BER-TLV node.
 *
 * Either a [Primitive] carrying raw value bytes, or a [Constructed] carrying
 * an ordered list of child nodes. The shape mirrors ISO/IEC 8825-1 §8.1.
 *
 * Both variants are regular classes (not data classes) so that auto-generated
 * `toString` / `componentN` / `copy` cannot accidentally expose value bytes
 * across future field additions. Equality is hand-rolled to compare value
 * content rather than reference identity on `ByteArray`.
 */
public sealed interface Tlv {
    /** The tag identifying this node. */
    public val tag: Tag

    /** A primitive node holding raw value bytes. */
    public class Primitive(
        override val tag: Tag,
        /**
         * The decoded value. The `Tlv.Primitive` instance retains this
         * reference; callers MUST treat the returned array as read-only.
         * Mutating it would be observable on this node.
         */
        public val value: ByteArray,
    ) : Tlv {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Primitive) return false
            return tag == other.tag && value.contentEquals(other.value)
        }

        override fun hashCode(): Int = 31 * tag.hashCode() + value.contentHashCode()

        override fun toString(): String = "Primitive(tag=$tag, length=${value.size})"
    }

    /** A constructed node holding child TLV nodes. */
    public class Constructed(
        override val tag: Tag,
        /** The decoded children, in source order. */
        public val children: List<Tlv>,
    ) : Tlv {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Constructed) return false
            return tag == other.tag && children == other.children
        }

        override fun hashCode(): Int = 31 * tag.hashCode() + children.hashCode()

        override fun toString(): String = "Constructed(tag=$tag, children=${children.size})"
    }
}
