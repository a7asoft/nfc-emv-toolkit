package io.github.a7asoft.nfcemv.reader

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCardError
import io.github.a7asoft.nfcemv.extract.TerminalConfig
import io.github.a7asoft.nfcemv.reader.internal.ApduCommands
import io.github.a7asoft.nfcemv.reader.internal.FakeApduTransport
import io.github.a7asoft.nfcemv.reader.internal.Transcripts
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContactlessReaderTest {

    @Test
    fun `read emits Visa Done state with the expected EmvCard`() = runTest {
        val transport = FakeApduTransport(visaScript())
        val states = ContactlessReader(transport).read().toList()
        assertProgressEndsInDone(states, expectedAid = Aid.fromHex("A0000000031010"))
        assertTrue(transport.closed)
    }

    @Test
    fun `read emits Mastercard Done state with the expected EmvCard`() = runTest {
        val transport = FakeApduTransport(mastercardScript())
        val states = ContactlessReader(transport).read().toList()
        assertProgressEndsInDone(states, expectedAid = Aid.fromHex("A0000000041010"))
    }

    @Test
    fun `read emits Amex Done state with the expected EmvCard`() = runTest {
        val transport = FakeApduTransport(amexScript())
        val states = ContactlessReader(transport).read().toList()
        assertProgressEndsInDone(states, expectedAid = Aid.fromHex("A000000025010701"))
    }

    @Test
    fun `read emits PpseUnsupported when PPSE returns 6A 82`() = runTest {
        val transport = FakeApduTransport(
            listOf(ApduCommands.PPSE_SELECT to Transcripts.PPSE_NOT_FOUND_RESPONSE),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        assertEquals(ReaderError.PpseUnsupported, failed.error)
    }

    @Test
    fun `read emits IoFailure with TagLost reason when transport throws TagLostException`() = runTest {
        val transport = FakeApduTransport(
            script = emptyList(),
            connectError = android.nfc.TagLostException("simulated tag lost"),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        val io = assertIs<ReaderError.IoFailure>(failed.error)
        assertEquals(IoReason.TagLost, io.reason)
    }

    @Test
    fun `read emits IoFailure with Generic reason on a generic IOException`() = runTest {
        val transport = FakeApduTransport(
            script = emptyList(),
            connectError = IOException("rf transport down"),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        val io = assertIs<ReaderError.IoFailure>(failed.error)
        assertEquals(IoReason.Generic, io.reason)
    }

    @Test
    fun `read emits IoFailure with Timeout reason on a SocketTimeoutException`() = runTest {
        val transport = FakeApduTransport(
            script = emptyList(),
            connectError = java.net.SocketTimeoutException("rf timeout"),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        val io = assertIs<ReaderError.IoFailure>(failed.error)
        assertEquals(IoReason.Timeout, io.reason)
    }

    @Test
    fun `read emits ApduStatusError on a non-9000 status from SELECT AID`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.SELECT_AID_WRONG_P1P2_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        val statusError = assertIs<ReaderError.ApduStatusError>(failed.error)
        assertEquals(0x6A.toByte(), statusError.sw1)
        assertEquals(0x86.toByte(), statusError.sw2)
    }

    @Test
    fun `read emits ApduStatusError on a non-9000 status from GPO`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.VISA_SELECT_FCI_RESPONSE,
                ApduCommands.GPO_DEFAULT to Transcripts.GPO_NOT_SUPPORTED_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        val statusError = assertIs<ReaderError.ApduStatusError>(
            (states.last() as ReaderState.Failed).error,
        )
        assertEquals(0x6A.toByte(), statusError.sw1)
        assertEquals(0x81.toByte(), statusError.sw2)
    }

    @Test
    fun `read emits ParseFailed when records are missing required tags`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.VISA_SELECT_FCI_RESPONSE,
                ApduCommands.GPO_DEFAULT to Transcripts.VISA_GPO_RESPONSE,
                readRecord1Sfi1Prefix() to Transcripts.INCOMPLETE_RECORD_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        val parseFailed = assertIs<ReaderError.ParseFailed>(failed.error)
        assertIs<EmvCardError.MissingRequiredTag>(parseFailed.cause)
    }

    @Test
    fun `read emits PpseRejected when PPSE body is malformed`() = runTest {
        val transport = FakeApduTransport(
            listOf(ApduCommands.PPSE_SELECT to Transcripts.PPSE_MALFORMED_BODY_RESPONSE),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        assertIs<ReaderError.PpseRejected>(failed.error)
    }

    @Test
    fun `read emits PpseRejected when PPSE has no application templates`() = runTest {
        val transport = FakeApduTransport(
            listOf(ApduCommands.PPSE_SELECT to Transcripts.PPSE_NO_APPLICATIONS_RESPONSE),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        assertIs<ReaderError.PpseRejected>(failed.error)
    }

    @Test
    fun `read emits GpoRejected when GPO returns a malformed body`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.VISA_SELECT_FCI_RESPONSE,
                ApduCommands.GPO_DEFAULT to Transcripts.GPO_MALFORMED_BODY_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        assertIs<ReaderError.GpoRejected>(failed.error)
    }

    @Test
    fun `read silently skips a non-9000 READ RECORD and continues with the next`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.VISA_SELECT_FCI_RESPONSE,
                ApduCommands.GPO_DEFAULT to Transcripts.VISA_GPO_RESPONSE_TWO_RECORDS,
                // First READ RECORD: returns 6A 83 (record not found).
                readRecord1Sfi1Prefix() to Transcripts.READ_RECORD_NOT_FOUND_RESPONSE,
                // Second READ RECORD: success — drives the EmvParser to a Done state.
                readRecord2Sfi1Prefix() to Transcripts.VISA_RECORD_1_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        assertIs<ReaderState.Done>(states.last())
    }

    @Test
    fun `read closes the transport when the collecting coroutine is cancelled`() = runTest {
        val transport = FakeApduTransport(visaScript())
        val collected = mutableListOf<ReaderState>()
        val job = launch {
            ContactlessReader(transport).read().collect { collected += it }
        }
        while (collected.isEmpty()) yield()
        job.cancelAndJoin()
        assertTrue(transport.closed, "transport must close when collector is cancelled mid-flow")
    }

    @Test
    fun `read selects the application with the lowest priority value among multiple PPSE entries`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.MULTI_AID_PPSE_RESPONSE,
                selectMastercardPrefix() to Transcripts.MASTERCARD_SELECT_FCI_RESPONSE,
                ApduCommands.GPO_DEFAULT to Transcripts.MASTERCARD_GPO_RESPONSE,
                readRecord1Sfi1Prefix() to Transcripts.MASTERCARD_RECORD_1_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        val selecting = states.filterIsInstance<ReaderState.SelectingAid>().single()
        assertEquals(Aid.fromHex("A0000000041010"), selecting.aid)
        assertIs<ReaderState.Done>(states.last())
    }

    @Test
    fun `read parses PDOL from Visa FCI and sends correct GPO command body`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.VISA_SELECT_FCI_WITH_PDOL_RESPONSE,
                Transcripts.VISA_GPO_COMMAND_PDOL to Transcripts.VISA_GPO_RESPONSE,
                readRecord1Sfi1Prefix() to Transcripts.VISA_RECORD_1_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        assertIs<ReaderState.Done>(states.last())
    }

    @Test
    fun `read uses injected AID and parses Visa records that lack 4F`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.VISA_SELECT_FCI_RESPONSE,
                ApduCommands.GPO_DEFAULT to Transcripts.VISA_GPO_RESPONSE,
                readRecord1Sfi1Prefix() to Transcripts.VISA_RECORD_WITHOUT_4F_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        val done = assertIs<ReaderState.Done>(states.last())
        assertEquals(Aid.fromHex("A0000000031010"), done.card.aid)
        assertEquals("4111111111111111", done.card.pan.unmasked())
    }

    @Test
    fun `read emits SelectAidFciRejected on a malformed FCI body`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.SELECT_FCI_MALFORMED_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        assertIs<ReaderError.SelectAidFciRejected>(failed.error)
    }

    @Test
    fun `read emits PdolRejected when PDOL bytes are structurally invalid`() = runTest {
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.SELECT_FCI_TRUNCATED_PDOL_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        assertIs<ReaderError.PdolRejected>(failed.error)
    }

    @Test
    fun `read with custom TerminalConfig overrides the default TTQ in the GPO body`() = runTest {
        val customTtq = byteArrayOf(0x26, 0x00, 0x00, 0x00)
        val expectedGpo = byteArrayOf(
            0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x06,
            0x83.toByte(), 0x04, 0x26, 0x00, 0x00, 0x00,
            0x00,
        )
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
                selectVisaPrefix() to Transcripts.VISA_SELECT_FCI_WITH_PDOL_RESPONSE,
                expectedGpo to Transcripts.VISA_GPO_RESPONSE,
                readRecord1Sfi1Prefix() to Transcripts.VISA_RECORD_1_RESPONSE,
            ),
        )
        val custom = TerminalConfig.default().copy(terminalTransactionQualifiers = customTtq)
        val states = ContactlessReader(transport).read(custom).toList()
        assertIs<ReaderState.Done>(states.last())
    }

    @Test
    fun `read with default Mastercard FCI sends empty PDOL GPO body for back compat`() = runTest {
        // why: Mastercard FCI omits 9F38 → reader sends GPO_DEFAULT-equivalent body.
        val transport = FakeApduTransport(
            listOf(
                ApduCommands.PPSE_SELECT to Transcripts.MASTERCARD_PPSE_RESPONSE,
                selectMastercardPrefix() to Transcripts.MASTERCARD_SELECT_FCI_RESPONSE,
                ApduCommands.GPO_DEFAULT to Transcripts.MASTERCARD_GPO_RESPONSE,
                readRecord1Sfi1Prefix() to Transcripts.MASTERCARD_RECORD_1_RESPONSE,
            ),
        )
        val states = ContactlessReader(transport).read().toList()
        assertIs<ReaderState.Done>(states.last())
    }

    // ---- helpers -------------------------------------------------------

    private fun assertProgressEndsInDone(states: List<ReaderState>, expectedAid: Aid) {
        assertIs<ReaderState.TagDetected>(states[0])
        assertIs<ReaderState.SelectingPpse>(states[1])
        val selecting = assertIs<ReaderState.SelectingAid>(states[2])
        assertEquals(expectedAid, selecting.aid)
        assertIs<ReaderState.ReadingRecords>(states[3])
        val done = assertIs<ReaderState.Done>(states[4])
        assertEquals(expectedAid, done.card.aid)
    }

    private fun visaScript(): List<Pair<ByteArray, ByteArray>> = listOf(
        ApduCommands.PPSE_SELECT to Transcripts.VISA_PPSE_RESPONSE,
        selectVisaPrefix() to Transcripts.VISA_SELECT_FCI_RESPONSE,
        ApduCommands.GPO_DEFAULT to Transcripts.VISA_GPO_RESPONSE,
        readRecord1Sfi1Prefix() to Transcripts.VISA_RECORD_1_RESPONSE,
    )

    private fun mastercardScript(): List<Pair<ByteArray, ByteArray>> = listOf(
        ApduCommands.PPSE_SELECT to Transcripts.MASTERCARD_PPSE_RESPONSE,
        selectMastercardPrefix() to Transcripts.MASTERCARD_SELECT_FCI_RESPONSE,
        ApduCommands.GPO_DEFAULT to Transcripts.MASTERCARD_GPO_RESPONSE,
        readRecord1Sfi1Prefix() to Transcripts.MASTERCARD_RECORD_1_RESPONSE,
    )

    private fun amexScript(): List<Pair<ByteArray, ByteArray>> = listOf(
        ApduCommands.PPSE_SELECT to Transcripts.AMEX_PPSE_RESPONSE,
        selectAmexPrefix() to Transcripts.AMEX_SELECT_FCI_RESPONSE,
        ApduCommands.GPO_DEFAULT to Transcripts.AMEX_GPO_RESPONSE,
        readRecord1Sfi1Prefix() to Transcripts.AMEX_RECORD_1_RESPONSE,
    )

    private fun selectVisaPrefix(): ByteArray = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
    ) + Transcripts.VISA_AID_BYTES

    private fun selectMastercardPrefix(): ByteArray = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
    ) + Transcripts.MASTERCARD_AID_BYTES

    private fun selectAmexPrefix(): ByteArray = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x08,
    ) + Transcripts.AMEX_AID_BYTES

    private fun readRecord1Sfi1Prefix(): ByteArray = byteArrayOf(
        0x00, 0xB2.toByte(), 0x01, 0x0C,
    )

    private fun readRecord2Sfi1Prefix(): ByteArray = byteArrayOf(
        0x00, 0xB2.toByte(), 0x02, 0x0C,
    )
}
