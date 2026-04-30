package io.github.a7asoft.nfcemv.extract.fixtures

/**
 * Sanitized APDU `READ RECORD` response fixtures, one per EMV brand.
 *
 * Each accessor returns a single [ByteArray] representing the response data
 * field of a successful `READ RECORD` (SW1 SW2 already stripped by the
 * transport layer), wrapped in a `70` (Application File Locator record
 * template) per EMV Book 3 §10.5.4. Each fixture carries the six tags
 * the parser needs for a complete [io.github.a7asoft.nfcemv.extract.EmvCard]:
 *
 * - `4F`  Application Identifier (AID) — EMV Book 3 §A.1
 * - `5A`  Primary Account Number (PAN), packed BCD — EMV Book 3 §A.1
 * - `5F24` Application Expiration Date, YYMMDD BCD — EMV Book 3 §A.1
 * - `5F20` Cardholder Name, ISO 8859-1 — EMV Book 3 §A.1
 * - `50`  Application Label, ISO 8859-1 — EMV Book 3 §A.1
 * - `57`  Track 2 Equivalent Data, packed BCD with `D` separator —
 *         EMV Book 3 §A.1 / ISO/IEC 7813 §3
 *
 * ## PCI safety
 *
 * Every fixture is **synthetic — hand-built from EMV spec; not derived
 * from any captured card.** Test PANs are drawn from publicly published
 * test ranges (Visa `4111…1111`, Mastercard `5500…0004`, Amex
 * `3782…0005`) that pass Luhn but do not correspond to any real account.
 * Discretionary Track 2 fields are zero-filled. ARQC, IAD, and other
 * cryptographic fields are absent — this milestone covers only the
 * read-only data path. See `CONTRIBUTING.md` "Test fixture PANs" for
 * the rule any new fixture must satisfy.
 *
 * ## Defensive copy
 *
 * Each accessor is a property `get()` that allocates a fresh [ByteArray]
 * per call. A test that mutates the returned array (e.g. to exercise a
 * malformed-byte error path) affects only its own copy; the next caller
 * sees the canonical bytes. There is no shared mutable state between
 * tests.
 *
 * ## Provenance
 *
 * Each fixture's byte sequence was constructed by hand, then verified by:
 * 1. Counting outer template length against sum of inner entry lengths.
 * 2. Counting Track 2 nibble total (must be 32, no F-pad needed).
 * 3. Decoding the test PAN under Luhn (`Pan.parse` integration).
 * 4. Decoding the test card through `EmvParser.parse` and asserting the
 *    expected fields match [FixtureExpectation] in `EmvParserFixturesTest`.
 *
 * If you change a fixture, you MUST recompute the outer length and the
 * Track 2 nibble count by hand. The integration test will catch
 * arithmetic errors but cycle time on a wrong byte is several minutes —
 * cheaper to recompute first.
 */
internal object Fixtures {

    /**
     * Visa Classic / Visa Credit-Debit contactless card.
     *
     * - AID: `A0000000031010` (Visa Credit/Debit, 7 bytes)
     * - PAN: `4111111111111111` (Stripe / Adyen test PAN; Luhn-valid)
     * - Expiry: 2028-12 (`5F24` value `28 12 31`)
     * - Cardholder: `VISA TEST`
     * - Label: `VISA`
     * - Track 2: PAN `4111111111111111`, expiry `2812`, service code
     *   `201` (international/normal authorization), discretionary
     *   `00000000`. 32 nibbles = 16 bytes.
     *
     * Outer template `70 3D` (61 inner bytes).
     *
     * Synthetic — not derived from any real card. See file-level KDoc.
     */
    internal val VISA_CLASSIC: ByteArray
        get() = byteArrayOf(
            0x70, 0x3D,
            // AID Visa Credit/Debit
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            // PAN BCD
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            // Expiry 2028-12-31 (YYMMDD BCD)
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
            // Cardholder "VISA TEST"
            0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
            // Application Label "VISA"
            0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
            // Track 2 Equivalent Data
            0x57, 0x10,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00, 0x00, 0x00,
        )

