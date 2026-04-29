package io.github.a7asoft.nfcemv.extract

/**
 * The outcome of `EmvParser.parse(apduResponses)`. Either `Ok` carrying
 * a fully composed [EmvCard] or `Err` carrying a typed [EmvCardError].
 *
 * Mirrors `TlvParseResult`, `PanResult`, and `Track2Result` for a
 * consistent control-flow shape across the toolkit.
 */
public sealed interface EmvCardResult {
    /** Successful parse carrying the composed [EmvCard]. */
    public data class Ok(val card: EmvCard) : EmvCardResult

    /** Parse failed; see [error] for the typed reason. */
    public data class Err(val error: EmvCardError) : EmvCardResult
}
