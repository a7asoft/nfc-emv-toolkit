package io.github.a7asoft.nfcemv.reader

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.Afl
import io.github.a7asoft.nfcemv.extract.EmvCardResult
import io.github.a7asoft.nfcemv.extract.EmvParser
import io.github.a7asoft.nfcemv.extract.Gpo
import io.github.a7asoft.nfcemv.extract.GpoResult
import io.github.a7asoft.nfcemv.extract.Ppse
import io.github.a7asoft.nfcemv.extract.PpseResult
import io.github.a7asoft.nfcemv.extract.parse
import io.github.a7asoft.nfcemv.reader.internal.ApduCommands
import io.github.a7asoft.nfcemv.reader.internal.ApduTransport
import io.github.a7asoft.nfcemv.reader.internal.IsoDepTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import java.io.IOException

/**
 * Orchestrates the EMV contactless read flow per Book 1 §11–12 over an
 * Android `IsoDep` channel. Read-only by design (CLAUDE.md §1):
 * - Sends `SELECT 2PAY.SYS.DDF01`, parses the PPSE FCI.
 * - Selects the highest-priority advertised application.
 * - Issues `GET PROCESSING OPTIONS` with empty PDOL data.
 * - Walks the AFL, issuing one `READ RECORD` per record.
 * - Hands the accumulated record stream to [EmvParser].
 *
 * Exposed as a `Flow<ReaderState>` so consumers can drive a UI off
 * progress states. Terminal states are [ReaderState.Done] (success) and
 * [ReaderState.Failed] (typed [ReaderError]).
 *
 * Cancellation closes the underlying IsoDep channel via `onCompletion`.
 * APDU exchanges run on `Dispatchers.IO`.
 */
public class ContactlessReader internal constructor(
    private val transport: ApduTransport,
) {

    /**
     * Returns a cold flow that, when collected, drives the contactless
     * read flow against the wrapped transport. Each emission is one
     * [ReaderState] transition.
     */
    public fun read(): Flow<ReaderState> = flow { drive() }
        .flowOn(Dispatchers.IO)
        .onCompletion { runCatching { transport.close() } }

    @Suppress(
        // why: each return is a distinct EMV Book 1 §11–12 step
        // (PPSE / app-pick / SELECT / GPO / parse). Collapsing obscures the
        // flow without reducing real complexity.
        "ReturnCount",
        "CyclomaticComplexMethod",
    )
    private suspend fun FlowCollector<ReaderState>.drive() {
        emit(ReaderState.TagDetected)
        try {
            transport.connect()
            val ppse = selectPpse() ?: return
            val chosen = chooseApplication(ppse) ?: return
            emit(ReaderState.SelectingAid(chosen.aid))
            if (!selectAid(chosen.aid)) return
            emit(ReaderState.ReadingRecords)
            val gpo = readGpo() ?: return
            emitParseOutcome(readAllRecords(gpo.afl))
        } catch (e: IOException) {
            emit(ReaderState.Failed(translateIo(e)))
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun FlowCollector<ReaderState>.selectPpse(): Ppse? {
        emit(ReaderState.SelectingPpse)
        val response = transport.transceive(ApduCommands.PPSE_SELECT)
        if (!ApduCommands.isSuccess(response)) {
            return failOnPpseStatus(response)
        }
        return when (val parsed = Ppse.parse(ApduCommands.dataField(response))) {
            is PpseResult.Ok -> parsed.ppse
            is PpseResult.Err -> {
                emit(ReaderState.Failed(ReaderError.PpseRejected(parsed.error)))
                null
            }
        }
    }

    private suspend fun FlowCollector<ReaderState>.failOnPpseStatus(response: ByteArray): Ppse? {
        val (sw1, sw2) = ApduCommands.statusWord(response)
        val error = if (sw1 == SW_FILE_NOT_FOUND_HI && sw2 == SW_FILE_NOT_FOUND_LO) {
            ReaderError.PpseUnsupported
        } else {
            ReaderError.ApduStatusError(sw1, sw2)
        }
        emit(ReaderState.Failed(error))
        return null
    }

    private suspend fun FlowCollector<ReaderState>.chooseApplication(ppse: Ppse): PickedApplication? {
        val entry = ppse.applications.minByOrNull { it.priority ?: Int.MAX_VALUE }
        if (entry == null) {
            emit(ReaderState.Failed(ReaderError.NoApplicationSelected))
            return null
        }
        return PickedApplication(entry.aid)
    }

    private suspend fun FlowCollector<ReaderState>.selectAid(aid: Aid): Boolean {
        val response = transport.transceive(ApduCommands.selectAid(aid))
        if (ApduCommands.isSuccess(response)) return true
        val (sw1, sw2) = ApduCommands.statusWord(response)
        emit(ReaderState.Failed(ReaderError.ApduStatusError(sw1, sw2)))
        return false
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun FlowCollector<ReaderState>.readGpo(): Gpo? {
        val response = transport.transceive(ApduCommands.GPO_DEFAULT)
        if (!ApduCommands.isSuccess(response)) {
            val (sw1, sw2) = ApduCommands.statusWord(response)
            emit(ReaderState.Failed(ReaderError.ApduStatusError(sw1, sw2)))
            return null
        }
        return when (val parsed = Gpo.parse(ApduCommands.dataField(response))) {
            is GpoResult.Ok -> parsed.gpo
            is GpoResult.Err -> {
                emit(ReaderState.Failed(ReaderError.GpoRejected(parsed.error)))
                null
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun readAllRecords(afl: Afl): List<ByteArray> {
        val collected = ArrayList<ByteArray>()
        for (entry in afl.entries) {
            for (record in entry.firstRecord..entry.lastRecord) {
                val response = transport.transceive(ApduCommands.readRecord(record, entry.sfi))
                if (ApduCommands.isSuccess(response)) {
                    collected += ApduCommands.dataField(response)
                }
            }
        }
        return collected
    }

    private suspend fun FlowCollector<ReaderState>.emitParseOutcome(records: List<ByteArray>) {
        when (val parsed = EmvParser.parse(records)) {
            is EmvCardResult.Ok -> emit(ReaderState.Done(parsed.card))
            is EmvCardResult.Err -> emit(ReaderState.Failed(ReaderError.ParseFailed(parsed.error)))
        }
    }

    private fun translateIo(e: IOException): ReaderError =
        if (e is TagLostException) ReaderError.TagLost else ReaderError.IoFailure(e)

    private data class PickedApplication(val aid: Aid)

    public companion object {
        /**
         * Build a reader from an Android [Tag] handed in by `NfcAdapter`.
         * Throws [IllegalArgumentException] if the tag does not support
         * IsoDep (consumer-side filter must guard NDEF-only tags).
         */
        public fun fromTag(tag: Tag): ContactlessReader {
            val isoDep = IsoDep.get(tag)
                ?: throw IllegalArgumentException("Tag does not support IsoDep")
            return ContactlessReader(IsoDepTransport(isoDep))
        }

        private const val SW_FILE_NOT_FOUND_HI: Byte = 0x6A.toByte()
        private const val SW_FILE_NOT_FOUND_LO: Byte = 0x82.toByte()
    }
}
