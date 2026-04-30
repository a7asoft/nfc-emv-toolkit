package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.brand.BrandResolver
import io.github.a7asoft.nfcemv.extract.internal.ExtractResult
import io.github.a7asoft.nfcemv.extract.internal.extractAid
import io.github.a7asoft.nfcemv.extract.internal.extractApplicationLabel
import io.github.a7asoft.nfcemv.extract.internal.extractCardholderName
import io.github.a7asoft.nfcemv.extract.internal.extractExpiry
import io.github.a7asoft.nfcemv.extract.internal.extractPan
import io.github.a7asoft.nfcemv.extract.internal.extractTrack2
import io.github.a7asoft.nfcemv.extract.internal.findFirst
import io.github.a7asoft.nfcemv.tlv.Strictness
import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import io.github.a7asoft.nfcemv.tlv.TlvDecoder
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseResult
import kotlinx.datetime.YearMonth
import kotlin.jvm.JvmName

/**
 * Top-level EMV card composer.
 *
 * Decodes a list of APDU response data fields (with SW1 SW2 already
 * stripped by the transport layer), recursively searches the resulting
 * TLV trees for the required (`4F`, `5A`, `5F24`) and optional (`5F20`,
 * `50`, `57`) tags, decodes each via per-format extractors, and
 * composes an [EmvCard] with the brand resolved through
 * [BrandResolver.resolveBrand].
 *
 * Two API styles:
 * - [parse] returns a sealed [EmvCardResult] (mirrors `Pan.parse`,
 *   `Track2.parse`, `TlvDecoder.parse`).
 * - [parseOrThrow] throws [IllegalArgumentException] on the first
 *   detected violation.
 *
 * ### TLV decoding mode
 *
 * Hardcoded to [Strictness.Lenient]: real cards have been observed
 * emitting non-minimal length encodings, and a strict reader would
 * falsely reject them. Caller-overridable strictness is intentionally
 * out of scope for v0.1.x; if a diagnostic / lab use case needs strict
 * mode, decode externally with [TlvDecoder.parse] first and feed the
 * result through bespoke extractors.
 *
 * ### Out-of-scope behaviors (deferred — caller responsibility)
 *
 * - **PSE / PPSE flow.** Caller MUST supply only one application's
 *   records. Mixing record streams from multiple applications causes
 *   `findFirst` to cross-pollinate fields between unrelated AIDs.
 * - **Multiple AIDs / Application Priority Indicator (tag `87`).**
 *   When more than one `4F` is present, the first DFS-order match
 *   wins. EMV Book 1 §12.4 priority handling is not implemented.
 * - **Tag `9F6B` Mastercard Track 2 fallback.** When tag `57` is
 *   absent, the parser does NOT consult `9F6B`; `track2` becomes
 *   `null`. Callers handling Mastercard contactless must rewrite
 *   `9F6B` to `57` upstream.
 * - **PAN agreement between `5A` and `57`.** EMV mandates the same
 *   PAN in both; this parser does NOT cross-check. A mismatch is
 *   silently kept (caller responsibility to verify).
 * - **Two-digit year mapping.** Tags `5F24` (and `Track2.expiry`)
 *   are mapped `YY ⇒ 20YY` (21st-century convention). Cards
 *   expiring 2100+ would mis-map; out of scope for v0.1.x.
 *
 * Each of these has an explicit pinning test (or a documentation note)
 * so a future implementation does not silently regress.
 */
public object EmvParser {

    private val LENIENT: TlvOptions = TlvOptions(strictness = Strictness.Lenient)
    private val TAG_AID = Tag.fromHex("4F")
    private val TAG_PAN = Tag.fromHex("5A")
    private val TAG_EXPIRY = Tag.fromHex("5F24")
    private val TAG_CARDHOLDER = Tag.fromHex("5F20")
    private val TAG_LABEL = Tag.fromHex("50")
    private val TAG_TRACK2 = Tag.fromHex("57")

