package io.github.a7asoft.nfcemv.emv

/**
 * Two-level binary classification used by the tag dictionary and any
 * downstream extractor.
 *
 * - [PCI] — value bytes are PCI DSS Cardholder Data (PAN, expiry, name,
 *   sequence number) or Sensitive Authentication Data
 *   (full Track 2, cryptograms, signed dynamic data, issuer-application
 *   data with cryptographic state). NEVER log, persist, or transmit
 *   raw; route through a typed extractor with masking on `toString`.
 * - [PUBLIC] — value bytes are operational metadata (AIDs, labels,
 *   country / currency / language codes, AIP, AFL, CDOLs, ATC, UN,
 *   transaction amount, CTQ, CID, FCI templates). Safe to log raw.
 *
 * The dichotomy is deliberately binary; a finer grain (e.g. `Cryptogram`,
 * `CardholderData`, `OperationalPublic`) can be layered later without
 * breaking callers that only check `== PCI`.
 */
public enum class TagSensitivity { PCI, PUBLIC }
