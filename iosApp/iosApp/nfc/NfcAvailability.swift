import Foundation
#if canImport(CoreNFC)
import CoreNFC
#endif

/// Categorical NFC capability state for UI display.
///
/// Mirrors the Kotlin `NfcAvailabilityStatus` sealed catalogue used by
/// the Android composeApp sample. iOS only distinguishes "available"
/// vs "unavailable" because CoreNFC has no "user disabled" state — the
/// hardware either supports `NFCTagReaderSession` or it does not.
public enum NfcAvailabilityStatus: Sendable, Equatable {
    /// The device exposes `NFCTagReaderSession.readingAvailable == true`.
    /// Ready to start a contactless read.
    case available
    /// Either the simulator (no CoreNFC), an iPad without NFC, or a
    /// device whose CoreNFC entitlement check failed at install time.
    case unavailable
}

/// Abstraction over `NFCTagReaderSession.readingAvailable` so the
/// view-model can be unit-tested without a real device. Production
/// implementation is ``SystemNfcAvailability``; tests inject a fake
/// matching this protocol.
public protocol NfcAvailability: Sendable {
    /// Snapshot the current device NFC capability state.
    func current() -> NfcAvailabilityStatus
}

/// Production ``NfcAvailability`` backed by
/// `NFCTagReaderSession.readingAvailable`.
///
/// On the iOS simulator (or any target without CoreNFC) the type still
/// compiles but always reports ``NfcAvailabilityStatus/unavailable`` —
/// the sample app remains buildable on the simulator while the real
/// read flow only runs on device.
public struct SystemNfcAvailability: NfcAvailability {

    /// Build the production NFC capability checker. Trivial — kept as
    /// an explicit initializer so callers in `iOSApp` make the
    /// dependency wiring readable.
    public init() {}

    public func current() -> NfcAvailabilityStatus {
        #if canImport(CoreNFC)
        return NFCTagReaderSession.readingAvailable ? .available : .unavailable
        #else
        return .unavailable
        #endif
    }
}
