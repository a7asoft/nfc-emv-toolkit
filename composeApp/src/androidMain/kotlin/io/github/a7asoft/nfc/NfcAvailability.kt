package io.github.a7asoft.nfc

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager

/**
 * Categorical NFC capability state for UI display.
 *
 * Mapped from the platform `NfcAdapter` API; abstracted via [NfcAvailability]
 * so the ViewModel can be unit-tested without Robolectric or a real device.
 */
public sealed interface NfcAvailabilityStatus {

    /** Device has no NFC hardware. App cannot read cards. */
    public data object Unavailable : NfcAvailabilityStatus

    /** NFC hardware exists but is disabled in system settings. User can fix. */
    public data object Disabled : NfcAvailabilityStatus

    /** NFC hardware exists and is enabled. Ready to read. */
    public data object Available : NfcAvailabilityStatus
}

/**
 * Abstraction over platform NFC capability checks. Production impl is
 * [SystemNfcAvailability]; tests inject a fake.
 */
public fun interface NfcAvailability {
    /** Snapshot the current device NFC capability state. */
    public fun current(): NfcAvailabilityStatus
}

/**
 * Production [NfcAvailability] backed by [NfcAdapter].
 *
 * Captures the [Context] only as an Application context so it cannot
 * leak Activity references. Re-checks on every [current] call so the
 * ViewModel can see system-settings changes after `onResume`.
 */
public class SystemNfcAvailability(context: Context) : NfcAvailability {

    private val applicationContext: Context = context.applicationContext

    override fun current(): NfcAvailabilityStatus {
        // why: NfcAdapter.getDefaultAdapter(Context) is deprecated since
        // API 33; use the NfcManager system service. minSdk 24 covers
        // getSystemService(Class).
        val adapter = applicationContext.getSystemService(NfcManager::class.java)?.defaultAdapter
            ?: return NfcAvailabilityStatus.Unavailable
        return if (adapter.isEnabled) NfcAvailabilityStatus.Available else NfcAvailabilityStatus.Disabled
    }
}
