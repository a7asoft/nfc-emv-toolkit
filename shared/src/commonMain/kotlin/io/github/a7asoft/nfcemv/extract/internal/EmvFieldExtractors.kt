package io.github.a7asoft.nfcemv.extract.internal

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCardError
import io.github.a7asoft.nfcemv.extract.Pan
import io.github.a7asoft.nfcemv.extract.PanResult
import io.github.a7asoft.nfcemv.tlv.Tlv

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
