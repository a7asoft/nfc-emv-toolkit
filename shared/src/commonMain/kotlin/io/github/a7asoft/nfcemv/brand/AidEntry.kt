package io.github.a7asoft.nfcemv.brand

/**
 * One entry in [AidDirectory]: the AID and the brand it identifies.
 *
 * Replaces the previous `Pair<Aid, EmvBrand>` shape so callers (especially
 * Swift consumers via the iOS XCFramework) see an idiomatic domain type
 * rather than `KotlinPair`. Per CLAUDE.md §3.2 ("sealed types for domain
 * alternatives") and §8 (Swift parity).
 */
public data class AidEntry(val aid: Aid, val brand: EmvBrand)
