package io.github.a7asoft.nfcemv.reader

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCard

/**
 * Discrete states a [ContactlessReader] passes through during a read.
 *
 * Terminal states are [Done] (success) and [Failed] (typed error). All
 * other variants are intermediate progress signals consumers can use to
 * drive a UI.
 */
public sealed interface ReaderState {

    /** `IsoDep` connected; no APDUs exchanged yet. */
    public data object TagDetected : ReaderState

    /** About to issue `SELECT 2PAY.SYS.DDF01`. */
    public data object SelectingPpse : ReaderState

    /** About to issue `SELECT [aid]` for the chosen application. */
    public data class SelectingAid(val aid: Aid) : ReaderState

    /** GPO succeeded; `READ RECORD` traversal in progress. */
    public data object ReadingRecords : ReaderState

    /** Terminal success state carrying the parsed [EmvCard]. */
    public data class Done(val card: EmvCard) : ReaderState

    /** Terminal failure state carrying the typed [ReaderError]. */
    public data class Failed(val error: ReaderError) : ReaderState
}
