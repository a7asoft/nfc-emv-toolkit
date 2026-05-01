package io.github.a7asoft.nfcemv.extract.internal

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCardError
import io.github.a7asoft.nfcemv.extract.Pan
import io.github.a7asoft.nfcemv.extract.PanResult
import io.github.a7asoft.nfcemv.extract.Track2
import io.github.a7asoft.nfcemv.extract.Track2Result
import io.github.a7asoft.nfcemv.tlv.Tlv
import kotlinx.datetime.YearMonth

/**
 * Result envelope for per-field extractors. Mirrors the toolkit's other
 * sealed Ok/Err pairs (`PanResult`, `Track2Result`, `EmvCardResult`).
 */
internal sealed interface ExtractResult<out T> {
    /** Successful extraction carrying the decoded value. */
    data class Ok<T>(val value: T) : ExtractResult<T>

    /** Extraction failed; see [error] for the typed reason. */
    data class Err(val error: EmvCardError) : ExtractResult<Nothing>
}

/**
 * Decode tag `4F` (Application Identifier) from a primitive node.
 *
 * Defers length validation to [Aid.fromBytes]; any length-out-of-range
 * surfaces as [EmvCardError.InvalidAid].
 */
internal fun extractAid(node: Tlv.Primitive): ExtractResult<Aid> {
    if (node.length !in MIN_AID_BYTES..MAX_AID_BYTES) {
        return ExtractResult.Err(EmvCardError.InvalidAid(byteCount = node.length))
    }
    return ExtractResult.Ok(Aid.fromBytes(node.copyValue()))
}

/**
 * Decode tag `5A` (Application PAN) from a primitive node.
 *
 * Tag `5A` uses CN format: BCD-packed digits with `0xF` padding when
 * the digit count is odd. This extractor unpacks the nibbles, strips a
 * single trailing `0xF` if present, and hands the resulting digit
 * string to [Pan.parse]. Any rejection (length, digits, Luhn) surfaces
 * as [EmvCardError.PanRejected].
 */
internal fun extractPan(node: Tlv.Primitive): ExtractResult<Pan> {
    val bytes = node.copyValue()
    return when (val unpacked = unpackPanDigits(bytes)) {
        is UnpackResult.Err -> ExtractResult.Err(EmvCardError.MalformedPanNibble(offset = unpacked.offset))
        is UnpackResult.Ok -> when (val parsed = Pan.parse(unpacked.digits)) {
            is PanResult.Ok -> ExtractResult.Ok(parsed.pan)
            is PanResult.Err -> ExtractResult.Err(EmvCardError.PanRejected(parsed.error))
        }
    }
}

private const val MIN_AID_BYTES: Int = 5
private const val MAX_AID_BYTES: Int = 16
private const val PAD_NIBBLE: Int = 0xF
private const val EXPIRY_NIBBLE_COUNT: Int = 6
private const val CENTURY_OFFSET: Int = 2000
private const val MIN_MONTH: Int = 1
private const val MAX_MONTH: Int = 12

/**
 * Decode tag `5F24` (Application Expiration Date) — `YYMMDD` BCD.
 *
 * Returns a [YearMonth] taking only the `YYMM` portion; the day field
 * is read but discarded (per the issue spec, `EmvCard.expiry` is a
 * month, not a date).
 *
 * **Two-digit-year mapping (deliberate deviation from EMV / no spec
 * guidance):** EMV Book 3 §10.2 / Annex A defines tag `5F24` as `n 6`
 * `YYMMDD` with no century encoding. Real kernels apply a sliding
 * window relative to the terminal's current date. This toolkit
 * intentionally chooses a static 21st-century mapping (`YY ⇒ 20YY`,
 * range 2000..2099) because:
 * 1. The toolkit targets EMV-issued cards, all of which post-date 2000.
 * 2. Sliding-window mapping requires a clock; this is a pure-parser
 *    layer with no time injection point in v0.1.x.
 * 3. The mapping is consistent project-wide with `Track2.expiry` (#6).
 *
 * Cards expiring 2100+ silently mis-map to `20XX`. A future
 * `EmvParserOptions(today: LocalDate)` parameter could enable the
 * sliding window; that's tracked separately and out of scope for
 * v0.1.x. The static mapping is pinned by a regression test.
 */
