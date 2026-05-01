import Foundation
import Shared

/// Test fixtures for `EmvReader` consumers. Lives inside the `EmvReader`
/// Swift package so consumers (sample app tests, integration suites)
/// don't have to import `Shared` or touch `KotlinByteArray` directly —
/// CLAUDE.md §8 forbids exposing KMP types past the iOS bridge layer.
///
/// The bytes here go through the same `EmvParser.parseOrThrow` path
/// the production reader uses, so the resulting ``EmvCard`` is
/// byte-identical to what a real card produces on the happy path.
public enum EmvReaderTestFixtures {

    /// Sanitized canonical Visa contactless test card. Identical to
    /// `:shared` `Fixtures.VISA_CLASSIC` — kept in sync with the Kotlin
    /// fixture so a regression in either surface fails both test suites.
    ///
    /// - PAN: `4111111111111111` (industry-standard test PAN, Luhn-valid).
    /// - Expiry: 2028-12.
    /// - Cardholder: `VISA TEST`.
    /// - Application label: `VISA`.
    /// - AID: `A0000000031010`.
    /// - Track 2: present.
    public static func sampleVisaCard() -> EmvCard {
        return EmvParser.shared.parseOrThrow(apduResponses: [makeKotlinByteArray(visaClassicBytes)])
    }

    /// Raw bytes for the Visa Classic READ RECORD response. Mirrors
    /// `:shared` `Fixtures.VISA_CLASSIC`. `Int8` storage so the
    /// per-byte conversion to `KotlinByteArray` (which holds signed
    /// bytes on the JVM/native side) is a no-op.
    private static let visaClassicBytes: [Int8] = [
        0x70, 0x3D,
        0x4F, 0x07, Int8(bitPattern: 0xA0), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
        0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        0x57, 0x10,
        0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        Int8(bitPattern: 0xD2), Int8(bitPattern: 0x81), 0x22, 0x01, 0x00, 0x00, 0x00, 0x00,
    ]

    private static func makeKotlinByteArray(_ bytes: [Int8]) -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(bytes.count))
        for (index, value) in bytes.enumerated() {
            array.set(index: Int32(index), value: value)
        }
        return array
    }
}
