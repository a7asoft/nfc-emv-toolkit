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
