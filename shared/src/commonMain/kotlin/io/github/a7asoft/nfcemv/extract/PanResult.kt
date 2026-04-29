package io.github.a7asoft.nfcemv.extract

/**
 * The outcome of `Pan.parse(raw)`. Either an `Ok` carrying a fully
 * validated [Pan] or an `Err` carrying a typed [PanError] reason.
 *
 * Mirrors the `tlv` decoder's `TlvParseResult` sealed pattern so callers
 * get a consistent control-flow shape across the toolkit.
 */
public sealed interface PanResult {
    public data class Ok(val pan: Pan) : PanResult
    public data class Err(val error: PanError) : PanResult
}
