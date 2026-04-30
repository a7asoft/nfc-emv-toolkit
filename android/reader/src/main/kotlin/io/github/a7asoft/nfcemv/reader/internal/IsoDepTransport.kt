package io.github.a7asoft.nfcemv.reader.internal

import android.nfc.tech.IsoDep

/**
 * Production [ApduTransport] backed by Android's [IsoDep] tech.
 *
 * The ONLY file in this module that imports from `android.nfc.*`. All
 * other reader logic operates against the [ApduTransport] abstraction
 * for testability.
 */
internal class IsoDepTransport(private val isoDep: IsoDep) : ApduTransport {
    override fun connect() {
        isoDep.connect()
    }

    override fun transceive(command: ByteArray): ByteArray = isoDep.transceive(command)

    override fun close() {
        isoDep.close()
    }
}
