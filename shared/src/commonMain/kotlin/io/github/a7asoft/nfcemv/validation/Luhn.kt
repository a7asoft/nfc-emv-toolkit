package io.github.a7asoft.nfcemv.validation

/**
 * Validate this string as a Luhn-checked numeric identifier per
 * ISO/IEC 7812-1 Annex B (mod-10 checksum).
 *
 * Predicate semantics: returns `true` only when the string is non-empty,
 * contains digits exclusively, and the Luhn checksum evaluates to a
 * multiple of ten. Empty input, embedded whitespace, letters, or any
 * non-digit codepoint all return `false` — no exceptions are thrown.
 *
 * ASCII digits only: Arabic-Indic (`'٠'..'٩'`), fullwidth (`'０'..'９'`),
 * and any other non-`'0'..'9'` codepoint are rejected. Use this as a strict
 * gate on inputs that should be plain ASCII numeric identifiers; do NOT
 * pre-normalize to ASCII before calling — let the predicate reject.
 *
 * The function is length-agnostic (this layer does not enforce PAN length
 * bounds; that is the responsibility of `Pan` once it ships per #5).
 *
 * Limitations of the algorithm itself: Luhn does not detect transpositions
 * `09 ↔ 90` nor twin errors `22 ↔ 55`, `33 ↔ 66`, `44 ↔ 77`. All other
 * single-digit errors and adjacent-digit transpositions are caught.
 */
public fun String.isValidLuhn(): Boolean {
    if (isEmpty() || any { it !in '0'..'9' }) return false
    val lastIndex = length - 1
    val sum = indices.sumOf { i ->
        val digit = this[i].code - '0'.code
        val doubled = if ((lastIndex - i) % 2 == 1) digit * 2 else digit
        if (doubled > 9) doubled - 9 else doubled
    }
    return sum % 10 == 0
}
