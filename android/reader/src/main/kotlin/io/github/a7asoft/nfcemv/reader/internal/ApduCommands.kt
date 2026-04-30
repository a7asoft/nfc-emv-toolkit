// why: every byte in an APDU header is spec-mandated by ISO/IEC 7816-4
// §5; naming each constant separately obscures the wire shape.
@file:Suppress("MagicNumber")

package io.github.a7asoft.nfcemv.reader.internal

import io.github.a7asoft.nfcemv.brand.Aid

/**
 * APDU command builders + status-word helpers per ISO/IEC 7816-4 §5.
 * All commands target contactless EMV applications; values are
 * spec-mandated bit patterns.
 */
internal object ApduCommands {

    /** Status word `90 00` indicates success per ISO/IEC 7816-4 §5.1.3. */
    internal const val SW_SUCCESS_HI: Byte = 0x90.toByte()
    internal const val SW_SUCCESS_LO: Byte = 0x00.toByte()

    /**
     * `SELECT 2PAY.SYS.DDF01` — discovers the contactless application
     * directory (PPSE) per EMV Book 1 §11.3.4.
     *
     * `00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00`
     *
     * Returns a fresh array per access so callers cannot mutate the
     * underlying bytes (same defensive-copy pattern as `Fixtures.*`).
     */
    val PPSE_SELECT: ByteArray
        get() = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
            // "2PAY.SYS.DDF01"
            0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53,
            0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
            0x00,
        )

    /**
     * `GET PROCESSING OPTIONS` with empty PDOL data (`83 00`).
     *
     * `80 A8 00 00 02 83 00 00`
     *
     * Returns a fresh array per access so callers cannot mutate the
     * underlying bytes.
     */
    val GPO_DEFAULT: ByteArray
        get() = byteArrayOf(
            0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02,
            0x83.toByte(), 0x00, 0x00,
        )

    /** Build a `SELECT` by AID command per ISO/IEC 7816-4 §5.4.1. */
    fun selectAid(aid: Aid): ByteArray {
        val aidBytes = aid.toBytes()
        val command = ByteArray(SELECT_HEADER_BYTES + aidBytes.size + LE_BYTES)
        command[0] = 0x00
        command[1] = 0xA4.toByte()
        command[2] = 0x04
        command[3] = 0x00
        command[LC_INDEX] = aidBytes.size.toByte()
        aidBytes.copyInto(command, destinationOffset = SELECT_HEADER_BYTES)
        return command
    }

    /**
     * Build a `READ RECORD` command per ISO/IEC 7816-4 §7.3.3.
     *
     * `00 B2 [recordNumber] [(sfi << 3) | 0x04] 00`
     */
    fun readRecord(recordNumber: Int, sfi: Int): ByteArray {
        require(recordNumber in 1..MAX_RECORD_NUMBER) {
            "recordNumber out of range: $recordNumber (must be 1..254 per ISO 7816-4 §7.3.3)"
        }
        require(sfi in 1..MAX_SFI) { "sfi out of range: $sfi" }
        val p2 = ((sfi shl SFI_SHIFT) or P2_RECORD_NUMBER_MODE).toByte()
        return byteArrayOf(0x00, 0xB2.toByte(), recordNumber.toByte(), p2, 0x00)
    }

    /** True when [response] ends with the `90 00` success status word. */
    fun isSuccess(response: ByteArray): Boolean {
        if (response.size < SW_BYTES) return false
        return response[response.size - 2] == SW_SUCCESS_HI &&
            response[response.size - 1] == SW_SUCCESS_LO
    }

    /**
     * Strip the 2-byte status word from [response]. Caller must ensure
     * `response.size >= 2` (use [isSuccess] first).
     */
    fun dataField(response: ByteArray): ByteArray =
        response.copyOfRange(0, response.size - SW_BYTES)

    /** Returns the trailing `(SW1, SW2)` pair from [response]. */
    fun statusWord(response: ByteArray): Pair<Byte, Byte> {
        require(response.size >= SW_BYTES)
        return response[response.size - 2] to response[response.size - 1]
    }

    private const val SELECT_HEADER_BYTES: Int = 5
    private const val LC_INDEX: Int = 4
    private const val LE_BYTES: Int = 1
    private const val SW_BYTES: Int = 2

    // why: ISO/IEC 7816-4 §7.3.3 reserves P1 = 0xFF for combined record
    // number / first-occurrence modes. For "READ RECORD by record number"
    // the valid P1 range is 0x01..0xFE.
    private const val MAX_RECORD_NUMBER: Int = 254
    private const val MAX_SFI: Int = 30
    private const val SFI_SHIFT: Int = 3
    private const val P2_RECORD_NUMBER_MODE: Int = 0x04
}
