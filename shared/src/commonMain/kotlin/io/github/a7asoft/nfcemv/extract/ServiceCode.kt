package io.github.a7asoft.nfcemv.extract

import kotlin.jvm.JvmInline

/**
 * The 3-digit ISO/IEC 7813 service code carried in a Track 2 stream.
 *
 * Encodes interchange / authorization processing / allowed services rules
 * (ISO/IEC 7813 §10.3). Service codes are categorical metadata, not PCI
 * data — `toString` returns the raw 3-digit form.
 *
 * Construction goes through one of the typed factory functions on the
 * companion:
 *
 * - [ServiceCode.parse] returns a sealed [ServiceCodeResult] carrying
 *   either the validated [ServiceCode] or a typed [ServiceCodeError].
 *   Mirrors `Pan.parse` and `TlvDecoder.parse`.
 * - [ServiceCode.parseOrThrow] throws [IllegalArgumentException] on the
 *   first detected violation. Mirrors `Pan.parseOrThrow`.
 *
 * Both validate (in this order):
 * 1. Input is non-empty.
 * 2. Length is exactly 3 characters.
 * 3. Every codepoint is in `'0'..'9'`.
 *
 * The primary constructor is `internal` and exists only so the factory
 * functions and same-module callers (e.g. the Track 2 parser, which has
 * already validated each BCD nibble before construction) can build
 * validated instances. External consumers of the library cannot bypass
 * the typed factories.
 *
 * Higher-level interpretation of each digit (`interchange`,
 * `authorization`, `allowedServices`) is intentionally left out of this
 * milestone; consumers that need it can compute from `toString`.
 */
@JvmInline
public value class ServiceCode internal constructor(private val raw: String) {

    override fun toString(): String = raw

    public companion object {
        private const val REQUIRED_LENGTH: Int = 3

        /**
         * Parse [raw] into a [ServiceCode], returning a typed
         * [ServiceCodeResult].
         *
         * Mirrors `Pan.parse` for callers who prefer sealed result-driven
         * control flow over try / catch.
         */
        public fun parse(raw: String): ServiceCodeResult {
            if (raw.isEmpty()) {
                return ServiceCodeResult.Err(ServiceCodeError.EmptyInput)
            }
            if (raw.length != REQUIRED_LENGTH) {
                return ServiceCodeResult.Err(ServiceCodeError.WrongLength(raw.length))
            }
            val badIndex = raw.indexOfFirst { it !in '0'..'9' }
            if (badIndex >= 0) {
                return ServiceCodeResult.Err(ServiceCodeError.NonDigitCharacter(badIndex))
            }
            return ServiceCodeResult.Ok(ServiceCode(raw))
        }

        /**
         * Parse [raw] into a [ServiceCode], or throw
         * [IllegalArgumentException] on the first detected violation.
         *
         * The exception message is built by an exhaustive `when` over
         * [ServiceCodeError] and never embeds the raw input — only
         * structural metadata (length, offset).
         */
        public fun parseOrThrow(raw: String): ServiceCode = when (val result = parse(raw)) {
            is ServiceCodeResult.Ok -> result.serviceCode
            is ServiceCodeResult.Err -> throw IllegalArgumentException(messageFor(result.error))
        }

        private fun messageFor(error: ServiceCodeError): String = when (error) {
            ServiceCodeError.EmptyInput -> "ServiceCode input is empty"
            is ServiceCodeError.WrongLength ->
                "ServiceCode must be $REQUIRED_LENGTH digits, was ${error.length}"
            is ServiceCodeError.NonDigitCharacter ->
                "ServiceCode contains a non-digit character at offset ${error.offset}"
        }
    }
}
