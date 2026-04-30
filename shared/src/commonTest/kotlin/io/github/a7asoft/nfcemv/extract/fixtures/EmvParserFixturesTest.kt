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
 * Drives every brand fixture in [Fixtures] through [EmvParser.parse] and
 * asserts the resulting [io.github.a7asoft.nfcemv.extract.EmvCard] matches
 * its [FixtureExpectation].
 *
 * Each test is an **integration pin** for one brand fixture: a single test
 * walks the full end-to-end parse and asserts every observable field of
 * the resulting `EmvCard` (PAN, expiry, cardholder, brand, application
 * label, AID) plus the Track 2 sub-components (PAN, expiry, service code)
 * in one go. This is deliberately multi-concept — a per-brand integration
 * pin reads more clearly than nine narrow tests that each redo the parse.
 * Mirrors the pre-existing per-fixture integration pattern in
 * `EmvParserTest` (see `EmvParserTest.kt:258-289`).
 *
 * Adding a new fixture: declare a `ByteArray` accessor in [Fixtures], a
 * matching [FixtureExpectation] entry below, and add one
 * `assertCardMatches` test driving the new fixture.
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

    private fun parseOk(fixture: ByteArray): EmvCardResult.Ok =
        assertIs(EmvParser.parse(listOf(fixture)))

    private fun assertCardMatches(expected: FixtureExpectation, fixture: ByteArray) {
        val card = parseOk(fixture).card
        // why: every fixture PAN MUST come from a public test range (Visa 4111…,
        // MC 5500…0004, Amex 3782…0005). The unmasked accessor is safe here ONLY
        // because of that constraint — see CONTRIBUTING.md "Test fixture PANs"
        // and Fixtures.kt class-level KDoc. NEVER copy-paste this assertion
        // pattern with a non-public PAN.
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
