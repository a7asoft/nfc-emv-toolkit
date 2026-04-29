package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.brand.EmvBrand
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EmvParserTest {

    /**
     * Canonical 60-byte EMV record fixture wrapping all six tags in a
     * `70` template. Mirrors the structure documented in the plan.
     */
    private val canonicalRecord: ByteArray = byteArrayOf(
        0x70, 0x3B,
        // 4F AID = A0000000031010
        0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        // 5A PAN = 4111111111111111
        0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        // 5F24 expiry = 28 12 31 (Dec 2028)
        0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
        // 5F20 cardholder = "VISA TEST"
        0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
        // 50 label = "VISA"
        0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        // 57 Track 2 (14 bytes BCD)
        0x57, 0x0E,
        0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0xD2.toByte(), 0x81.toByte(), 0x22, 0x01, 0x00, 0x00,
    )

    @Test
    fun `parse extracts every required field from the canonical fixture`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        val card = ok.card
        assertEquals("4111111111111111", card.pan.unmasked())
        assertEquals(YearMonth(2028, 12), card.expiry)
        assertEquals(Aid.fromHex("A0000000031010"), card.aid)
        assertEquals(EmvBrand.VISA, card.brand)
    }

    @Test
    fun `parse extracts cardholder name and application label`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        assertEquals("VISA TEST", ok.card.cardholderName)
        assertEquals("VISA", ok.card.applicationLabel)
    }

    @Test
    fun `parse extracts Track 2 when tag 57 is present`() {
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(canonicalRecord)))
        val track2 = assertNotNull(ok.card.track2)
        assertEquals("4111111111111111", track2.pan.unmasked())
        assertEquals(YearMonth(2028, 12), track2.expiry)
        assertEquals("201", track2.serviceCode.toString())
    }

    @Test
    fun `parse returns track2 null when tag 57 is absent`() {
        val without57 = byteArrayOf(
            0x70, 0x2B,
            0x4F, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
            0x5A, 0x08, 0x41, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
            0x5F, 0x24, 0x03, 0x28, 0x12, 0x31,
            0x5F, 0x20, 0x09, 0x56, 0x49, 0x53, 0x41, 0x20, 0x54, 0x45, 0x53, 0x54,
            0x50, 0x04, 0x56, 0x49, 0x53, 0x41,
        )
        val ok = assertIs<EmvCardResult.Ok>(EmvParser.parse(listOf(without57)))
        assertNull(ok.card.track2)
    }
}
