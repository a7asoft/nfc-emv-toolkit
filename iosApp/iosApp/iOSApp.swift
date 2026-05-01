import SwiftUI
import EmvReader

/// Sample app entry point. Constructs the production-wired
/// ``ReaderViewModel`` and hands it to ``ContentView``.
///
/// Production wiring:
/// - ``SystemNfcAvailability`` — backed by `NFCTagReaderSession.readingAvailable`.
/// - `EmvReader().read()` factory closure — fresh `AsyncStream<ReaderState>`
///   per scan.
@main
struct iOSApp: App {

    @StateObject private var viewModel: ReaderViewModel = ReaderViewModel(
        availability: SystemNfcAvailability(),
        readerFactory: { EmvReader().read() }
    )

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
        }
    }
}
