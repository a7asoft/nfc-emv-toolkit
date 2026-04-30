import XCTest
import Shared
@testable import EmvReader

/// Integration tests for ``EmvReader`` driven by the
/// ``FakeIso7816Transport`` test double. iOS Simulator does NOT
/// support CoreNFC, so the production ``NFCISO7816TagTransport`` is
/// not exercised here; real-device validation lives in the sample app.
final class EmvReaderTests: XCTestCase {

    // MARK: Happy paths — Visa / Mastercard / Amex

    func testReadEmitsVisaDoneStateWithExpectedEmvCard() async {
        let transport = FakeIso7816Transport(script: visaScript())
        let states = await collectStates(transport: transport)
        assertProgressEndsInDone(states: states, expectedAidHex: "A0000000031010")
        XCTAssertTrue(transport.closed)
    }

    func testReadEmitsMastercardDoneStateWithExpectedEmvCard() async {
        let transport = FakeIso7816Transport(script: mastercardScript())
        let states = await collectStates(transport: transport)
        assertProgressEndsInDone(states: states, expectedAidHex: "A0000000041010")
    }

    func testReadEmitsAmexDoneStateWithExpectedEmvCard() async {
        let transport = FakeIso7816Transport(script: amexScript())
        let states = await collectStates(transport: transport)
        assertProgressEndsInDone(states: states, expectedAidHex: "A000000025010701")
    }

    // MARK: Error paths

