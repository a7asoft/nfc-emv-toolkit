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
}
