import SwiftUI

/// Top region of the reader screen. Shows a state-driven headline and
/// subtitle so the user always knows what the app is doing.
///
/// Mirrors the Android composeApp `StatusHeader` discipline: the
/// `headline` / `subtitle` switches are exhaustive over
/// ``ReaderUiState`` so adding a new case forces a compile error here.
public struct StatusHeader: View {

    private let state: ReaderUiState

    /// Build a header bound to the current ``ReaderUiState``.
    public init(state: ReaderUiState) {
        self.state = state
    }

    public var body: some View {
        VStack(spacing: 12) {
            Text(headline)
                .font(.title2)
                .multilineTextAlignment(.center)
            Text(subtitle)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var headline: String {
        switch state {
        case .nfcUnavailable: return "NFC not supported"
        case .idle: return "Tap a card"
        case .scanning: return "Hold a card"
        case .done: return "Card read"
        case .failed: return "Read failed"
        case .tagDetected, .selectingPpse, .selectingApplication, .readingRecords: return "Reading"
        }
    }

    private var subtitle: String {
        switch state {
        case .nfcUnavailable: return "This device has no NFC reader. The sample cannot run here."
        case .idle: return "Press Scan and hold a contactless EMV card against the top of the device."
        case .scanning: return "Hold the card flat against the top of the device."
        case .done: return "Press Read another card to scan again."
        case .failed: return "Press Try again, or use a different card."
        case .tagDetected, .selectingPpse, .selectingApplication, .readingRecords: return "Hold the card steady"
        }
    }
}
