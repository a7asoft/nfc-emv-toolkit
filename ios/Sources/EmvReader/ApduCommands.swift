import Foundation

/// Boundary errors thrown by ``ApduCommands`` builders when caller
/// inputs violate ISO/IEC 7816-4 length constraints.
///
/// These are programming errors — the orchestrator (``EmvReader``)
/// always passes values inside the spec range (AID lengths come from
/// `Ppse.parse`, record numbers / SFIs come from `Afl.parse`, both of
/// which validate up front). The errors exist so unit tests can pin
/// the boundary contract without crashing the test process and so the
/// guards survive release-mode optimization (`precondition` is a no-op
/// in `-O`).
public enum ApduCommandError: Error, Sendable {
    /// AID byte length outside `5..16` per ISO/IEC 7816-4 §5.4.1.
    case aidLengthOutOfRange(byteCount: Int)
    /// READ RECORD record number outside `1..254` per §7.3.3
    /// (`0` and `0xFF` are RFU).
    case recordNumberOutOfRange(value: Int)
    /// READ RECORD SFI outside `1..30` per §7.3.3.
    case sfiOutOfRange(value: Int)
    /// Response shorter than 2 bytes — no status word present.
    case responseMissingStatusWord
}

/// APDU command builders + status-word helpers per ISO/IEC 7816-4 §5.
///
/// Mirrors the Kotlin `ApduCommands` object. All commands target
/// contactless EMV applications; values are spec-mandated bit patterns,
/// not magic numbers.
internal enum ApduCommands {
    /// SELECT 2PAY.SYS.DDF01 — discovers the contactless application
    /// directory (PPSE) per EMV Book 1 §11.3.4.
    ///
    /// `00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00`
    ///
    /// Fresh `Data` per access; callers cannot mutate the underlying
    /// bytes (mirrors Kotlin's `get() = byteArrayOf(...)` defensive-copy
    /// pattern).
    static var ppseSelect: Data {
        Data([
            0x00, 0xA4, 0x04, 0x00, 0x0E,
            0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
            0x00,
        ])
    }

    /// GET PROCESSING OPTIONS with empty PDOL data (`83 00`).
    ///
    /// `80 A8 00 00 02 83 00 00`
    static var gpoDefault: Data {
        Data([0x80, 0xA8, 0x00, 0x00, 0x02, 0x83, 0x00, 0x00])
    }

    /// Build a SELECT-by-AID command per ISO/IEC 7816-4 §5.4.1 from
    /// raw AID bytes (5..16 bytes). The `Aid` Kotlin value class is
    /// not callable across the ObjC bridge, so callers obtain the
    /// AID hex via ``Mapping/aidHex(_:)`` and bytes via
    /// ``Mapping/aidBytes(_:)``.
    ///
    /// Throws ``ApduCommandError/aidLengthOutOfRange(byteCount:)`` if
    /// the AID is not in `5..16`.
    static func selectAid(aidBytes: Data) throws -> Data {
        guard aidBytes.count >= 5 && aidBytes.count <= 16 else {
            throw ApduCommandError.aidLengthOutOfRange(byteCount: aidBytes.count)
        }
        var command = Data(count: 5 + aidBytes.count + 1)
        command[0] = 0x00
        command[1] = 0xA4
        command[2] = 0x04
        command[3] = 0x00
        command[4] = UInt8(aidBytes.count)
        command.replaceSubrange(5..<(5 + aidBytes.count), with: aidBytes)
        return command
    }

    /// Build a READ RECORD command per ISO/IEC 7816-4 §7.3.3.
    ///
    /// `00 B2 [recordNumber] [(sfi << 3) | 0x04] 00`
    /// Valid `recordNumber`: 1..254 (`0xFF` is RFU). Valid `sfi`: 1..30.
    ///
    /// Throws ``ApduCommandError/recordNumberOutOfRange(value:)`` or
    /// ``ApduCommandError/sfiOutOfRange(value:)``.
    static func readRecord(recordNumber: UInt8, sfi: UInt8) throws -> Data {
        guard recordNumber >= 1 && recordNumber <= 254 else {
            throw ApduCommandError.recordNumberOutOfRange(value: Int(recordNumber))
        }
        guard sfi >= 1 && sfi <= 30 else {
            throw ApduCommandError.sfiOutOfRange(value: Int(sfi))
        }
        let p2: UInt8 = (sfi << 3) | 0x04
        return Data([0x00, 0xB2, recordNumber, p2, 0x00])
    }

    /// Returns true if the response ends with `90 00` (success).
    static func isSuccess(_ response: Data) -> Bool {
        guard response.count >= 2 else { return false }
        return response[response.count - 2] == 0x90 && response[response.count - 1] == 0x00
    }

    /// Strip the 2-byte status word from the response.
    static func dataField(_ response: Data) -> Data {
        precondition(response.count >= 2, "response missing status word")
        return response.prefix(response.count - 2)
    }

    /// Returns the (sw1, sw2) byte pair from the tail of the response.
    static func statusWord(_ response: Data) -> (UInt8, UInt8) {
        precondition(response.count >= 2, "response missing status word")
        return (response[response.count - 2], response[response.count - 1])
    }
}
