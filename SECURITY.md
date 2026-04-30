# Security Policy

`nfc-emv-toolkit` parses contactless EMV card data — including primary account numbers (PAN), Track 2 equivalents, and application cryptograms (ARQC). A vulnerability in this code can leak PCI-class data. We treat reports seriously.

## Supported versions

| Version | Supported |
|---------|-----------|
| Latest minor on `main` | ✅ |
| Older minors | ❌ — please reproduce against the latest release first |

We ship rolling releases against `main`. There is no LTS branch.

## Reporting a vulnerability

**Preferred channel — GitHub Private Vulnerability Reporting:**
1. Open the repository's **Security** tab.
2. Click **Report a vulnerability** (the green button on the right).
3. Fill in the form. The report is private to repository maintainers; no other GitHub user sees it until we publish an advisory.

This is the fastest path because it links the report directly to a draft advisory we can collaborate on.

**Email fallback** — if you cannot or will not use GitHub:

- `a7asoft@gmail.com`
- Subject line prefix: `[nfc-emv-toolkit security]`
- Plaintext is fine. We do not currently publish a PGP key; if you need encrypted transport, request one via the same address and we'll arrange it.

**Please do NOT** open a public GitHub issue, pull request, or discussion for a security report. Public exposure before a fix is what we are trying to avoid.

## What is in scope

In scope (per [`docs/threat-model.md`](docs/threat-model.md)):

- Any path by which a sensitive type's `toString`, error message, or accessor leaks raw PAN, Track 2, or ARQC bytes.
- Crashes triggered by malformed APDU input that an attacker controls (denial-of-service via NFC tag).
- Determinism breaks in `:shared:checkKotlinAbi` that allow a public-API change to bypass the gate.
- Dependency vulnerabilities (Kotlin stdlib, kotlinx.datetime, Android Gradle Plugin, Compose runtime when the sample is built).
- Supply-chain attacks against the build (`gradle/`, `.github/workflows/`).

Out of scope:

- EMV kernel certification claims — this library is explicitly **not** a payment-terminal SDK (see `CLAUDE.md` §1 "Mission and non-goals").
- Caller misuse outside the library — for example, an application that logs `card.pan.unmasked()` is leaking through documented escape hatches we cannot prevent.
- Memory-dump attacks against PAN bytes living in JVM / Swift memory after parsing. Page locking and zeroization are deferred to a future milestone.
- ARQC replay attacks against the card — that protection lives in EMV, not in this library.
- Hostile passive NFC listeners within reading range — a card / issuer concern, not a library concern.

## Response timeline

| Stage | Target |
|-------|--------|
| First-touch acknowledgment | 5 business days from report |
| Triage decision (in scope / out of scope / accepted risk) | 10 business days |
| Fix + advisory drafted | Up to 90 days from report; longer with reporter agreement |
| Public disclosure | After the fix is released, or 90 days after report — whichever is sooner |

If a report turns out to be in scope but low severity, we may agree with the reporter to ship the fix in the next regular release without an advisory. If high severity, we cut a security release and publish a CVE-numbered advisory.

## What you will get

- Acknowledgment within the 5-day SLO above.
- Credit in the advisory and (optionally) the changelog, unless you ask to remain anonymous.
- A patch tagged on `main` with a release note pointing back to the advisory.

We do not run a paid bug bounty program. We will not pay for reports. We will respect your time and respond promptly.

## Hardening already in place

The following are documented defaults — not promises against every threat:

- `Pan.toString()` masks (PCI-DSS §3.4 / EMV Book 4 §5.3); the raw form is reachable only via the explicit `Pan.unmasked()` accessor.
- Track 2 and ARQC types follow the same masking discipline.
- No production logging in `commonMain` (no `println`, `Log.*`, `print` — enforced by detekt's `ForbiddenMethodCall`).
- Public API drift is gated by `:shared:checkKotlinAbi`; an accidental new accessor that returns raw bytes is caught at PR time.
- Error variants (`TlvError`, `PanError`, `Track2Error`, `EmvCardError`) carry only structural metadata (offsets, byte counts, hex tag identifiers — no value bytes).
- Codeowner approval is required on `shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/**`, `docs/threat-model.md`, and `CLAUDE.md`.

## What is NOT yet in place

- CodeQL static analysis on PRs — tracked in **#37**.
- Encrypted reporting transport (PGP key for `a7asoft@gmail.com`) — available on request only.

Both will be added in subsequent milestones.

---

Last reviewed: 2026-04-30. This document supersedes any prior informal reporting guidance.