    /**
     * Parse [apduResponses] (each ByteArray = one APDU response data
     * field with SW1 SW2 already stripped) into an [EmvCardResult].
     *
     * Uses the `4F` (AID) tag found inside the records. Real EMV cards
     * generally do NOT include `4F` in READ RECORD records — `4F` lives
     * in the PPSE / SELECT AID FCI responses. Callers reading from a
     * physical card should use the [parse] overload that accepts an
     * [Aid] explicitly. This 1-arg overload remains for callers feeding
     * synthetic fixtures (or older transcripts) where `4F` is inline.
     */
    public fun parse(apduResponses: List<ByteArray>): EmvCardResult =
        parseInternal(injectedAid = null, apduResponses = apduResponses)

    /**
     * Parse [apduResponses] using [aid] as the application identifier.
     *
     * The injected [aid] takes precedence over any `4F` tag found in
     * the records. This overload matches the real-card flow: the
     * reader extracts the AID from the PPSE / SELECT AID FCI response
     * and passes it here directly.
     */
    public fun parse(aid: Aid, apduResponses: List<ByteArray>): EmvCardResult =
        parseInternal(injectedAid = aid, apduResponses = apduResponses)

    /**
     * Parse pre-decoded [tlvNodes] (the TLV union of all card data
     * sources — typically GPO body inline tags concatenated with the
     * decoded READ RECORD bodies) using [aid] as the application
     * identifier.
     *
     * This is the canonical entry point. The
     * [parse]\(aid, apduResponses) overload exists for callers that
     * still hold raw APDU response byte arrays; it decodes each then
     * delegates here.
     *
     * Returns [EmvCardResult.Err]\([EmvCardError.EmptyInput]) when
     * [tlvNodes] is empty.
     */
    @JvmName("parseTlv")
    public fun parse(aid: Aid, tlvNodes: List<Tlv>): EmvCardResult {
        if (tlvNodes.isEmpty()) return EmvCardResult.Err(EmvCardError.EmptyInput)
        return parseFromNodes(injectedAid = aid, nodes = tlvNodes)
    }

    /**
     * Parse [apduResponses] into an [EmvCard], or throw
     * [IllegalArgumentException] on the first detected violation.
     */
    public fun parseOrThrow(apduResponses: List<ByteArray>): EmvCard =
        when (val result = parse(apduResponses)) {
            is EmvCardResult.Ok -> result.card
            is EmvCardResult.Err -> throw IllegalArgumentException(messageFor(result.error))
        }

    /**
     * Parse [apduResponses] using [aid] as the application identifier,
     * or throw [IllegalArgumentException]. Mirrors [parseOrThrow] for
     * the AID-injection overload.
     */
    public fun parseOrThrow(aid: Aid, apduResponses: List<ByteArray>): EmvCard =
        when (val result = parse(aid, apduResponses)) {
            is EmvCardResult.Ok -> result.card
            is EmvCardResult.Err -> throw IllegalArgumentException(messageFor(result.error))
        }

