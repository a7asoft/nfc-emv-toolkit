package io.github.a7asoft.nfcemv.extract

import kotlin.jvm.JvmInline

/**
 * The 3-digit ISO/IEC 7813 service code carried in a Track 2 stream.
 *
 * Encodes interchange / authorization processing / allowed services rules
 * (ISO/IEC 7813 §10.3). Service codes are categorical metadata, not PCI
 * data — `toString` returns the raw 3-digit form.
 *
 * Validation: exactly three ASCII digits. Construction throws
 * [IllegalArgumentException] otherwise.
 *
 * Higher-level interpretation of each digit (`interchange`, `authorization`,
 * `allowedServices`) is intentionally left out of this milestone; consumers
 * that need it can compute from `toString`.
 */
@JvmInline
public value class ServiceCode(private val raw: String) {
    init {
        require(raw.length == 3) { "ServiceCode must be 3 digits, was ${raw.length}" }
        require(raw.all { it in '0'..'9' }) { "ServiceCode must be ASCII digits only" }
    }

    override fun toString(): String = raw
}
