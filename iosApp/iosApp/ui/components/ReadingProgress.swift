import SwiftUI

/// Linear-progress + stage-label pair shown during the read flow.
///
/// The caller passes a human-readable `stageLabel` derived from the
/// current ``ReaderUiState``. Mirrors the Android composeApp
/// `ReadingProgress` composable.
public struct ReadingProgress: View {

    private let stageLabel: String

    /// Build a progress row with the supplied stage label.
    public init(stageLabel: String) {
        self.stageLabel = stageLabel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ProgressView().progressViewStyle(.linear)
            Text(stageLabel)
                .font(.body)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
