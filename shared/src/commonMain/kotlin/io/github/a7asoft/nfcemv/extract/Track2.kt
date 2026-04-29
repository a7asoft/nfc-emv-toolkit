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
 * Wire format (BCD nibbles, high-nibble-first per byte):
 * `PAN(12-19) | D | YYMM | SSS | discretionary(0-19) | optional F-pad`
 *
 * The PAN is delegated to [Pan.parse], so all PCI / Luhn / length rules
 * stay in one place. Two-digit expiry years are interpreted as 21st
 * century (`YY` ⇒ `20YY`); the toolkit targets EMV-issued cards which
 * post-date 2000 and are not expected to remain valid past 2099.
 *
 * Discretionary length is intentionally NOT capped at the ISO/IEC 7813
 * 40-character total: real cards do emit longer streams in practice, and
 * downstream extractors enforce per-tag policy. This layer only
 * structurally validates BCD form and segment ordering.
 *
 * This is a regular class (NOT a `data class`) so auto-generated
 * `componentN` / `copy` cannot leak the discretionary bytes by destructuring.
 * `toString` masks both the PAN (via [Pan.toString]) and the discretionary
 * (size only). Equality is hand-rolled for content comparison.
 *
 * The only path to retrieve the raw discretionary digits is
 * [unmaskedDiscretionary]. PCI-scoped: the caller is responsible for not
 * logging, persisting, or transmitting it.
 */
