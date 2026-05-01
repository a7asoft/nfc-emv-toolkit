package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.extract.internal.findFirst
import io.github.a7asoft.nfcemv.extract.internal.firstChildByTag
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
 * Carries:
 * - [pdolBytes] — raw `9F38` value when the card declares one (Visa
 *   qVSDC / Discover D-PAS / etc.). `null` when the card omits `9F38`
 *   (typical Mastercard contactless); reader sends GPO with empty PDOL
 *   data (`83 00`).
 * - [inlineTlv] — `A5` (FCI Proprietary Template) children excluding
 *   `9F38` (already consumed). Typically carries `50` Application
 *   Label, `87` Application Priority Indicator, `5F 2D` Language
 *   Preference, `9F 11` Issuer Code Table Index, `9F 12` Application
 *   Preferred Name, `BF 0C` Issuer Discretionary Data. The reader
 *   unions this list with the GPO body inline TLV and the AFL READ
 *   RECORD bodies before calling [EmvParser.parse]. Empty when the FCI
 *   omits the `A5` template entirely (rare) or when `A5` carries only
 *   `9F 38`. Callers iterating this list and invoking
 *   [Tlv.Primitive.copyValue] obtain raw bytes — none of the standard
 *   FCI tags carry PAN, but bespoke issuer extensions may; treat as
 *   read-only metadata.
 */
public class SelectAidFci internal constructor(
    /** Raw `9F38` value bytes, or `null` if the FCI lacks the tag. */
    public val pdolBytes: ByteArray?,
    /**
     * The `A5` (FCI Proprietary Template) children excluding `9F38`
     * (which was already consumed by the GPO PDOL response builder).
     *
     * Typically carries `50` (Application Label), `87` (Application
     * Priority Indicator), `5F 2D` (Language Preference), `9F 11`
     * (Issuer Code Table Index), `9F 12` (Application Preferred Name),
     * `BF 0C` (Issuer Discretionary Data). The reader unions this list
     * with the GPO body's inline TLV and the AFL READ RECORD bodies
     * before passing them to [EmvParser.parse].
     *
     * Empty when the FCI omits the `A5` template entirely (rare) or
     * when the `A5` template carries only `9F 38`.
     */
    public val inlineTlv: List<Tlv>,
) {

    @Suppress("CyclomaticComplexMethod")
    // why: structural equals over a nullable ByteArray + List<Tlv>; auto-equals
    // would compare ByteArray references, breaking value semantics.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectAidFci) return false
        if (inlineTlv != other.inlineTlv) return false
        return when {
            pdolBytes == null && other.pdolBytes == null -> true
            pdolBytes == null || other.pdolBytes == null -> false
            else -> pdolBytes.contentEquals(other.pdolBytes)
        }
    }

    override fun hashCode(): Int {
        var result = pdolBytes?.contentHashCode() ?: 0
        result = 31 * result + inlineTlv.hashCode()
        return result
    }

    override fun toString(): String =
        "SelectAidFci(pdol=${pdolBytes?.size ?: 0} bytes, inlineTlv.size=${inlineTlv.size})"

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
    val proprietary = firstChildByTag(fciTemplate, TAG_FCI_PROPRIETARY) as? Tlv.Constructed
    val pdol = (findFirst(listOf(fciTemplate), TAG_PDOL) as? Tlv.Primitive)?.copyValue()
    val inline = proprietary?.children?.filter { it.tag != TAG_PDOL } ?: emptyList()
    return SelectAidFciResult.Ok(SelectAidFci(pdolBytes = pdol, inlineTlv = inline))
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
private val TAG_FCI_PROPRIETARY = Tag.fromHex("A5")
private val TAG_PDOL = Tag.fromHex("9F38")
