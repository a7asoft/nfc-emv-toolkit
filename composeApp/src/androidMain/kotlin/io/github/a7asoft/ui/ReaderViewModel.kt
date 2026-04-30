package io.github.a7asoft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.a7asoft.nfc.NfcAvailability
import io.github.a7asoft.nfc.NfcAvailabilityStatus
import io.github.a7asoft.nfcemv.reader.IoReason
import io.github.a7asoft.nfcemv.reader.ReaderError
import io.github.a7asoft.nfcemv.reader.ReaderState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the [ReaderUiState] for the reader screen.
 *
 * Plain [ViewModel] (not [androidx.lifecycle.AndroidViewModel]) so it
 * can be unit-tested without Robolectric. Platform-specific dependencies
 * ([NfcAvailability], the [ReaderHandle] per read) are injected via the
 * [provideFactory] builder and the public API.
 *
 * Concurrency contract: only one read flow runs at a time. A new
 * [onReadRequested] cancels any in-flight collection before starting
 * the new one (defensive against rapid taps).
 */
public class ReaderViewModel internal constructor(
    private val nfcAvailability: NfcAvailability,
) : ViewModel() {

    private val _state: MutableStateFlow<ReaderUiState> = MutableStateFlow(initialState())

    /** Current UI state. UI cannot mutate; only [onReadRequested] / [reset] / [refreshNfcAvailability] do. */
    public val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val activeRead: AtomicReference<Job?> = AtomicReference(null)

    /**
     * Re-check NFC availability — call from `MainActivity.onResume`.
     *
     * Only refreshes when state is one of the resettable gates
     * ([ReaderUiState.NfcUnavailable], [ReaderUiState.NfcDisabled],
     * [ReaderUiState.Idle]). In-flight reads (TagDetected … ReadingRecords)
     * and terminal states (Done, Failed) are preserved so the user does
     * not lose progress or results when returning from system settings.
     */
    @Suppress("CyclomaticComplexMethod")
    // why: 3-branch sealed dispatch over the resettable subset of
    // [ReaderUiState]; detekt counts the function itself as +1, tipping
    // the threshold. The suppression matches that algorithm.
    public fun refreshNfcAvailability() {
        val current = _state.value
        val refreshable = current is ReaderUiState.NfcUnavailable ||
            current is ReaderUiState.NfcDisabled ||
            current is ReaderUiState.Idle
        if (refreshable) _state.value = initialState()
    }

    /**
     * Begin a read using [handle]. Cancels any in-flight read first.
     *
     * Uses an atomic Idle → TagDetected transition to guard against
     * concurrent invocations from NFC reader-mode binder-thread callbacks
     * (a TOCTOU race that a plain `if (state is Idle)` check cannot
     * defend against).
     */
    public fun onReadRequested(handle: ReaderHandle) {
        // why: atomic gate. NFC reader-mode callbacks fire on a binder
        // thread, not main; two concurrent taps could both pass a naive
        // `is Idle` check. compareAndSet is the smallest defensible fix.
        // The flow's first emission may overwrite TagDetected with the
        // same TagDetected (or the next state) — UI effect is identical.
        if (!_state.compareAndSet(ReaderUiState.Idle, ReaderUiState.TagDetected)) return
        activeRead.getAndSet(null)?.cancel()
        val job = handle.read()
            .onEach { state -> _state.value = mapReaderState(state) }
            // why: defensive. Any unexpected upstream throw should land as
            // Failed, not propagate to viewModelScope's exception handler.
            .catch { _state.value = ReaderUiState.Failed(ReaderError.IoFailure(IoReason.Generic)) }
            .launchIn(viewModelScope)
        activeRead.set(job)
    }

    /** Reset to the initial state after a Done/Failed terminal. */
    public fun reset() {
        activeRead.getAndSet(null)?.cancel()
        _state.value = initialState()
    }

    override fun onCleared() {
        activeRead.getAndSet(null)?.cancel()
        super.onCleared()
    }

    @Suppress("CyclomaticComplexMethod")
    // why: 3-branch sealed dispatch over [NfcAvailabilityStatus]. detekt's
    // branch-counting algorithm re-flags this at threshold 4; the
    // suppression matches that algorithm.
    private fun initialState(): ReaderUiState = when (nfcAvailability.current()) {
        NfcAvailabilityStatus.Unavailable -> ReaderUiState.NfcUnavailable
        NfcAvailabilityStatus.Disabled -> ReaderUiState.NfcDisabled
        NfcAvailabilityStatus.Available -> ReaderUiState.Idle
    }

    @Suppress("CyclomaticComplexMethod")
    // why: exhaustive `when` over the sealed `ReaderState` catalogue is the
    // project idiom (CLAUDE.md §3.2). Each branch is a single mapping, not
    // an arm of real cyclomatic complexity.
    private fun mapReaderState(state: ReaderState): ReaderUiState = when (state) {
        is ReaderState.TagDetected -> ReaderUiState.TagDetected
        is ReaderState.SelectingPpse -> ReaderUiState.SelectingPpse
        is ReaderState.SelectingAid -> ReaderUiState.SelectingApplication(state.aid)
        is ReaderState.ReadingRecords -> ReaderUiState.ReadingRecords
        is ReaderState.Done -> ReaderUiState.Done(state.card)
        is ReaderState.Failed -> ReaderUiState.Failed(state.error)
    }

    public companion object {
        /**
         * Build the production-wired [ViewModelProvider.Factory].
         *
         * @param availability NFC capability checker (typically
         *   [io.github.a7asoft.nfc.SystemNfcAvailability]).
         */
        public fun provideFactory(
            availability: NfcAvailability,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReaderViewModel(availability) }
        }
    }
}
