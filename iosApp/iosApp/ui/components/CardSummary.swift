import SwiftUI

/// Renders an ``EmvCardSummary`` — a PCI-safe display projection of
/// the parsed ``Shared/EmvCard``.
///
/// The summary's `pan` field already comes pre-masked from
/// ``EmvCardSummary/from(_:)``; this view never has access to the raw
/// PAN. Mirrors the Android composeApp `CardSummary` composable.
public struct CardSummary: View {

    private let card: EmvCardSummary
    private let onTryAgain: () -> Void

    /// Build a card summary view. `onTryAgain` is invoked when the
    /// user wants to scan another card; the parent typically dispatches
    /// to ``ReaderViewModel/reset()`` to return to ``ReaderUiState/idle``.
    public init(card: EmvCardSummary, onTryAgain: @escaping () -> Void) {
        self.card = card
        self.onTryAgain = onTryAgain
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            field(label: "PAN", value: card.pan)
            field(label: "Brand", value: card.brand)
            field(label: "Expiry", value: card.expiry)
            field(label: "Cardholder", value: card.cardholderName ?? "<not provided>")
            field(label: "Label", value: card.applicationLabel ?? "<not provided>")
            field(label: "AID", value: card.aidHex)
            field(label: "Track 2", value: card.hasTrack2 ? "present" : "<not provided>")
            Button("Read another card", action: onTryAgain)
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.secondarySystemBackground))
        )
    }

    private func field(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.body)
        }
    }
}
