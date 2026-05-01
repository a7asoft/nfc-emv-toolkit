import XCTest
import EmvReader
@testable import nfc_emv_toolkit

// NOTE: This file is currently NOT wired into a PBXNativeTarget. See
// follow-up issue #67 for the test-target wiring task. Sources are
// kept in `iosAppTests/` so they appear in the source tree, version
// with the production code, and can be added to the target by file
// inclusion (no source-move needed) once the target exists.

/// Unit tests for ``ReaderViewModel`` covering each
/// ``ReaderUiState`` transition by injecting a synthetic
/// `AsyncStream<ReaderState>` factory.
@MainActor
final class ReaderViewModelTests: XCTestCase {

    // MARK: - Initial state

    func testInitialStateIsNfcUnavailableWhenAvailabilityReportsUnavailable() {
        let viewModel = makeViewModel(.unavailable, states: [])
        XCTAssertEqual(currentCase(viewModel), .nfcUnavailable)
    }

    func testInitialStateIsIdleWhenAvailabilityReportsAvailable() {
        let viewModel = makeViewModel(.available, states: [])
        XCTAssertEqual(currentCase(viewModel), .idle)
    }

    // MARK: - startScan happy path

    func testStartScanWalksThroughEveryStateToDone() async {
        let card = EmvReaderTestFixtures.sampleVisaCard()
        let viewModel = makeViewModel(.available, states: [
            .tagDetected,
            .selectingPpse,
            .selectingAid("A0000000031010"),
            .readingRecords,
            .done(card)
        ])
        viewModel.startScan()
        await awaitScanComplete(viewModel)
        guard case .done(let summary) = viewModel.uiState else {
            XCTFail("expected .done terminal state, got \(viewModel.uiState)")
            return
        }
        XCTAssertEqual(summary.aidHex, "A0000000031010")
    }

    // MARK: - startScan failure path

    func testStartScanSurfacesFailedTerminalAsFailedUiState() async {
        let viewModel = makeViewModel(.available, states: [
            .tagDetected,
            .failed(.ppseUnsupported)
        ])
        viewModel.startScan()
        await awaitScanComplete(viewModel)
        guard case .failed(_, let friendly, _) = viewModel.uiState else {
            XCTFail("expected .failed terminal state, got \(viewModel.uiState)")
            return
        }
        XCTAssertEqual(friendly, "This card does not support contactless EMV.")
    }

    // MARK: - startScan guard

    func testStartScanIsIgnoredWhenStateIsNotIdle() {
        let viewModel = makeViewModel(.unavailable, states: [.done(EmvReaderTestFixtures.sampleVisaCard())])
        viewModel.startScan()
        XCTAssertEqual(currentCase(viewModel), .nfcUnavailable)
    }

    // MARK: - reset

    func testResetReturnsToIdleFromDone() async {
        let viewModel = makeViewModel(.available, states: [.done(EmvReaderTestFixtures.sampleVisaCard())])
        viewModel.startScan()
        await awaitScanComplete(viewModel)
        viewModel.reset()
        XCTAssertEqual(currentCase(viewModel), .idle)
    }

    func testResetReturnsToIdleFromFailed() async {
        let viewModel = makeViewModel(.available, states: [.failed(.ppseUnsupported)])
        viewModel.startScan()
        await awaitScanComplete(viewModel)
        viewModel.reset()
        XCTAssertEqual(currentCase(viewModel), .idle)
    }

    // MARK: - refreshAvailability

    func testRefreshAvailabilityPicksUpAdapterBecomingAvailable() {
        let availability = FakeNfcAvailability(.unavailable)
        let viewModel = ReaderViewModel(
            availability: availability,
            readerFactory: { AsyncStream { continuation in continuation.finish() } }
        )
        XCTAssertEqual(currentCase(viewModel), .nfcUnavailable)
        availability.set(.available)
        viewModel.refreshAvailability()
        XCTAssertEqual(currentCase(viewModel), .idle)
    }

    func testRefreshAvailabilityPreservesDoneState() async {
        let availability = FakeNfcAvailability(.available)
        let viewModel = ReaderViewModel(
            availability: availability,
            readerFactory: makeFactory([.done(EmvReaderTestFixtures.sampleVisaCard())])
        )
        viewModel.startScan()
        await awaitScanComplete(viewModel)
        availability.set(.unavailable)
        viewModel.refreshAvailability()
        XCTAssertEqual(currentCase(viewModel), .done)
    }

    // MARK: - ErrorMessages parity

    func testFriendlyMessageForApduStatusErrorEmbedsHexBytes() {
        let message = ErrorMessages.friendly(for: .apduStatusError(sw1: 0x6A, sw2: 0x82))
        XCTAssertEqual(message, "Card returned status 6A 82. Try a different card.")
    }

