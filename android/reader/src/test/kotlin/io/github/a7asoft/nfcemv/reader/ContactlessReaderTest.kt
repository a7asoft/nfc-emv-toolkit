package io.github.a7asoft.nfcemv.reader

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCardError
import io.github.a7asoft.nfcemv.reader.internal.ApduCommands
import io.github.a7asoft.nfcemv.reader.internal.FakeApduTransport
import io.github.a7asoft.nfcemv.reader.internal.Transcripts
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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
    fun `read emits TagLost when transport throws TagLostException on connect`() = runTest {
        val transport = FakeApduTransport(
            script = emptyList(),
            connectError = android.nfc.TagLostException("simulated tag lost"),
        )
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        assertEquals(ReaderError.TagLost, failed.error)
    }

    @Test
    fun `read emits IoFailure when transport throws generic IOException on connect`() = runTest {
        val cause = IOException("rf transport down")
        val transport = FakeApduTransport(script = emptyList(), connectError = cause)
        val states = ContactlessReader(transport).read().toList()
        val failed = assertIs<ReaderState.Failed>(states.last())
        val ioFailure = assertIs<ReaderError.IoFailure>(failed.error)
        assertEquals(cause, ioFailure.cause)
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
    fun `read closes the transport after Flow completion on a success path`() = runTest {
        val transport = FakeApduTransport(visaScript())
        ContactlessReader(transport).read().toList()
        assertTrue(transport.closed)
    }

    @Test
    fun `read closes the transport after Flow cancellation`() = runTest {
        val transport = FakeApduTransport(visaScript())
        ContactlessReader(transport).read().take(2).toList()
        assertTrue(transport.closed)
    }

    @Test
    fun `read selects highest-priority application from a multi-AID PPSE`() = runTest {
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
}
