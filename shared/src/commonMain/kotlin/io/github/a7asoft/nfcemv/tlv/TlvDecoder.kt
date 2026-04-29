package io.github.a7asoft.nfcemv.tlv

import io.github.a7asoft.nfcemv.tlv.internal.TlvReader
import io.github.a7asoft.nfcemv.tlv.internal.readNode
import io.github.a7asoft.nfcemv.tlv.internal.skipZeroPaddingToEof

/**
 * Public entry point for BER-TLV decoding.
 *
 * The input MUST be the data field of a card response, with SW1 SW2 status
 * bytes stripped by the transport layer. Passing raw APDU output produces a
 * fake trailing primitive (the SW bytes parse as a valid TLV); the decoder
 * has no way to detect this at the BER-TLV layer.
 *
 * Two API styles are offered:
 * - [parse] returns a sealed [TlvParseResult] for callers who prefer
 *   type-driven control flow.
 * - [parseOrThrow] throws [TlvParseException] for callers who prefer
 *   try / catch.
 *
 * Both share the same error catalogue ([TlvError]).
 */
public object TlvDecoder {

    /**
     * Decode [input] into a list of top-level TLV nodes.
     *
     * @return [TlvParseResult.Ok] on success, [TlvParseResult.Err] on the first
     *   detected violation. The decoder does not partially recover.
     */
    public fun parse(input: ByteArray, options: TlvOptions = TlvOptions()): TlvParseResult {
        return try {
            TlvParseResult.Ok(decodeAll(input, options))
        } catch (e: TlvParseException) {
            TlvParseResult.Err(e.error)
        }
    }

    /**
     * Decode [input] into a list of top-level TLV nodes, or throw
     * [TlvParseException] on the first detected violation.
     */
    public fun parseOrThrow(input: ByteArray, options: TlvOptions = TlvOptions()): List<Tlv> =
        decodeAll(input, options)

    private fun decodeAll(input: ByteArray, options: TlvOptions): List<Tlv> {
        val reader = TlvReader(input)
        val nodes = mutableListOf<Tlv>()
        while (!reader.isEof) {
            if (options.tolerateZeroPadding) skipZeroPaddingToEof(reader)
            if (reader.isEof) break
            nodes.add(readNode(reader, options, depth = 0))
        }
        return nodes
    }
}
