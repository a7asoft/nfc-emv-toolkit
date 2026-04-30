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
    ///
    /// Cancellation observed BETWEEN APDU exchanges (not during them).
    /// Cancellation during the initial NFC presentation sheet (while
    /// `connect()` awaits user gesture) is also not observed; the system
    /// sheet remains until the user dismisses or the session times out.
    public func read() -> AsyncStream<ReaderState> {
        return read(config: TerminalConfig.default)
    }

    /// Begin a contactless read using the supplied [TerminalConfig]
    /// to seed the GPO PDOL response (TTQ, country, currency, etc.).
    /// Use this overload to override the standard defaults — for
    /// example to flip TTQ bits when validating against a card that
    /// rejects the conservative `36 00 80 00`.
    public func read(config: TerminalConfig) -> AsyncStream<ReaderState> {
        let transport = transportFactory()
        return AsyncStream { continuation in
            let task = Task {
                await drive(transport: transport, config: config, continuation: continuation)
            }
            continuation.onTermination = { _ in
                task.cancel()
                Task { await transport.close() }
            }
        }
    }

    private func drive(
        transport: Iso7816Transport,
        config: TerminalConfig,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async {
        do {
            try await transport.connect()
            continuation.yield(.tagDetected)
            try await runFlow(transport: transport, config: config, continuation: continuation)
        } catch {
            continuation.yield(.failed(.ioFailure(Mapping.ioReason(from: error))))
        }
        continuation.finish()
        await transport.close()
    }

    private func runFlow(
        transport: Iso7816Transport,
        config: TerminalConfig,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws {
        guard let ppse = try await runPpse(transport: transport, continuation: continuation) else { return }
        guard let chosenAid = chooseApplication(ppse: ppse, continuation: continuation) else { return }
        continuation.yield(.selectingAid(chosenAid))
        guard let fciBody = try await runSelectAid(aid: chosenAid, transport: transport, continuation: continuation) else { return }
        guard let gpoBody = buildGpoBody(fciBody: fciBody, config: config, continuation: continuation) else { return }
        continuation.yield(.readingRecords)
        guard let gpo = try await runGpo(gpoBody: gpoBody, transport: transport, continuation: continuation) else { return }
        let records = try await readAllRecords(afl: gpo.afl, transport: transport)
        yieldParseOutcome(aid: chosenAid, records: records, continuation: continuation)
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
        // why: ppse.applications is never empty here because Ppse.parse
        // returns Err(NoApplicationsFound) → ppseRejected before we can
        // construct an empty Ppse. The lowest-priority pick is therefore
        // total over the non-empty list.
        let entries = ppse.applications
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
    ) async throws -> Data? {
        // why: try! is intentional. AID bytes come from Ppse.parse, which
        // already enforces the 5..16 length bound (EMV Book 1 §12.2.3 +
        // ISO/IEC 7816-4 §5.4.1). A throw here would mean Ppse.parse
        // contract was violated — a programming error, not a runtime one.
        let command = try! ApduCommands.selectAid(aidBytes: Mapping.aidBytes(aid))
        let response = try await transport.transceive(command)
        if ApduCommands.isSuccess(response) { return ApduCommands.dataField(response) }
        let (sw1, sw2) = ApduCommands.statusWord(response)
        continuation.yield(.failed(.apduStatusError(sw1: sw1, sw2: sw2)))
        return nil
    }

    private func buildGpoBody(
        fciBody: Data,
        config: TerminalConfig,
        continuation: AsyncStream<ReaderState>.Continuation
    ) -> Data? {
        let pdolBytes: Data?
        switch Mapping.parseSelectAidFci(fciBody) {
        case .ok(let fci):
            pdolBytes = fci.pdolBytes?.toData()
        case .err(let error):
            continuation.yield(.failed(.selectAidFciRejected(error)))
            return nil
        }
        guard let pdolBytes = pdolBytes else {
            // why: empty PDOL → 83 00 GPO body (Mastercard contactless
            // pattern). Force-try because gpoCommand only throws on
            // > 253 byte input — empty Data is always valid.
            return try! ApduCommands.gpoCommand(pdolResponse: Data())
        }
        let pdol: Pdol
        switch Mapping.parsePdol(pdolBytes) {
        case .ok(let parsed):
            pdol = parsed
        case .err(let error):
            continuation.yield(.failed(.pdolRejected(error)))
            return nil
        }
        let response = Mapping.buildPdolResponse(
            pdol: pdol,
            config: config,
            transactionDate: transactionDateBcd(),
            unpredictableNumber: unpredictableNumber()
        )
        // why: gpoCommand only throws on > 253 byte input. PdolResponseBuilder
        // emits at most sum-of-PDOL-lengths bytes; even a maximally-padded PDOL
        // (252 bytes) stays under the limit.
        return try! ApduCommands.gpoCommand(pdolResponse: response)
    }

    private func runGpo(
        gpoBody: Data,
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws -> Gpo? {
        let response = try await transport.transceive(gpoBody)
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

    // why: BCD-encoded YYMMDD for tag 9A from current UTC date. Tag 9A is
    // informational for our read-only flow; the card uses it to seed
    // cryptograms we never validate.
    private func transactionDateBcd() -> Data {
        let calendar = Calendar(identifier: .gregorian)
        var utc = calendar
        utc.timeZone = TimeZone(identifier: "UTC") ?? .current
        let now = utc.dateComponents([.year, .month, .day], from: Date())
        let yy = (now.year ?? 2000) % 100
        let mm = now.month ?? 1
        let dd = now.day ?? 1
        return Data([bcd(yy / 10, yy % 10), bcd(mm / 10, mm % 10), bcd(dd / 10, dd % 10)])
    }

    private func bcd(_ high: Int, _ low: Int) -> UInt8 {
        return UInt8(((high & 0x0F) << 4) | (low & 0x0F))
    }

    // why: 4 random bytes for tag 9F37. NOT crypto-grade — anti-replay
    // protocol-level only and we never validate the card cryptogram.
    private func unpredictableNumber() -> Data {
        var bytes = Data(count: 4)
        for i in 0..<4 {
            bytes[i] = UInt8.random(in: 0...0xFF)
        }
        return bytes
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
            // why: try! is intentional. recordNumber and sfi come from
            // Afl.parse, which already validates 1..254 and 1..30 per
            // ISO/IEC 7816-4 §7.3.3. A throw here would mean Afl.parse
            // contract was violated — a programming error, not a runtime one.
            let cmd = try! ApduCommands.readRecord(recordNumber: UInt8(record), sfi: sfi)
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
        aid: Any,
        records: [Data],
        continuation: AsyncStream<ReaderState>.Continuation
    ) {
        switch Mapping.parseEmvCard(aid: aid, records) {
        case .ok(let card):
            continuation.yield(.done(card))
        case .err(let error):
            continuation.yield(.failed(.parseFailed(error)))
        }
    }
}
