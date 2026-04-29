package io.github.a7asoft.nfcemv.extract

import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Track2Test {

    /**
     * Canonical 16-digit Visa Track 2 fixture (no F-pad, total 28 nibbles).
     *
     * PAN  = 4111111111111111
     * D    = separator
     * YYMM = 2812 (Dec 2028)
     * SSS  = 201
     * disc = 0000 (4 digits, makes total nibbles even, no F-pad needed)
     *
     * Bytes: 41 11 11 11 11 11 11 11 D2 81 22 01 00 00
     */
    private val visaFixture = byteArrayOf(
        0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
    )

    @Test
    fun `parse decodes a canonical 16-digit Visa Track2 fixture`() {
        val ok = assertIs<Track2Result.Ok>(Track2.parse(visaFixture))
        val track2 = ok.track2
        assertEquals("4111111111111111", track2.pan.unmasked())
        assertEquals(YearMonth(2028, 12), track2.expiry)
        assertEquals("201", track2.serviceCode.toString())
        assertEquals("0000", track2.unmaskedDiscretionary())
        assertEquals(4, track2.discretionaryLength)
    }
}