    /**
     * Mastercard PayPass / Mastercard Credit-Debit contactless card.
     *
     * - AID: `A0000000041010` (Mastercard Credit/Debit, 7 bytes)
     * - PAN: `5500000000000004` (older 51–55 IIN range; Luhn-valid)
     * - Expiry: 2027-06 (`5F24` value `27 06 30`)
     * - Cardholder: `MC TEST`
     * - Label: `MasterCard`
     * - Track 2: PAN `5500000000000004`, expiry `2706`, service code
     *   `201`, discretionary `00000000`. 32 nibbles = 16 bytes.
     *
     * Outer template `70 41` (65 inner bytes).
     *
     * Synthetic — not derived from any real card. See file-level KDoc.
     */
    internal val MASTERCARD_PAYPASS: ByteArray
        get() = byteArrayOf(
            0x70, 0x41,
            // AID Mastercard Credit/Debit
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10,
            // PAN BCD
            0x5A, 0x08, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04,
            // Expiry 2027-06-30 (YYMMDD BCD)
            0x5F, 0x24, 0x03, 0x27, 0x06, 0x30,
            // Cardholder "MC TEST"
            0x5F, 0x20, 0x07, 0x4D, 0x43, 0x20, 0x54, 0x45, 0x53, 0x54,
            // Application Label "MasterCard"
            0x50, 0x0A, 0x4D, 0x61, 0x73, 0x74, 0x65, 0x72, 0x43, 0x61, 0x72, 0x64,
            // Track 2 Equivalent Data
            0x57, 0x10,
            0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04,
            0xD2.toByte(), 0x70, 0x62, 0x01, 0x00, 0x00, 0x00, 0x00,
        )

    /**
     * American Express ExpressPay contactless card.
     *
     * - AID: `A000000025010701` (American Express ExpressPay, 8 bytes)
     * - PAN: `378282246310005` (Stripe test PAN; 15 digits; Luhn-valid)
     * - Expiry: 2027-09 (`5F24` value `27 09 30`)
     * - Cardholder: `AMEX TEST`
     * - Label: `AMERICAN`
     * - Track 2: PAN `378282246310005`, expiry `2709`, service code
     *   `201`, discretionary `000000000`. 32 nibbles = 16 bytes.
     *   15-digit PAN consumes 15 nibbles vs 16 for Visa/Mastercard, so
     *   discretionary is 9 nibbles (vs 8) to keep the total at an even
     *   32 — no F-pad needed.
     *
     * The 15-digit PAN packs to 16 nibbles in `5A` with a trailing
     * `F` pad: `37 82 82 24 63 10 00 5F`.
     *
     * Outer template `70 42` (66 inner bytes).
     *
     * Synthetic — not derived from any real card. See file-level KDoc.
     */
    internal val AMEX_EXPRESSPAY: ByteArray
        get() = byteArrayOf(
            0x70, 0x42,
            // AID Amex ExpressPay (contactless)
            0x4F, 0x08, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x25, 0x01, 0x07, 0x01,
            // PAN BCD with F pad
            0x5A, 0x08, 0x37, 0x82.toByte(), 0x82.toByte(), 0x24, 0x63, 0x10, 0x00, 0x5F,
            // Expiry 2027-09-30 (YYMMDD BCD)
            0x5F, 0x24, 0x03, 0x27, 0x09, 0x30,
            // Cardholder "AMEX TEST"
            0x5F, 0x20, 0x09, 0x41, 0x4D, 0x45, 0x58, 0x20, 0x54, 0x45, 0x53, 0x54,
            // Application Label "AMERICAN"
            0x50, 0x08, 0x41, 0x4D, 0x45, 0x52, 0x49, 0x43, 0x41, 0x4E,
            // Track 2 Equivalent Data
            0x57, 0x10,
            0x37, 0x82.toByte(), 0x82.toByte(), 0x24, 0x63, 0x10, 0x00, 0x5D,
            0x27, 0x09, 0x20, 0x10, 0x00, 0x00, 0x00, 0x00,
        )
}
