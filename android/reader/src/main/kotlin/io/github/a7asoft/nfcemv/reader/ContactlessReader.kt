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
import io.github.a7asoft.nfcemv.extract.Pdol
import io.github.a7asoft.nfcemv.extract.PdolResponseBuilder
import io.github.a7asoft.nfcemv.extract.PdolResult
import io.github.a7asoft.nfcemv.extract.Ppse
import io.github.a7asoft.nfcemv.extract.PpseResult
import io.github.a7asoft.nfcemv.extract.SelectAidFci
import io.github.a7asoft.nfcemv.extract.SelectAidFciResult
import io.github.a7asoft.nfcemv.extract.TerminalConfig
import io.github.a7asoft.nfcemv.extract.parse
import io.github.a7asoft.nfcemv.reader.internal.ApduCommands
import io.github.a7asoft.nfcemv.reader.internal.ApduTransport
import io.github.a7asoft.nfcemv.reader.internal.IsoDepTransport
import io.github.a7asoft.nfcemv.tlv.Strictness
import io.github.a7asoft.nfcemv.tlv.Tlv
import io.github.a7asoft.nfcemv.tlv.TlvDecoder
import io.github.a7asoft.nfcemv.tlv.TlvOptions
import io.github.a7asoft.nfcemv.tlv.TlvParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.random.Random