    func testReadEmitsPpseUnsupportedWhenPpseReturns6A82() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.ppseNotFoundResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .ppseUnsupported = error else {
            return XCTFail("Expected .failed(.ppseUnsupported), got \(String(describing: states.last))")
        }
    }

    func testReadEmitsApduStatusErrorOnNon9000FromSelectAid() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.selectAidWrongP1P2Response),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .apduStatusError(let sw1, let sw2) = error else {
            return XCTFail("Expected .apduStatusError, got \(String(describing: states.last))")
        }
        XCTAssertEqual(sw1, 0x6A)
        XCTAssertEqual(sw2, 0x86)
    }

    func testReadEmitsApduStatusErrorOnNon9000FromGpo() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.visaSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.gpoNotSupportedResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .apduStatusError(let sw1, let sw2) = error else {
            return XCTFail("Expected .apduStatusError, got \(String(describing: states.last))")
        }
        XCTAssertEqual(sw1, 0x6A)
        XCTAssertEqual(sw2, 0x81)
    }

    func testReadEmitsParseFailedWhenRecordsAreMissingRequiredTags() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.visaSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.visaGpoResponse),
            (readRecord1Sfi1Prefix(), Transcripts.incompleteRecordResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .parseFailed(let cause) = error else {
            return XCTFail("Expected .parseFailed, got \(String(describing: states.last))")
        }
        XCTAssertTrue(cause is EmvCardErrorMissingRequiredTag,
                      "Expected MissingRequiredTag, got \(type(of: cause))")
    }

    func testReadEmitsPpseRejectedWhenPpseBodyIsMalformed() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.ppseMalformedBodyResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .ppseRejected = error else {
            return XCTFail("Expected .ppseRejected, got \(String(describing: states.last))")
        }
    }

    func testReadEmitsPpseRejectedWhenPpseHasNoApplicationTemplates() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.ppseNoApplicationsResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .ppseRejected = error else {
            return XCTFail("Expected .ppseRejected, got \(String(describing: states.last))")
        }
    }

    func testReadEmitsGpoRejectedWhenGpoReturnsAMalformedBody() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.visaSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.gpoMalformedBodyResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .gpoRejected = error else {
            return XCTFail("Expected .gpoRejected, got \(String(describing: states.last))")
        }
    }

    // MARK: I/O failure paths

    func testReadEmitsIoFailureGenericWhenTransportThrowsArbitraryError() async {
        struct Boom: Error {}
        let transport = FakeIso7816Transport(script: [], connectError: Boom())
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .ioFailure(let reason) = error else {
            return XCTFail("Expected .ioFailure, got \(String(describing: states.last))")
        }
        XCTAssertEqual(reason, .generic)
    }

    // MARK: Silent-skip + multi-AID

    func testReadSilentlySkipsANon9000ReadRecordAndContinuesWithTheNext() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.visaSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.visaGpoResponseTwoRecords),
            (readRecord1Sfi1Prefix(), Transcripts.readRecordNotFoundResponse),
            (readRecord2Sfi1Prefix(), Transcripts.visaRecord1Response),
        ])
        let states = await collectStates(transport: transport)
        guard case .done = states.last else {
            return XCTFail("Expected .done after silent-skip, got \(String(describing: states.last))")
        }
    }

    func testReadSelectsApplicationWithLowestPriorityAmongMultiplePpseEntries() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.multiAidPpseResponse),
            (selectMastercardPrefix(), Transcripts.mastercardSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.mastercardGpoResponse),
            (readRecord1Sfi1Prefix(), Transcripts.mastercardRecord1Response),
        ])
        let states = await collectStates(transport: transport)
        let selectingStates = states.compactMap { state -> String? in
            if case .selectingAid(let aid) = state { return Mapping.aidHex(aid) }
            return nil
        }
        XCTAssertEqual(selectingStates, ["A0000000041010"])
        guard case .done = states.last else {
            return XCTFail("Expected .done, got \(String(describing: states.last))")
        }
    }

    // MARK: Cancellation

    func testReadClosesTransportWhenCollectingTaskIsCancelled() async throws {
        let transport = FakeIso7816Transport(script: visaScript())
        let reader = EmvReader(transportFactory: { transport })
        let collected = StateCollector()

        let task = Task {
            for await state in reader.read() {
                await collected.append(state)
            }
        }
        // Wait until at least one state has been observed, then cancel.
        while await collected.count == 0 { await Task.yield() }
        task.cancel()
        _ = await task.value

        // onTermination dispatches an async close; allow it to settle.
        try? await Task.sleep(nanoseconds: 200_000_000)
        XCTAssertTrue(transport.closed,
                      "transport must close when collecting Task is cancelled")
    }

    // MARK: Helpers

    private func collectStates(transport: FakeIso7816Transport) async -> [ReaderState] {
        let reader = EmvReader(transportFactory: { transport })
        var collected: [ReaderState] = []
        for await state in reader.read() {
            collected.append(state)
        }
        return collected
    }

    private func assertProgressEndsInDone(
        states: [ReaderState],
        expectedAidHex: String,
        file: StaticString = #file,
        line: UInt = #line
    ) {
        XCTAssertGreaterThanOrEqual(states.count, 5, "expected ≥5 states", file: file, line: line)
        guard states.count >= 5 else { return }
        if case .tagDetected = states[0] {} else {
            XCTFail("states[0] should be .tagDetected, got \(states[0])", file: file, line: line)
        }
        if case .selectingPpse = states[1] {} else {
            XCTFail("states[1] should be .selectingPpse, got \(states[1])", file: file, line: line)
        }
        guard case .selectingAid(let aid) = states[2] else {
            XCTFail("states[2] should be .selectingAid, got \(states[2])", file: file, line: line)
            return
        }
        XCTAssertEqual(Mapping.aidHex(aid), expectedAidHex, file: file, line: line)
        if case .readingRecords = states[3] {} else {
            XCTFail("states[3] should be .readingRecords, got \(states[3])", file: file, line: line)
        }
        guard case .done(let card) = states[4] else {
            XCTFail("states[4] should be .done, got \(states[4])", file: file, line: line)
            return
        }
        XCTAssertEqual(Mapping.aidHex(card.aid), expectedAidHex, file: file, line: line)
    }

    // MARK: Scripts

    private func visaScript() -> [(Data, Data)] {
        [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.visaSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.visaGpoResponse),
            (readRecord1Sfi1Prefix(), Transcripts.visaRecord1Response),
        ]
    }

    private func mastercardScript() -> [(Data, Data)] {
        [
            (ApduCommands.ppseSelect, Transcripts.mastercardPpseResponse),
            (selectMastercardPrefix(), Transcripts.mastercardSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.mastercardGpoResponse),
            (readRecord1Sfi1Prefix(), Transcripts.mastercardRecord1Response),
        ]
    }

    private func amexScript() -> [(Data, Data)] {
        [
            (ApduCommands.ppseSelect, Transcripts.amexPpseResponse),
            (selectAmexPrefix(), Transcripts.amexSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.amexGpoResponse),
            (readRecord1Sfi1Prefix(), Transcripts.amexRecord1Response),
        ]
    }

    private func selectVisaPrefix() -> Data {
        Data([0x00, 0xA4, 0x04, 0x00, 0x07]) + Transcripts.visaAidBytes
    }

    private func selectMastercardPrefix() -> Data {
        Data([0x00, 0xA4, 0x04, 0x00, 0x07]) + Transcripts.mastercardAidBytes
    }

    private func selectAmexPrefix() -> Data {
        Data([0x00, 0xA4, 0x04, 0x00, 0x08]) + Transcripts.amexAidBytes
    }

    private func readRecord1Sfi1Prefix() -> Data {
        Data([0x00, 0xB2, 0x01, 0x0C])
    }

    private func readRecord2Sfi1Prefix() -> Data {
        Data([0x00, 0xB2, 0x02, 0x0C])
    }
}

/// Actor used by the cancellation test to safely accumulate states
/// from a Task that may be cancelled at any time.
private actor StateCollector {
    private var states: [ReaderState] = []

    func append(_ state: ReaderState) {
        states.append(state)
    }

    var count: Int { states.count }
}