    /**
     * Parse [tlvNodes] using [aid] or throw [IllegalArgumentException].
     * Mirrors [parseOrThrow] for the TLV-native overload.
     */
    @JvmName("parseOrThrowTlv")
    public fun parseOrThrow(aid: Aid, tlvNodes: List<Tlv>): EmvCard =
        when (val result = parse(aid, tlvNodes)) {
            is EmvCardResult.Ok -> result.card
            is EmvCardResult.Err -> throw IllegalArgumentException(messageFor(result.error))
        }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    // why: thin adapter — empty-input gate + decode + delegate. Each
    // return is a distinct typed exit (EmptyInput / TlvDecodeFailed /
    // delegate result); splitting further moves the gate through more
    // signatures without reducing real complexity.
    private fun parseInternal(injectedAid: Aid?, apduResponses: List<ByteArray>): EmvCardResult {
        if (apduResponses.isEmpty()) return EmvCardResult.Err(EmvCardError.EmptyInput)
        val nodes = when (val outcome = decodeAll(apduResponses)) {
            is DecodeOutcome.Ok -> outcome.nodes
            is DecodeOutcome.Err -> return EmvCardResult.Err(outcome.error)
        }
        return parseFromNodes(injectedAid, nodes)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    // why: each return is a distinct EMV parse step (track2 / required /
    // optional). Splitting forces ferrying nodes through more signatures
    // without reducing real complexity.
    private fun parseFromNodes(injectedAid: Aid?, nodes: List<Tlv>): EmvCardResult {
        val track2 = when (val r = extractTrack2Once(nodes)) {
            Track2Outcome.Absent -> null
            is Track2Outcome.Present -> r.track2
            is Track2Outcome.Failed -> return EmvCardResult.Err(r.error)
        }
        val required = when (val r = extractRequiredFields(injectedAid, nodes, track2)) {
            is RequiredOutcome.Ok -> r.fields
            is RequiredOutcome.Err -> return EmvCardResult.Err(r.error)
        }
        val optional = when (val r = extractOptionalFields(nodes, track2)) {
            is OptionalOutcome.Ok -> r.fields
            is OptionalOutcome.Err -> return EmvCardResult.Err(r.error)
        }
        return EmvCardResult.Ok(composeCard(required, optional))
    }

    private fun composeCard(required: RequiredFields, optional: OptionalFields): EmvCard {
        val brand = BrandResolver.resolveBrand(aid = required.aid, pan = required.pan)
        return EmvCard(
            pan = required.pan, expiry = required.expiry,
            cardholderName = optional.cardholderName, brand = brand,
            applicationLabel = optional.applicationLabel,
            track2 = optional.track2, aid = required.aid,
        )
    }

    // ----- private orchestrators -----

    private data class RequiredFields(val aid: Aid, val pan: Pan, val expiry: YearMonth)

    private data class OptionalFields(
        val cardholderName: String?,
        val applicationLabel: String?,
        val track2: Track2?,
    )

    private sealed interface DecodeOutcome {
        data class Ok(val nodes: List<Tlv>) : DecodeOutcome
        data class Err(val error: EmvCardError) : DecodeOutcome
    }

    private sealed interface RequiredOutcome {
        data class Ok(val fields: RequiredFields) : RequiredOutcome
        data class Err(val error: EmvCardError) : RequiredOutcome
    }

    private sealed interface OptionalOutcome {
        data class Ok(val fields: OptionalFields) : OptionalOutcome
        data class Err(val error: EmvCardError) : OptionalOutcome
    }

    private sealed interface AidOutcome {
        data class Ok(val aid: Aid) : AidOutcome
        data class Err(val error: EmvCardError) : AidOutcome
    }

    private sealed interface PanOutcome {
        data class Ok(val pan: Pan) : PanOutcome
        data class Err(val error: EmvCardError) : PanOutcome
    }

    private sealed interface ExpiryOutcome {
        data class Ok(val expiry: YearMonth) : ExpiryOutcome
        data class Err(val error: EmvCardError) : ExpiryOutcome
    }

    private sealed interface Track2Outcome {
        data object Absent : Track2Outcome
        data class Present(val track2: Track2) : Track2Outcome
        data class Failed(val error: EmvCardError) : Track2Outcome
    }

    @Suppress("CyclomaticComplexMethod")
    // why: 4-branch sealed dispatch (find / null / Ok / Err). Splitting moves
    // the AID node lookup through more signatures without reducing complexity.
    private fun resolveAid(nodes: List<Tlv>): AidOutcome {
        val aidNode = findFirst(nodes, TAG_AID) as? Tlv.Primitive
            ?: return AidOutcome.Err(EmvCardError.MissingRequiredTag("4F"))
        return when (val r = extractAid(aidNode)) {
            is ExtractResult.Ok -> AidOutcome.Ok(r.value)
            is ExtractResult.Err -> AidOutcome.Err(r.error)
        }
    }

    private fun decodeAll(apduResponses: List<ByteArray>): DecodeOutcome {
        val collected = mutableListOf<Tlv>()
        for (response in apduResponses) {
            when (val parsed = TlvDecoder.parse(response, LENIENT)) {
                is TlvParseResult.Ok -> collected.addAll(parsed.tlvs)
                is TlvParseResult.Err -> return DecodeOutcome.Err(EmvCardError.TlvDecodeFailed(parsed.error))
            }
        }
        return DecodeOutcome.Ok(collected)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    // why: each return surfaces a distinct typed required-field rejection
    // (missing AID / PAN / expiry, plus their per-extractor errors). PAN
    // and expiry both accept Track 2 fallbacks (see resolvePan, resolveExpiry,
    // and issue #59).
    private fun extractRequiredFields(
        injectedAid: Aid?,
        nodes: List<Tlv>,
        track2: Track2?,
    ): RequiredOutcome {
        val aid = injectedAid ?: when (val r = resolveAid(nodes)) {
            is AidOutcome.Ok -> r.aid
            is AidOutcome.Err -> return RequiredOutcome.Err(r.error)
        }
        val pan = when (val r = resolvePan(nodes, track2)) {
            is PanOutcome.Ok -> r.pan
            is PanOutcome.Err -> return RequiredOutcome.Err(r.error)
        }
        val expiry = when (val r = resolveExpiry(nodes, track2)) {
            is ExpiryOutcome.Ok -> r.expiry
            is ExpiryOutcome.Err -> return RequiredOutcome.Err(r.error)
        }
        return RequiredOutcome.Ok(RequiredFields(aid, pan, expiry))
    }

    /**
     * Resolve PAN with the EMV-canonical fallback chain:
     *
     * 1. If standalone tag `5A` is present, decode it via [extractPan].
     * 2. Else if [track2] was successfully parsed from tag `57`, use
     *    its embedded `pan`. Per IDTECH knowledge base "Why aren't tags
     *    57 and 5A returned" and the EMV Book 3 record layout, tag
     *    `5A` is OPTIONAL when tag `57` is present — the embedded PAN
     *    is the canonical source.
     * 3. Else fail `MissingRequiredTag(5A)`.
     */
    @Suppress("CyclomaticComplexMethod")
    // why: 5-branch fallback chain (5A present / extractor Ok / extractor
    // Err / track2 fallback / both absent). Each branch is a distinct
    // EMV-canonical PAN source; splitting moves the chain through more
    // signatures without reducing complexity.
    private fun resolvePan(nodes: List<Tlv>, track2: Track2?): PanOutcome {
        val panNode = findFirst(nodes, TAG_PAN) as? Tlv.Primitive
        if (panNode != null) {
            return when (val r = extractPan(panNode)) {
                is ExtractResult.Ok -> PanOutcome.Ok(r.value)
                is ExtractResult.Err -> PanOutcome.Err(r.error)
            }
        }
        if (track2 != null) return PanOutcome.Ok(track2.pan)
        return PanOutcome.Err(EmvCardError.MissingRequiredTag("5A"))
    }

    /**
     * Resolve expiry with the EMV-canonical fallback chain:
     *
     * 1. If standalone tag `5F 24` is present, decode it via [extractExpiry].
     * 2. Else if [track2] was successfully parsed from tag `57`, use
     *    its [Track2.expiry]. Per ISO 7813 + EMV Book 3, the expiry in
     *    Track 2 (positions YYMM after the `D` separator) is canonical
     *    when standalone `5F 24` is absent.
     * 3. Else fail `MissingRequiredTag(5F24)`.
     */
    @Suppress("CyclomaticComplexMethod")
    // why: 5-branch fallback chain (5F24 present / extractor Ok / extractor
    // Err / track2 fallback / both absent). Each branch is a distinct
    // EMV-canonical expiry source; splitting moves the chain through more
    // signatures without reducing complexity. Mirrors resolvePan exactly.
    private fun resolveExpiry(nodes: List<Tlv>, track2: Track2?): ExpiryOutcome {
        val expiryNode = findFirst(nodes, TAG_EXPIRY) as? Tlv.Primitive
        if (expiryNode != null) {
            return when (val r = extractExpiry(expiryNode)) {
                is ExtractResult.Ok -> ExpiryOutcome.Ok(r.value)
                is ExtractResult.Err -> ExpiryOutcome.Err(r.error)
            }
        }
        if (track2 != null) return ExpiryOutcome.Ok(track2.expiry)
        return ExpiryOutcome.Err(EmvCardError.MissingRequiredTag("5F24"))
    }

    private fun extractOptionalFields(
        nodes: List<Tlv>,
        track2: Track2?,
    ): OptionalOutcome {
        val cardholderName = (findFirst(nodes, TAG_CARDHOLDER) as? Tlv.Primitive)?.let { extractCardholderName(it) }
        val applicationLabel = (findFirst(nodes, TAG_LABEL) as? Tlv.Primitive)?.let { extractApplicationLabel(it) }
        return OptionalOutcome.Ok(OptionalFields(cardholderName, applicationLabel, track2))
    }

    /**
     * Pre-parse Track 2 (tag `57`) once so both required-field PAN
     * fallback and optional-field exposure see the same instance.
     * Returns a [Track2Outcome] sealed result rather than a nullable
     * to surface decode errors as `EmvCardError.Track2Rejected`.
     */
    @Suppress("CyclomaticComplexMethod")
    // why: 4-branch sealed dispatch (find / null / Ok / Err), mirroring
    // resolveAid. Splitting moves the Track 2 node lookup through more
    // signatures without reducing complexity.
    private fun extractTrack2Once(nodes: List<Tlv>): Track2Outcome {
        val node = findFirst(nodes, TAG_TRACK2) as? Tlv.Primitive
            ?: return Track2Outcome.Absent
        return when (val r = extractTrack2(node)) {
            is ExtractResult.Ok -> Track2Outcome.Present(r.value)
            is ExtractResult.Err -> Track2Outcome.Failed(r.error)
        }
    }

    // ----- IAE message rendering -----

    private fun messageFor(error: EmvCardError): String = when (error) {
        EmvCardError.EmptyInput -> "EmvCard input is empty"
        is EmvCardError.TlvDecodeFailed -> "EmvCard TLV decode failed: ${describeTlvError(error.cause)}"
        is EmvCardError.MissingRequiredTag -> "EmvCard missing required tag ${error.tagHex}"
        is EmvCardError.PanRejected -> "EmvCard PAN rejected: ${describePanError(error.cause)}"
        is EmvCardError.Track2Rejected -> "EmvCard Track 2 rejected: ${describeTrack2Error(error.cause)}"
        is EmvCardError.InvalidExpiryFormat -> "EmvCard expiry malformed: ${error.nibbleCount} nibbles"
        is EmvCardError.InvalidExpiryMonth -> "EmvCard expiry month out of range: ${error.month}"
        is EmvCardError.InvalidAid -> "EmvCard AID byte length out of range: ${error.byteCount}"
        is EmvCardError.MalformedPanNibble -> "EmvCard PAN malformed BCD nibble at offset ${error.offset}"
    }

    private fun describePanError(cause: PanError): String = when (cause) {
        is PanError.LengthOutOfRange -> "LengthOutOfRange"
        PanError.NonDigitCharacters -> "NonDigitCharacters"
        PanError.LuhnCheckFailed -> "LuhnCheckFailed"
    }

    private fun describeTrack2Error(cause: Track2Error): String = when (cause) {
        Track2Error.EmptyInput -> "EmptyInput"
        Track2Error.MissingSeparator -> "MissingSeparator"
        is Track2Error.PanRejected -> "PanRejected:${describePanError(cause.cause)}"
        is Track2Error.ExpiryTooShort -> "ExpiryTooShort"
        is Track2Error.InvalidExpiryMonth -> "InvalidExpiryMonth"
        Track2Error.ServiceCodeTooShort -> "ServiceCodeTooShort"
        is Track2Error.MalformedBcdNibble -> "MalformedBcdNibble"
        Track2Error.MalformedFPadding -> "MalformedFPadding"
    }

    private fun describeTlvError(cause: TlvError): String = when (cause) {
        is TlvError.UnexpectedEof -> "UnexpectedEof"
        is TlvError.IndefiniteLengthForbidden -> "IndefiniteLengthForbidden"
        is TlvError.InvalidLengthOctet -> "InvalidLengthOctet"
        is TlvError.IncompleteTag -> "IncompleteTag"
        is TlvError.TagTooLong -> "TagTooLong"
        is TlvError.NonMinimalTagEncoding -> "NonMinimalTagEncoding"
        is TlvError.NonMinimalLengthEncoding -> "NonMinimalLengthEncoding"
        is TlvError.ChildrenLengthMismatch -> "ChildrenLengthMismatch"
        is TlvError.MaxDepthExceeded -> "MaxDepthExceeded"
        is TlvError.LengthOverflow -> "LengthOverflow"
    }
}
