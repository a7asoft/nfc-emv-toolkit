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
     */
    public fun parse(apduResponses: List<ByteArray>): EmvCardResult {
        if (apduResponses.isEmpty()) return EmvCardResult.Err(EmvCardError.EmptyInput)
        val nodes = when (val outcome = decodeAll(apduResponses)) {
            is DecodeOutcome.Ok -> outcome.nodes
            is DecodeOutcome.Err -> return EmvCardResult.Err(outcome.error)
        }
        val required = when (val r = extractRequiredFields(nodes)) {
            is RequiredOutcome.Ok -> r.fields
            is RequiredOutcome.Err -> return EmvCardResult.Err(r.error)
        }
        val optional = when (val r = extractOptionalFields(nodes)) {
            is OptionalOutcome.Ok -> r.fields
            is OptionalOutcome.Err -> return EmvCardResult.Err(r.error)
        }
        val brand = BrandResolver.resolveBrand(aid = required.aid, pan = required.pan)
        return EmvCardResult.Ok(
            EmvCard(
                pan = required.pan, expiry = required.expiry,
                cardholderName = optional.cardholderName, brand = brand,
                applicationLabel = optional.applicationLabel,
                track2 = optional.track2, aid = required.aid,
            ),
        )
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

    private fun extractRequiredFields(nodes: List<Tlv>): RequiredOutcome {
        val aidNode = findFirst(nodes, TAG_AID) as? Tlv.Primitive
            ?: return RequiredOutcome.Err(EmvCardError.MissingRequiredTag("4F"))
        val aid = when (val r = extractAid(aidNode)) {
            is ExtractResult.Ok -> r.value
            is ExtractResult.Err -> return RequiredOutcome.Err(r.error)
        }
        val panNode = findFirst(nodes, TAG_PAN) as? Tlv.Primitive
            ?: return RequiredOutcome.Err(EmvCardError.MissingRequiredTag("5A"))
        val pan = when (val r = extractPan(panNode)) {
            is ExtractResult.Ok -> r.value
            is ExtractResult.Err -> return RequiredOutcome.Err(r.error)
        }
        val expiryNode = findFirst(nodes, TAG_EXPIRY) as? Tlv.Primitive
            ?: return RequiredOutcome.Err(EmvCardError.MissingRequiredTag("5F24"))
        val expiry = when (val r = extractExpiry(expiryNode)) {
            is ExtractResult.Ok -> r.value
            is ExtractResult.Err -> return RequiredOutcome.Err(r.error)
        }
        return RequiredOutcome.Ok(RequiredFields(aid, pan, expiry))
    }

    private fun extractOptionalFields(nodes: List<Tlv>): OptionalOutcome {
        val cardholderName = (findFirst(nodes, TAG_CARDHOLDER) as? Tlv.Primitive)?.let { extractCardholderName(it) }
        val applicationLabel = (findFirst(nodes, TAG_LABEL) as? Tlv.Primitive)?.let { extractApplicationLabel(it) }
        val track2Node = findFirst(nodes, TAG_TRACK2) as? Tlv.Primitive
        val track2 = if (track2Node == null) {
            null
        } else {
            when (val r = extractTrack2(track2Node)) {
                is ExtractResult.Ok -> r.value
                is ExtractResult.Err -> return OptionalOutcome.Err(r.error)
            }
        }
        return OptionalOutcome.Ok(OptionalFields(cardholderName, applicationLabel, track2))
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
