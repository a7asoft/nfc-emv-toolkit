import CoreNFC
import Foundation

/// Production ``Iso7816Transport`` backed by CoreNFC's
/// `NFCTagReaderSession` + `NFCISO7816Tag`.
///
/// The ONLY file in this module that imports `CoreNFC`. All other
/// reader logic operates against the protocol abstraction for
/// testability (mirrors the Android reader's "only `IsoDepTransport.kt`
/// imports `android.nfc.*`" rule).
///
/// Lifecycle:
/// 1. ``connect()`` opens an `NFCTagReaderSession`, awaits the
///    `tagReaderSession(_:didDetect:)` delegate callback, picks the
///    first `NFCISO7816Tag`, and connects.
/// 2. ``transceive(_:)`` sends an APDU via `sendCommand(apdu:)` and
///    awaits the response continuation.
/// 3. ``close()`` invalidates the session.
///
/// The session can be initiated only from a foreground app context
/// triggered by user gesture per Apple's NFC reader rules. Library
/// consumers must ensure ``EmvReader/read()`` is invoked in
/// response to a UI button or similar gesture.
internal final class NFCISO7816TagTransport: NSObject, Iso7816Transport, @unchecked Sendable {
    private var session: NFCTagReaderSession?
    private var connectedTag: NFCISO7816Tag?
    private var connectContinuation: CheckedContinuation<Void, Error>?
    private let lock = NSLock()

    func connect() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            lock.lock()
            self.connectContinuation = continuation
            lock.unlock()
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                let session = NFCTagReaderSession(
                    pollingOption: [.iso14443],
                    delegate: self,
                    queue: nil
                )
                session?.alertMessage = "Hold your card near the device"
                self.session = session
                session?.begin()
            }
        }
    }

    func transceive(_ command: Data) async throws -> Data {
        guard let tag = connectedTag else {
            throw TransportError.io(.timeout)
        }
        guard let apdu = NFCISO7816APDU(data: command) else {
            throw TransportError.io(.generic)
        }
        return try await withCheckedThrowingContinuation { continuation in
            tag.sendCommand(apdu: apdu) { responseData, sw1, sw2, error in
                if let error = error {
                    continuation.resume(throwing: Self.translate(error))
                    return
                }
                var response = Data(responseData)
                response.append(sw1)
                response.append(sw2)
                continuation.resume(returning: response)
            }
        }
    }

    /// Translate a CoreNFC `Error` into the platform-neutral
    /// ``TransportError``. Confines CoreNFC error codes to this file.
    private static func translate(_ error: Error) -> TransportError {
        guard let nfcError = error as? NFCReaderError else { return .io(.generic) }
        switch nfcError.code {
        case .readerTransceiveErrorTagConnectionLost:
            return .io(.tagLost)
        case .readerSessionInvalidationErrorSessionTimeout:
            return .io(.timeout)
        default:
            return .io(.generic)
        }
    }

    func close() async {
        lock.lock()
        let s = self.session
        self.session = nil
        self.connectedTag = nil
        lock.unlock()
        s?.invalidate()
    }

    private func resumeConnect(_ result: Result<Void, Error>) {
        lock.lock()
        let cont = self.connectContinuation
        self.connectContinuation = nil
        lock.unlock()
        cont?.resume(with: result)
    }
}

// MARK: - NFCTagReaderSessionDelegate

extension NFCISO7816TagTransport: NFCTagReaderSessionDelegate {
    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let first = tags.first, case let .iso7816(tag) = first else {
            session.invalidate(errorMessage: "Tag is not ISO 7816 — only contactless EMV cards are supported.")
            return
        }
        session.connect(to: first) { [weak self] error in
            guard let self = self else { return }
            if let error = error {
                self.resumeConnect(.failure(Self.translate(error)))
                return
            }
            self.connectedTag = tag
            self.resumeConnect(.success(()))
        }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        // Only surface to the connect continuation if it's still pending.
        // If the session invalidated AFTER connect succeeded, the in-flight
        // transceive continuation will surface the error instead.
        resumeConnect(.failure(Self.translate(error)))
    }
}