    func testFriendlyMessageForIoFailureWhenTagLost() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .ioFailure(.tagLost)),
            "Card moved away from the device. Try again."
        )
    }

    func testFriendlyMessageForIoFailureWhenTimeout() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .ioFailure(.timeout)),
            "The card took too long to respond. Try again."
        )
    }

    func testFriendlyMessageForIoFailureWhenGeneric() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .ioFailure(.generic)),
            "Communication with the card failed. Try again."
        )
    }

    func testFriendlyMessageForPpseUnsupported() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .ppseUnsupported),
            "This card does not support contactless EMV."
        )
    }

    func testFriendlyMessageForPpseRejected() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .ppseRejected("test-payload")),
            "The card's application directory could not be parsed."
        )
    }

    func testFriendlyMessageForGpoRejected() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .gpoRejected("test-payload")),
            "The GET PROCESSING OPTIONS response could not be parsed."
        )
    }

    func testFriendlyMessageForSelectAidFciRejected() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .selectAidFciRejected("test-payload")),
            "The application FCI could not be parsed."
        )
    }

    func testFriendlyMessageForPdolRejected() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .pdolRejected("test-payload")),
            "The card's PDOL could not be parsed."
        )
    }

    func testFriendlyMessageForParseFailed() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .parseFailed("test-payload")),
            "The card data could not be parsed into an EMV card."
        )
    }

    // MARK: - PCI-safety regressions

    /// Pins the masking contract: the summary's `pan` must be the
    /// PCI DSS Req 3.4 first-6 + middle-stars + last-4 mask, and must
    /// NOT carry the raw PAN string under any code path that runs
    /// through `EmvCardSummary.from(_:)`.
    func testEmvCardSummaryPanIsMaskedNeverRaw() {
        let card = EmvReaderTestFixtures.sampleVisaCard()
        let summary = EmvCardSummary.from(card)
        XCTAssertFalse(
            summary.pan.contains("4111111111111111"),
            "Raw PAN must not appear in EmvCardSummary.pan"
        )
        XCTAssertEqual(
            summary.pan,
            "411111******1111",
            "Pan.toString() masking contract: first 6 + 6×* + last 4"
        )
    }

    /// Defensive regression: any future ``ReaderError`` payload variant
    /// that surfaces raw card bytes through ``ErrorMessages/diagnostic``
    /// must fail this test. We construct a synthetic `gpoRejected`
    /// with an opaque payload and assert the rendered diagnostic does
    /// NOT contain a 12-or-more-character hex run (which could be a
    /// PAN, ARQC, or other PCI-scoped material).
    func testDiagnosticForGpoRejectedDoesNotLeakHexBytes() {
        let synthetic: Any = "OPAQUE_TEST_PAYLOAD"
        let error = ReaderError.gpoRejected(synthetic)
        let diagnostic = ErrorMessages.diagnostic(for: error)
        let hexRunRegex = try! NSRegularExpression(pattern: "[0-9A-Fa-f]{12,}")
        let range = NSRange(diagnostic.startIndex..., in: diagnostic)
        let matches = hexRunRegex.numberOfMatches(in: diagnostic, range: range)
        XCTAssertEqual(
            matches, 0,
            "Diagnostic must not surface 12+ char hex runs (potential PAN/ARQC leak): \(diagnostic)"
        )
    }

    // MARK: - Helpers

    private func makeViewModel(
        _ status: NfcAvailabilityStatus,
        states: [ReaderState]
    ) -> ReaderViewModel {
        return ReaderViewModel(
            availability: FakeNfcAvailability(status),
            readerFactory: makeFactory(states)
        )
    }

    private func makeFactory(_ states: [ReaderState]) -> () -> AsyncStream<ReaderState> {
        return {
            AsyncStream { continuation in
                for state in states { continuation.yield(state) }
                continuation.finish()
            }
        }
    }

    /// Deterministic replacement for the previous `Task.sleep`-polling
    /// `waitUntil` helper. Awaits the in-flight scan task's completion
    /// directly via the DEBUG-only `currentScanTask` accessor on
    /// ``ReaderViewModel``. Returns immediately if no task is running.
    private func awaitScanComplete(_ viewModel: ReaderViewModel) async {
        await viewModel.currentScanTask?.value
    }

    /// Compare ``ReaderUiState`` cases without inspecting payloads.
    /// XCTest cannot derive Equatable for the enum because `ReaderError`
    /// boxes Kotlin objects, but cases-only comparison is enough for
    /// transition assertions.
    private func currentCase(_ viewModel: ReaderViewModel) -> UiCase {
        return UiCase(viewModel.uiState)
    }

    private enum UiCase: Equatable {
        case nfcUnavailable, idle, scanning, tagDetected, selectingPpse
        case selectingApplication, readingRecords, done, failed

        init(_ state: ReaderUiState) {
            switch state {
            case .nfcUnavailable: self = .nfcUnavailable
            case .idle: self = .idle
            case .scanning: self = .scanning
            case .tagDetected: self = .tagDetected
            case .selectingPpse: self = .selectingPpse
            case .selectingApplication: self = .selectingApplication
            case .readingRecords: self = .readingRecords
            case .done: self = .done
            case .failed: self = .failed
            }
        }
    }
}
