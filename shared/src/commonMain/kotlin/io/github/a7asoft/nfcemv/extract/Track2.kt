package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.extract.internal.nibbleAt
import io.github.a7asoft.nfcemv.extract.internal.nibbleCount
import kotlinx.datetime.YearMonth

/**
 * Decoded EMV tag 57 / ISO 7813 Track 2 Equivalent Data.
 *
 * Construction goes through one of the typed factory functions on the
 * companion:
 *
 * - [Track2.parse] returns a sealed [Track2Result] with either `Ok(Track2)`
 *   or `Err(Track2Error)`. Mirrors `Pan.parse`.
 * - [Track2.parseOrThrow] throws [IllegalArgumentException] on the first
 *   detected violation. Mirrors `Pan.parseOrThrow`.
 *
 * Wire format (BCD nibbles):
 * `PAN(12-19) | D | YYMM | SSS | discretionary(0-19) | optional F-pad`
 *
 * The PAN is delegated to [Pan.parse], so all PCI / Luhn / length rules
 * stay in one place. Two-digit expiry years are interpreted as 21st
 * century (`YY` ⇒ `20YY`) — the EMV-issued cards this library targets do
 * not predate 2000 nor are expected to remain valid past 2099.
 *
 * This is a regular class (NOT a `data class`) so auto-generated
 * `componentN` / `copy` cannot leak the discretionary digits by destructuring.
 * `toString` masks both the PAN (via [Pan.toString]) and the discretionary
 * (size only). Equality is hand-rolled for content comparison.
 *
 * The only path to retrieve the raw discretionary digits is
 * [unmaskedDiscretionary]. PCI-scoped: the caller is responsible for not
 * logging, persisting, or transmitting it.
 */
