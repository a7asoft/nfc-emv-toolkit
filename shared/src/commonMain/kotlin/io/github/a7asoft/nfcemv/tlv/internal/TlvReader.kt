package io.github.a7asoft.nfcemv.tlv.internal

import io.github.a7asoft.nfcemv.tlv.TlvError
import io.github.a7asoft.nfcemv.tlv.TlvParseException

/**
 * Cursor over a [ByteArray] used by the BER-TLV decoder.
 *
 * Methods that consume bytes throw [TlvParseException] with [TlvError.UnexpectedEof]
 * when input is shorter than required. The decoder boundary catches and
 * wraps into [io.github.a7asoft.nfcemv.tlv.TlvParseResult.Err].
 */
internal class TlvReader(
    private val src: ByteArray,
    initialPos: Int = 0,
) {
    var pos: Int = initialPos
        private set

    val remaining: Int get() = src.size - pos

    val isEof: Boolean get() = pos >= src.size

    fun peek(): Byte {
        if (isEof) throw TlvParseException(TlvError.UnexpectedEof(pos))
        return src[pos]
    }

    fun read(): Byte {
        val b = peek()
        pos++
        return b
    }

    fun readBytes(n: Int): ByteArray {
        requireNonNegative(n)
        if (remaining < n) throw TlvParseException(TlvError.UnexpectedEof(pos))
        val out = src.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    fun skip(n: Int) {
        requireNonNegative(n)
        if (remaining < n) throw TlvParseException(TlvError.UnexpectedEof(pos))
        pos += n
    }

    private fun requireNonNegative(n: Int) {
        require(n >= 0) { "Count must be non-negative: $n" }
    }
}
