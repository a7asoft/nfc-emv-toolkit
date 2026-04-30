import Foundation
import Shared

/// Bridges between Kotlin types (exposed via `Shared.xcframework`)
/// and Swift values used by the reader flow.
///
/// Centralising the mapping here keeps the (sometimes awkward)
/// runtime `is` checks against ObjC-bridged Kotlin sealed types out
/// of `EmvReader.swift`'s read flow.
///
/// The Kotlin value classes `Aid` and `Pan` are NOT directly callable
/// across the ObjC bridge — they appear as `Any` (boxed). Their
/// `description` (Kotlin `toString()`) is the only accessible method,
/// which fortunately returns the uppercase normalised hex form for
/// `Aid` (and the masked PAN for `Pan`). We extract bytes by hex-
/// decoding the description.
/// Two-case outcome carrying either a parsed Kotlin value or a Kotlin
/// sealed-error variant boxed as `Any`. `Swift.Result` is unsuitable
/// because Kotlin sealed-error protocols (`PpseError`, `GpoError`,
/// `EmvCardError`) do not conform to `Swift.Error`.
internal enum ParseOutcome<T> {
    case ok(T)
    case err(Any)
}

internal enum Mapping {

    // MARK: - Aid

    // why: hex-decoding String(describing:) is scoped to Aid (a non-PCI
    // value class whose toString returns uppercase hex). MUST NOT be
    // applied to Pan/Track2 — those mask their toString and the decode
    // would either misbehave or fire `decodeHex`'s preconditionFailure.

    /// Uppercase hex string for an `Aid` (Kotlin value class, boxed
    /// into Swift as `Any`). Kotlin's `Aid.toString()` returns the
    /// normalised uppercase hex; we call `description` on the boxed
    /// value to retrieve it.
    static func aidHex(_ aid: Any) -> String {
        return String(describing: aid).uppercased()
    }

    /// Raw bytes of an `Aid`, derived from its uppercase hex form.
    static func aidBytes(_ aid: Any) -> Data {
        return decodeHex(aidHex(aid))
    }

    // MARK: - Companion-extension parse calls

    /// Run `Ppse.parse` on the given response payload (status word
    /// stripped) and adapt the sealed-result outcome into a Swift
    /// ``ParseOutcome``. Kotlin sealed-error protocols (`PpseError`,
    /// `GpoError`, `EmvCardError`) cannot conform to `Swift.Error`,
    /// so we use our own two-case enum rather than `Result`.
    static func parsePpse(_ bytes: Data) -> ParseOutcome<Ppse> {
        let kotlinBytes = bytes.toKotlinByteArray()
        let result = Ppse.companion.parse(bytes: kotlinBytes)
        if let ok = result as? PpseResultOk {
            return .ok(ok.ppse)
        }
        if let err = result as? PpseResultErr {
            return .err(err.error)
        }
        fatalError("Unhandled PpseResult variant — XCFramework export changed")
    }

    /// Run `Gpo.parse` on the given response payload and adapt the
    /// sealed-result outcome into a Swift ``ParseOutcome``.
    static func parseGpo(_ bytes: Data) -> ParseOutcome<Gpo> {
        let kotlinBytes = bytes.toKotlinByteArray()
        let result = Gpo.companion.parse(bytes: kotlinBytes)
        if let ok = result as? GpoResultOk {
            return .ok(ok.gpo)
        }
        if let err = result as? GpoResultErr {
            return .err(err.error)
        }
        fatalError("Unhandled GpoResult variant — XCFramework export changed")
    }

    /// Run `EmvParser.parse` on accumulated READ RECORD payloads
    /// (status word already stripped). Returns ``ParseOutcome/ok(_:)``
    /// with the composed `EmvCard` or ``ParseOutcome/err(_:)`` carrying
    /// the boxed `EmvCardError`.
    static func parseEmvCard(_ records: [Data]) -> ParseOutcome<EmvCard> {
        let kotlinRecords = records.map { $0.toKotlinByteArray() }
        let result = EmvParser.shared.parse(apduResponses: kotlinRecords)
        if let ok = result as? EmvCardResultOk {
            return .ok(ok.card)
        }
        if let err = result as? EmvCardResultErr {
            return .err(err.error)
        }
        fatalError("Unhandled EmvCardResult variant — XCFramework export changed")
    }

    // MARK: - Error mapping

    /// Map a transport-thrown `Error` to ``IoReason``.
    ///
    /// The production ``NFCISO7816TagTransport`` translates CoreNFC's
    /// `NFCReaderError` codes into ``TransportError`` before throwing,
    /// so the read flow stays free of CoreNFC types. Any other `Error`
    /// (test fakes, programmer error) maps to ``IoReason/generic``.
    static func ioReason(from error: Error) -> IoReason {
        if let transport = error as? TransportError, case .io(let reason) = transport {
            return reason
        }
        return .generic
    }

    // MARK: - Hex helpers

    private static func decodeHex(_ hex: String) -> Data {
        precondition(hex.count % 2 == 0, "hex length must be even")
        var data = Data(capacity: hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else {
                preconditionFailure("non-hex character in AID description")
            }
            data.append(byte)
            index = next
        }
        return data
    }
}

// MARK: - Bridges between Foundation Data and Kotlin ByteArray

extension Data {
    /// `Data` → `KotlinByteArray` for APDU input to bridged Kotlin parsers.
    func toKotlinByteArray() -> KotlinByteArray {
        let bytes = KotlinByteArray(size: Int32(count))
        for (i, byte) in self.enumerated() {
            bytes.set(index: Int32(i), value: Int8(bitPattern: byte))
        }
        return bytes
    }
}
