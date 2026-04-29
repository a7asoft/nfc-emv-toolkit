package io.github.a7asoft.nfcemv.extract

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.brand.EmvBrand
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class EmvCardTest {

    private fun sample(
        cardholderName: String? = "VISA TEST",
        applicationLabel: String? = "VISA",
        track2: Track2? = null,
    ): EmvCard = EmvCard(
        pan = Pan.parseOrThrow("4111111111111111"),
        expiry = YearMonth(2028, 12),
        cardholderName = cardholderName,
        brand = EmvBrand.VISA,
        applicationLabel = applicationLabel,
        track2 = track2,
        aid = Aid.fromHex("A0000000031010"),
    )

    @Test
    fun `toString masks the PAN via Pan toString`() {
        assertFalse("4111111111111111" in sample().toString())
    }

    @Test
    fun `toString reports cardholderName as a length-only placeholder`() {
        assertEquals(true, "<9 chars>" in sample().toString())
    }

    @Test
    fun `toString never embeds the raw cardholder name string`() {
        assertFalse("VISA TEST" in sample().toString())
    }

    @Test
    fun `toString reports cardholderName as null literal when null`() {
        assertEquals(true, "cardholderName=null" in sample(cardholderName = null).toString())
    }

    @Test
    fun `toString shows the application label raw because it is not PCI`() {
        assertEquals(true, "applicationLabel=VISA" in sample().toString())
    }

    @Test
    fun `equality is data-class structural over all fields`() {
        assertEquals(sample(), sample())
    }

    @Test
    fun `equality differs when cardholderName differs`() {
        assertNotEquals(sample(cardholderName = "A"), sample(cardholderName = "B"))
    }

    @Test
    fun `componentN destructuring exposes the public fields`() {
        val card = sample()
        val (pan, expiry, cardholderName, brand, applicationLabel, track2, aid) = card
        assertEquals(card.pan, pan)
        assertEquals(card.expiry, expiry)
        assertEquals(card.cardholderName, cardholderName)
        assertEquals(card.brand, brand)
        assertEquals(card.applicationLabel, applicationLabel)
        assertEquals(card.track2, track2)
        assertEquals(card.aid, aid)
    }
}
