package io.github.a7asoft.nfcemv.reader.internal

import java.io.IOException

/**
 * Stateful APDU exchange channel. Implementations wrap `IsoDep` in
 * production and a fake byte-array recorder in unit tests.
 *
 * Thread-safety contract:
 * - [connect] and [transceive] are called from the reader's IO-bound
 *   coroutine (via `flowOn(Dispatchers.IO)`). Implementations MUST be
 *   safe to call sequentially from a single thread; they need not be
 *   thread-safe across threads.
 * - [close] is called from the collector's context (via
 *   `onCompletion`), AFTER all [transceive] calls have completed. It
 *   may run on a different thread than [connect] / [transceive].
 *   Implementations MUST tolerate this (e.g. `IsoDep.close()` is safe
 *   to call from any thread per the platform docs).
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
