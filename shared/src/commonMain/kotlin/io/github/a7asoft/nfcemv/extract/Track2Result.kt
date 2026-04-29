package io.github.a7asoft.nfcemv.extract

/**
 * The outcome of `Track2.parse(raw)`. Either `Ok` carrying a fully
 * validated [Track2] or `Err` carrying a typed [Track2Error].
 *
 * Mirrors `TlvParseResult` and `PanResult` for consistent control flow
 * across the toolkit.
 */
public sealed interface Track2Result {
    /** Successful parse carrying the validated [Track2]. */
    public data class Ok(val track2: Track2) : Track2Result

    /** Failed parse carrying the typed [Track2Error]. */
    public data class Err(val error: Track2Error) : Track2Result
}
