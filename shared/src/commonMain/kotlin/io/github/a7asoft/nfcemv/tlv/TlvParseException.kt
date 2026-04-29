package io.github.a7asoft.nfcemv.tlv

/**
 * Thrown by [TlvDecoder.parseOrThrow] (and internally by the decoder) to
 * carry a structured [TlvError].
 *
 * Public callers that prefer exceptions over [TlvParseResult] receive this
 * type; both API styles share the same error catalogue.
 *
 * The message intentionally exposes only the error class name and offset,
 * never value bytes.
 */
public class TlvParseException(
    public val error: TlvError,
) : RuntimeException("TLV parse failed at offset ${error.offset}: ${error::class.simpleName}")
