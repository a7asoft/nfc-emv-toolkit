package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.tlv.Strictness
import io.github.a7asoft.nfcemv.tlv.Tag
import io.github.a7asoft.nfcemv.tlv.Tlv
import io.github.a7asoft.nfcemv.tlv.TlvDecoder
import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseResult

/**
 * Decoded PPSE FCI (Proximity Payment System Environment FCI) per
 * EMV Book 1 §11.3.4. Carries the list of contactless applications the
 * card advertises in response to `SELECT 2PAY.SYS.DDF01`.
 */
public data class Ppse internal constructor(public val applications: List<PpseEntry>) {
    public companion object
}

/**
 * One application entry inside a PPSE Application Template (tag `61`).
 *
 * - [aid] — EMV tag `4F`, validated through `Aid.fromBytes`.
 * - [priority] — Application Priority Indicator low nibble (1..15) per
 *   EMV Book 1 §12.2.3, or `null` if the application has no priority
 *   assigned (the card omitted tag `87`, sent priority 0, or sent only
 *   RFU/flag bits). Lower value wins; absent priority sorts last via
 *   the reader's `minByOrNull { priority ?: Int.MAX_VALUE }`.
 */
public data class PpseEntry(
    public val aid: Aid,
    public val priority: Int?,
)

/**
 * A typed reason [Ppse.parse] rejected an FCI.
 *
 * Variants carry only structural metadata; no value bytes are embedded.
 */
public sealed interface PpseError {
    public data object EmptyInput : PpseError
    public data class TlvDecodeFailed(val cause: TlvError) : PpseError
    public data object UnknownTemplate : PpseError
    public data object NoApplicationsFound : PpseError
    public data class InvalidAid(val byteCount: Int) : PpseError
}

/** Outcome of [Ppse.parse]. */
public sealed interface PpseResult {
    public data class Ok(val ppse: Ppse) : PpseResult
    public data class Err(val error: PpseError) : PpseResult
}

private val PPSE_TLV_OPTIONS = TlvOptions(strictness = Strictness.Lenient)
private val TAG_FCI = Tag.fromHex("6F")
private val TAG_FCI_PROPRIETARY = Tag.fromHex("A5")
private val TAG_FCI_DISCRETIONARY = Tag.fromHex("BF0C")
private val TAG_APPLICATION = Tag.fromHex("61")
private val TAG_AID = Tag.fromHex("4F")
private val TAG_PRIORITY = Tag.fromHex("87")
private const val AID_MIN_BYTES: Int = 5
private const val AID_MAX_BYTES: Int = 16

/**
 * Parse [bytes] (the data field of a `SELECT 2PAY.SYS.DDF01` response,
 * status word stripped) into a typed [PpseResult]. Mirrors `TlvDecoder.parse`.
 */
@Suppress(
    // why: each return is a distinct EMV Book 1 §11.3.4 contract step
    // (empty / decode / outer / no-apps / per-entry validation). Collapsing
    // obscures the FCI walk without reducing real complexity.
    "ReturnCount",
    "CyclomaticComplexMethod",
)
public fun Ppse.Companion.parse(bytes: ByteArray): PpseResult {
    if (bytes.isEmpty()) return PpseResult.Err(PpseError.EmptyInput)
    val nodes = when (val decoded = TlvDecoder.parse(bytes, PPSE_TLV_OPTIONS)) {
        is TlvParseResult.Ok -> decoded.tlvs
        is TlvParseResult.Err -> return PpseResult.Err(PpseError.TlvDecodeFailed(decoded.error))
    }
    val applicationTemplates = collectApplicationTemplates(nodes)
        ?: return PpseResult.Err(PpseError.UnknownTemplate)
    if (applicationTemplates.isEmpty()) return PpseResult.Err(PpseError.NoApplicationsFound)
    val entries = ArrayList<PpseEntry>(applicationTemplates.size)
    for (template in applicationTemplates) {
        when (val outcome = readEntry(template)) {
            is PpseEntryOutcome.Ok -> entries.add(outcome.entry)
            is PpseEntryOutcome.Skip -> Unit
            is PpseEntryOutcome.Err -> return PpseResult.Err(outcome.error)
        }
    }
    if (entries.isEmpty()) return PpseResult.Err(PpseError.NoApplicationsFound)
    return PpseResult.Ok(Ppse(entries))
}

