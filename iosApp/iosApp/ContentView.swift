import SwiftUI

/// Thin wrapper that hosts ``ReaderScreen`` inside the window. Kept
/// separate from ``iOSApp`` so the screen can be previewed without
/// bringing in the production view-model wiring.
struct ContentView: View {

    @ObservedObject var viewModel: ReaderViewModel

    var body: some View {
        ReaderScreen(viewModel: viewModel)
    }
}
