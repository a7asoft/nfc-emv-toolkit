package io.github.a7asoft.nfcemv.extract.fixtures

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.brand.EmvBrand
import kotlinx.datetime.YearMonth

/**
 * Expected parse outcome for one [Fixtures] fixture.
 *
 * Used by `EmvParserFixturesTest` to assert that
 * `EmvParser.parse(listOf(fixture))` produces the expected
 * [io.github.a7asoft.nfcemv.extract.EmvCard] field-by-field.
 *
 * Track 2 components are split out (`track2Pan`, `track2Expiry`,
 * `track2ServiceCode`) so the test can assert them individually
 * without constructing a full `Track2` instance, which would require
 * driving the same fixture bytes through `Track2.parse` separately.
 */
internal data class FixtureExpectation(
    val name: String,
    val pan: String,
    val expiry: YearMonth,
    val cardholderName: String?,
    val brand: EmvBrand,
    val applicationLabel: String?,
    val aid: Aid,
    val track2Pan: String,
    val track2Expiry: YearMonth,
    val track2ServiceCode: String,
)
