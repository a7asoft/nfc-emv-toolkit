package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.tlv.Strictness
import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import io.github.a7asoft.nfcemv.tlv.TlvDecoder
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseResult

/**
 * Decoded GET PROCESSING OPTIONS response per EMV Book 3 §6.5.8.
 *
 * Carries:
 * - [applicationInterchangeProfile] — EMV tag `82` raw value (always 2 bytes).
 * - [afl] — already-validated [Afl] derived from the response payload.
 * - [inlineTlv] — format-2 (`77` template) application-data children
 *   excluding AIP, AFL, and sensitive cryptographic tags (ARQC `9F26`,
 *   CID `9F27`, IAD `9F10`, ATC `9F36`, SDAD `9F4B`). Format-1 (`80`)
 *   returns an empty list. Callers iterating this list and invoking
 *   [Tlv.Primitive.copyValue] obtain raw bytes — for tag `57` (Track 2)
 *   those bytes carry an embedded PAN and MUST be treated as PCI scope.
 *   Prefer consuming the parsed [EmvCard] surfaces.
 *
 * Construction goes through [Gpo.parse] / [Gpo.parseOrThrow]. Both
 * response formats (tag `80` format-1 and tag `77` format-2) are handled.
 */
public class Gpo internal constructor(
    aip: ByteArray,
    public val afl: Afl,
    public val inlineTlv: List<Tlv>,
) {
    private val storedAip: ByteArray = aip.copyOf()

    /**
     * Application Interchange Profile (EMV Book 3 Annex C1) — a 2-byte
     * capability bitmap. Returns a fresh defensive copy per access so
     * callers cannot mutate the underlying bytes (mirrors the
     * `Tlv.Primitive` pattern from PR #1).
     */
    public val applicationInterchangeProfile: ByteArray
        get() = storedAip.copyOf()

    // why: hand-rolled `equals` mirrors the `Tlv.Primitive` pattern from
    // PR #1 — each early-return is a structural invariant check
    // (identity / type / AIP content / AFL / inline TLV).
    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Gpo) return false
        if (!storedAip.contentEquals(other.storedAip)) return false
        if (afl != other.afl) return false
        if (inlineTlv != other.inlineTlv) return false
        return true
    }

    override fun hashCode(): Int {
        var result = storedAip.contentHashCode()
        result = 31 * result + afl.hashCode()
        result = 31 * result + inlineTlv.hashCode()
        return result
    }

    override fun toString(): String =
        "Gpo(aip=${storedAip.size} bytes, afl=$afl, inlineTlv.size=${inlineTlv.size})"

    public companion object
}

/**
 * A typed reason [Gpo.parse] rejected a response.
 *
 * No variant embeds value bytes; structural metadata only.
 */
public sealed interface GpoError {
    public data object EmptyInput : GpoError
    public data class TlvDecodeFailed(val cause: TlvError) : GpoError
    public data object UnknownTemplate : GpoError
    public data object MissingAip : GpoError
    public data class InvalidAipLength(val byteCount: Int) : GpoError
    public data class AflRejected(val cause: AflError) : GpoError
}

/** Outcome of [Gpo.parse]. */
public sealed interface GpoResult {
    public data class Ok(val gpo: Gpo) : GpoResult
    public data class Err(val error: GpoError) : GpoResult
}

private val GPO_TLV_OPTIONS = TlvOptions(strictness = Strictness.Lenient)
private val TAG_FORMAT_1 = Tag.fromHex("80")
private val TAG_FORMAT_2 = Tag.fromHex("77")
private val TAG_AIP = Tag.fromHex("82")
private val TAG_AFL = Tag.fromHex("94")
private const val AIP_BYTES: Int = 2

// why: per docs/threat-model.md §19+§28, ARQC / CID / IAD / ATC / SDAD
// are single-use cryptographic context and must never reach a public
// List<Tlv> surface where Tlv.Primitive.copyValue() would expose raw
// bytes. AIP (82) and AFL (94) are already extracted to dedicated
// typed fields. Excluding them all from inlineTlv keeps the public
// surface to display-safe application data only.
private val INLINE_TLV_EXCLUSIONS: Set<Tag> = setOf(
    TAG_AIP,                   // 82  — extracted to Gpo.applicationInterchangeProfile
    TAG_AFL,                   // 94  — extracted to Gpo.afl
    Tag.fromHex("9F26"),       // ARQC
    Tag.fromHex("9F27"),       // Cryptogram Information Data (CID)
    Tag.fromHex("9F10"),       // Issuer Application Data (IAD)
    Tag.fromHex("9F36"),       // Application Transaction Counter (ATC)
    Tag.fromHex("9F4B"),       // Signed Dynamic Application Data (SDAD)
)

/**
 * Parse [bytes] (the data field of a `GET PROCESSING OPTIONS` response,
 * status-word stripped) into a typed [GpoResult].
 *
 * Format 1 (tag `80`): `80 [len] [AIP 2 bytes] [AFL ...]`.
 * Format 2 (tag `77`): `77 [len] [...nested TLVs containing 82 and 94]`.
 */
