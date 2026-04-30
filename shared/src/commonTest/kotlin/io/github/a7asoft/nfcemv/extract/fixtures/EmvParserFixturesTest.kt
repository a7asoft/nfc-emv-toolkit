package io.github.a7asoft.nfcemv.extract.fixtures

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.brand.EmvBrand
import io.github.a7asoft.nfcemv.extract.EmvCardResult
import io.github.a7asoft.nfcemv.extract.EmvParser
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Drives every brand fixture in [Fixtures] through [EmvParser.parse]
 * and asserts the result matches its [FixtureExpectation]. One assertion
 * concept per test (CLAUDE.md §6).
 *
 * Adding a new fixture: declare a `ByteArray` in [Fixtures], a
 * matching [FixtureExpectation] entry below, and copy the per-field
 * test block — keep one test per concept rather than parametrising
 * across fixtures (parametric tests obscure the failure surface
 * when only one fixture regresses).
 */
class EmvParserFixturesTest {

    @Test
    fun `parses Visa Classic fixture into the expected EmvCard`() {
        assertCardMatches(VISA, Fixtures.VISA_CLASSIC)
    }

    @Test
    fun `parses Mastercard PayPass fixture into the expected EmvCard`() {
        assertCardMatches(MASTERCARD, Fixtures.MASTERCARD_PAYPASS)
    }

    @Test
    fun `parses Amex ExpressPay fixture into the expected EmvCard`() {
        assertCardMatches(AMEX, Fixtures.AMEX_EXPRESSPAY)
    }

    @Test
    fun `Visa fixture resolves the PAN exactly`() {
        val ok = parseOk(Fixtures.VISA_CLASSIC)
        assertEquals(VISA.pan, ok.card.pan.unmasked())
    }

    @Test
    fun `Mastercard fixture resolves the PAN exactly`() {
        val ok = parseOk(Fixtures.MASTERCARD_PAYPASS)
        assertEquals(MASTERCARD.pan, ok.card.pan.unmasked())
    }

    @Test
    fun `Amex fixture resolves the 15 digit PAN exactly`() {
        val ok = parseOk(Fixtures.AMEX_EXPRESSPAY)
        assertEquals(AMEX.pan, ok.card.pan.unmasked())
    }

    @Test
    fun `Visa fixture resolves the brand to VISA`() {
        val ok = parseOk(Fixtures.VISA_CLASSIC)
        assertEquals(EmvBrand.VISA, ok.card.brand)
    }

    @Test
    fun `Mastercard fixture resolves the brand to MASTERCARD`() {
        val ok = parseOk(Fixtures.MASTERCARD_PAYPASS)
        assertEquals(EmvBrand.MASTERCARD, ok.card.brand)
    }

    @Test
    fun `Amex fixture resolves the brand to AMERICAN_EXPRESS`() {
        val ok = parseOk(Fixtures.AMEX_EXPRESSPAY)
        assertEquals(EmvBrand.AMERICAN_EXPRESS, ok.card.brand)
    }

    @Test
    fun `Visa fixture exposes a non null Track 2`() {
        val ok = parseOk(Fixtures.VISA_CLASSIC)
        assertNotNull(ok.card.track2)
    }

    @Test
    fun `Mastercard fixture exposes a non null Track 2`() {
        val ok = parseOk(Fixtures.MASTERCARD_PAYPASS)
        assertNotNull(ok.card.track2)
    }

    @Test
    fun `Amex fixture exposes a non null Track 2`() {
        val ok = parseOk(Fixtures.AMEX_EXPRESSPAY)
        assertNotNull(ok.card.track2)
    }

    private fun parseOk(fixture: ByteArray): EmvCardResult.Ok =
        assertIs(EmvParser.parse(listOf(fixture)))

    private fun assertCardMatches(expected: FixtureExpectation, fixture: ByteArray) {
        val card = parseOk(fixture).card
        assertEquals(expected.pan, card.pan.unmasked(), "${expected.name} PAN")
        assertEquals(expected.expiry, card.expiry, "${expected.name} expiry")
        assertEquals(expected.cardholderName, card.cardholderName, "${expected.name} cardholderName")
        assertEquals(expected.brand, card.brand, "${expected.name} brand")
        assertEquals(expected.applicationLabel, card.applicationLabel, "${expected.name} applicationLabel")
        assertEquals(expected.aid, card.aid, "${expected.name} aid")

        val track2 = assertNotNull(card.track2, "${expected.name} track2 not null")
        assertEquals(expected.track2Pan, track2.pan.unmasked(), "${expected.name} track2 pan")
        assertEquals(expected.track2Expiry, track2.expiry, "${expected.name} track2 expiry")
        assertEquals(expected.track2ServiceCode, track2.serviceCode.toString(), "${expected.name} track2 serviceCode")
    }

    private companion object {
        val VISA = FixtureExpectation(
            name = "Visa Classic",
            pan = "4111111111111111",
            expiry = YearMonth(2028, 12),
            cardholderName = "VISA TEST",
            brand = EmvBrand.VISA,
            applicationLabel = "VISA",
            aid = Aid.fromHex("A0000000031010"),
            track2Pan = "4111111111111111",
            track2Expiry = YearMonth(2028, 12),
            track2ServiceCode = "201",
        )

        val MASTERCARD = FixtureExpectation(
            name = "Mastercard PayPass",
            pan = "5500000000000004",
            expiry = YearMonth(2027, 6),
            cardholderName = "MC TEST",
            brand = EmvBrand.MASTERCARD,
            applicationLabel = "MasterCard",
            aid = Aid.fromHex("A0000000041010"),
            track2Pan = "5500000000000004",
            track2Expiry = YearMonth(2027, 6),
            track2ServiceCode = "201",
        )

        val AMEX = FixtureExpectation(
            name = "Amex ExpressPay",
            pan = "378282246310005",
            expiry = YearMonth(2027, 9),
            cardholderName = "AMEX TEST",
            brand = EmvBrand.AMERICAN_EXPRESS,
            applicationLabel = "AMERICAN",
            aid = Aid.fromHex("A000000025010701"),
            track2Pan = "378282246310005",
            track2Expiry = YearMonth(2027, 9),
            track2ServiceCode = "201",
        )
    }
}