internal fun extractExpiry(node: Tlv.Primitive): ExtractResult<YearMonth> {
    val bytes = node.copyValue()
    val nibbleCount = bytes.nibbleCount()
    if (nibbleCount != EXPIRY_NIBBLE_COUNT) {
        return ExtractResult.Err(EmvCardError.InvalidExpiryFormat(nibbleCount = nibbleCount))
    }
    val yy = bytes.nibbleAt(0) * 10 + bytes.nibbleAt(1)
    val mm = bytes.nibbleAt(2) * 10 + bytes.nibbleAt(3)
    if (mm !in MIN_MONTH..MAX_MONTH) {
        return ExtractResult.Err(EmvCardError.InvalidExpiryMonth(month = mm))
    }
    return ExtractResult.Ok(YearMonth(CENTURY_OFFSET + yy, mm))
}

/**
 * Decode tag `5F20` (Cardholder Name) — `AN` ISO-8859-1 bytes,
 * right-padded with `0x20`. Returns `null` when the value is empty
 * or contains only spaces.
 *
 * The returned `String` carries cardholder personally-identifying
 * information per PCI DSS Cardholder Data scope. Caller MUST NOT log
 * it raw; downstream code should route it through a typed extractor
 * or mask before any persistence / transmission. `EmvCard.toString`
 * enforces this by emitting a length-only placeholder.
 *
 * **Limitation — `9F 11` Issuer Code Table Index NOT honored.** EMV
 * Book 4 §3 specifies that `5F 20` Cardholder Name encoding follows
 * the issuer-declared `9F 11` Issuer Code Table Index. This extractor
 * unconditionally decodes as Latin-1. See [extractApplicationLabel]
 * for the same caveat applied to `9F 12`.
 */
internal fun extractCardholderName(node: Tlv.Primitive): String? =
    decodeTrimmedLatin1(node.copyValue())

/**
 * Decode tag `50` (Application Label) or `9F 12` (Application Preferred
 * Name) value bytes as ISO/IEC 8859-1 trimmed text. Returns null if the
 * byte array is empty or trims to an empty string.
 *
 * Application Label is operational metadata, not PCI data — safe to
 * log raw.
 *
 * **Limitation — `9F 11` Issuer Code Table Index NOT honored.** EMV
 * Book 4 §3 specifies that `9F 12` (and `5F 20` Cardholder Name)
 * encoding follows the issuer-declared `9F 11` Issuer Code Table
 * Index, which may select ISO 8859-2..16. This extractor unconditionally
 * decodes as Latin-1 (the `9F 11`-absent default). Glyph mis-rendering
 * may occur for issuers that declare a non-Latin-1 code table.
 * Honoring `9F 11` is deferred to a future issue.
 */
internal fun extractApplicationLabel(node: Tlv.Primitive): String? =
    decodeTrimmedLatin1(node.copyValue())

private fun decodeTrimmedLatin1(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val text = buildString(bytes.size) {
        for (b in bytes) append(Char(b.toInt() and 0xFF))
    }.trimEnd(' ')
    return text.ifEmpty { null }
}

/**
 * Decode tag `57` (Track 2 Equivalent Data) by delegating to
 * [Track2.parse]. Failures wrap as [EmvCardError.Track2Rejected].
 */
internal fun extractTrack2(node: Tlv.Primitive): ExtractResult<Track2> =
    when (val parsed = Track2.parse(node.copyValue())) {
        is Track2Result.Ok -> ExtractResult.Ok(parsed.track2)
        is Track2Result.Err -> ExtractResult.Err(EmvCardError.Track2Rejected(parsed.error))
    }

private sealed interface UnpackResult {
    data class Ok(val digits: String) : UnpackResult
    data class Err(val offset: Int) : UnpackResult
}

private fun unpackPanDigits(bytes: ByteArray): UnpackResult {
    val totalNibbles = bytes.nibbleCount()
    if (totalNibbles == 0) return UnpackResult.Ok("")
    val effective =
        if (bytes.nibbleAt(totalNibbles - 1) == PAD_NIBBLE) totalNibbles - 1 else totalNibbles
    for (i in 0 until effective) {
        val n = bytes.nibbleAt(i)
        if (n !in 0..9) return UnpackResult.Err(offset = i)
    }
    return UnpackResult.Ok(
        buildString(effective) {
            for (i in 0 until effective) append('0' + bytes.nibbleAt(i))
        },
    )
}
