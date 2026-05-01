import SwiftUI

/// Top-level SwiftUI screen for the reader sample.
///
/// Pure presentation: state and callbacks come from the supplied
/// ``ReaderViewModel``. The screen renders the ``StatusHeader`` plus
/// one of ``ReadingProgress`` / ``CardSummary`` / ``ErrorPanel`` /
/// the Scan button depending on ``ReaderUiState``.
public struct ReaderScreen: View {

    @ObservedObject private var viewModel: ReaderViewModel

    /// Build a screen bound to the supplied view-model.
    public init(viewModel: ReaderViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                StatusHeader(state: viewModel.uiState)
                content
            }
            .padding(24)
        }
        .onAppear { viewModel.refreshAvailability() }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.uiState {
        case .nfcUnavailable:
            EmptyView()
        case .idle:
            scanButton
        case .scanning:
            ReadingProgress(stageLabel: "Starting scan")
        case .tagDetected:
            ReadingProgress(stageLabel: "Tag detected")
        case .selectingPpse:
            ReadingProgress(stageLabel: "Selecting application directory")
        case .selectingApplication(let aidHex):
            ReadingProgress(stageLabel: "Selecting application \(aidHex)")
        case .readingRecords:
            ReadingProgress(stageLabel: "Reading records")
        case .done(let card):
            CardSummary(card: card, onTryAgain: viewModel.reset)
        case .failed(_, let friendly, let diagnostic):
            ErrorPanel(
                friendlyMessage: friendly,
                diagnostic: diagnostic,
                onTryAgain: viewModel.reset
            )
        }
    }

    private var scanButton: some View {
        Button(action: viewModel.startScan) {
            Text("Scan card")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
    }
}
