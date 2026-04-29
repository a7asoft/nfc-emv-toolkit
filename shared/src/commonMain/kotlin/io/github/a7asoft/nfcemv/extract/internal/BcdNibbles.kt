package io.github.a7asoft.nfcemv.extract.internal

/**
 * Total number of BCD nibbles in this byte array (two per byte, high-first).
 */
internal fun ByteArray.nibbleCount(): Int = size * 2

/**
 * Read the BCD nibble at the given zero-based index. Index `0` is the high
 * nibble of byte `0`; index `1` is the low nibble of byte `0`; etc.
 *
 * @throws IndexOutOfBoundsException if [index] is outside `0 until nibbleCount()`.
 */
internal fun ByteArray.nibbleAt(index: Int): Int {
    if (index < 0 || index >= nibbleCount()) {
        throw IndexOutOfBoundsException(
            "nibble index $index out of bounds for nibbleCount=${nibbleCount()}",
        )
    }
    val byte = this[index / 2].toInt() and 0xFF
    return if (index % 2 == 0) (byte ushr 4) and 0xF else byte and 0xF
}
