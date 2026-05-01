import XCTest
import EmvReader
@testable import nfc_emv_toolkit

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
        let card = sampleCard()
        let viewModel = makeViewModel(.available, states: [
            .tagDetected,
            .selectingPpse,
            .selectingAid("A0000000031010"),
            .readingRecords,
            .done(card)
        ])
        viewModel.startScan()
        await waitUntil { if case .done = viewModel.uiState { return true } else { return false } }
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
        await waitUntil { if case .failed = viewModel.uiState { return true } else { return false } }
        guard case .failed(_, let friendly, _) = viewModel.uiState else {
            XCTFail("expected .failed terminal state, got \(viewModel.uiState)")
            return
        }
        XCTAssertEqual(friendly, "This card does not support contactless EMV.")
    }

    // MARK: - startScan guard

    func testStartScanIsIgnoredWhenStateIsNotIdle() {
        let viewModel = makeViewModel(.unavailable, states: [.done(sampleCard())])
        viewModel.startScan()
        XCTAssertEqual(currentCase(viewModel), .nfcUnavailable)
    }

    // MARK: - reset

    func testResetReturnsToIdleFromDone() async {
        let viewModel = makeViewModel(.available, states: [.done(sampleCard())])
        viewModel.startScan()
        await waitUntil { if case .done = viewModel.uiState { return true } else { return false } }
        viewModel.reset()
        XCTAssertEqual(currentCase(viewModel), .idle)
    }

    func testResetReturnsToIdleFromFailed() async {
        let viewModel = makeViewModel(.available, states: [.failed(.ppseUnsupported)])
        viewModel.startScan()
        await waitUntil { if case .failed = viewModel.uiState { return true } else { return false } }
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
            readerFactory: makeFactory([.done(sampleCard())])
        )
        viewModel.startScan()
        await waitUntil { if case .done = viewModel.uiState { return true } else { return false } }
        availability.set(.unavailable)
        viewModel.refreshAvailability()
        XCTAssertEqual(currentCase(viewModel), .done)
    }

    // MARK: - ErrorMessages parity

    func testFriendlyMessageForApduStatusErrorEmbedsHexBytes() {
        let message = ErrorMessages.friendly(for: .apduStatusError(sw1: 0x6A, sw2: 0x82))
        XCTAssertEqual(message, "Card returned status 6A 82. Try a different card.")
    }

    func testFriendlyMessageForIoFailureMapsEachIoReason() {
        XCTAssertEqual(
            ErrorMessages.friendly(for: .ioFailure(.tagLost)),
            "Card moved away from the device. Try again."
        )
        XCTAssertEqual(
            ErrorMessages.friendly(for: .ioFailure(.timeout)),
            "The card took too long to respond. Try again."
        )
        XCTAssertEqual(
            ErrorMessages.friendly(for: .ioFailure(.generic)),
            "Communication with the card failed. Try again."
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

    /// Spins the run loop until `predicate` becomes true or 1s elapses.
    /// AsyncStream consumption happens on the main actor task — we
    /// give it a chance to drain before asserting.
    private func waitUntil(_ predicate: () -> Bool) async {
        let deadline = Date().addingTimeInterval(1.0)
        while !predicate() && Date() < deadline {
            try? await Task.sleep(nanoseconds: 5_000_000)
        }
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

    /// Build a sample ``EmvCard`` by parsing the canonical Visa fixture
    /// bytes. ``EmvCard`` has an internal Kotlin constructor; the
    /// supported construction path is `EmvParser.parseOrThrow`.
    private func sampleCard() -> EmvCard {
        // KEEP IN SYNC: identical to :shared:commonTest Fixtures.VISA_CLASSIC.
        let fixture: [Int8] = [
            0x70, 0x3D,
            0x4F, 0x07, Int8(bitPattern: 0xA0), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
            0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
            0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
            0x57, 0x10,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            Int8(bitPattern: 0xD2), Int8(bitPattern: 0x81), 0x22, 0x01, 0x00, 0x00, 0x00, 0x00
        ]
        let kotlinBytes = KotlinByteArray(size: Int32(fixture.count))
        for (i, value) in fixture.enumerated() {
            kotlinBytes.set(index: Int32(i), value: value)
        }
        return EmvParser.shared.parseOrThrow(apduResponses: [kotlinBytes])
    }
}

