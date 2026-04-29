package io.github.a7asoft.nfcemv.tlv

/**
 * A decoded BER-TLV node.
 *
 * Either a [Primitive] carrying raw value bytes, or a [Constructed] carrying
 * an ordered list of child nodes. The shape mirrors ISO/IEC 8825-1 §8.1.
 */
public sealed interface Tlv {
    /** The tag identifying this node. */
    public val tag: Tag

    /** A primitive node holding raw value bytes. */
    public class Primitive(
        override val tag: Tag,
        /** The decoded value. Treat as immutable; the parser does not retain a reference. */
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
    public data class Constructed(
        override val tag: Tag,
        /** The decoded children, in source order. */
        public val children: List<Tlv>,
    ) : Tlv {
        override fun toString(): String = "Constructed(tag=$tag, children=${children.size})"
    }
}
