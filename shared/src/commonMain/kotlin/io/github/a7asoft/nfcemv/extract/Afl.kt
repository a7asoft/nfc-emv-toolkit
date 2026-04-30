package io.github.a7asoft.nfcemv.extract

/**
 * Application File Locator (EMV Book 3 §6.5.8 / Annex A tag `94`).
 *
 * The AFL is a flat list of 4-byte entries describing which records the
 * terminal must read from which Short File Identifiers. A reader walks
 * this list and issues one `READ RECORD` APDU per record.
 *
 * Each [AflEntry] carries:
 * - [AflEntry.sfi] — Short File Identifier (1..30); occupies the high
 *   5 bits of the first byte (`firstByte ushr 3`).
 * - [AflEntry.firstRecord] — first record number to read (inclusive).
 * - [AflEntry.lastRecord] — last record number to read (inclusive).
 * - [AflEntry.odaCount] — count of records used for offline data
 *   authentication; the v0.2.0 reader does not act on this.
 *
 * Construction goes through [Afl.parse] / [Afl.parseOrThrow]. The primary
 * constructor is `internal` so external consumers cannot bypass validation.
 */
public data class Afl internal constructor(public val entries: List<AflEntry>) {
    public companion object
}

/**
 * One row of an [Afl]. Describes a contiguous record range within a
 * single Short File Identifier.
 */
public data class AflEntry(
    public val sfi: Int,
    public val firstRecord: Int,
    public val lastRecord: Int,
    public val odaCount: Int,
)

/**
 * A typed reason [Afl.parse] rejected an AFL byte sequence.
 *
 * Variants carry only structural metadata (offsets, counts). They never
 * embed PAN, track2, or other sensitive bytes.
 */
public sealed interface AflError {
    /** Caller passed an empty `ByteArray` to [Afl.parse]. */
    public data object EmptyInput : AflError

    /** AFL byte length must be a positive multiple of 4. */
    public data class InvalidLength(val byteCount: Int) : AflError

    /** Decoded SFI (high 5 bits of first byte) was outside `1..30`. */
    public data class InvalidSfi(val offset: Int, val sfi: Int) : AflError

    /** First record was less than 1, or first > last. */
    public data class InvalidRecordRange(val offset: Int, val first: Int, val last: Int) : AflError
}

/** Outcome of [Afl.parse]. */
public sealed interface AflResult {
    public data class Ok(val afl: Afl) : AflResult
    public data class Err(val error: AflError) : AflResult
}

private const val AFL_ENTRY_BYTES: Int = 4
private const val FIRST_RECORD_OFFSET: Int = 1
private const val LAST_RECORD_OFFSET: Int = 2
private const val ODA_COUNT_OFFSET: Int = 3
private const val SFI_SHIFT: Int = 3
private const val SFI_MIN: Int = 1
private const val SFI_MAX: Int = 30

/**
 * Parse [bytes] (the value of EMV tag `94`, or the AFL slice of a GPO
 * format-1 response) into a typed [AflResult]. Mirrors `TlvDecoder.parse`.
 */
@Suppress(
    // why: each return is a distinct spec validation (empty / length /
    // per-entry SFI / per-entry record range). Collapsing obscures the
    // checks without reducing real complexity. Same idiom as `ServiceCode.parse`.
    "ReturnCount",
    "CyclomaticComplexMethod",
)
public fun Afl.Companion.parse(bytes: ByteArray): AflResult {
    if (bytes.isEmpty()) return AflResult.Err(AflError.EmptyInput)
    if (bytes.size % AFL_ENTRY_BYTES != 0) return AflResult.Err(AflError.InvalidLength(bytes.size))
    val entries = ArrayList<AflEntry>(bytes.size / AFL_ENTRY_BYTES)
    var offset = 0
    while (offset < bytes.size) {
        when (val outcome = readEntry(bytes, offset)) {
            is AflEntryOutcome.Ok -> entries.add(outcome.entry)
            is AflEntryOutcome.Err -> return AflResult.Err(outcome.error)
        }
        offset += AFL_ENTRY_BYTES
    }
    return AflResult.Ok(Afl(entries))
}

/** Parse [bytes] into an [Afl], or throw [IllegalArgumentException]. */
public fun Afl.Companion.parseOrThrow(bytes: ByteArray): Afl =
    when (val result = parse(bytes)) {
        is AflResult.Ok -> result.afl
        is AflResult.Err -> throw IllegalArgumentException(messageForAflError(result.error))
    }

private sealed interface AflEntryOutcome {
    data class Ok(val entry: AflEntry) : AflEntryOutcome
    data class Err(val error: AflError) : AflEntryOutcome
}

@Suppress("CyclomaticComplexMethod")
private fun readEntry(bytes: ByteArray, offset: Int): AflEntryOutcome {
    val sfi = (bytes[offset].toInt() and 0xFF) ushr SFI_SHIFT
    if (sfi !in SFI_MIN..SFI_MAX) return AflEntryOutcome.Err(AflError.InvalidSfi(offset, sfi))
    val first = bytes[offset + FIRST_RECORD_OFFSET].toInt() and 0xFF
    val last = bytes[offset + LAST_RECORD_OFFSET].toInt() and 0xFF
    if (first < 1 || first > last) {
        return AflEntryOutcome.Err(AflError.InvalidRecordRange(offset, first, last))
    }
    val oda = bytes[offset + ODA_COUNT_OFFSET].toInt() and 0xFF
    return AflEntryOutcome.Ok(AflEntry(sfi, first, last, oda))
}

// why: exhaustive `when` over the sealed [AflError] catalogue (CLAUDE.md §3.2).
@Suppress("CyclomaticComplexMethod")
private fun messageForAflError(error: AflError): String = when (error) {
    AflError.EmptyInput -> "AFL input is empty"
    is AflError.InvalidLength -> "AFL byte length not a multiple of 4: ${error.byteCount}"
    is AflError.InvalidSfi -> "AFL SFI out of range at offset ${error.offset}: ${error.sfi}"
    is AflError.InvalidRecordRange ->
        "AFL record range invalid at offset ${error.offset}: ${error.first}..${error.last}"
}
