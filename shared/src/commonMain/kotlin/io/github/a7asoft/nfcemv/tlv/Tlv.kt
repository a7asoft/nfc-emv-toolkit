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

    /**
     * A primitive node holding raw value bytes.
     *
     * The constructor takes ownership of [value] via a defensive copy; the
     * stored bytes are never exposed by reference. Callers obtain bytes
     * through [copyValue], which returns a fresh copy each call so mutation
     * cannot propagate into the node's identity (preserving [equals] and
     * [hashCode] invariants).
     *
     * For PAN-bearing tags (`5A`, `57`, etc.) the only safe sink for the
     * returned bytes is a typed extractor (e.g. `Pan`) that masks on
     * `toString`. Never log, persist, or transmit `copyValue()` directly.
     */
    public class Primitive(
        override val tag: Tag,
        value: ByteArray,
    ) : Tlv {
        private val storedValue: ByteArray = value.copyOf()

        /** Number of bytes in the value field. */
        public val length: Int get() = storedValue.size

        /** Returns a fresh defensive copy of the value bytes. */
        public fun copyValue(): ByteArray = storedValue.copyOf()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Primitive) return false
            return tag == other.tag && storedValue.contentEquals(other.storedValue)
        }

        override fun hashCode(): Int = 31 * tag.hashCode() + storedValue.contentHashCode()

        override fun toString(): String = "Primitive(tag=$tag, length=${storedValue.size})"
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
