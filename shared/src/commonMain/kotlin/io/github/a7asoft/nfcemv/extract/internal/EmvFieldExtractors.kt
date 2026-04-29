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
    val bytes = node.copyValue()
    if (bytes.size !in MIN_AID_BYTES..MAX_AID_BYTES) {
        return ExtractResult.Err(EmvCardError.InvalidAid(byteCount = bytes.size))
    }
    return ExtractResult.Ok(Aid.fromBytes(bytes))
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
    val digits = unpackPanDigits(bytes)
    return when (val parsed = Pan.parse(digits)) {
        is PanResult.Ok -> ExtractResult.Ok(parsed.pan)
        is PanResult.Err -> ExtractResult.Err(EmvCardError.PanRejected(parsed.error))
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
 * month, not a date). Two-digit `YY` is mapped to 21st century
 * (`YY ⇒ 20YY`), matching the `Track2` convention from #6.
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
 * Decode tag `5F20` (Cardholder Name) — `AN` ASCII bytes,
 * right-padded with `0x20`. Returns `null` when the value is empty
 * or contains only spaces.
 *
 * The returned `String` carries cardholder personally-identifying
 * information per PCI DSS Cardholder Data scope. Caller MUST NOT log
 * it raw; downstream code should route it through a typed extractor
 * or mask before any persistence / transmission. `EmvCard.toString`
 * enforces this by emitting a length-only placeholder.
 */
internal fun extractCardholderName(node: Tlv.Primitive): String? =
    decodeTrimmedAscii(node.copyValue())

/**
 * Decode tag `50` (Application Label) — `AN` ASCII bytes,
 * right-padded with `0x20`. Returns `null` when the value is empty
 * or contains only spaces.
 *
 * Application Label is operational metadata, not PCI data — safe to
 * log raw.
 */
internal fun extractApplicationLabel(node: Tlv.Primitive): String? =
    decodeTrimmedAscii(node.copyValue())

private fun decodeTrimmedAscii(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val text = bytes.decodeToString().trimEnd(' ')
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

private fun unpackPanDigits(bytes: ByteArray): String {
    val totalNibbles = bytes.nibbleCount()
    if (totalNibbles == 0) return ""
    val effective =
        if (bytes.nibbleAt(totalNibbles - 1) == PAD_NIBBLE) totalNibbles - 1 else totalNibbles
    return buildString(effective) {
        for (i in 0 until effective) {
            append('0' + bytes.nibbleAt(i))
        }
    }
}
