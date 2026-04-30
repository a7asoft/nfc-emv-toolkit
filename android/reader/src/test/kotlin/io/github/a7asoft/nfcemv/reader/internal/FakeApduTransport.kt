package io.github.a7asoft.nfcemv.reader.internal

import java.io.IOException

/**
 * Test [ApduTransport] that replays a hand-built APDU script.
 *
 * Each script entry is a `(commandPrefix, response)` pair: the fake
 * verifies the command starts with [commandPrefix] (lets tests check
 * just the header for variable AID-bearing commands) and returns the
 * canned response.
 */
internal class FakeApduTransport(
    private val script: List<Pair<ByteArray, ByteArray>>,
    private val connectError: IOException? = null,
) : ApduTransport {

    private var connected: Boolean = false
    private var index: Int = 0
    var closed: Boolean = false
        private set

    override fun connect() {
        connectError?.let { throw it }
        connected = true
    }

    override fun transceive(command: ByteArray): ByteArray {
        check(connected) { "transceive called before connect" }
        check(index < script.size) {
            "no scripted response for command #$index (size=${command.size})"
        }
        val (expectedPrefix, response) = script[index]
        check(command.startsWithBytes(expectedPrefix)) {
            "command #$index does not match expected prefix"
        }
        index++
        return response
    }

    override fun close() {
        closed = true
    }

    @Suppress("CyclomaticComplexMethod")
    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }
}
