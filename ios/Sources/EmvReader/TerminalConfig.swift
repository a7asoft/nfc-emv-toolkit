import Foundation

/// Terminal-side data the reader supplies in the GPO PDOL response.
///
/// Mirrors the Kotlin `io.github.a7asoft.nfcemv.extract.TerminalConfig`
/// data class. Defaults match "read-only contactless terminal" — TTQ
/// `36 00 00 00`, US country / USD currency, zero amounts. Override
/// individual fields to validate against cards that reject the
/// conservative defaults.
///
/// Transaction date and unpredictable number are NOT stored here so a
/// long-lived ``TerminalConfig`` does not ship stale values; they are
/// computed at READ time inside ``EmvReader``.
public struct TerminalConfig: Sendable {
    /// Tag `9F66` — Terminal Transaction Qualifiers, 4 bytes.
    public let terminalTransactionQualifiers: Data
    /// Tag `9F1A` — Terminal Country Code (ISO 3166-1 numeric), 2 bytes.
    public let terminalCountryCode: Data
    /// Tag `5F2A` — Transaction Currency Code (ISO 4217 numeric), 2 bytes.
    public let transactionCurrencyCode: Data
    /// Tag `9F02` — Amount, Authorised (n12 BCD), 6 bytes.
    public let amountAuthorised: Data
    /// Tag `9F03` — Amount, Other (n12 BCD), 6 bytes.
    public let amountOther: Data
    /// Tag `95` — Terminal Verification Results, 5 bytes.
    public let terminalVerificationResults: Data
    /// Tag `9C` — Transaction Type, 1 byte.
    public let transactionType: UInt8
    /// Tag `9F35` — Terminal Type, 1 byte.
    public let terminalType: UInt8
    /// Tag `9F33` — Terminal Capabilities, 3 bytes.
    public let terminalCapabilities: Data
    /// Tag `9F40` — Additional Terminal Capabilities, 5 bytes.
    public let additionalTerminalCapabilities: Data
    /// Tag `9F09` — Application Version Number (Terminal), 2 bytes.
    public let applicationVersionNumber: Data

    public init(
        terminalTransactionQualifiers: Data,
        terminalCountryCode: Data,
        transactionCurrencyCode: Data,
        amountAuthorised: Data,
        amountOther: Data,
        terminalVerificationResults: Data,
        transactionType: UInt8,
        terminalType: UInt8,
        terminalCapabilities: Data,
        additionalTerminalCapabilities: Data,
        applicationVersionNumber: Data
    ) {
        self.terminalTransactionQualifiers = terminalTransactionQualifiers
        self.terminalCountryCode = terminalCountryCode
        self.transactionCurrencyCode = transactionCurrencyCode
        self.amountAuthorised = amountAuthorised
        self.amountOther = amountOther
        self.terminalVerificationResults = terminalVerificationResults
        self.transactionType = transactionType
        self.terminalType = terminalType
        self.terminalCapabilities = terminalCapabilities
        self.additionalTerminalCapabilities = additionalTerminalCapabilities
        self.applicationVersionNumber = applicationVersionNumber
    }

    /// "Read-only contactless terminal" defaults — same byte values as
    /// the Kotlin `TerminalConfig.Companion.default()` factory. TTQ
    /// `36 00 00 00` keeps the online-cryptogram bit cleared so issuer
    /// kernels (Visa kernel-3 in particular) emit the AFL in the GPO
    /// response. See issue #59 for the failure mode this avoids.
    public static let `default`: TerminalConfig = TerminalConfig(
        terminalTransactionQualifiers: Data([0x36, 0x00, 0x00, 0x00]),
        terminalCountryCode: Data([0x08, 0x40]),
        transactionCurrencyCode: Data([0x08, 0x40]),
        amountAuthorised: Data(repeating: 0, count: 6),
        amountOther: Data(repeating: 0, count: 6),
        terminalVerificationResults: Data(repeating: 0, count: 5),
        transactionType: 0x00,
        terminalType: 0x22,
        terminalCapabilities: Data([0x60, 0x08, 0x08]),
        additionalTerminalCapabilities: Data(repeating: 0, count: 5),
        applicationVersionNumber: Data([0x00, 0x8C])
    )
}
