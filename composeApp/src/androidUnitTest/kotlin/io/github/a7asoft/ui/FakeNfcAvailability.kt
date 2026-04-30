package io.github.a7asoft.ui

import io.github.a7asoft.nfc.NfcAvailability
import io.github.a7asoft.nfc.NfcAvailabilityStatus
import java.util.concurrent.atomic.AtomicReference

internal class FakeNfcAvailability(initial: NfcAvailabilityStatus) : NfcAvailability {
    private val current: AtomicReference<NfcAvailabilityStatus> = AtomicReference(initial)
    override fun current(): NfcAvailabilityStatus = current.get()
    fun set(status: NfcAvailabilityStatus) {
        current.set(status)
    }
}