public class Track2 internal constructor(
    public val pan: Pan,
    public val expiry: YearMonth,
    public val serviceCode: ServiceCode,
    private val discretionary: String,
) {
    /** Number of digits in the discretionary segment. */
    public val discretionaryLength: Int get() = discretionary.length

    /** Returns the raw discretionary digit string. PCI-scoped. */
    public fun unmaskedDiscretionary(): String = discretionary

    override fun toString(): String =
        "Track2(pan=$pan, expiry=$expiry, service=$serviceCode, discretionary.size=$discretionaryLength)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Track2) return false
        return pan == other.pan &&
            expiry == other.expiry &&
            serviceCode == other.serviceCode &&
            discretionary == other.discretionary
    }

    override fun hashCode(): Int {
        val parts = listOf(pan, expiry, serviceCode, discretionary)
        return parts.fold(0) { acc, part -> 31 * acc + part.hashCode() }
    }

    public companion object {
        private const val SEPARATOR_NIBBLE = 0xD
        private const val PAD_NIBBLE = 0xF
        private const val EXPIRY_NIBBLES = 4
        private const val SERVICE_CODE_NIBBLES = 3
        private const val CENTURY_OFFSET = 2000
        private const val MIN_MONTH = 1
        private const val MAX_MONTH = 12

        /**
         * Parse [raw] into a [Track2], returning a typed [Track2Result].
         */
        public fun parse(raw: ByteArray): Track2Result {
            if (raw.isEmpty()) return Track2Result.Err(Track2Error.EmptyInput)

            val totalNibbles = raw.nibbleCount()
            val effectiveNibbles = stripTrailingPad(raw, totalNibbles)
                ?: return Track2Result.Err(Track2Error.MalformedFPadding)

            val separatorIndex = findSeparator(raw, effectiveNibbles)
                ?: return Track2Result.Err(Track2Error.MissingSeparator)

            val panDigits = readDigits(raw, fromInclusive = 0, toExclusive = separatorIndex)
                ?: return Track2Result.Err(Track2Error.MalformedBcdNibble(offset = 0))

            val pan = when (val parsed = Pan.parse(panDigits)) {
                is PanResult.Ok -> parsed.pan
                is PanResult.Err -> return Track2Result.Err(Track2Error.PanRejected(parsed.error))
            }

            val expiryStart = separatorIndex + 1
            val expiryEnd = expiryStart + EXPIRY_NIBBLES
            if (expiryEnd > effectiveNibbles) {
                return Track2Result.Err(Track2Error.ExpiryTooShort(effectiveNibbles - expiryStart))
            }
            val expiryDigits = readDigits(raw, fromInclusive = expiryStart, toExclusive = expiryEnd)
                ?: return Track2Result.Err(Track2Error.MalformedBcdNibble(offset = expiryStart))
            val year = CENTURY_OFFSET + expiryDigits.substring(0, 2).toInt()
            val month = expiryDigits.substring(2, 4).toInt()
            if (month !in MIN_MONTH..MAX_MONTH) {
                return Track2Result.Err(Track2Error.InvalidExpiryMonth(month))
            }
            val expiry = YearMonth(year, month)

            val serviceStart = expiryEnd
            val serviceEnd = serviceStart + SERVICE_CODE_NIBBLES
            if (serviceEnd > effectiveNibbles) return Track2Result.Err(Track2Error.ServiceCodeTooShort)
            val serviceDigits = readDigits(raw, fromInclusive = serviceStart, toExclusive = serviceEnd)
                ?: return Track2Result.Err(Track2Error.MalformedBcdNibble(offset = serviceStart))
            val serviceCode = ServiceCode(serviceDigits)

            val discretionary = readDigits(raw, fromInclusive = serviceEnd, toExclusive = effectiveNibbles)
                ?: return Track2Result.Err(Track2Error.MalformedBcdNibble(offset = serviceEnd))

            return Track2Result.Ok(Track2(pan, expiry, serviceCode, discretionary))
        }

        /**
         * Parse [raw] into a [Track2], or throw [IllegalArgumentException]
         * on the first detected violation.
         */
        public fun parseOrThrow(raw: ByteArray): Track2 = when (val result = parse(raw)) {
            is Track2Result.Ok -> result.track2
            is Track2Result.Err -> throw IllegalArgumentException(messageFor(result.error))
        }

        private fun stripTrailingPad(raw: ByteArray, totalNibbles: Int): Int? {
            if (totalNibbles == 0) return 0
            val lastNibble = raw.nibbleAt(totalNibbles - 1)
            if (lastNibble == PAD_NIBBLE) return totalNibbles - 1
            return totalNibbles
        }

        private fun findSeparator(raw: ByteArray, effective: Int): Int? {
            for (i in 0 until effective) {
                val n = raw.nibbleAt(i)
                if (n == SEPARATOR_NIBBLE) return i
                if (n == PAD_NIBBLE) return null
                if (n !in 0..9) continue
            }
            return null
        }

        private fun readDigits(raw: ByteArray, fromInclusive: Int, toExclusive: Int): String? {
            val builder = StringBuilder(toExclusive - fromInclusive)
            for (i in fromInclusive until toExclusive) {
                val n = raw.nibbleAt(i)
                if (n !in 0..9) return null
                builder.append('0' + n)
            }
            return builder.toString()
        }

        private fun messageFor(error: Track2Error): String = when (error) {
            Track2Error.EmptyInput -> "Track2 input is empty"
            Track2Error.MissingSeparator -> "Track2 missing 'D' separator nibble"
            is Track2Error.PanRejected -> "Track2 PAN rejected: ${error.cause::class.simpleName}"
            is Track2Error.ExpiryTooShort -> "Track2 expiry truncated: ${error.nibblesAvailable} nibbles available"
            is Track2Error.InvalidExpiryMonth -> "Track2 expiry month out of range: ${error.month}"
            Track2Error.ServiceCodeTooShort -> "Track2 service code truncated"
            is Track2Error.MalformedBcdNibble -> "Track2 malformed BCD nibble at offset ${error.offset}"
            Track2Error.MalformedFPadding -> "Track2 'F' pad nibble appeared in non-trailing position"
        }
    }
}
