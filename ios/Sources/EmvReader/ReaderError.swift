import Foundation
import Shared

/// Reasons ``EmvReader/read()`` can terminate with
/// ``ReaderState/failed(_:)``.
///
/// Mirrors the Kotlin `io.github.a7asoft.nfcemv.reader.ReaderError`
/// catalogue. Structural metadata only — never embeds raw card bytes
/// (CLAUDE.md §5.8). Kotlin sub-error protocols (`PpseError`,
/// `GpoError`, `EmvCardError`) cross the bridge as
/// `id<SharedXxxError>` and are passed through as `Any` here.
public enum ReaderError: @unchecked Sendable {
    /// CoreNFC's `NFCISO7816Tag` connection or transceive raised an
    /// `Error`. ``IoReason`` distinguishes tag-loss vs timeout vs
    /// generic failure without surfacing the underlying error's
    /// `localizedDescription` (which can carry implementation noise).
    case ioFailure(IoReason)

    /// PPSE returned `6A 82` (file not found) — the card has no
    /// contactless application directory. PSE (`1PAY.SYS.DDF01`)
    /// fallback is out of scope for v0.2.x.
    case ppseUnsupported

    /// A card APDU returned a non-`90 00` status word at a stage
    /// other than PPSE-not-found. Status-word bytes are non-sensitive
    /// (public protocol).
    case apduStatusError(sw1: UInt8, sw2: UInt8)

    /// PPSE response body had the `90 00` success status but the
    /// FCI structure failed `Ppse.parse`. The associated value is the
    /// Kotlin `PpseError` sealed interface boxed as `Any`.
    case ppseRejected(Any)

    /// GPO response body was `90 00` but failed `Gpo.parse`. The
    /// associated value is the Kotlin `GpoError` sealed interface
    /// boxed as `Any`.
    case gpoRejected(Any)

    /// READ RECORD records were collected but `EmvParser.parse`
    /// surfaced an error. The associated value is the Kotlin
    /// `EmvCardError` sealed interface boxed as `Any`.
    case parseFailed(Any)
}

/// Categorical I/O failure reason for ``ReaderError/ioFailure(_:)``.
///
/// Mirrors the Kotlin `IoReason` enum from the Android reader. Maps
/// CoreNFC's `NFCReaderError` codes to a small, stable set of
/// platform-neutral categories so consumers don't have to depend on
/// CoreNFC error code semantics.
public enum IoReason: Sendable {
    /// Maps to `NFCReaderError.readerTransceiveErrorTagConnectionLost`
    /// or any session invalidation caused by the tag leaving the
    /// reader's RF field.
    case tagLost

    /// Maps to session timeout invalidation
    /// (`NFCReaderError.readerSessionInvalidationErrorSessionTimeout`).
    case timeout

    /// Catch-all for any other CoreNFC error.
    case generic
}
