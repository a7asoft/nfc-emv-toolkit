import Foundation
import EmvReader
import Shared

/// Owns the ``ReaderUiState`` for the reader screen.
///
/// Decouples the SwiftUI view tree from the `EmvReader` AsyncStream
/// lifecycle. Mirrors the Android `ReaderViewModel` (composeApp #55):
/// platform dependencies are injected via initialiser closures so the
/// type can be unit-tested without a simulator.
///
/// Concurrency: the read task runs on the main actor — every state
/// transition happens on the UI thread, matching SwiftUI's expectations
/// for `@Published` mutations.
@MainActor
public final class ReaderViewModel: ObservableObject {

    /// Current UI state. Drives ``ReaderScreen`` rendering. UI cannot
    /// mutate; only ``startScan()``, ``reset()``, and
    /// ``refreshAvailability()`` do.
    @Published public private(set) var uiState: ReaderUiState

    private let availability: NfcAvailability
    private let readerFactory: () -> AsyncStream<ReaderState>
    private var activeTask: Task<Void, Never>?

    /// Build a view-model with explicit dependencies.
    ///
    /// - Parameters:
    ///   - availability: NFC capability checker. Production:
    ///     ``SystemNfcAvailability``.
    ///   - readerFactory: Factory producing a fresh
    ///     `AsyncStream<ReaderState>` per scan. Production:
    ///     `{ EmvReader().read() }`.
    public init(
        availability: NfcAvailability,
        readerFactory: @escaping () -> AsyncStream<ReaderState>
    ) {
        self.availability = availability
        self.readerFactory = readerFactory
        self.uiState = Self.initialState(availability: availability)
    }

    /// Re-check NFC availability — call from `.onAppear`.
    ///
    /// Only refreshes when state is one of the resettable gates
    /// (``ReaderUiState/nfcUnavailable`` or ``ReaderUiState/idle``).
    /// In-flight reads and terminal states (``ReaderUiState/done(card:)``,
    /// ``ReaderUiState/failed(error:friendlyMessage:diagnostic:)``) are
    /// preserved so the user does not lose progress or results.
    public func refreshAvailability() {
        if isResettable(uiState) {
            uiState = Self.initialState(availability: availability)
        }
    }

    /// Begin a new read. No-op unless current state is
    /// ``ReaderUiState/idle``.
    ///
    /// The stream factory is invoked synchronously; the resulting
    /// ``EmvReader`` AsyncStream is consumed on the main actor and
    /// each ``ReaderState`` is mapped into the UI state.
    public func startScan() {
        guard case .idle = uiState else { return }
        uiState = .scanning
        let stream = readerFactory()
        activeTask?.cancel()
        activeTask = Task { [weak self] in
            await self?.consume(stream)
        }
    }

    /// Reset to the initial state after a Done/Failed terminal.
    public func reset() {
        activeTask?.cancel()
        activeTask = nil
        uiState = Self.initialState(availability: availability)
    }

    private func consume(_ stream: AsyncStream<ReaderState>) async {
        for await state in stream {
            uiState = map(state)
        }
    }

    private func map(_ state: ReaderState) -> ReaderUiState {
        switch state {
        case .tagDetected: return .tagDetected
        case .selectingPpse: return .selectingPpse
        case .selectingAid(let aid): return .selectingApplication(aidHex: String(describing: aid).uppercased())
        case .readingRecords: return .readingRecords
        case .done(let card): return .done(card: EmvCardSummary.from(card))
        case .failed(let error): return mapFailure(error)
        }
    }

    private func mapFailure(_ error: ReaderError) -> ReaderUiState {
        return .failed(
            error: error,
            friendlyMessage: ErrorMessages.friendly(for: error),
            diagnostic: ErrorMessages.diagnostic(for: error)
        )
    }

    private func isResettable(_ state: ReaderUiState) -> Bool {
        switch state {
        case .nfcUnavailable, .idle: return true
        default: return false
        }
    }

    private static func initialState(availability: NfcAvailability) -> ReaderUiState {
        switch availability.current() {
        case .available: return .idle
        case .unavailable: return .nfcUnavailable
        }
    }
}

/// Friendly + diagnostic strings for ``ReaderError`` variants.
///
/// Mirrors the Android composeApp `ErrorPanel.friendlyMessage`. Kept
/// `internal` so tests can drive the same mapping without going through
/// the view-model.
internal enum ErrorMessages {

    static func friendly(for error: ReaderError) -> String {
        switch error {
        case .ioFailure(let reason): return ioMessage(reason)
        case .ppseUnsupported: return "This card does not support contactless EMV."
        case .ppseRejected: return "The card's application directory could not be parsed."
        case .gpoRejected: return "The GET PROCESSING OPTIONS response could not be parsed."
        case .selectAidFciRejected: return "The application FCI could not be parsed."
        case .pdolRejected: return "The card's PDOL could not be parsed."
        case .apduStatusError(let sw1, let sw2):
            return String(format: "Card returned status %02X %02X. Try a different card.", sw1, sw2)
        case .parseFailed: return "The card data could not be parsed into an EMV card."
        }
    }

    static func diagnostic(for error: ReaderError) -> String {
        switch error {
        case .ioFailure(let reason): return "ioFailure(\(reason))"
        case .ppseUnsupported: return "ppseUnsupported"
        case .ppseRejected(let cause): return "ppseRejected(\(String(describing: cause)))"
        case .gpoRejected(let cause): return "gpoRejected(\(String(describing: cause)))"
        case .selectAidFciRejected(let cause): return "selectAidFciRejected(\(String(describing: cause)))"
        case .pdolRejected(let cause): return "pdolRejected(\(String(describing: cause)))"
        case .apduStatusError(let sw1, let sw2):
            return String(format: "apduStatusError(SW1=%02X, SW2=%02X)", sw1, sw2)
        case .parseFailed(let cause): return "parseFailed(\(String(describing: cause)))"
        }
    }

    private static func ioMessage(_ reason: IoReason) -> String {
        switch reason {
        case .tagLost: return "Card moved away from the device. Try again."
        case .timeout: return "The card took too long to respond. Try again."
        case .generic: return "Communication with the card failed. Try again."
        }
    }
}
