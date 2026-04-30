package io.github.a7asoft.nfcemv.extract

/**
 * The outcome of `ServiceCode.parse(raw)`. Either an `Ok` carrying a
 * fully validated [ServiceCode] or an `Err` carrying a typed
 * [ServiceCodeError] reason.
 *
 * Mirrors the `Pan` and `tlv` decoder result types so callers get a
 * consistent control-flow shape across the toolkit.
 */
public sealed interface ServiceCodeResult {
    public data class Ok(val serviceCode: ServiceCode) : ServiceCodeResult
    public data class Err(val error: ServiceCodeError) : ServiceCodeResult
}
