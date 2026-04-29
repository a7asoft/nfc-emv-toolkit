package io.github.a7asoft.nfcemv.tlv.internal

private const val HEX_DIGITS = "0123456789ABCDEF"

internal fun ByteArray.toUpperHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) appendHex(out, b)
    return out.toString()
}

private fun appendHex(builder: StringBuilder, byte: Byte) {
    val v = byte.toInt() and 0xFF
    builder.append(HEX_DIGITS[v ushr 4])
    builder.append(HEX_DIGITS[v and 0x0F])
}

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    val out = ByteArray(length / 2)
    for (i in out.indices) out[i] = hexByteAt(i * 2)
    return out
}

private fun String.hexByteAt(index: Int): Byte {
    val hi = hexDigit(this[index])
    val lo = hexDigit(this[index + 1])
    return ((hi shl 4) or lo).toByte()
}

private fun hexDigit(c: Char): Int {
    val idx = HEX_DIGITS.indexOf(c.uppercaseChar())
    require(idx >= 0) { "Invalid hex digit: $c" }
    return idx
}
