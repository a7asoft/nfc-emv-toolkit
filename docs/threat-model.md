# Threat Model

## Scope

`nfc-emv-toolkit` is a **read-only** library that extracts publicly-readable data from contactless EMV cards over NFC. Anything outside the read path is out of scope.

## What's actually exposed by a contactless tap

When a card is tapped against a phone, the following EMV tags become readable **without the cardholder's PIN** and **without the issuer's keys**:

| Tag | Name | Sensitivity |
|-----|------|-------------|
| `5A` | PAN (card number) | **Sensitive — PCI scope** |
| `57` | Track2 equivalent (PAN + expiry) | **Sensitive — PCI scope** |
| `5F24` | Expiry | Sensitive |
| `5F20` | Cardholder name | Sometimes redacted to `/` |
| `5F2D` | Preferred language | Public |
| `9F12` | App preferred name | Public |
| `9F26` | Application cryptogram (ARQC) | Single-use, **never persist** |
| `9F36` | ATC (transaction counter) | Public |

Modern Visa/MC contactless cards return a **tokenized PAN** in many cases — but legacy cards still return the real PAN. The library treats every PAN as sensitive.

## Library defaults (PCI-safe)

1. **`Pan.toString()` always masks**. The full PAN is reachable only via `Pan.unmasked()`, which logs a warning and is intended for transient in-memory use.
2. **No logging of `57` (track2)** under any log level.
3. **No logging of `9F26` (ARQC)** — single-use credentials must not appear in logs.
4. **No persistence**. The library does not write to disk, SharedPreferences, Keychain, or analytics. Callers are responsible for any storage decisions.
5. **`equals` / `hashCode` use the masked PAN** to prevent leaking the value in stack traces or test output.

## What this library does NOT protect against

- **Caller misuse**: if the application logs `card.pan.unmasked()`, it leaks. The library cannot prevent this.
- **Memory dumps**: PAN bytes live in JVM/Swift memory after parsing. The library does not lock pages or zero buffers (out of scope for v0.1.0).
- **Hostile NFC environments**: a malicious passive listener within ~4 cm could capture the same data the phone reads. This is a card/issuer concern, not a library concern.
- **Replay attacks against the card**: ARQC is single-use; replaying it does not authorize a new transaction. But this protection is in EMV, not us.

## What this library is not

- **Not a payment terminal SDK.** It does not authorize, capture, or settle transactions.
- **Not EMV-Co kernel certified.** Use it for enrollment, card-on-file lookups, or analytics — not for accepting payment.
- **Not a substitute for tokenization.** Issuers' contactless tokens (`9F6B`/`5A` per kernel) help, but if your use case touches PCI scope, you still need a compliant payments processor.

## Reporting vulnerabilities

If you discover a way the library can leak sensitive data despite safe-default APIs, do **not** open a public issue. Email the maintainer (see profile) with details. Disclosure follows a 90-day timeline.
