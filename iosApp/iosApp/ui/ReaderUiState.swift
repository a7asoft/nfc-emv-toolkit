import Foundation
import EmvReader
import Shared

/// UI-mapped state for the reader screen.
///
/// Combines the iOS NFC availability gate with the underlying
/// ``EmvReader/ReaderState`` stream into a single sealed enum so
/// ``ReaderScreen`` renders from one source of truth. Mirrors the
/// Kotlin `ReaderUiState` from the Android composeApp.
///
/// The associated ``EmvCardSummary`` is a PCI-safe display projection
/// of ``Shared/EmvCard`` — it never carries the unmasked PAN.
public enum ReaderUiState {

    /// Device has no NFC hardware (or runs on simulator). Permanent
    /// "this app needs NFC" message; no actionable CTA.
    case nfcUnavailable

    /// Ready to read. UI prompts "Tap a card to start".
    case idle

    /// User pressed Scan; reader stream has been started but no
    /// state has been emitted yet. Bridges the user-gesture instant
    /// to the first ``EmvReader/ReaderState/tagDetected``.
    case scanning

    /// Tag was just detected. UI shows initial progress.
    case tagDetected

    /// Reader is in PPSE select stage.
    case selectingPpse

    /// Reader chose an AID and is selecting it. UI shows the AID hex.
    case selectingApplication(aidHex: String)

    /// Reader is iterating AFL records.
    case readingRecords

    /// Read succeeded. UI displays the parsed ``EmvCardSummary``.
    case done(card: EmvCardSummary)

    /// Read failed. UI displays a friendly message + diagnostic detail.
    case failed(error: ReaderError, friendlyMessage: String, diagnostic: String)
}

/// PCI-safe display projection of ``Shared/EmvCard``.
///
/// Stores already-stringified fields so the UI never has access to the
/// unmasked PAN. The `pan` field carries the result of
/// `String(describing:)` on the Kotlin `Pan` value class — that
/// `toString()` masks per PCI DSS Req 3.4 (first-6 + last-4).
///
/// NEVER call `card.pan.unmasked()` when constructing a summary — the
/// sample app explicitly does not show raw PAN even with the card in
/// hand (mirrors the Android `CardSummary` discipline).
public struct EmvCardSummary: Equatable {
    /// Masked PAN string from the Kotlin `Pan.toString()`.
    public let pan: String
    /// Brand display name (e.g. "Visa", "Mastercard", "Unknown").
    public let brand: String
    /// `YearMonth.toString()` form (`YYYY-MM`).
    public let expiry: String
    /// Cardholder name as recorded by the issuer, or `nil` if absent.
    /// Treated as PCI Cardholder Data — display-only, never logged.
    public let cardholderName: String?
    /// Application label from tag `50` (or `9F12`), or `nil` if absent.
    public let applicationLabel: String?
    /// Uppercase hex of the selected AID.
    public let aidHex: String
    /// Whether the parsed card carried a Track 2 equivalent (tag `57`).
    /// We surface presence only — never the value.
    public let hasTrack2: Bool
}

extension EmvCardSummary {
    /// Build a summary from the bridged Kotlin ``Shared/EmvCard``.
    ///
    /// All field accesses go through the Kotlin `description` (i.e.
    /// `toString()`) because that path is the one with the masking
    /// contract baked in.
    public static func from(_ card: EmvCard) -> EmvCardSummary {
        return EmvCardSummary(
            pan: String(describing: card.pan),
            brand: card.brand.displayName,
            expiry: String(describing: card.expiry),
            cardholderName: card.cardholderName,
            applicationLabel: card.applicationLabel,
            aidHex: String(describing: card.aid).uppercased(),
            hasTrack2: card.track2 != nil
        )
    }
}
