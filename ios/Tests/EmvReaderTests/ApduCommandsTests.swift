import XCTest
@testable import EmvReader

/// Direct unit tests for ``ApduCommands`` boundary conditions.
///
/// Mirrors the Android `ApduCommandsTest` discipline (added in #49):
/// every spec-mandated bound on ``ApduCommands/selectAid(aidBytes:)``
/// and ``ApduCommands/readRecord(recordNumber:sfi:)`` gets a positive
/// and a negative test, and the response-tail helpers are pinned for
/// short / minimal inputs.
final class ApduCommandsTests: XCTestCase {

    // MARK: selectAid

    func testSelectAidWithValidSevenByteAidProducesExpectedHeader() throws {
        let aid = Data([0xA0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10])
        let command = try ApduCommands.selectAid(aidBytes: aid)
        XCTAssertEqual(
            command,
            Data([0x00, 0xA4, 0x04, 0x00, 0x07,
                  0xA0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
                  0x00])
        )
    }

    func testSelectAidWithFourByteAidThrowsAidLengthOutOfRange() {
        let tooShort = Data([0xA0, 0x00, 0x00, 0x03])
        XCTAssertThrowsError(try ApduCommands.selectAid(aidBytes: tooShort)) { error in
            guard case ApduCommandError.aidLengthOutOfRange(let count) = error else {
                return XCTFail("Expected aidLengthOutOfRange, got \(error)")
            }
            XCTAssertEqual(count, 4)
        }
    }

    func testSelectAidWithSeventeenByteAidThrowsAidLengthOutOfRange() {
        let tooLong = Data(repeating: 0xA0, count: 17)
        XCTAssertThrowsError(try ApduCommands.selectAid(aidBytes: tooLong)) { error in
            guard case ApduCommandError.aidLengthOutOfRange(let count) = error else {
                return XCTFail("Expected aidLengthOutOfRange, got \(error)")
            }
            XCTAssertEqual(count, 17)
        }
    }

    // MARK: readRecord

    func testReadRecordWithValidParamsProducesExpectedFiveBytes() throws {
        let command = try ApduCommands.readRecord(recordNumber: 1, sfi: 1)
        XCTAssertEqual(command, Data([0x00, 0xB2, 0x01, 0x0C, 0x00]))
    }

    func testReadRecordWithRecordNumberZeroThrowsRecordNumberOutOfRange() {
        XCTAssertThrowsError(try ApduCommands.readRecord(recordNumber: 0, sfi: 1)) { error in
            guard case ApduCommandError.recordNumberOutOfRange(let value) = error else {
                return XCTFail("Expected recordNumberOutOfRange, got \(error)")
            }
            XCTAssertEqual(value, 0)
        }
    }

    func testReadRecordWithRecordNumber255ThrowsRecordNumberOutOfRange() {
        // why: 0xFF is reserved for future use per ISO/IEC 7816-4 §7.3.3.
        XCTAssertThrowsError(try ApduCommands.readRecord(recordNumber: 255, sfi: 1)) { error in
            guard case ApduCommandError.recordNumberOutOfRange(let value) = error else {
                return XCTFail("Expected recordNumberOutOfRange, got \(error)")
            }
            XCTAssertEqual(value, 255)
        }
    }

    func testReadRecordWithSfiZeroThrowsSfiOutOfRange() {
        XCTAssertThrowsError(try ApduCommands.readRecord(recordNumber: 1, sfi: 0)) { error in
            guard case ApduCommandError.sfiOutOfRange(let value) = error else {
                return XCTFail("Expected sfiOutOfRange, got \(error)")
            }
            XCTAssertEqual(value, 0)
        }
    }

    func testReadRecordWithSfi31ThrowsSfiOutOfRange() {
        XCTAssertThrowsError(try ApduCommands.readRecord(recordNumber: 1, sfi: 31)) { error in
            guard case ApduCommandError.sfiOutOfRange(let value) = error else {
                return XCTFail("Expected sfiOutOfRange, got \(error)")
            }
            XCTAssertEqual(value, 31)
        }
    }

    // MARK: status word helpers

    func testIsSuccessReturnsTrueOn9000() {
        XCTAssertTrue(ApduCommands.isSuccess(Data([0x70, 0x00, 0x90, 0x00])))
    }

    func testIsSuccessReturnsFalseOn6A82() {
        XCTAssertFalse(ApduCommands.isSuccess(Data([0x6A, 0x82])))
    }

    func testIsSuccessReturnsFalseOnSingleByteResponse() {
        XCTAssertFalse(ApduCommands.isSuccess(Data([0x90])))
    }

    func testDataFieldStripsStatusWord() {
        let response = Data([0x6F, 0x05, 0x84, 0x01, 0xAA, 0x90, 0x00])
        XCTAssertEqual(ApduCommands.dataField(response),
                       Data([0x6F, 0x05, 0x84, 0x01, 0xAA]))
    }

    func testStatusWordReturnsLastTwoBytes() {
        let response = Data([0x70, 0x00, 0x6A, 0x82])
        let (sw1, sw2) = ApduCommands.statusWord(response)
        XCTAssertEqual(sw1, 0x6A)
        XCTAssertEqual(sw2, 0x82)
    }
}