public class Track2 internal constructor(
    public val pan: Pan,
    /**
     * Card expiry month. Two-digit year is mapped to 21st century
     * (`YY` ⇒ `20YY`); cards expiring in 2100 or later would be
     * mis-mapped — out of scope for this milestone.
     */
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

    // why: canonical hashCode accumulator. CLAUDE.md §5.4 explicitly forgives
    // `var` for this idiom (single bounded local cursor, no escape).
    override fun hashCode(): Int {
        var result = pan.hashCode()
        result = 31 * result + expiry.hashCode()
        result = 31 * result + serviceCode.hashCode()
        result = 31 * result + discretionary.hashCode()
        return result
    }

    public companion object {
        private const val SEPARATOR_NIBBLE = 0xD
        private const val PAD_NIBBLE = 0xF
        private const val EXPIRY_NIBBLES = 4
        private const val SERVICE_CODE_NIBBLES = 3
        private const val CENTURY_OFFSET = 2000
        private const val MIN_MONTH = 1
        private const val MAX_MONTH = 12

        /** Internal envelope for short-circuiting [parseInternal]. Never escapes. */
        private class Track2ParseException(val error: Track2Error) : RuntimeException()

        /** Outcome of the single full-input pre-validation pass. */
        private sealed interface SeparatorScan {
            data class Found(val at: Int) : SeparatorScan
            data object NotFound : SeparatorScan
            data class NonTrailingF(val at: Int) : SeparatorScan
            data class IllegalNibble(val at: Int) : SeparatorScan
        }

        /**
         * Parse [raw] into a [Track2], returning a typed [Track2Result].
         */
        public fun parse(raw: ByteArray): Track2Result = try {
            Track2Result.Ok(parseInternal(raw))
        } catch (e: Track2ParseException) {
            Track2Result.Err(e.error)
        }

        /**
         * Parse [raw] into a [Track2], or throw [IllegalArgumentException]
         * on the first detected violation.
         *
         * The exception message is built by an exhaustive `when` over
         * [Track2Error] and never reflects on class names — safe under R8 /
         * Kotlin/Native name stripping.
         */
        public fun parseOrThrow(raw: ByteArray): Track2 = when (val result = parse(raw)) {
            is Track2Result.Ok -> result.track2
            is Track2Result.Err -> throw IllegalArgumentException(messageFor(result.error))
        }

        private fun parseInternal(raw: ByteArray): Track2 {
            if (raw.isEmpty()) throw Track2ParseException(Track2Error.EmptyInput)
            val effective = effectiveNibbles(raw)
            val separatorAt = locateSeparator(raw, effective)
            val pan = parsePanSegment(raw, end = separatorAt)
            val expiryStart = separatorAt + 1
            val expiry = parseExpirySegment(raw, expiryStart, effective)
            val serviceStart = expiryStart + EXPIRY_NIBBLES
            val serviceCode = parseServiceCodeSegment(raw, serviceStart, effective)
            val discretionaryStart = serviceStart + SERVICE_CODE_NIBBLES
            val discretionary = readDigits(raw, discretionaryStart, effective)
            return Track2(pan, expiry, serviceCode, discretionary)
        }

        private fun effectiveNibbles(raw: ByteArray): Int {
            val total = raw.nibbleCount()
            if (total == 0) return 0
            return if (raw.nibbleAt(total - 1) == PAD_NIBBLE) total - 1 else total
        }

        private fun locateSeparator(raw: ByteArray, effective: Int): Int =
            when (val scan = scanInput(raw, effective)) {
                is SeparatorScan.Found -> scan.at
                SeparatorScan.NotFound -> throw Track2ParseException(Track2Error.MissingSeparator)
                is SeparatorScan.NonTrailingF -> throw Track2ParseException(Track2Error.MalformedFPadding)
                is SeparatorScan.IllegalNibble -> throw Track2ParseException(Track2Error.MalformedBcdNibble(scan.at))
            }

        // why: single-pass linear scanner with one local index cursor; the
        // var captures the "first separator seen" state and is bounded to
        // this function. Same idiom as `hashCode` accumulator above.
        private fun scanInput(raw: ByteArray, effective: Int): SeparatorScan {
            var separator: Int? = null
            for (i in 0 until effective) {
                when (val nibble = raw.nibbleAt(i)) {
                    in 0..9 -> Unit
                    SEPARATOR_NIBBLE -> {
                        if (separator != null) return SeparatorScan.IllegalNibble(i)
                        separator = i
                    }
                    PAD_NIBBLE -> return SeparatorScan.NonTrailingF(i)
                    else -> return SeparatorScan.IllegalNibble(i)
                }
            }
            return separator?.let { SeparatorScan.Found(it) } ?: SeparatorScan.NotFound
        }

        private fun parsePanSegment(raw: ByteArray, end: Int): Pan {
            val digits = readDigits(raw, 0, end)
            return when (val parsed = Pan.parse(digits)) {
                is PanResult.Ok -> parsed.pan
                is PanResult.Err -> throw Track2ParseException(Track2Error.PanRejected(parsed.error))
            }
        }

        private fun parseExpirySegment(raw: ByteArray, start: Int, effective: Int): YearMonth {
            val end = start + EXPIRY_NIBBLES
            if (end > effective) {
                throw Track2ParseException(Track2Error.ExpiryTooShort(effective - start))
            }
            val digits = readDigits(raw, start, end)
            val month = digits.substring(2, 4).toInt()
            if (month !in MIN_MONTH..MAX_MONTH) {
                throw Track2ParseException(Track2Error.InvalidExpiryMonth(month))
            }
            val year = CENTURY_OFFSET + digits.substring(0, 2).toInt()
            return YearMonth(year, month)
        }

        private fun parseServiceCodeSegment(raw: ByteArray, start: Int, effective: Int): ServiceCode {
            val end = start + SERVICE_CODE_NIBBLES
            if (end > effective) {
                throw Track2ParseException(Track2Error.ServiceCodeTooShort)
            }
            return ServiceCode(readDigits(raw, start, end))
        }

        // why: callers reach this only after `scanInput` has validated every
        // nibble in [0, effective). The range read here therefore contains
        // only digits (no D, no F, no illegal nibble), so no per-nibble
        // re-validation is needed.
        private fun readDigits(raw: ByteArray, fromInclusive: Int, toExclusive: Int): String {
            val builder = StringBuilder(toExclusive - fromInclusive)
            for (i in fromInclusive until toExclusive) {
                builder.append('0' + raw.nibbleAt(i))
            }
            return builder.toString()
        }

        private fun messageFor(error: Track2Error): String = when (error) {
            Track2Error.EmptyInput -> "Track2 input is empty"
            Track2Error.MissingSeparator -> "Track2 missing 'D' separator nibble"
            is Track2Error.PanRejected -> "Track2 PAN rejected: ${describePanError(error.cause)}"
            is Track2Error.ExpiryTooShort -> "Track2 expiry truncated: ${error.nibblesAvailable} nibbles available"
            is Track2Error.InvalidExpiryMonth -> "Track2 expiry month out of range: ${error.month}"
            Track2Error.ServiceCodeTooShort -> "Track2 service code truncated"
            is Track2Error.MalformedBcdNibble -> "Track2 malformed BCD nibble at offset ${error.offset}"
            Track2Error.MalformedFPadding -> "Track2 'F' pad nibble appeared in non-trailing position"
        }

        // why: explicit when over the PanError catalogue avoids
        // ::class.simpleName, which is fragile under R8 and Kotlin/Native
        // (per the kotlin-architect review). The message strings here are
        // the contract pinned by tests.
        private fun describePanError(cause: PanError): String = when (cause) {
            is PanError.LengthOutOfRange -> "LengthOutOfRange"
            PanError.NonDigitCharacters -> "NonDigitCharacters"
            PanError.LuhnCheckFailed -> "LuhnCheckFailed"
        }
    }
}
