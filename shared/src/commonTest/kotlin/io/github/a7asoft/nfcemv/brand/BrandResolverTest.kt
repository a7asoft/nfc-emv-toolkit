package io.github.a7asoft.nfcemv.brand

import io.github.a7asoft.nfcemv.extract.Pan
import kotlin.test.Test
import kotlin.test.assertEquals

class BrandResolverTest {

    @Test
    fun `resolveBrand returns UNKNOWN when both AID and PAN are null`() {
        assertEquals(EmvBrand.UNKNOWN, BrandResolver.resolveBrand(aid = null, pan = null))
    }

    @Test
    fun `resolveBrand returns the AID-resolved brand when AID is registered`() {
        val aid = Aid.fromHex("A0000000031010")
        assertEquals(EmvBrand.VISA, BrandResolver.resolveBrand(aid = aid, pan = null))
    }

    @Test
    fun `resolveBrand falls back to BIN match when AID is null`() {
        val pan = Pan.parseOrThrow("4111111111111111")
        assertEquals(EmvBrand.VISA, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand falls back to BIN when AID is unregistered`() {
        val unknownAid = Aid.fromHex("A0000000FF1010")
        val pan = Pan.parseOrThrow("4111111111111111")
        assertEquals(EmvBrand.VISA, BrandResolver.resolveBrand(aid = unknownAid, pan = pan))
    }

    @Test
    fun `resolveBrand returns UNKNOWN when AID is unregistered and PAN BIN does not match`() {
        val unknownAid = Aid.fromHex("A0000000FF1010")
        val pan = Pan.parseOrThrow("9000000000000019")
        assertEquals(EmvBrand.UNKNOWN, BrandResolver.resolveBrand(aid = unknownAid, pan = pan))
    }

    @Test
    fun `resolveBrand classifies a Mastercard 5-prefix PAN`() {
        val pan = Pan.parseOrThrow("5555555555554444")
        assertEquals(EmvBrand.MASTERCARD, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand classifies a Mastercard 2-series PAN inside the 2221 to 2720 range`() {
        val pan = Pan.parseOrThrow("2223000000000007")
        assertEquals(EmvBrand.MASTERCARD, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand classifies an Amex 34-prefix PAN`() {
        val pan = Pan.parseOrThrow("378282246310005")
        assertEquals(EmvBrand.AMERICAN_EXPRESS, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand classifies a Discover 6011-prefix PAN`() {
        val pan = Pan.parseOrThrow("6011111111111117")
        assertEquals(EmvBrand.DISCOVER, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand classifies a JCB 35-prefix PAN`() {
        val pan = Pan.parseOrThrow("3530111333300000")
        assertEquals(EmvBrand.JCB, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand classifies a Diners 30-prefix PAN`() {
        val pan = Pan.parseOrThrow("30569309025904")
        assertEquals(EmvBrand.DINERS_CLUB, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand prefers Discover over UnionPay in the 622126 to 622925 sub-range`() {
        val pan = Pan.parseOrThrow("6221260000000000")
        assertEquals(EmvBrand.DISCOVER, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand falls back to UnionPay outside Discover sub-range but inside 62 prefix`() {
        val pan = Pan.parseOrThrow("6212000000000001")
        assertEquals(EmvBrand.UNIONPAY, BrandResolver.resolveBrand(aid = null, pan = pan))
    }

    @Test
    fun `resolveBrand short-circuits on AID match without consulting BIN`() {
        val visaAid = Aid.fromHex("A0000000031010")
        val mcLikePan = Pan.parseOrThrow("5555555555554444")
        assertEquals(EmvBrand.VISA, BrandResolver.resolveBrand(aid = visaAid, pan = mcLikePan))
    }

    @Test
    fun `resolveBrand returns UNKNOWN when only AID is provided and unregistered`() {
        val unknownAid = Aid.fromHex("A0000000FF1010")
        assertEquals(EmvBrand.UNKNOWN, BrandResolver.resolveBrand(aid = unknownAid, pan = null))
    }
}
