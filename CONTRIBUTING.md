# Contributing

Thanks for your interest. A few ground rules.

## Scope

This library is **read-only contactless EMV**. Before opening a PR, check it does not push us toward any of the [non-goals in the README](./README.md#what-it-is-not):

- No EMV kernel certification work
- No online authorization
- No ARQC verification or terminal cryptography
- No contact (chip-and-pin) reading

PRs that expand scope into payment-terminal territory will be closed.

## Workflow

1. Open an issue first for anything beyond a typo. Discuss API shape before code.
2. Branch from `main`: `feat/<short-name>`, `fix/<short-name>`, `docs/<short-name>`.
3. Run formatters + tests locally before pushing:
   ```bash
   ./gradlew ktlintFormat ktlintCheck detekt :shared:allTests
   ```
   CI runs `ktlintCheck` and `detekt` on every PR (the `lint` job gates `kmp` and `ios`).

   If you hit a detekt finding you believe is wrong, do **not** add it to
   `detekt-baseline.xml` — open a PR adjusting `detekt.yml` instead, with a
   justification. The baseline is for legacy debt only.
4. Open a PR. CI must pass.

## Reporting a security issue

If you found a way to make `Pan`, `Track2`, `EmvCard`, or any error message leak raw bytes — or any path by which malformed input crashes the parser without a typed `EmvCardResult.Err` — please follow [`SECURITY.md`](SECURITY.md). **Do not open a public issue or PR.** GitHub Private Vulnerability Reporting routes the report straight to maintainers; email fallback is documented in the security policy.

## Test fixture PANs

Every PAN appearing in a `commonTest` fixture (Kotlin constant or otherwise) MUST come from a publicly published test range — Visa `4111111111111111`, Mastercard `5500000000000004` / `2223000000000007`, Amex `378282246310005`, Discover `6011111111111117`, etc. Stripe and Adyen publish canonical lists.

Why: `EmvParserFixturesTest` calls `card.pan.unmasked()` to assert parser correctness; on assertion failure the test framework prints both expected and actual values to stdout. For a public test PAN this is safe; for a real or unknown-provenance PAN it is a PCI leak.

A fixture PAN that fails Luhn (synthetic but invalid) cannot be used either — the parser rejects it before any test assertion runs.

When in doubt, ask in the PR.

## Commit format

Conventional Commits. Subject ≤ 72 chars, present tense.

```
feat(parser): support BER-TLV constructed tags > 0x1F
fix(android): release IsoDep on cancellation
docs(readme): clarify iOS entitlement steps
```

## Code style

- Kotlin: `ktlint` + `detekt` defaults, modified by `.editorconfig`.
- Swift: standard `swift-format` defaults.
- No abbreviations in public APIs (`pan`, not `pn`).
- Prefer sealed classes / sealed interfaces over enums for state machines.
- Public APIs require KDoc / DocC comments.

## Tests

- Every parser change ships with a fixture in `fixtures/` (sanitized hex APDU trace).
- Every public API ships with at least one happy-path and one error-path test.
- PCI-safety: any new field carrying PAN/track2/ARQC must include a test that proves `toString()` does not expose it.

## iOS Swift package (`ios/`)

The `ios/` Swift Package consumes the KMP `:shared` module via a local
`binaryTarget` pointing at `shared/build/XCFrameworks/release/Shared.xcframework`.
Build the framework BEFORE opening the package in Xcode or running tests:

```bash
./gradlew :shared:assembleSharedReleaseXCFramework
xcodebuild \
  -scheme EmvReader \
  -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
  -derivedDataPath ios/.build-ios \
  test \
  -workspace ios/.swiftpm/xcode/package.xcworkspace
```

The ONLY file in `ios/Sources/EmvReader` that imports `CoreNFC` is
`NFCISO7816TagTransport.swift`. New reader-flow code MUST go through the
`Iso7816Transport` protocol abstraction (mirrors the Android module's
"only `IsoDepTransport.kt` imports `android.nfc.*`" rule). iOS Simulator
does NOT support CoreNFC, so the production transport is exercised only
on real-device manual QA — automated tests run against
`FakeIso7816Transport`.

Kotlin `@JvmInline value class` types (`Aid`, `Pan`) cannot expose
methods through the ObjC interop bridge — they appear as boxed `Any` to
Swift. Their `description` (Kotlin `toString()`) is the only accessible
method. Helpers in `Mapping.swift` reconstitute byte arrays by
hex-decoding the description.

## Public API discipline

The `:shared` module's public API is gated by Kotlin's built-in ABI validation. Reference dumps live under `shared/api/`:

- `shared/api/android/shared.api` — captures `commonMain + androidMain` (JVM/Android signatures, ProGuard format)
- `shared/api/shared.klib.api` — captures `commonMain + iosMain` (KLIB ABI, unified across iOS targets while their ABIs match; per-target files appear under `shared/api/klib/<target>/` only when targets diverge)

If your change adds, removes, renames, or alters a public symbol on `commonMain`, `androidMain`, or `iosMain`, run:

```bash
./gradlew :shared:updateKotlinAbi
```

then commit the regenerated `shared/api/` files in the same PR. CI runs `:shared:checkKotlinAbi` and fails if the committed dump diverges from the actual public surface.

Implementation-only changes (private helpers, internal utilities, refactors that don't move public boundaries) require no dump update.

## Security-sensitive PRs

If your change touches PAN handling, logging, or anything in `docs/threat-model.md`, flag it in the PR description and request a security-focused review.

## Releasing (maintainers)

1. Tag `vX.Y.Z` from `main`.
2. CI publishes Maven artifacts to Central Portal and updates SPM tag.
3. Update `CHANGELOG.md`.
4. Cut a GitHub Release with notes and signed checksums.
