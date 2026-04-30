package io.github.a7asoft.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.a7asoft.nfc.NfcAvailability
import io.github.a7asoft.nfc.NfcAvailabilityStatus
import io.github.a7asoft.nfcemv.reader.ReaderState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Re-check NFC availability — call from MainActivity.onResume. */
    public fun refreshNfcAvailability() {
        if (_state.value is ReaderUiState.Done || _state.value is ReaderUiState.Failed) return
        _state.value = initialState()
    }

    /** Begin a read using [handle]. Cancels any in-flight read first. */
    public fun onReadRequested(handle: ReaderHandle) {
        if (_state.value !is ReaderUiState.Idle) return
        activeRead.getAndSet(null)?.cancel()
        val job = handle.read()
            .onEach { state -> _state.value = mapReaderState(state) }
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
    // why: exhaustive `when` over the sealed [NfcAvailabilityStatus] catalogue.
    // Each branch is a single mapping, not real cyclomatic complexity.
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