@Suppress(
    // why: each return is a distinct spec check (empty / decode-fail /
    // unknown template / format dispatch). Same idiom as `Pan.parse`.
    "ReturnCount",
    "CyclomaticComplexMethod",
)
public fun Gpo.Companion.parse(bytes: ByteArray): GpoResult {
    if (bytes.isEmpty()) return GpoResult.Err(GpoError.EmptyInput)
    val nodes = when (val decoded = TlvDecoder.parse(bytes, GPO_TLV_OPTIONS)) {
        is TlvParseResult.Ok -> decoded.tlvs
        is TlvParseResult.Err -> return GpoResult.Err(GpoError.TlvDecodeFailed(decoded.error))
    }
    val outer = nodes.firstOrNull() ?: return GpoResult.Err(GpoError.UnknownTemplate)
    return when {
        outer is Tlv.Primitive && outer.tag == TAG_FORMAT_1 -> parseFormat1(outer)
        outer is Tlv.Constructed && outer.tag == TAG_FORMAT_2 -> parseFormat2(outer)
        else -> GpoResult.Err(GpoError.UnknownTemplate)
    }
}

/** Parse [bytes] into a [Gpo], or throw [IllegalArgumentException]. */
public fun Gpo.Companion.parseOrThrow(bytes: ByteArray): Gpo =
    when (val result = parse(bytes)) {
        is GpoResult.Ok -> result.gpo
        is GpoResult.Err -> throw IllegalArgumentException(messageForGpoError(result.error))
    }

private fun parseFormat1(node: Tlv.Primitive): GpoResult {
    val payload = node.copyValue()
    if (payload.size < AIP_BYTES) return GpoResult.Err(GpoError.InvalidAipLength(payload.size))
    val aip = payload.copyOfRange(0, AIP_BYTES)
    val aflBytes = payload.copyOfRange(AIP_BYTES, payload.size)
    return composeGpo(aip, aflBytes, inlineTlv = emptyList())
}

// why: per EMV Contactless Book C-3 (Visa kernel-3) §5.4 GPO response
// processing — when AIP indicates MSD-only mode and application data
// is delivered inline in the format-2 template, AFL is OPTIONAL.
// Treating absent AFL as Afl(emptyList()) lets MSD-only flows succeed
// without READ RECORD, matching the Visa Chase real-card capture
// (issue #59).
@Suppress("ReturnCount", "CyclomaticComplexMethod")
private fun parseFormat2(node: Tlv.Constructed): GpoResult {
    val aipNode = findChild(node, TAG_AIP) ?: return GpoResult.Err(GpoError.MissingAip)
    val aip = aipNode.copyValue()
    if (aip.size != AIP_BYTES) return GpoResult.Err(GpoError.InvalidAipLength(aip.size))
    val aflNode = findChild(node, TAG_AFL)
    val aflBytes = aflNode?.copyValue() ?: ByteArray(0)
    val inline = node.children.filter { it.tag !in INLINE_TLV_EXCLUSIONS }
    return composeGpo(aip, aflBytes, inline)
}

// why: empty-AFL fast-path is a spec invariant (EMV Book 3 §10 / EMVCo
// Book C-3 §5.4 — AFL is OPTIONAL when format-2 carries application
// data inline). The `when` arms then cover the AFL parse outcomes.
@Suppress("CyclomaticComplexMethod")
private fun composeGpo(aip: ByteArray, aflBytes: ByteArray, inlineTlv: List<Tlv>): GpoResult {
    if (aflBytes.isEmpty()) return GpoResult.Ok(Gpo(aip, Afl(emptyList()), inlineTlv))
    return when (val parsed = Afl.parse(aflBytes)) {
        is AflResult.Ok -> GpoResult.Ok(Gpo(aip, parsed.afl, inlineTlv))
        is AflResult.Err -> GpoResult.Err(GpoError.AflRejected(parsed.error))
    }
}

// why: scan-and-return loop; CC includes the type-test plus tag-match
// guard. Splitting into a helper would just ferry `parent` and `tag`
// through a second signature.
@Suppress("CyclomaticComplexMethod")
private fun findChild(parent: Tlv.Constructed, tag: Tag): Tlv.Primitive? {
    for (child in parent.children) {
        if (child is Tlv.Primitive && child.tag == tag) return child
    }
    return null
}

// why: exhaustive `when` over the sealed [GpoError] catalogue (CLAUDE.md §3.2).
@Suppress("CyclomaticComplexMethod")
private fun messageForGpoError(error: GpoError): String = when (error) {
    GpoError.EmptyInput -> "GPO input is empty"
    is GpoError.TlvDecodeFailed -> "GPO TLV decode failed"
    GpoError.UnknownTemplate -> "GPO outer template is neither tag 80 nor tag 77"
    GpoError.MissingAip -> "GPO format-2 missing AIP (tag 82)"
    is GpoError.InvalidAipLength -> "GPO AIP length must be 2 bytes, was ${error.byteCount}"
    is GpoError.AflRejected -> "GPO AFL rejected"
}
