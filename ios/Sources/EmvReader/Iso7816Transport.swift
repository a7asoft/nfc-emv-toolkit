import Foundation

/// Categorical errors a transport can throw without leaking
/// platform-specific noise into the read flow.
///
/// Production ``NFCISO7816TagTransport`` translates CoreNFC's
/// `NFCReaderError` codes into a ``TransportError/io(_:)`` carrying
/// the right ``IoReason`` before throwing — this keeps CoreNFC types
/// confined to a single file (CLAUDE.md §7 boundary). Any other
/// `Error` reaching the orchestrator maps to ``IoReason/generic``.
internal enum TransportError: Error {
    case io(IoReason)
}

/// Stateful APDU exchange channel.
///
/// Mirrors the Kotlin `ApduTransport` interface. Implementations wrap
/// `NFCISO7816Tag` in production and a fake recorder in tests.
///
/// Thread-safety contract:
/// - ``connect()`` and ``transceive(_:)`` are called sequentially from
///   the same `Task` (the read flow's orchestration coroutine).
/// - ``close()`` may be called from a different `Task` than the one
///   doing the I/O, e.g. during `AsyncStream` cancellation.
///   Implementations MUST tolerate this; CoreNFC's
///   `NFCTagReaderSession.invalidate()` is documented as thread-safe.
internal protocol Iso7816Transport: AnyObject, Sendable {
    func connect() async throws
    func transceive(_ command: Data) async throws -> Data
    func close() async
}
