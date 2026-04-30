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
    ///
    /// When `aid` is non-nil, the 2-arg `EmvParser.parse(aid:apduResponses:)`
    /// overload runs — matching the real-card flow where AID lives only in
    /// PPSE / SELECT FCI (not in records). When `aid` is nil, the 1-arg
    /// overload runs, used for synthetic transcripts that include `4F`
    /// inline.
    static func parseEmvCard(aid: Any? = nil, _ records: [Data]) -> ParseOutcome<EmvCard> {
        let kotlinRecords = records.map { $0.toKotlinByteArray() }
        let result: Any
        if let aid = aid {
            result = EmvParser.shared.parse(aid: aid, apduResponses: kotlinRecords)
        } else {
            result = EmvParser.shared.parse(apduResponses: kotlinRecords)
        }
        if let ok = result as? EmvCardResultOk {
            return .ok(ok.card)
        }
        if let err = result as? EmvCardResultErr {
            return .err(err.error)
        }
        fatalError("Unhandled EmvCardResult variant — XCFramework export changed")
    }

    /// Run `SelectAidFci.parse` on a SELECT AID FCI response body
    /// (status word stripped). Returns the parsed `SelectAidFci`
    /// (which carries optional PDOL bytes) or the boxed
    /// `SelectAidFciError`.
    static func parseSelectAidFci(_ bytes: Data) -> ParseOutcome<SelectAidFci> {
        let kotlinBytes = bytes.toKotlinByteArray()
        let result = SelectAidFci.companion.parse(bytes: kotlinBytes)
        if let ok = result as? SelectAidFciResultOk {
            return .ok(ok.fci)
        }
        if let err = result as? SelectAidFciResultErr {
            return .err(err.error)
        }
        fatalError("Unhandled SelectAidFciResult variant — XCFramework export changed")
    }

    /// Run `Pdol.parse` on PDOL bytes (the value of tag `9F38`).
    /// Returns the parsed `Pdol` or the boxed `PdolError`.
    static func parsePdol(_ bytes: Data) -> ParseOutcome<Pdol> {
        let kotlinBytes = bytes.toKotlinByteArray()
        let result = Pdol.companion.parse(bytes: kotlinBytes)
        if let ok = result as? PdolResultOk {
            return .ok(ok.pdol)
        }
        if let err = result as? PdolResultErr {
            return .err(err.error)
        }
        fatalError("Unhandled PdolResult variant — XCFramework export changed")
    }

    /// Build the GPO PDOL response bytes per EMV Book 3 §5.4.
    static func buildPdolResponse(
        pdol: Pdol,
        config: TerminalConfig,
        transactionDate: Data,
        unpredictableNumber: Data
    ) -> Data {
        let kotlinConfig = toKotlinTerminalConfig(config)
        let bytes = PdolResponseBuilder.shared.build(
            pdol: pdol,
            config: kotlinConfig,
            transactionDate: transactionDate.toKotlinByteArray(),
            unpredictableNumber: unpredictableNumber.toKotlinByteArray()
        )
        return bytes.toData()
    }

    /// Bridge a Swift ``TerminalConfig`` to the Kotlin
    /// `Shared.TerminalConfig` data class. Uses the public
    /// designated initializer auto-generated by the Kotlin/Native
    /// ObjC bridge.
    static func toKotlinTerminalConfig(_ config: TerminalConfig) -> Shared.TerminalConfig {
        return Shared.TerminalConfig(
            terminalTransactionQualifiers: config.terminalTransactionQualifiers.toKotlinByteArray(),
            terminalCountryCode: config.terminalCountryCode.toKotlinByteArray(),
            transactionCurrencyCode: config.transactionCurrencyCode.toKotlinByteArray(),
            amountAuthorised: config.amountAuthorised.toKotlinByteArray(),
            amountOther: config.amountOther.toKotlinByteArray(),
            terminalVerificationResults: config.terminalVerificationResults.toKotlinByteArray(),
            transactionType: Int8(bitPattern: config.transactionType),
            terminalType: Int8(bitPattern: config.terminalType),
            terminalCapabilities: config.terminalCapabilities.toKotlinByteArray(),
            additionalTerminalCapabilities: config.additionalTerminalCapabilities.toKotlinByteArray(),
            applicationVersionNumber: config.applicationVersionNumber.toKotlinByteArray()
        )
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

extension KotlinByteArray {
    /// `KotlinByteArray` → `Data`. Used to surface bytes returned from
    /// bridged Kotlin builders (e.g. `PdolResponseBuilder.build`).
    func toData() -> Data {
        var data = Data(count: Int(size))
        for i in 0..<Int(size) {
            data[i] = UInt8(bitPattern: get(index: Int32(i)))
        }
        return data
    }
}