/**
 * Orchestrates the EMV contactless read flow per Book 1 §11–12 over an
 * Android `IsoDep` channel. Read-only by design (CLAUDE.md §1):
 * - Sends `SELECT 2PAY.SYS.DDF01`, parses the PPSE FCI.
 * - Selects the highest-priority advertised application.
 * - Sends `SELECT [AID]`, parses the FCI for the optional `9F38` PDOL.
 * - Builds a PDOL response from [TerminalConfig] (or the empty `83 00`
 *   form when the card omits `9F38`) and issues
 *   `GET PROCESSING OPTIONS`.
 * - Walks the AFL (which may be empty for MSD-only cards), issuing one
 *   `READ RECORD` per record and decoding each successful body to TLV.
 * - Unions the SELECT AID FCI inline TLV, the GPO body inline TLV, and
 *   the AFL READ RECORD TLV nodes; hands the merged node list and the
 *   AID to [EmvParser.parse].
 *
 * MSD-only cards (Visa qVSDC kernel-3) return AIP + Track 2 inline in
 * the GPO body with NO AFL — those flows produce zero `READ RECORD`
 * APDUs. See `:shared/extract/Gpo.inlineTlv` for the architectural
 * union pattern.
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
     * read flow against the wrapped transport using the default
     * [TerminalConfig]. Each emission is one [ReaderState] transition.
     *
     * Cancellation semantics: the Flow honors collector cancellation
     * BETWEEN APDU exchanges (at suspension points), not DURING a
     * blocking `IsoDep.transceive` call. A cancellation that arrives
     * mid-exchange will be observed only after the in-flight `transceive`
     * returns (or throws). The transport is closed on completion
     * regardless of whether the Flow ended via terminal emit, error, or
     * cancellation.
     */
    public fun read(): Flow<ReaderState> = read(TerminalConfig.default())

    /**
     * Returns a cold flow driving the read with [config] supplying the
     * terminal-side PDOL response defaults (TTQ, country, currency,
     * etc.). Use this overload to override the standard defaults — for
     * example to set a non-default TTQ when validating against a card
     * that rejects the conservative `36 00 00 00`.
     */
    public fun read(config: TerminalConfig): Flow<ReaderState> = flow { drive(config) }
        .flowOn(Dispatchers.IO)
        .onCompletion { runCatching { transport.close() } }

    @Suppress(
        // why: each return is a distinct EMV Book 1 §11–12 step
        // (PPSE / app-pick / SELECT / FCI / PDOL / GPO / parse). Collapsing
        // obscures the flow without reducing real complexity.
        "ReturnCount",
        "CyclomaticComplexMethod",
    )
    private suspend fun FlowCollector<ReaderState>.drive(config: TerminalConfig) {
        emit(ReaderState.TagDetected)
        try {
            transport.connect()
            val ppse = selectPpse() ?: return
            val chosen = chooseApplication(ppse) ?: return
            emit(ReaderState.SelectingAid(chosen.aid))
            val fciBody = selectAid(chosen.aid) ?: return
            val (gpoBody, fci) = buildGpoBody(fciBody, config) ?: return
            emit(ReaderState.ReadingRecords)
            val gpo = readGpo(gpoBody) ?: return
            val recordTlv = readAllRecordsAsTlv(gpo.afl)
            emitParseOutcome(chosen.aid, fci.inlineTlv + gpo.inlineTlv + recordTlv)
        } catch (e: IOException) {
            emit(ReaderState.Failed(translateIo(e)))
        }
    }

    // why: emit progress, transceive, dispatch on status, then parse — each
    // is a distinct EMV Book 1 §11.3.4 step. Splitting further would just
    // ferry response bytes through more helper signatures.
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

    private suspend fun FlowCollector<ReaderState>.selectAid(aid: Aid): ByteArray? {
        val response = transport.transceive(ApduCommands.selectAid(aid))
        if (ApduCommands.isSuccess(response)) return ApduCommands.dataField(response)
        val (sw1, sw2) = ApduCommands.statusWord(response)
        emit(ReaderState.Failed(ReaderError.ApduStatusError(sw1, sw2)))
        return null
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    // why: each return surfaces a distinct typed reader error per the
    // EMV Book 3 §6.5.5–§6.5.8 SELECT FCI / PDOL / GPO sequence.
    // Returns the GPO command bytes paired with the parsed FCI so the
    // caller can union fci.inlineTlv with downstream TLV sources.
    private suspend fun FlowCollector<ReaderState>.buildGpoBody(
        fciBody: ByteArray,
        config: TerminalConfig,
    ): Pair<ByteArray, SelectAidFci>? {
        val fci = when (val parsed = SelectAidFci.parse(fciBody)) {
            is SelectAidFciResult.Ok -> parsed.fci
            is SelectAidFciResult.Err -> {
                emit(ReaderState.Failed(ReaderError.SelectAidFciRejected(parsed.error)))
                return null
            }
        }
        val pdolBytes = fci.pdolBytes ?: return ApduCommands.gpoCommand(EMPTY_BYTES) to fci
        val pdol = when (val parsed = Pdol.parse(pdolBytes)) {
            is PdolResult.Ok -> parsed.pdol
            is PdolResult.Err -> {
                emit(ReaderState.Failed(ReaderError.PdolRejected(parsed.error)))
                return null
            }
        }
        val response = PdolResponseBuilder.build(pdol, config, transactionDate(), unpredictableNumber())
        return ApduCommands.gpoCommand(response) to fci
    }

    // why: transceive, dispatch on status, parse — each is a distinct EMV
    // Book 3 §6.5.8 step. Same shape as `selectPpse`; further splitting
    // adds parameter shuffling without reducing real complexity.
    @Suppress("CyclomaticComplexMethod")
    private suspend fun FlowCollector<ReaderState>.readGpo(gpoCommand: ByteArray): Gpo? {
        val response = transport.transceive(gpoCommand)
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

    // why: walk AFL × records, transceive each, decode the success bodies
    // into TLV nodes once. Non-9000 responses silently skipped (same
    // policy as pre-#59). Returns the flat TLV node list, ready to union
    // with the GPO body's inline TLV before EmvParser.parse.
    @Suppress("CyclomaticComplexMethod")
    private fun readAllRecordsAsTlv(afl: Afl): List<Tlv> {
        val collected = ArrayList<Tlv>()
        for (entry in afl.entries) {
            for (record in entry.firstRecord..entry.lastRecord) {
                val response = transport.transceive(ApduCommands.readRecord(record, entry.sfi))
                if (!ApduCommands.isSuccess(response)) continue
                val body = ApduCommands.dataField(response)
                when (val parsed = TlvDecoder.parse(body, LENIENT_TLV)) {
                    is TlvParseResult.Ok -> collected += parsed.tlvs
                    // why: a record that fails to decode is silently
                    // dropped — same risk-balance as the pre-#59 non-9000
                    // skip. EmvParser surfaces MissingRequiredTag if a
                    // dropped record carried essential data.
                    is TlvParseResult.Err -> Unit
                }
            }
        }
        return collected
    }

    private suspend fun FlowCollector<ReaderState>.emitParseOutcome(
        chosenAid: Aid,
        nodes: List<Tlv>,
    ) {
        when (val parsed = EmvParser.parse(chosenAid, nodes)) {
            is EmvCardResult.Ok -> emit(ReaderState.Done(parsed.card))
            is EmvCardResult.Err -> emit(ReaderState.Failed(ReaderError.ParseFailed(parsed.error)))
        }
    }

    // why: exhaustive `when` over the IOException subtypes we map to
    // [IoReason]. Each branch is a single mapping, not real complexity.
    @Suppress("CyclomaticComplexMethod")
    private fun translateIo(e: IOException): ReaderError = ReaderError.IoFailure(
        reason = when (e) {
            is TagLostException -> IoReason.TagLost
            is SocketTimeoutException -> IoReason.Timeout
            else -> IoReason.Generic
        },
    )

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
        private val EMPTY_BYTES: ByteArray = ByteArray(0)
        private val LENIENT_TLV: TlvOptions = TlvOptions(strictness = Strictness.Lenient)

        // why: BCD-encoded YYMMDD from the current wall clock — UTC for
        // deterministic behavior across devices. Tag 9A is informational
        // for our read-only flow; the card uses it to seed cryptograms we
        // do not validate.
        @OptIn(kotlin.time.ExperimentalTime::class)
        @Suppress("MagicNumber")
        private fun transactionDate(): ByteArray {
            val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.UTC)
            val yy = now.year % 100
            return byteArrayOf(
                bcd(yy / 10, yy % 10),
                bcd(now.monthNumber / 10, now.monthNumber % 10),
                bcd(now.dayOfMonth / 10, now.dayOfMonth % 10),
            )
        }

        @Suppress("MagicNumber")
        private fun bcd(high: Int, low: Int): Byte =
            (((high and 0x0F) shl 4) or (low and 0x0F)).toByte()

        // why: 4 random bytes for tag 9F37. NOT crypto-grade — the
        // unpredictable number is anti-replay protocol-level only and
        // we never validate the card cryptogram.
        @Suppress("MagicNumber")
        private fun unpredictableNumber(): ByteArray {
            val bytes = ByteArray(UN_BYTES)
            Random.nextBytes(bytes)
            return bytes
        }

        private const val UN_BYTES: Int = 4
    }
}
