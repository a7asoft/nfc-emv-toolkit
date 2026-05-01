package io.github.a7asoft.nfcemv.reader

import io.github.a7asoft.nfcemv.extract.EmvCardError
import io.github.a7asoft.nfcemv.extract.GpoError
import io.github.a7asoft.nfcemv.extract.PdolError
import io.github.a7asoft.nfcemv.extract.PpseError
import io.github.a7asoft.nfcemv.extract.SelectAidFciError

/**
 * Typed reasons a [ContactlessReader] read terminated in [ReaderState.Failed].
 *
 * Variants carry only structural metadata (status words, structural
 * sub-error references, [IoReason] enum). They never embed raw value
 * bytes from the card or arbitrary [Throwable] messages — the latter
 * would let the underlying exception's `message` string surface in
 * `toString()` and potentially leak PCI-sensitive context.
 */
public sealed interface ReaderError {

    /**
     * The IsoDep channel produced an `IOException`. The structural
     * [reason] enum identifies the subtype without embedding the raw
     * exception message.
     */
    public data class IoFailure(val reason: IoReason) : ReaderError

    /**
     * `SELECT 2PAY.SYS.DDF01` returned `6A 82` (file/application not
     * found). Card does not advertise a PPSE — fall back to PSE / direct
     * AID selection is out of scope for v0.2.0.
     */
    public data object PpseUnsupported : ReaderError

    /** PPSE returned no usable application templates. */
    public data object NoApplicationSelected : ReaderError

    /** A non-`90 00` status word was returned by an APDU we cannot recover from. */
    public data class ApduStatusError(val sw1: Byte, val sw2: Byte) : ReaderError

    /** PPSE FCI was malformed. */
    public data class PpseRejected(val cause: PpseError) : ReaderError

    /** GPO response was malformed. */
    public data class GpoRejected(val cause: GpoError) : ReaderError

    /** SELECT AID FCI body was malformed. */
    public data class SelectAidFciRejected(val cause: SelectAidFciError) : ReaderError

    /** PDOL bytes from SELECT AID FCI failed structural parse. */
    public data class PdolRejected(val cause: PdolError) : ReaderError

    /** `EmvParser.parse` rejected the assembled record stream. */
    public data class ParseFailed(val cause: EmvCardError) : ReaderError
}

/**
 * Structural classification of the IO failure surfaced by
 * [ReaderError.IoFailure]. Maps `android.nfc.tech.IsoDep`'s I/O
 * exception classes to a closed enum so [ReaderError.toString] cannot
 * leak whatever the underlying exception's message string carries.
 */
public enum class IoReason {
    /** `android.nfc.TagLostException` was thrown — tag moved out of field. */
    TagLost,

    /** RF transceive timed out without a response (e.g. `SocketTimeoutException`). */
    Timeout,

    /** Generic `java.io.IOException` without a more specific subtype. */
    Generic,
}
