package io.github.a7asoft.ui

import io.github.a7asoft.nfcemv.reader.ReaderState
import kotlinx.coroutines.flow.Flow

/**
 * Single-method abstraction over a contactless read driver.
 *
 * Decouples [ReaderViewModel] from `android.nfc.Tag`: production wiring
 * (`MainActivity`) wraps a `Tag` in a handle that calls
 * `ContactlessReader.fromTag(tag).read()`; unit tests pass
 * `ReaderHandle { flowOf(...) }` directly. This is what makes the
 * ViewModel testable without Robolectric.
 */
public fun interface ReaderHandle {
    /** Drive a single contactless read; emits one [ReaderState] per stage. */
    public fun read(): Flow<ReaderState>
}
