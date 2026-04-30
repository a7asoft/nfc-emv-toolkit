import Foundation
import Shared

/// Top-level Swift entry point for reading a contactless EMV card on iOS.
///
/// Wraps CoreNFC's `NFCTagReaderSession` and orchestrates the
/// EMV Book 1 §11–12 contactless flow:
/// PPSE → SELECT AID → GPO → READ RECORD → `EmvParser.parse`.
/// Each stage emits a ``ReaderState`` over the returned `AsyncStream`.
/// Terminal states are ``ReaderState/done(_:)`` and
/// ``ReaderState/failed(_:)``.
///
/// Cancellation: cancelling the consuming `Task` cancels the stream.
/// The `onTermination` handler invalidates the underlying NFC session.
/// Note that cancellation is observed only BETWEEN APDU exchanges
/// (CoreNFC `sendCommand(apdu:)` callbacks are not cancellable).
public final class EmvReader {

    private let transportFactory: @Sendable () -> Iso7816Transport

    /// Build an `EmvReader` backed by a fresh
    /// ``NFCISO7816TagTransport`` per `read()` call.
    public init() {
        self.transportFactory = { NFCISO7816TagTransport() }
    }

    /// Internal initializer for testing — caller supplies the
    /// transport directly.
    internal init(transportFactory: @Sendable @escaping () -> Iso7816Transport) {
        self.transportFactory = transportFactory
    }

    /// Begin a contactless read. Returns an `AsyncStream` that emits
    /// state transitions as the read progresses, ending with either
    /// ``ReaderState/done(_:)`` or ``ReaderState/failed(_:)``.
    ///
    /// Apple's NFC reader rules require this to be invoked in response
    /// to a user gesture; the library cannot enforce this, so callers
    /// are responsible.
    public func read() -> AsyncStream<ReaderState> {
        let transport = transportFactory()
        return AsyncStream { continuation in
            let task = Task {
                await drive(transport: transport, continuation: continuation)
            }
            continuation.onTermination = { _ in
                task.cancel()
                Task { await transport.close() }
            }
        }
    }

    private func drive(
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async {
        do {
            try await transport.connect()
            continuation.yield(.tagDetected)
            try await runFlow(transport: transport, continuation: continuation)
        } catch {
            continuation.yield(.failed(.ioFailure(Mapping.ioReason(from: error))))
        }
        continuation.finish()
        await transport.close()
    }

    private func runFlow(
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws {
        guard let ppse = try await runPpse(transport: transport, continuation: continuation) else { return }
        guard let chosenAid = chooseApplication(ppse: ppse, continuation: continuation) else { return }
        continuation.yield(.selectingAid(chosenAid))
        guard try await runSelectAid(aid: chosenAid, transport: transport, continuation: continuation) else { return }
        continuation.yield(.readingRecords)
        guard let gpo = try await runGpo(transport: transport, continuation: continuation) else { return }
        let records = try await readAllRecords(afl: gpo.afl, transport: transport)
        yieldParseOutcome(records: records, continuation: continuation)
    }

    private func runPpse(
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws -> Ppse? {
        continuation.yield(.selectingPpse)
        let response = try await transport.transceive(ApduCommands.ppseSelect)
        guard ApduCommands.isSuccess(response) else {
            yieldPpseStatusFailure(response: response, continuation: continuation)
            return nil
        }
        switch Mapping.parsePpse(ApduCommands.dataField(response)) {
        case .ok(let ppse):
            return ppse
        case .err(let error):
            continuation.yield(.failed(.ppseRejected(error)))
            return nil
        }
    }

    private func yieldPpseStatusFailure(
        response: Data,
        continuation: AsyncStream<ReaderState>.Continuation
    ) {
        let (sw1, sw2) = ApduCommands.statusWord(response)
        if sw1 == 0x6A && sw2 == 0x82 {
            continuation.yield(.failed(.ppseUnsupported))
        } else {
            continuation.yield(.failed(.apduStatusError(sw1: sw1, sw2: sw2)))
        }
    }

    private func chooseApplication(
        ppse: Ppse,
        continuation: AsyncStream<ReaderState>.Continuation
    ) -> Any? {
        let entries = ppse.applications
        guard !entries.isEmpty else {
            continuation.yield(.failed(.noApplicationSelected))
            return nil
        }
        let chosen = entries.min(by: { lhs, rhs in
            priorityValue(lhs.priority) < priorityValue(rhs.priority)
        })
        return chosen.map { $0.aid }
    }

    private func priorityValue(_ priority: KotlinInt?) -> Int {
        return priority?.intValue ?? Int.max
    }

    private func runSelectAid(
        aid: Any,
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws -> Bool {
        let command = ApduCommands.selectAid(aidBytes: Mapping.aidBytes(aid))
        let response = try await transport.transceive(command)
        if ApduCommands.isSuccess(response) { return true }
        let (sw1, sw2) = ApduCommands.statusWord(response)
        continuation.yield(.failed(.apduStatusError(sw1: sw1, sw2: sw2)))
        return false
    }

    private func runGpo(
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws -> Gpo? {
        let response = try await transport.transceive(ApduCommands.gpoDefault)
        guard ApduCommands.isSuccess(response) else {
            let (sw1, sw2) = ApduCommands.statusWord(response)
            continuation.yield(.failed(.apduStatusError(sw1: sw1, sw2: sw2)))
            return nil
        }
        switch Mapping.parseGpo(ApduCommands.dataField(response)) {
        case .ok(let gpo):
            return gpo
        case .err(let error):
            continuation.yield(.failed(.gpoRejected(error)))
            return nil
        }
    }

    private func readAllRecords(afl: Afl, transport: Iso7816Transport) async throws -> [Data] {
        var collected: [Data] = []
        for entry in afl.entries {
            try await readEntryRecords(entry: entry, transport: transport, into: &collected)
        }
        return collected
    }

    private func readEntryRecords(
        entry: AflEntry,
        transport: Iso7816Transport,
        into collected: inout [Data]
    ) async throws {
        let first = Int(entry.firstRecord)
        let last = Int(entry.lastRecord)
        let sfi = UInt8(entry.sfi)
        for record in first...last {
            let cmd = ApduCommands.readRecord(recordNumber: UInt8(record), sfi: sfi)
            let response = try await transport.transceive(cmd)
            // why: a non-9000 READ RECORD is silently skipped rather than aborting
            // the whole flow. Real cards sometimes advertise records that aren't
            // readable. EmvParser surfaces MissingRequiredTag downstream if essential
            // data was in a skipped record. Mirrors the Android reader contract (#48).
            if ApduCommands.isSuccess(response) {
                collected.append(ApduCommands.dataField(response))
            }
        }
    }

    private func yieldParseOutcome(
        records: [Data],
        continuation: AsyncStream<ReaderState>.Continuation
    ) {
        switch Mapping.parseEmvCard(records) {
        case .ok(let card):
            continuation.yield(.done(card))
        case .err(let error):
            continuation.yield(.failed(.parseFailed(error)))
        }
    }
}
