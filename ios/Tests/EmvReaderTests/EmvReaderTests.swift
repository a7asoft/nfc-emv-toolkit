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
        XCTAssertTrue(transport.closed)
    }

    func testReadEmitsAmexDoneStateWithExpectedEmvCard() async {
        let transport = FakeIso7816Transport(script: amexScript())
        let states = await collectStates(transport: transport)
        assertProgressEndsInDone(states: states, expectedAidHex: "A000000025010701")
        XCTAssertTrue(transport.closed)
    }

    // MARK: Error paths

    func testReadEmitsPpseUnsupportedWhenPpseReturns6A82() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.ppseNotFoundResponse),
        ])
        let states = await collectStates(transport: transport)
        // why: ReaderState's bridged description chain ends in EmvCard.toString
        // (Kotlin side, masked per #8). String(describing:) on Obj-C bridged
        // Kotlin classes calls -description, which Kotlin/Native exports from
        // toString(). Safe because the underlying Kotlin types mask.
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
        // why: see ppseUnsupported test above for String(describing:) safety note.
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
        // why: see ppseUnsupported test above for String(describing:) safety note.
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
        // why: see ppseUnsupported test above for String(describing:) safety note.
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
        // why: see ppseUnsupported test above for String(describing:) safety note.
        guard case .failed(let error) = states.last,
              case .ppseRejected = error else {
            return XCTFail("Expected .ppseRejected, got \(String(describing: states.last))")
        }
    }

    func testReadEmitsPpseRejectedNoApplicationsFoundOnEmptyPpse() async {
        // why: this test was previously named ...PpseHasNoApplicationTemplates
        // and asserted .noApplicationSelected, expecting EmvReader to detect
        // an empty Ppse.applications list. After Fix 4 we know that path is
        // unreachable: Ppse.parse returns Err(NoApplicationsFound) → ppseRejected
        // BEFORE the empty-Ppse object can be constructed. The corrected
        // contract is asserted here and ReaderError.noApplicationSelected has
        // been removed from the public catalogue.
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.ppseNoApplicationsResponse),
        ])
        let states = await collectStates(transport: transport)
        // why: see ppseUnsupported test above for String(describing:) safety note.
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
        // why: see ppseUnsupported test above for String(describing:) safety note.
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
        // why: see ppseUnsupported test above for String(describing:) safety note.
        guard case .failed(let error) = states.last,
              case .ioFailure(let reason) = error else {
            return XCTFail("Expected .ioFailure, got \(String(describing: states.last))")
        }
        XCTAssertEqual(reason, .generic)
    }

    func testReadEmitsIoFailureTagLostWhenTransportThrowsTagLost() async {
        let transport = FakeIso7816Transport(
            script: [],
            connectError: TransportError.io(.tagLost)
        )
        let states = await collectStates(transport: transport)
        // why: see ppseUnsupported test above for String(describing:) safety note.
        guard case .failed(let error) = states.last,
              case .ioFailure(let reason) = error else {
            return XCTFail("Expected .ioFailure, got \(String(describing: states.last))")
        }
        XCTAssertEqual(reason, .tagLost)
    }

    func testReadEmitsIoFailureTimeoutWhenTransportThrowsTimeout() async {
        let transport = FakeIso7816Transport(
            script: [],
            connectError: TransportError.io(.timeout)
        )
        let states = await collectStates(transport: transport)
        // why: see ppseUnsupported test above for String(describing:) safety note.
        guard case .failed(let error) = states.last,
              case .ioFailure(let reason) = error else {
            return XCTFail("Expected .ioFailure, got \(String(describing: states.last))")
        }
        XCTAssertEqual(reason, .timeout)
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
        // why: see ppseUnsupported test above for String(describing:) safety note.
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
        // why: see ppseUnsupported test above for String(describing:) safety note.
        guard case .done = states.last else {
            return XCTFail("Expected .done, got \(String(describing: states.last))")
        }
    }

    // MARK: Cancellation

    func testReadClosesTransportWhenCollectingTaskIsCancelled() async {
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

        // why: deterministic — waitForClose() resumes as soon as
        // FakeIso7816Transport.close() runs (driven by the AsyncStream's
        // onTermination handler). No Task.sleep guesswork.
        await transport.waitForClose()
        XCTAssertTrue(transport.closed,
                      "transport must close when collecting Task is cancelled")
    }

    // MARK: PDOL-aware GPO + AID injection

    func testReadParsesPdolFromVisaFciAndSendsCorrectGpoCommandBody() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.visaSelectFciWithPdolResponse),
            (Transcripts.visaGpoCommandPdol, Transcripts.visaGpoResponse),
            (readRecord1Sfi1Prefix(), Transcripts.visaRecord1Response),
        ])
        let states = await collectStates(transport: transport)
        guard case .done = states.last else {
            return XCTFail("Expected .done after PDOL flow, got \(String(describing: states.last))")
        }
    }

    func testReadUsesInjectedAidAndParsesRecordsLacking4F() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.visaSelectFciResponse),
            (ApduCommands.gpoDefault, Transcripts.visaGpoResponse),
            (readRecord1Sfi1Prefix(), Transcripts.visaRecordWithout4FResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .done(let card) = states.last else {
            return XCTFail("Expected .done, got \(String(describing: states.last))")
        }
        XCTAssertEqual(Mapping.aidHex(card.aid), "A0000000031010")
    }

    func testReadEmitsSelectAidFciRejectedOnMalformedFciBody() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.selectFciMalformedResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .selectAidFciRejected = error else {
            return XCTFail("Expected .selectAidFciRejected, got \(String(describing: states.last))")
        }
    }

    func testReadEmitsPdolRejectedOnTruncatedPdolBytes() async {
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.selectFciTruncatedPdolResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .failed(let error) = states.last,
              case .pdolRejected = error else {
            return XCTFail("Expected .pdolRejected, got \(String(describing: states.last))")
        }
    }

    func testReadWithCustomTerminalConfigOverridesDefaultTtq() async {
        let customTtq = Data([0x26, 0x00, 0x00, 0x00])
        let expectedGpo = Data([
            0x80, 0xA8, 0x00, 0x00, 0x06,
            0x83, 0x04, 0x26, 0x00, 0x00, 0x00,
            0x00,
        ])
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            (selectVisaPrefix(), Transcripts.visaSelectFciWithPdolResponse),
            (expectedGpo, Transcripts.visaGpoResponse),
            (readRecord1Sfi1Prefix(), Transcripts.visaRecord1Response),
        ])
        let custom = TerminalConfig(
            terminalTransactionQualifiers: customTtq,
            terminalCountryCode: TerminalConfig.default.terminalCountryCode,
            transactionCurrencyCode: TerminalConfig.default.transactionCurrencyCode,
            amountAuthorised: TerminalConfig.default.amountAuthorised,
            amountOther: TerminalConfig.default.amountOther,
            terminalVerificationResults: TerminalConfig.default.terminalVerificationResults,
            transactionType: TerminalConfig.default.transactionType,
            terminalType: TerminalConfig.default.terminalType,
            terminalCapabilities: TerminalConfig.default.terminalCapabilities,
            additionalTerminalCapabilities: TerminalConfig.default.additionalTerminalCapabilities,
            applicationVersionNumber: TerminalConfig.default.applicationVersionNumber
        )
        let reader = EmvReader(transportFactory: { transport })
        var collected: [ReaderState] = []
        for await state in reader.read(config: custom) {
            collected.append(state)
        }
        guard case .done = collected.last else {
            return XCTFail("Expected .done with custom config, got \(String(describing: collected.last))")
        }
    }

    // MARK: Real-card transcripts (#59) — FCI + GPO + records TLV union

    func testReadReturnsDoneForVisaCreditChaseMsdOnlyTranscript() async {
        // why: MSD-only mode — GPO format-2 carries inline Track 2 + AIP,
        // no AFL. Reader MUST union FCI inline TLV + GPO inline TLV (no
        // records issued because Afl is empty). Pre-#59 record-only flow
        // surfaced MissingRequiredTag(57) here.
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaCreditChasePpseResponse),
            (selectVisaPrefix(), Transcripts.visaCreditChaseSelectAidFci),
            (gpoCommandHeaderForFullPdol(), Transcripts.visaCreditChaseGpoResponse),
        ])
        let states = await collectStates(transport: transport)
        guard case .done(let card) = states.last else {
            return XCTFail("Expected .done for Visa Credit Chase, got \(String(describing: states.last))")
        }
        XCTAssertEqual(Mapping.aidHex(card.aid), "A0000000031010")
        // why: EmvCard.toString() interpolates `pan` through Pan.toString,
        // which masks (first 6 + asterisks + last 4). Asserting the masked
        // form appears proves the parser resolved the inline 57 from the
        // GPO body — not via the legacy record-only path which would have
        // surfaced MissingRequiredTag(57) and never reached .done.
        XCTAssertTrue(String(describing: card).contains("411111******1111"),
                      "Expected masked PAN in card description; got \(String(describing: card))")
    }

    func testReadReturnsDoneForVisaDebitChaseSplitTranscript() async {
        // why: split-data mode — GPO format-2 carries inline Track 2 +
        // AIP + AFL pointing at SFI 1 record 6. Record 6 itself carries
        // only 5F28 + 9F07 (no 5A, no 57). Reader MUST union all three
        // sources for EmvParser to resolve PAN. Pre-#59 record-only flow
        // surfaced MissingRequiredTag(57) here.
        let transport = FakeIso7816Transport(script: [
            (ApduCommands.ppseSelect, Transcripts.visaDebitChasePpseResponse),
            (selectVisaPrefix(), Transcripts.visaDebitChaseSelectAidFci),
            (gpoCommandHeaderForFullPdol(), Transcripts.visaDebitChaseGpoResponse),
            (readRecord6Sfi1Prefix(), Transcripts.visaDebitChaseRecord6Sfi1),
        ])
        let states = await collectStates(transport: transport)
        guard case .done(let card) = states.last else {
            return XCTFail("Expected .done for Visa Debit Chase, got \(String(describing: states.last))")
        }
        XCTAssertEqual(Mapping.aidHex(card.aid), "A0000000031010")
        // why: canonical Visa test PAN 4012888888881881 → masked form.
        // Asserting the masked PAN in card.toString proves union worked:
        // PAN lived inline in the GPO 57 tag (record 6 carried only 5F28
        // + 9F07), so a record-only flow would surface MissingRequiredTag.
        XCTAssertTrue(String(describing: card).contains("401288******1881"),
                      "Expected masked PAN in card description; got \(String(describing: card))")
    }

    // why: 5-byte GPO command header with Lc = 0x23 (35 = 2-byte template
    // wrapper + 33-byte PDOL response). Used as a prefix matcher because
    // the trailing PDOL response varies with the unpredictable number and
    // current date — both untestable as deterministic exact bytes.
    private func gpoCommandHeaderForFullPdol() -> Data {
        Data([0x80, 0xA8, 0x00, 0x00, 0x23])
    }

    private func readRecord6Sfi1Prefix() -> Data {
        Data([0x00, 0xB2, 0x06, 0x0C])
    }

    // MARK: PCI regression — done(card) bridged description must not leak PAN

    func testDoneStateStringDescribingDoesNotLeakRawPan() async {
        let transport = FakeIso7816Transport(script: visaScript())
        let states = await collectStates(transport: transport)
        let rendered = String(describing: states.last)
        XCTAssertFalse(
            rendered.contains("4111111111111111"),
            "ReaderState.done description must not contain raw PAN; got: \(rendered.prefix(200))"
        )
    }

    func testDoneStateDumpDoesNotLeakRawPan() async {
        let transport = FakeIso7816Transport(script: visaScript())
        let states = await collectStates(transport: transport)
        var output = ""
        dump(states.last, to: &output)
        XCTAssertFalse(
            output.contains("4111111111111111"),
            "ReaderState.done dump must not contain raw PAN; got: \(output.prefix(500))"
        )
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
