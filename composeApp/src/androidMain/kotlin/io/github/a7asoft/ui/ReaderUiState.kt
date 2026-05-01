package io.github.a7asoft.ui

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCard
import io.github.a7asoft.nfcemv.reader.ReaderError

/**
 * UI-mapped state for the reader screen.
 *
 * Combines NFC availability gates and the underlying
 * `io.github.a7asoft.nfcemv.reader.ReaderState` flow into a single
 * sealed catalogue so [ReaderScreen] renders from one source of truth.
 *
 * The mapping happens in [ReaderViewModel]; this file only declares the
 * shape.
 */
public sealed interface ReaderUiState {

    /** Device has no NFC hardware. Permanent "this app needs NFC" message. */
    public data object NfcUnavailable : ReaderUiState

    /** NFC is off in system settings. Show "Open NFC settings" CTA. */
    public data object NfcDisabled : ReaderUiState

    /** Ready to read. UI prompts "Tap a card to read". */
    public data object Idle : ReaderUiState

    /** Tag was just detected. UI shows initial progress. */
    public data object TagDetected : ReaderUiState

    /** Reader is in PPSE select stage. */
    public data object SelectingPpse : ReaderUiState

    /** Reader chose an AID and is selecting it. UI shows the AID hex. */
    public data class SelectingApplication(public val aid: Aid) : ReaderUiState

    /** Reader is iterating AFL records. */
    public data object ReadingRecords : ReaderUiState

    /** Read succeeded. UI displays the parsed [EmvCard]. */
    public data class Done(public val card: EmvCard) : ReaderUiState

    /** Read failed. UI displays a friendly message + diagnostic detail. */
    public data class Failed(public val error: ReaderError) : ReaderUiState
}
