package io.github.a7asoft.ui

import io.github.a7asoft.nfc.NfcAvailabilityStatus
import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCard
import io.github.a7asoft.nfcemv.extract.EmvParser
import io.github.a7asoft.nfcemv.reader.ReaderError
import io.github.a7asoft.nfcemv.reader.ReaderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @BeforeTest
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is NfcUnavailable when adapter is missing`() {
        val vm = vm(NfcAvailabilityStatus.Unavailable)
        assertEquals(ReaderUiState.NfcUnavailable, vm.state.value)
    }

    @Test
    fun `initial state is NfcDisabled when adapter is off`() {
        val vm = vm(NfcAvailabilityStatus.Disabled)
        assertEquals(ReaderUiState.NfcDisabled, vm.state.value)
    }

    @Test
    fun `initial state is Idle when adapter is enabled`() {
        val vm = vm(NfcAvailabilityStatus.Available)
        assertEquals(ReaderUiState.Idle, vm.state.value)
    }

    @Test
    fun `onReadRequested transitions through every reader state to Done`() = runTest {
        val card = sampleCard()
        val flow = flowOf<ReaderState>(
            ReaderState.TagDetected,
            ReaderState.SelectingPpse,
            ReaderState.SelectingAid(aid = sampleAid()),
            ReaderState.ReadingRecords,
            ReaderState.Done(card),
        )
        val vm = ReaderViewModel(
            nfcAvailability = FakeNfcAvailability(NfcAvailabilityStatus.Available),
        )
        vm.onReadRequested(ReaderHandle { flow })
        val done = assertIs<ReaderUiState.Done>(vm.state.value)
        assertEquals(card, done.card)
    }

    @Test
    fun `onReadRequested surfaces Failed when reader emits Failed terminal`() = runTest {
        val error = ReaderError.PpseUnsupported
        val flow = flowOf<ReaderState>(ReaderState.TagDetected, ReaderState.Failed(error))
        val vm = ReaderViewModel(
            nfcAvailability = FakeNfcAvailability(NfcAvailabilityStatus.Available),
        )
        vm.onReadRequested(ReaderHandle { flow })
        val failed = assertIs<ReaderUiState.Failed>(vm.state.value)
        assertEquals(error, failed.error)
    }

    @Test
    fun `onReadRequested is ignored when state is not Idle`() = runTest {
        val vm = ReaderViewModel(
            nfcAvailability = FakeNfcAvailability(NfcAvailabilityStatus.Disabled),
        )
        vm.onReadRequested(ReaderHandle { flowOf(ReaderState.Done(sampleCard())) })
        assertEquals(ReaderUiState.NfcDisabled, vm.state.value)
    }

    @Test
    fun `onReadRequested is ignored when state is mid read`() = runTest {
        val flow = MutableSharedFlow<ReaderState>(replay = 1).also { it.tryEmit(ReaderState.TagDetected) }
        val vm = ReaderViewModel(
            nfcAvailability = FakeNfcAvailability(NfcAvailabilityStatus.Available),
        )
        vm.onReadRequested(ReaderHandle { flow })
        assertEquals(ReaderUiState.TagDetected, vm.state.value)
        vm.onReadRequested(ReaderHandle { flowOf(ReaderState.Done(sampleCard())) })
        assertEquals(ReaderUiState.TagDetected, vm.state.value)
    }

    @Test
    fun `reset returns to Idle from Done`() = runTest {
        val vm = ReaderViewModel(
            nfcAvailability = FakeNfcAvailability(NfcAvailabilityStatus.Available),
        )
        vm.onReadRequested(ReaderHandle { flowOf(ReaderState.Done(sampleCard())) })
        vm.reset()
        assertEquals(ReaderUiState.Idle, vm.state.value)
    }

    @Test
    fun `reset returns to Idle from Failed`() = runTest {
        val vm = ReaderViewModel(
            nfcAvailability = FakeNfcAvailability(NfcAvailabilityStatus.Available),
        )
        vm.onReadRequested(
            ReaderHandle {
                flowOf(ReaderState.TagDetected, ReaderState.Failed(ReaderError.PpseUnsupported))
            },
        )
        require(vm.state.value is ReaderUiState.Failed) { "precondition failed: state must be Failed" }
        vm.reset()
        assertEquals(ReaderUiState.Idle, vm.state.value)
    }

    @Test
    fun `refreshNfcAvailability picks up adapter being enabled`() {
        val availability = FakeNfcAvailability(NfcAvailabilityStatus.Disabled)
        val vm = ReaderViewModel(nfcAvailability = availability)
        availability.set(NfcAvailabilityStatus.Available)
        vm.refreshNfcAvailability()
        assertEquals(ReaderUiState.Idle, vm.state.value)
    }

    @Test
    fun `refreshNfcAvailability preserves Done state`() = runTest {
        val availability = FakeNfcAvailability(NfcAvailabilityStatus.Available)
        val vm = ReaderViewModel(nfcAvailability = availability)
        vm.onReadRequested(ReaderHandle { flowOf(ReaderState.Done(sampleCard())) })
        val before = vm.state.value
        require(before is ReaderUiState.Done) { "precondition failed: state must be Done" }
        availability.set(NfcAvailabilityStatus.Disabled)
        vm.refreshNfcAvailability()
        assertEquals(before, vm.state.value)
    }

    @Test
    fun `refreshNfcAvailability preserves Failed state`() = runTest {
        val availability = FakeNfcAvailability(NfcAvailabilityStatus.Available)
        val vm = ReaderViewModel(nfcAvailability = availability)
        vm.onReadRequested(
            ReaderHandle {
                flowOf(ReaderState.TagDetected, ReaderState.Failed(ReaderError.PpseUnsupported))
            },
        )
        val before = vm.state.value
        require(before is ReaderUiState.Failed) { "precondition failed: state must be Failed" }
        availability.set(NfcAvailabilityStatus.Disabled)
        vm.refreshNfcAvailability()
        assertEquals(before, vm.state.value)
    }

    private fun vm(status: NfcAvailabilityStatus): ReaderViewModel = ReaderViewModel(
        nfcAvailability = FakeNfcAvailability(status),
    )

    private fun sampleAid(): Aid = Aid.fromHex("A0000000031010")

    // why: EmvCard's `internal` constructor cannot be invoked from this
    // module's tests. We rebuild it by parsing the canonical Visa fixture
    // bytes (verbatim copy of `:shared:commonTest:Fixtures.VISA_CLASSIC`).
    // EmvParser.parseOrThrow is the supported construction path.
    //
    // KEEP IN SYNC: if Fixtures.VISA_CLASSIC bytes change in :shared,
    // update this constant too. There is no compile-time link.
    private fun sampleCard(): EmvCard {
        val fixture = byteArrayOf(
            0x70, 0x3D,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
            0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
            0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
            0x57, 0x10,
            0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00, 0x00, 0x00,
        )
        return EmvParser.parseOrThrow(listOf(fixture))
    }
}
