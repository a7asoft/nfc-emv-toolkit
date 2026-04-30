package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.extract.internal.findFirst
import io.github.a7asoft.nfcemv.tlv.Strictness
import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import io.github.a7asoft.nfcemv.tlv.TlvDecoder
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseResult

/**
 * Parsed SELECT AID FCI response per EMV Book 1 §11.3.4 / Book 3 §6.5.5.
 *
 * Carries the application's PDOL bytes (`9F38` value) when the card
 * declares one (Visa qVSDC / Discover D-PAS / etc.). Mastercard
 * contactless typically omits `9F38`; in that case [pdolBytes] is
 * `null` and the reader should send the GPO command with empty PDOL
 * data (`83 00`).
 *
 * Only the `9F38` value is exposed in this milestone. Other FCI
 * proprietary template fields (`50` Application Label, `87` Priority,
 * `5F2D` Language, `9F12` Preferred Name, `BF0C` Issuer Discretionary)
 * are intentionally ignored — the reader does not act on them yet.
 */
public class SelectAidFci internal constructor(
    /** Raw `9F38` value bytes, or `null` if the FCI lacks the tag. */
    public val pdolBytes: ByteArray?,
) {

    @Suppress("CyclomaticComplexMethod")
    // why: structural equals over a nullable ByteArray field; auto-equals
    // would compare references, breaking value semantics.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectAidFci) return false
        return when {
            pdolBytes == null && other.pdolBytes == null -> true
            pdolBytes == null || other.pdolBytes == null -> false
            else -> pdolBytes.contentEquals(other.pdolBytes)
        }
    }

    override fun hashCode(): Int = pdolBytes?.contentHashCode() ?: 0

    override fun toString(): String = "SelectAidFci(pdol=${pdolBytes?.size ?: 0} bytes)"

    public companion object
}

/** Structural reasons [SelectAidFci.parse] can reject input. */
public sealed interface SelectAidFciError {
    /** Input was empty. */
    public data object EmptyInput : SelectAidFciError

    /** TLV decoder rejected the bytes. */
    public data class TlvDecodeFailed(val cause: TlvError) : SelectAidFciError

    /** Outer `6F` template was absent at the top level. */
    public data object MissingFciTemplate : SelectAidFciError
}

/** Two-case result mirroring `parse`/`parseOrThrow` convention. */
public sealed interface SelectAidFciResult {
    public data class Ok(val fci: SelectAidFci) : SelectAidFciResult
    public data class Err(val error: SelectAidFciError) : SelectAidFciResult
}

/**
 * Parse a SELECT AID FCI response (status word stripped) into a typed
 * [SelectAidFci]. Lenient TLV decoding mirrors [EmvParser.parse] —
 * real cards have been observed emitting non-minimal length encodings.
 */
@Suppress("ReturnCount", "CyclomaticComplexMethod")
// why: each return is a distinct typed FCI-rejection path (empty input,
// TLV decode failure, missing 6F template). Splitting moves the
// continuation through more signatures without reducing complexity.
public fun SelectAidFci.Companion.parse(bytes: ByteArray): SelectAidFciResult {
    if (bytes.isEmpty()) return SelectAidFciResult.Err(SelectAidFciError.EmptyInput)
    val tlvs = when (val parsed = TlvDecoder.parse(bytes, TlvOptions(strictness = Strictness.Lenient))) {
        is TlvParseResult.Ok -> parsed.tlvs
        is TlvParseResult.Err -> return SelectAidFciResult.Err(SelectAidFciError.TlvDecodeFailed(parsed.error))
    }
    val fciTemplate = findFirst(tlvs, TAG_FCI) as? Tlv.Constructed
        ?: return SelectAidFciResult.Err(SelectAidFciError.MissingFciTemplate)
    val pdol = (findFirst(listOf(fciTemplate), TAG_PDOL) as? Tlv.Primitive)?.copyValue()
    return SelectAidFciResult.Ok(SelectAidFci(pdolBytes = pdol))
}

/**
 * Parse a SELECT AID FCI response, throwing [IllegalArgumentException]
 * on any structural rejection. Mirrors [SelectAidFci.parse].
 */
public fun SelectAidFci.Companion.parseOrThrow(bytes: ByteArray): SelectAidFci =
    when (val r = parse(bytes)) {
        is SelectAidFciResult.Ok -> r.fci
        is SelectAidFciResult.Err -> throw IllegalArgumentException("SelectAidFci.parse failed: ${r.error}")
    }

private val TAG_FCI = Tag.fromHex("6F")
private val TAG_PDOL = Tag.fromHex("9F38")
