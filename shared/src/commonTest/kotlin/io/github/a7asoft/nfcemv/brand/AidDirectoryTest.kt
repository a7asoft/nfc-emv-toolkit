package io.github.a7asoft.nfcemv.brand

import kotlin.test.Test
import kotlin.test.assertEquals

class AidDirectoryTest {

    @Test
    fun `EmvBrand has 10 distinct variants`() {
        assertEquals(10, EmvBrand.entries.size)
    }

    @Test
    fun `EmvBrand UNKNOWN is the catch-all`() {
        assertEquals("Unknown", EmvBrand.UNKNOWN.displayName)
    }

    @Test
    fun `EmvBrand displayName is human-readable for VISA`() {
        assertEquals("Visa", EmvBrand.VISA.displayName)
    }

    @Test
    fun `EmvBrand displayName is human-readable for AMERICAN_EXPRESS`() {
        assertEquals("American Express", EmvBrand.AMERICAN_EXPRESS.displayName)
    }

    @Test
    fun `lookup returns null for an unknown AID`() {
        kotlin.test.assertNull(AidDirectory.lookup(Aid.fromHex("A0000000FF1010")))
    }

    @Test
    fun `lookup resolves the canonical Visa Credit Debit AID`() {
        assertEquals(EmvBrand.VISA, AidDirectory.lookup(Aid.fromHex("A0000000031010")))
    }

    @Test
    fun `lookup resolves Visa Electron`() {
        assertEquals(EmvBrand.VISA, AidDirectory.lookup(Aid.fromHex("A0000000032010")))
    }

    @Test
    fun `lookup resolves Mastercard Credit Debit`() {
        assertEquals(EmvBrand.MASTERCARD, AidDirectory.lookup(Aid.fromHex("A0000000041010")))
    }

    @Test
    fun `lookup resolves Maestro distinct from Mastercard`() {
        assertEquals(EmvBrand.MAESTRO, AidDirectory.lookup(Aid.fromHex("A0000000043060")))
    }

    @Test
    fun `lookup resolves American Express ALIS`() {
        assertEquals(
            EmvBrand.AMERICAN_EXPRESS,
            AidDirectory.lookup(Aid.fromHex("A000000025010402")),
        )
    }

    @Test
    fun `lookup resolves Discover Common Debit`() {
        assertEquals(EmvBrand.DISCOVER, AidDirectory.lookup(Aid.fromHex("A0000003241010")))
    }

    @Test
    fun `lookup resolves Diners issued by Discover`() {
        assertEquals(EmvBrand.DINERS_CLUB, AidDirectory.lookup(Aid.fromHex("A0000001523010")))
    }

    @Test
    fun `lookup resolves JCB`() {
        assertEquals(EmvBrand.JCB, AidDirectory.lookup(Aid.fromHex("A0000000651010")))
    }

    @Test
    fun `lookup resolves UnionPay Credit`() {
        assertEquals(EmvBrand.UNIONPAY, AidDirectory.lookup(Aid.fromHex("A000000333010101")))
    }

    @Test
    fun `lookup resolves Interac`() {
        assertEquals(EmvBrand.INTERAC, AidDirectory.lookup(Aid.fromHex("A0000002771010")))
    }

    @Test
    fun `directory has no duplicate AID entries`() {
        val aids = AidDirectory.all.map { it.aid }
        assertEquals(aids.size, aids.toSet().size)
    }

    @Test
    fun `lookup resolves every registered AID by its key`() {
        val mismatches = AidDirectory.all.filter { entry ->
            AidDirectory.lookup(entry.aid) != entry.brand
        }
        assertEquals(emptyList(), mismatches, "lookup mismatch on listed entries")
    }

    @Test
    fun `directory covers every non-UNKNOWN brand at least once`() {
        val covered = AidDirectory.all.map { it.brand }.toSet()
        val missing = EmvBrand.entries.filter { it != EmvBrand.UNKNOWN && it !in covered }
        assertEquals(emptyList(), missing, "brands without an AID entry")
    }

    @Test
    fun `directory does not register UNKNOWN as a brand for any AID`() {
        kotlin.test.assertFalse(
            AidDirectory.all.any { it.brand == EmvBrand.UNKNOWN },
            "UNKNOWN must not be a registered brand value",
        )
    }
}
