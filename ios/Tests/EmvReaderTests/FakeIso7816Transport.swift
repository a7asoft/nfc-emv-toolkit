import Foundation
@testable import EmvReader

/// In-memory test double for ``Iso7816Transport``.
///
/// Mirrors the Kotlin `FakeApduTransport` from `:android:reader`. Each
/// scripted entry has a command-prefix matcher and a fixed response.
/// `transceive` advances an index and returns the next pre-recorded
/// response; mismatched prefixes / scripts running out fail fast via
/// `precondition`. An optional `connectError` lets callers exercise
/// the I/O failure paths without needing real CoreNFC.
internal final class FakeIso7816Transport: Iso7816Transport, @unchecked Sendable {
    private let script: [(prefix: Data, response: Data)]
    private let connectError: Error?
    private let lock = NSLock()
    private var index = 0
    private var connectedFlag = false
    private var closedFlag = false

    /// Lock + state for ``waitForClose()`` — separate from `lock` so a
    /// caller awaiting close can never deadlock against transceive/close.
    private let closeLock = NSLock()
    private var closeContinuation: CheckedContinuation<Void, Never>?

    init(script: [(prefix: Data, response: Data)], connectError: Error? = nil) {
        self.script = script
        self.connectError = connectError
    }

    var closed: Bool {
        lock.lock(); defer { lock.unlock() }
        return closedFlag
    }

    func connect() async throws {
        if let error = connectError {
            throw error
        }
        lock.lock()
        connectedFlag = true
        lock.unlock()
    }

    func transceive(_ command: Data) async throws -> Data {
        lock.lock()
        precondition(connectedFlag, "transport not connected")
        precondition(index < script.count,
                     "no more scripted responses (script size \(script.count))")
        let entry = script[index]
        precondition(command.starts(with: entry.prefix),
                     "command #\(index) does not start with expected prefix")
        index += 1
        let response = entry.response
        lock.unlock()
        return response
    }

    func close() async {
        lock.lock()
        closedFlag = true
        lock.unlock()
        closeLock.lock()
        let cont = closeContinuation
        closeContinuation = nil
        closeLock.unlock()
        cont?.resume()
    }

    /// Suspend until ``close()`` has fired at least once. Resumes
    /// immediately if close already happened. Used by the cancellation
    /// test to avoid `Task.sleep(...)`-based flakiness.
    func waitForClose() async {
        if closed { return }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            closeLock.lock()
            if closed {
                closeLock.unlock()
                continuation.resume()
                return
            }
            closeContinuation = continuation
            closeLock.unlock()
        }
    }
}
