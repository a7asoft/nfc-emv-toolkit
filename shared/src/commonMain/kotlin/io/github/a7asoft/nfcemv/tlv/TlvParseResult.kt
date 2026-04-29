package io.github.a7asoft.nfcemv.tlv

/**
 * Outcome of [TlvDecoder.parse].
 *
 * A sealed type allows callers to exhaustively destructure the success and
 * failure cases without using exceptions for control flow.
 */
public sealed interface TlvParseResult {
    /** Successful decode. */
    public data class Ok(val tlvs: List<Tlv>) : TlvParseResult

    /** Decode failed; see [error] for the typed reason and offset. */
    public data class Err(val error: TlvError) : TlvParseResult
}
