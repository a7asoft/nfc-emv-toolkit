package io.github.a7asoft.nfcemv.extract

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
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseResult

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
 * TLV decoding uses lenient strictness so cards that emit non-minimal
 * length encodings (rare but observed in the wild) still parse.
 *
 * Two API styles:
 * - [parse] returns a sealed [EmvCardResult] (mirrors `Pan.parse`,
 *   `Track2.parse`, `TlvDecoder.parse`).
 * - [parseOrThrow] throws [IllegalArgumentException] on the first
 *   detected violation.
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
     * Parse [apduResponses] into an [EmvCardResult].
     *
     * Empty input surfaces as [EmvCardError.EmptyInput]. TLV decode
     * failures, missing required tags, or per-field validation errors
     * surface as their respective [EmvCardError] variants without
     * embedding any raw card-derived bytes.
     */
    public fun parse(apduResponses: List<ByteArray>): EmvCardResult {
        if (apduResponses.isEmpty()) return EmvCardResult.Err(EmvCardError.EmptyInput)

        val nodes = when (val outcome = decodeAll(apduResponses)) {
            is DecodeOutcome.Err -> return EmvCardResult.Err(outcome.error)
            is DecodeOutcome.Ok -> outcome.nodes
        }

        val aidNode = findRequired(nodes, TAG_AID) ?: return missing("4F")
        val aid = when (val r = extractAid(aidNode)) {
            is ExtractResult.Ok -> r.value
            is ExtractResult.Err -> return EmvCardResult.Err(r.error)
        }

        val panNode = findRequired(nodes, TAG_PAN) ?: return missing("5A")
        val pan = when (val r = extractPan(panNode)) {
            is ExtractResult.Ok -> r.value
            is ExtractResult.Err -> return EmvCardResult.Err(r.error)
        }

        val expiryNode = findRequired(nodes, TAG_EXPIRY) ?: return missing("5F24")
        val expiry = when (val r = extractExpiry(expiryNode)) {
            is ExtractResult.Ok -> r.value
            is ExtractResult.Err -> return EmvCardResult.Err(r.error)
        }

        val cardholderName = (findFirst(nodes, TAG_CARDHOLDER) as? Tlv.Primitive)
            ?.let { extractCardholderName(it) }
        val applicationLabel = (findFirst(nodes, TAG_LABEL) as? Tlv.Primitive)
            ?.let { extractApplicationLabel(it) }

        val track2Node = findFirst(nodes, TAG_TRACK2) as? Tlv.Primitive
        val track2 = if (track2Node == null) {
            null
        } else {
            when (val r = extractTrack2(track2Node)) {
                is ExtractResult.Ok -> r.value
                is ExtractResult.Err -> return EmvCardResult.Err(r.error)
            }
        }

        val brand = BrandResolver.resolveBrand(aid = aid, pan = pan)

        return EmvCardResult.Ok(
            EmvCard(
                pan = pan, expiry = expiry, cardholderName = cardholderName,
                brand = brand, applicationLabel = applicationLabel,
                track2 = track2, aid = aid,
            ),
        )
    }

    /**
     * Parse [apduResponses] or throw [IllegalArgumentException] on the
     * first detected violation. Exception messages embed only static
     * structural metadata and are exact-form pinned by tests.
     */
    public fun parseOrThrow(apduResponses: List<ByteArray>): EmvCard =
        when (val result = parse(apduResponses)) {
            is EmvCardResult.Ok -> result.card
            is EmvCardResult.Err -> throw IllegalArgumentException(messageFor(result.error))
        }

    private sealed interface DecodeOutcome {
        data class Ok(val nodes: List<Tlv>) : DecodeOutcome
        data class Err(val error: EmvCardError) : DecodeOutcome
    }

    // why: bounded local accumulator; mutableListOf never escapes this
    // function. Canonical pattern per CLAUDE.md §5.4.
    private fun decodeAll(apduResponses: List<ByteArray>): DecodeOutcome {
        val collected = mutableListOf<Tlv>()
        for (response in apduResponses) {
            when (val parsed = TlvDecoder.parse(response, LENIENT)) {
                is TlvParseResult.Ok -> collected.addAll(parsed.tlvs)
                is TlvParseResult.Err -> return DecodeOutcome.Err(
                    EmvCardError.TlvDecodeFailed(cause = parsed.error),
                )
            }
        }
        return DecodeOutcome.Ok(collected)
    }

    private fun findRequired(nodes: List<Tlv>, tag: Tag): Tlv.Primitive? =
        findFirst(nodes, tag) as? Tlv.Primitive

    private fun missing(tagHex: String): EmvCardResult.Err =
        EmvCardResult.Err(EmvCardError.MissingRequiredTag(tagHex = tagHex))

    private fun messageFor(error: EmvCardError): String = when (error) {
        EmvCardError.EmptyInput -> "EmvCard input is empty"
        is EmvCardError.TlvDecodeFailed -> "EmvCard TLV decode failed: ${describeTlvError(error.cause)}"
        is EmvCardError.MissingRequiredTag -> "EmvCard missing required tag ${error.tagHex}"
        is EmvCardError.PanRejected -> "EmvCard PAN rejected: ${describePanError(error.cause)}"
        is EmvCardError.Track2Rejected -> "EmvCard Track 2 rejected: ${describeTrack2Error(error.cause)}"
        is EmvCardError.InvalidExpiryFormat -> "EmvCard expiry malformed: ${error.nibbleCount} nibbles"
        is EmvCardError.InvalidExpiryMonth -> "EmvCard expiry month out of range: ${error.month}"
        is EmvCardError.InvalidAid -> "EmvCard AID byte length out of range: ${error.byteCount}"
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

    // why: explicit when over the TlvError catalogue avoids
    // ::class.simpleName, which is fragile under R8 and Kotlin/Native.
    // The message strings here are the contract pinned by tests.
    private fun describeTlvError(cause: io.github.a7asoft.nfcemv.tlv.TlvError): String = when (cause) {
        is io.github.a7asoft.nfcemv.tlv.TlvError.UnexpectedEof -> "UnexpectedEof"
        is io.github.a7asoft.nfcemv.tlv.TlvError.IndefiniteLengthForbidden -> "IndefiniteLengthForbidden"
        is io.github.a7asoft.nfcemv.tlv.TlvError.InvalidLengthOctet -> "InvalidLengthOctet"
        is io.github.a7asoft.nfcemv.tlv.TlvError.LengthOverflow -> "LengthOverflow"
        is io.github.a7asoft.nfcemv.tlv.TlvError.IncompleteTag -> "IncompleteTag"
        is io.github.a7asoft.nfcemv.tlv.TlvError.TagTooLong -> "TagTooLong"
        is io.github.a7asoft.nfcemv.tlv.TlvError.NonMinimalTagEncoding -> "NonMinimalTagEncoding"
        is io.github.a7asoft.nfcemv.tlv.TlvError.NonMinimalLengthEncoding -> "NonMinimalLengthEncoding"
        is io.github.a7asoft.nfcemv.tlv.TlvError.ChildrenLengthMismatch -> "ChildrenLengthMismatch"
        is io.github.a7asoft.nfcemv.tlv.TlvError.MaxDepthExceeded -> "MaxDepthExceeded"
    }
}
