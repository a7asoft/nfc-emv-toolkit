package io.github.a7asoft.nfcemv.reader

import io.github.a7asoft.nfcemv.extract.AflError
import io.github.a7asoft.nfcemv.extract.EmvCardError
import io.github.a7asoft.nfcemv.extract.GpoError
import io.github.a7asoft.nfcemv.extract.PpseError

/**
 * Typed reasons a [ContactlessReader] read terminated in [ReaderState.Failed].
 *
 * Variants carry only structural metadata (status words, structural
 * sub-error references). They never embed raw value bytes from the card.
 */
public sealed interface ReaderError {

    /** Tag moved out of field while the read was in flight. */
    public data object TagLost : ReaderError

    /** Generic IO error from the IsoDep channel. */
    public data class IoFailure(val cause: Throwable) : ReaderError

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

    /** AFL bytes inside the GPO response were malformed. */
    public data class AflRejected(val cause: AflError) : ReaderError

    /** `EmvParser.parse` rejected the assembled record stream. */
    public data class ParseFailed(val cause: EmvCardError) : ReaderError
}
