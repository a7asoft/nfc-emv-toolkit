package io.github.a7asoft.nfcemv.tlv

/**
 * Outcome of [TlvDecoder.parse].
 *
 * Public APIs do not return [kotlin.Result] (discouraged by JetBrains for
 * library boundaries); a custom sealed type makes exhaustive `when`
 * destructuring possible without a compiler opt-in.
 */
public sealed interface TlvParseResult {
    /** Successful decode. */
    public data class Ok(val tlvs: List<Tlv>) : TlvParseResult

    /** Decode failed; see [error] for the typed reason and offset. */
    public data class Err(val error: TlvError) : TlvParseResult
}
