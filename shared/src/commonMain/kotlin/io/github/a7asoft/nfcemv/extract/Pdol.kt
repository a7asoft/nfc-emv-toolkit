package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.tlv.Tag

/**
 * Processing Options Data Object List per EMV Book 3 §5.4 / Annex A
 * tag `9F38`.
 *
 * The PDOL is a list of tag-length pairs (no values) describing what
 * data the card wants the terminal to provide in the GET PROCESSING
 * OPTIONS command body. The reader builds the response by looking up
 * each requested tag in [TerminalConfig] and concatenating the values
 * in the order requested. See [PdolResponseBuilder].
 *
 * Only short-form lengths (single byte 0..127) are accepted. Long-form
 * BER-TLV lengths are theoretically allowed but never observed in real
 * PDOLs and are rejected as [PdolError.InvalidLength].
 *
 * Construction goes through [Pdol.parse] / [Pdol.parseOrThrow]. The
 * primary constructor is `internal` so external consumers cannot bypass
 * validation.
 */
public class Pdol internal constructor(
    /** Decoded tag-length entries in source order. */
    public val entries: List<PdolEntry>,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Pdol) return false
        return entries == other.entries
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "Pdol(entries=${entries.size})"

    public companion object
}

/** One tag-length pair from a [Pdol]. */
public data class PdolEntry(public val tag: Tag, public val length: Int)

/** Structural reasons [Pdol.parse] can reject input. Carries no value bytes. */
public sealed interface PdolError {
    /** Input was empty. */
    public data object EmptyInput : PdolError

    /** Tag bytes ended without a terminating final byte. */
    public data class IncompleteTag(val offset: Int) : PdolError

    /** No length byte followed the decoded tag. */
    public data class IncompleteLength(val offset: Int) : PdolError

    /** Length byte exceeds short-form (long-form not allowed in PDOL). */
    public data class InvalidLength(val offset: Int, val byte: Byte) : PdolError
}

/** Two-case result mirroring `parse`/`parseOrThrow` convention. */
public sealed interface PdolResult {
    public data class Ok(val pdol: Pdol) : PdolResult
    public data class Err(val error: PdolError) : PdolResult
}

/**
 * Parse a PDOL byte sequence into a typed [Pdol] tree.
 *
 * Returns [PdolResult.Ok] on success or [PdolResult.Err] carrying a
 * structural [PdolError] that references only offsets — never raw value
 * bytes.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
// why: each return is one DOL parse step per EMV Book 3 §5.4
// (empty / incomplete tag / incomplete length / invalid length).
// Splitting into helpers ferries the offset cursor through more
// signatures without reducing real complexity.
public fun Pdol.Companion.parse(bytes: ByteArray): PdolResult {
    if (bytes.isEmpty()) return PdolResult.Err(PdolError.EmptyInput)
    val entries = mutableListOf<PdolEntry>()
    var offset = 0
    while (offset < bytes.size) {
        val tagResult = readTag(bytes, offset) ?: return PdolResult.Err(PdolError.IncompleteTag(offset))
        val (tag, tagEnd) = tagResult
        if (tagEnd >= bytes.size) return PdolResult.Err(PdolError.IncompleteLength(tagEnd))
        val lengthByte = bytes[tagEnd].toInt() and 0xFF
        if (lengthByte > 0x7F) return PdolResult.Err(PdolError.InvalidLength(tagEnd, bytes[tagEnd]))
        entries.add(PdolEntry(tag, lengthByte))
        offset = tagEnd + 1
    }
    return PdolResult.Ok(Pdol(entries))
}

/**
 * Parse a PDOL byte sequence, throwing [IllegalArgumentException] on
 * any structural rejection. Mirrors [Pdol.parse] for callers that
 * prefer exceptions at trusted boundaries.
 */
public fun Pdol.Companion.parseOrThrow(bytes: ByteArray): Pdol = when (val r = parse(bytes)) {
    is PdolResult.Ok -> r.pdol
    is PdolResult.Err -> throw IllegalArgumentException("Pdol.parse failed: ${r.error}")
}

// why: BER-TLV tag decoder for DOL contexts. First byte's low 5 bits != 0x1F
// → 1-byte tag. Otherwise multi-byte: subsequent bytes have high bit set
// (continuation), terminator has high bit clear. Bounded by Tag.MAX_BYTES (4)
// → return null past the limit so callers surface IncompleteTag rather than
// letting Tag.ofBytes throw.
private const val MAX_TAG_BYTES: Int = 4

@Suppress("ReturnCount", "CyclomaticComplexMethod")
// why: BER-TLV tag decode walks a state machine — bounds, single byte vs
// multi-byte, continuation byte, MAX_TAG_BYTES guard. Splitting forces the
// state to be ferried through more signatures without reducing complexity.
private fun readTag(bytes: ByteArray, offset: Int): Pair<Tag, Int>? {
    if (offset >= bytes.size) return null
    val first = bytes[offset].toInt() and 0xFF
    if ((first and 0x1F) != 0x1F) {
        return Tag.ofBytes(byteArrayOf(first.toByte())) to (offset + 1)
    }
    var end = offset + 1
    while (end < bytes.size && (bytes[end].toInt() and 0x80) != 0) {
        if (end - offset >= MAX_TAG_BYTES) return null
        end++
    }
    if (end >= bytes.size) return null
    end++
    if (end - offset > MAX_TAG_BYTES) return null
    val tagBytes = bytes.copyOfRange(offset, end)
    return Tag.ofBytes(tagBytes) to end
}
