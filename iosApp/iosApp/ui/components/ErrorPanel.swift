import SwiftUI
import EmvReader

/// Renders a ``ReaderError`` as a friendly headline plus a collapsible
/// diagnostic detail and a "Try again" CTA.
///
/// The `friendlyMessage` and `diagnostic` strings are pre-computed by
/// ``ReaderViewModel`` (via `ErrorMessages`) so this view stays a
/// pure renderer. Diagnostics are safe to display: every
/// ``ReaderError`` variant carries only structural metadata (status
/// words, structural sub-error references, ``IoReason`` enum) — never
/// raw card bytes (CLAUDE.md §5.8).
public struct ErrorPanel: View {

    private let friendlyMessage: String
    private let diagnostic: String
    private let onTryAgain: () -> Void
    @State private var detailsExpanded: Bool = false

    /// Build an error panel. The caller passes pre-rendered messages
    /// so the view is a pure function of its inputs.
    public init(
        friendlyMessage: String,
        diagnostic: String,
        onTryAgain: @escaping () -> Void
    ) {
        self.friendlyMessage = friendlyMessage
        self.diagnostic = diagnostic
        self.onTryAgain = onTryAgain
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(friendlyMessage)
                .font(.headline)
            DisclosureGroup("Details", isExpanded: $detailsExpanded) {
                Text(diagnostic)
                    .font(.system(.caption, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 4)
            }
            Button("Try again", action: onTryAgain)
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.systemRed).opacity(0.1))
        )
    }
}
