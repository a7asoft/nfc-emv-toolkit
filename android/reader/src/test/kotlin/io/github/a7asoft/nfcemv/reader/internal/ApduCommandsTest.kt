package io.github.a7asoft.nfcemv.reader.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApduCommandsTest {

    // ---- Task 3 — MAX_RECORD_NUMBER tightened from 255 to 254 ----------

    @Test
    fun `readRecord rejects record number 255 per ISO 7816 4 7 3 3`() {
        assertFailsWith<IllegalArgumentException> {
            ApduCommands.readRecord(recordNumber = 255, sfi = 1)
        }
    }

    @Test
    fun `readRecord accepts record number 254 the upper bound`() {
        val cmd = ApduCommands.readRecord(recordNumber = 254, sfi = 1)
        assertEquals(0xFE.toByte(), cmd[2])
    }

    @Test
    fun `readRecord rejects record number 0`() {
        assertFailsWith<IllegalArgumentException> {
            ApduCommands.readRecord(recordNumber = 0, sfi = 1)
        }
    }

    // ---- Task 4 — defensive-copy `get()` accessors ---------------------

    @Test
    fun `PPSE_SELECT returns a fresh array per access`() {
        val first = ApduCommands.PPSE_SELECT
        val second = ApduCommands.PPSE_SELECT
        first[0] = 0x42
        assertEquals(0x00, second[0])
    }

    @Test
    fun `GPO_DEFAULT returns a fresh array per access`() {
        val first = ApduCommands.GPO_DEFAULT
        val second = ApduCommands.GPO_DEFAULT
        first[0] = 0x42
        assertEquals(0x80.toByte(), second[0])
    }
}
