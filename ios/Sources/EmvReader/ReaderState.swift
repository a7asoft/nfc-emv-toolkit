import Foundation
import Shared

/// Discrete states emitted by ``EmvReader/read()`` during a contactless
/// EMV read.
///
/// Mirrors the Kotlin `io.github.a7asoft.nfcemv.reader.ReaderState`
/// catalogue from the Android reader. Variants are duplicated as
/// Swift enum cases for idiomatic exhaustive `switch`. The associated
/// Kotlin types (``Aid``, ``EmvCard``) cross the bridge boxed as
/// reference objects from the shared XCFramework; both expose
/// `description` (their Kotlin `toString()`) which already masks
/// PCI-sensitive fields.
public enum ReaderState: @unchecked Sendable {
    /// Emitted right after the transport's `connect()` succeeds. The
    /// CoreNFC session has detected an `NFCISO7816Tag` and is ready to
    /// exchange APDUs.
    case tagDetected

    /// The reader is about to send the PPSE SELECT command
    /// (`SELECT 2PAY.SYS.DDF01`). Emitted before the APDU goes out.
    case selectingPpse

    /// PPSE returned a list of applications and the reader has chosen
    /// the lowest-priority entry; about to send SELECT-by-AID.
    /// The associated value is the Kotlin `Aid` value class boxed as
    /// `Any`; consumers can render it via `String(describing:)` to
    /// obtain the uppercase hex form.
    case selectingAid(Any)

    /// SELECT-AID succeeded. The reader is sending GPO and the
    /// subsequent READ RECORD APDUs derived from the AFL.
    case readingRecords

    /// Terminal state on success. The accumulated READ RECORD payloads
    /// were composed into an ``EmvCard`` by `EmvParser.parse`.
    case done(EmvCard)

    /// Terminal state on failure. ``ReaderError`` carries a structured
    /// reason for the failure.
    case failed(ReaderError)
}
