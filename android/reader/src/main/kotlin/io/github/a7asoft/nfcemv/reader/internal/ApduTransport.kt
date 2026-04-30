package io.github.a7asoft.nfcemv.reader.internal

import java.io.IOException

/**
 * Stateful APDU exchange channel. Implementations wrap `IsoDep` in
 * production and a fake byte-array recorder in unit tests.
 *
 * Implementations are NOT thread-safe; the reader's flow uses a single
 * IO-bound coroutine and closes the transport on completion.
 */
internal interface ApduTransport {
    @Throws(IOException::class)
    fun connect()

    /**
     * Send [command] to the card, return the response (data field +
     * 2-byte status word). Throws [IOException] on RF errors, including
     * the special `android.nfc.TagLostException` wrapper.
     */
    @Throws(IOException::class)
    fun transceive(command: ByteArray): ByteArray

    fun close()
}