/** Parse [bytes] into a [Ppse], or throw [IllegalArgumentException]. */
public fun Ppse.Companion.parseOrThrow(bytes: ByteArray): Ppse =
    when (val result = parse(bytes)) {
        is PpseResult.Ok -> result.ppse
        is PpseResult.Err -> throw IllegalArgumentException(messageForPpseError(result.error))
    }

@Suppress("ReturnCount", "CyclomaticComplexMethod")
private fun collectApplicationTemplates(roots: List<Tlv>): List<Tlv.Constructed>? {
    val fci = roots.firstOrNull() as? Tlv.Constructed ?: return null
    if (fci.tag != TAG_FCI) return null
    val proprietary = fci.children.firstOrNull { it.tag == TAG_FCI_PROPRIETARY } as? Tlv.Constructed
        ?: return null
    val discretionary = proprietary.children.firstOrNull {
        it.tag == TAG_FCI_DISCRETIONARY
    } as? Tlv.Constructed ?: return null
    return discretionary.children
        .filterIsInstance<Tlv.Constructed>()
        .filter { it.tag == TAG_APPLICATION }
}

private sealed interface PpseEntryOutcome {
    data class Ok(val entry: PpseEntry) : PpseEntryOutcome
    data object Skip : PpseEntryOutcome
    data class Err(val error: PpseError) : PpseEntryOutcome
}

@Suppress("ReturnCount", "CyclomaticComplexMethod")
private fun readEntry(template: Tlv.Constructed): PpseEntryOutcome {
    val aidNode = template.children.firstOrNull {
        it is Tlv.Primitive && it.tag == TAG_AID
    } as? Tlv.Primitive ?: return PpseEntryOutcome.Skip
    val aidBytes = aidNode.copyValue()
    if (aidBytes.size !in AID_MIN_BYTES..AID_MAX_BYTES) {
        return PpseEntryOutcome.Err(PpseError.InvalidAid(aidBytes.size))
    }
    val priorityNode = template.children.firstOrNull {
        it is Tlv.Primitive && it.tag == TAG_PRIORITY
    } as? Tlv.Primitive
    // why: per EMV Book 1 §12.2.3, only the low nibble of the Application
    // Priority Indicator carries the priority value (1..15). Bit b8 is the
    // "cardholder confirmation supported" flag and b5–b7 are RFU. A card
    // sending 0x81 means priority 1 with confirmation required, NOT priority
    // 0x81 = 129. Decoding the full byte ranks confirmation-requiring cards
    // last instead of first. Treat 0x00 as "no priority assigned" (null).
    val priority = priorityNode
        ?.copyValue()
        ?.firstOrNull()
        ?.toInt()
        ?.and(0x0F)
        ?.takeIf { it != 0 }
    return PpseEntryOutcome.Ok(PpseEntry(Aid.fromBytes(aidBytes), priority))
}

// why: exhaustive `when` over the sealed [PpseError] catalogue (CLAUDE.md §3.2).
@Suppress("CyclomaticComplexMethod")
private fun messageForPpseError(error: PpseError): String = when (error) {
    PpseError.EmptyInput -> "PPSE input is empty"
    is PpseError.TlvDecodeFailed -> "PPSE TLV decode failed"
    PpseError.UnknownTemplate -> "PPSE outer template not 6F/A5/BF0C"
    PpseError.NoApplicationsFound -> "PPSE has no application templates"
    is PpseError.InvalidAid -> "PPSE AID length out of 5..16: ${error.byteCount}"
}
