# Changelog

All notable changes to this project will be documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed — Real-card support: GPO inline TLV + Track 2 fallbacks + TTQ default (#59)
Architectural fix for Visa qVSDC kernel-3 cards (Chase Credit + Debit observed). Captured the real-card APDU traffic via diagnostic logging and corrected the model of what `EmvParser` parses.

**Breaking changes (pre-1.0):**
- **Removed `GpoError.MissingAfl`.** Format-2 GPO responses (template `77`) without an AFL tag are now accepted — the card may be in MSD-only mode and deliver application data inline. `Gpo.afl.entries` is empty in this case. EMV Book 3 §10 / EMVCo Book C-3 (Visa kernel-3) §5.4.3 treat AFL as optional when the application data is inline.
- **`Gpo` exposes `inlineTlv: List<Tlv>`** carrying the format-2 application-data children (everything except AIP and AFL). Format-1 (`80`) returns `emptyList()`. Additive to the public API.
- **New `EmvParser.parse(aid: Aid, tlvNodes: List<Tlv>): EmvCardResult`** overload (`@JvmName("parseTlv")` to disambiguate from the `List<ByteArray>` overload at the JVM level) — the canonical TLV-native entry point. `parseOrThrow(aid, tlvNodes)` mirror also added. The existing `parse(aid, apduResponses: List<ByteArray>)` overload still works (decodes then delegates).

**Reader behavior changes:**
- `ContactlessReader` (Android) now decodes each AFL READ RECORD body to TLV nodes once, concatenates with `gpo.inlineTlv`, and calls the new `EmvParser.parse(aid, nodes)` overload. Cards that ship Track 2 / cardholder name / dates inline in the GPO body — common on Visa qVSDC kernel-3 — now succeed even when their AFL records are empty or carry only supplementary tags (like `5F 28` Issuer Country, `9F 07` Application Usage Control).
- `EmvParser` now falls back to Track 2's embedded PAN when standalone tag `5A` is absent (canonical per IDTECH KB and EMV Book 3 record layout). Symmetrically, falls back to Track 2's embedded expiry when standalone tag `5F 24` is absent (canonical per ISO 7813 and EMV Book 3). Track 2 is pre-parsed once per call so required-field resolution and optional-field exposure see byte-identical data. New private sealed types `PanOutcome`, `ExpiryOutcome`, `Track2Outcome` keep the dispatch flag-free per CLAUDE.md §3.2.
- `TerminalConfig.default()` TTQ default lowered from `36 00 80 00` to `36 00 00 00`. The "Online cryptogram required" bit (byte 2, `0x80`) is now cleared so issuer kernels emit the GPO response shape that read-only readers need. iOS `TerminalConfig.default` mirrors the new value. Override via `TerminalConfig.default().copy(terminalTransactionQualifiers = ...)` if a specific issuer kernel needs the legacy bit set.

**Real-card regression coverage:**
- Sanitized `:android:reader` integration tests for the Visa Credit Chase MSD-only flow (no AFL, all data inline) and the Visa Debit Chase split flow (inline `57` + supplementary AFL record with no PAN-bearing tags).

### Added — PDOL-aware GPO + EmvParser AID injection (#57)
- Reader now reads tag `9F38` from the SELECT AID FCI response, parses the DOL format, builds a structurally-valid PDOL response from terminal defaults (TTQ, country, currency, date, type, UN), wraps in `83 [Lc] [response]`, and sends it as the GPO command body. Cards return records instead of `6A 80` / `69 85`.
- New 2-arg overload `EmvParser.parse(aid: Aid, records: List<ByteArray>)` (and the matching `parseOrThrow`). Real cards put `4F` (AID) in PPSE / SELECT AID FCI but NOT in READ RECORD records — the reader passes the PPSE-extracted AID directly. Existing 1-arg `parse(records)` keeps its behavior for v0.1.x synthetic fixtures.
- New `:shared:commonMain` public types: `Pdol` + `PdolEntry` + `PdolError` + `PdolResult`, `SelectAidFci` + `SelectAidFciError` + `SelectAidFciResult`, `TerminalConfig` (with `Companion.default()`), `PdolResponseBuilder`. All follow the established `parse` / `parseOrThrow` mirror pattern.
- `EmvTags` dictionary extended with `9F38`, `9F66`, `9F1A`, `95`, `9A`, `9C`, `9F33`, `9F35`, `9F40`, `9F09` entries.
- `:android:reader` new public API: `ContactlessReader.read(config: TerminalConfig)` overload (the no-arg `read()` delegates to `read(TerminalConfig.default())`). New `internal ApduCommands.gpoCommand(pdolResponse)` builder. Two new `ReaderError` variants: `SelectAidFciRejected(cause)` and `PdolRejected(cause)`.
- `ios/Sources/EmvReader` mirrors the Android refactor: parallel Swift `TerminalConfig` struct with `static let default`, new `Mapping` bridges (`parsePdol`, `parseSelectAidFci`, `buildPdolResponse`, `parseEmvCard(aid:records:)`), refactored `EmvReader.read(config:)` overload, two new `ReaderError` cases (`selectAidFciRejected`, `pdolRejected`).
- `composeApp` `ErrorPanel` updated with friendly messages for the two new variants.
- TTQ default `36 00 80 00` per javaemvreader / nfc-frog reference implementations. Country / currency US/USD per ISO 3166-1 / 4217 numeric `0840`. Amounts zero (read-only flow does not commit a transaction). Transaction date and unpredictable number computed at READ time so a long-lived `TerminalConfig` does not ship stale values.
- ABI gate regenerated for the additive surface (4 new public types + 1 new `EmvParser.parse` overload + tag dict additions). Determinism verified.
- Test coverage delta: `:shared:allTests` +49 (PdolTest, SelectAidFciTest, TerminalConfigTest, PdolResponseBuilderTest, EmvTagsTest, plus 5 new EmvParserTest cases). `:android:reader:check` +6 (PDOL-flow Visa, AID-injection records-without-4F, SelectAidFci/PDOL rejection, custom TTQ override, Mastercard back-compat). `EmvReaderTests` +5 (Swift mirror).
- Module boundaries preserved per CLAUDE.md §7: only `NFCISO7816TagTransport.swift` imports CoreNFC; new bridges live in `Mapping.swift`. Out-of-scope per CLAUDE.md §1: ARQC, DDA/CDA, online auth, GENERATE AC, kernel certification.

### Added — iOS contactless reader (#50)
- New `ios/Sources/EmvReader` Swift Package providing `EmvReader().read() -> AsyncStream<ReaderState>`. Wraps CoreNFC's `NFCTagReaderSession` + `NFCISO7816Tag` and orchestrates the full EMV contactless read flow per Book 1 §11–12: PPSE → SELECT AID → GPO → READ RECORD → `EmvParser.parse`. Mirrors the v0.2.0 Android reader (#48), but as Swift-idiomatic enums.
- `ReaderState` and `ReaderError` defined as parallel Swift enums (decision: NOT promoted to `commonMain`). Manual mapping at the Kotlin/Swift boundary lives in a single `Mapping.swift`. Kept `commonMain` platform-neutral and gives iOS consumers idiomatic `switch` exhaustiveness on Swift-native types.
- `IoReason` enum mirrors the Android reader's: `tagLost` / `timeout` / `generic`. CoreNFC's `NFCReaderError` codes are mapped to these categories so consumers don't depend on CoreNFC error semantics.
- New `XCFramework("Shared")` Gradle wiring in `shared/build.gradle.kts` produces `Shared.xcframework` for the Swift package's `binaryTarget` to consume. New tasks: `assembleSharedReleaseXCFramework`, `assembleSharedDebugXCFramework`, `assembleSharedXCFramework`.
- CI: `ios` job now builds the XCFramework and runs `xcodebuild test` for the EmvReader Swift package on the iPhone simulator before the sample-app xcodebuild.
- Module boundary verified per CLAUDE.md §7: `EmvReader` depends only on `Shared` (XCFramework) and `CoreNFC`. The ONLY file importing `CoreNFC` is `NFCISO7816TagTransport.swift` (mirrors `:android:reader`'s "only `IsoDepTransport.kt` imports `android.nfc.*`" rule). Does NOT depend on UIKit, SwiftUI, or `iosApp/`.
- Sample-app integration is out of scope for this PR — `iosApp/` is untouched. Sample integration tracked as a separate scope per CLAUDE.md §2 architecture (`ios/Sources/EmvToolkitUI`).
- Test coverage: 14 integration tests in `EmvReaderTests` (Visa / Mastercard / Amex happy paths plus every `ReaderError` variant including `ioFailure(.generic)`, multi-AID lowest-priority selection, silent-skip on non-9000 READ RECORD, and real `Task` cancellation). Tests run against a `FakeIso7816Transport` test double; iOS Simulator does NOT support CoreNFC, so the production `NFCISO7816TagTransport` is exercised only on real-device manual QA.
- Public ABI surface change: `:shared:checkKotlinAbi` UNCHANGED — this PR consumes `:shared`'s public surface, does not modify it.
- Bridge note: Kotlin `@JvmInline value class` types (`Aid`, `Pan`) cannot expose methods through the ObjC interop bridge — they appear as boxed `Any` to Swift. Their `description` (Kotlin `toString()`) is the only accessible method. Swift consumers obtain the AID hex form via `String(describing: card.aid)`; raw bytes are reconstructed by hex-decoding the description in `Mapping.aidBytes(_:)`. Documented in CONTRIBUTING.md.
- Post-review fixes from PR #51 multi-agent review:
  - **Concurrency hardening:** `NFCISO7816TagTransport` now holds `lock` across ALL `session` and `connectedTag` accesses (was missing the `session` write from the main-queue dispatch and the `connectedTag` read in `transceive`). Two data races closed. `transceive` with a closed session now throws `TransportError.io(.generic)` instead of `.timeout` (the previous mapping was misleading — session was torn down externally, not a CoreNFC timeout).
  - **API correctness:** Removed dead `ReaderError.noApplicationSelected` variant — unreachable given current `Ppse.parse` behavior (returns `Err(NoApplicationsFound)` before constructing an empty Ppse). `ApduCommands.selectAid` and `readRecord` boundary checks converted from `precondition()` to `throws ApduCommandError` so they survive release-mode optimization (CLAUDE.md §3.1); call sites use `try!` with `// why:` comments because AFL/PPSE parsers already validate the inputs.
  - **Test discipline:** Direct unit tests for `ApduCommands` boundary conditions (mirrors Android #49's `ApduCommandsTest`). Added `IoFailure(.tagLost)` and `IoFailure(.timeout)` terminal-state tests. Cancellation test now uses deterministic continuation-based `waitForClose()` instead of `Task.sleep(200ms)`. Added Swift PCI-safety regression tests pinning that `String(describing:)` AND `dump()` of `ReaderState.done(card)` do not leak raw PAN. All three brand happy-paths now assert `transport.closed`.
  - **Doc + lint:** Amex AID corrected to `A000000025010701` (ExpressPay) in README. CI workflow's `xcodebuild | xcpretty` pipe now uses `set -o pipefail` so xcodebuild failure propagates. Cancellation-during-connect limitation documented in `EmvReader.read()` DocC. `// why:` comments added scoping `aidBytes`/`aidHex` to `Aid` only (forbidding pattern reuse for `Pan`/`Track2`).

### Added — Android contactless reader (#48)
- New `:android:reader` Gradle module providing `ContactlessReader.fromTag(tag).read()` returning a `Flow<ReaderState>`. Wraps `android.nfc.tech.IsoDep` and orchestrates the full EMV contactless read flow (PPSE → SELECT AID → GPO → READ RECORD → `EmvParser.parse`) per EMV Book 1 §11–12.
- `ReaderState` sealed catalogue: `TagDetected`, `SelectingPpse`, `SelectingAid(aid)`, `ReadingRecords`, `Done(card)`, `Failed(error)`.
- `ReaderError` sealed catalogue: `TagLost`, `IoFailure(cause)`, `PpseUnsupported`, `NoApplicationSelected`, `ApduStatusError(sw1, sw2)`, `PpseRejected(cause)`, `GpoRejected(cause)`, `AflRejected(cause)`, `ParseFailed(cause)`.
- Three new pure-fn parsers in `:shared:commonMain` consumed by the reader (and the future iOS reader): `Ppse.parse`, `Gpo.parse` (handles tag-`80` format-1 and tag-`77` format-2), `Afl.parse`. All follow the established `parse` / `parseOrThrow` mirror pattern with sealed `*Result` and `*Error` types.
- New `Aid.toBytes()` helper on the existing value class so reader code can build `SELECT` APDUs without re-parsing the hex form.
- Module boundary verified per CLAUDE.md §7: `:android:reader` depends on `:shared` + `kotlinx-coroutines-core 1.10.2` only. Does NOT depend on Compose, sample apps, or iOS code. The only file in the module importing `android.nfc.*` is `IsoDepTransport.kt`.
- Cancellation: collector cancellation closes the `IsoDep` channel via `onCompletion`. APDU exchanges run on `Dispatchers.IO`; collector can run on Main.
- Testability: `ContactlessReader`'s production constructor is internal; `fromTag(Tag)` factory builds the production reader, while tests inject a `FakeApduTransport` against an internal `ApduTransport` interface.
- Test coverage: 12 integration tests in `ContactlessReaderTest` (Visa / Mastercard / Amex happy paths plus every `ReaderError` variant plus cancellation), 38 unit tests across the three new `:shared` parsers (12 each + property fuzz × 1000 random inputs each).
- Public ABI surface change: `:shared:checkKotlinAbi` regenerated (+662 lines across `shared/api/android/shared.api` and `shared/api/shared.klib.api`). ABI gate now pins the additive surface.
- Post-review fixes from PR #49 multi-agent review:
  - **Spec correctness:** `Ppse` now decodes the Application Priority Indicator's low nibble per EMV Book 1 §12.2.3 (was decoding the full byte; cards sending `0x81` for priority 1 with cardholder-confirmation flag were ranked LAST instead of FIRST). `ApduCommands.readRecord` now rejects record number 255 (RFU per ISO/IEC 7816-4 §7.3.3); valid range tightened to 1..254.
  - **API correctness:** `ReaderError.AflRejected` removed (dead code — AFL failures wrap as `GpoError.AflRejected → ReaderError.GpoRejected`). `ReaderError.IoFailure(Throwable)` narrowed to `IoFailure(reason: IoReason)` enum (`TagLost` / `Timeout` / `Generic`) so `toString` cannot leak whatever the underlying exception's message carries; `ReaderError.TagLost` folded into `IoFailure(IoReason.TagLost)`. `Gpo.applicationInterchangeProfile` returns a fresh defensive copy per access (was reference-mutable). `Gpo` is no longer a `data class` (hand-rolled `equals` / `hashCode` / `toString`; mirrors the `Tlv.Primitive` pattern). `ApduCommands.PPSE_SELECT` and `GPO_DEFAULT` are now defensive-copy `get()` accessors (mirrors `Fixtures.kt`).
  - **Lint enforcement:** `detekt.yml`'s `ForbiddenMethodCall.includes` now covers `**/android/reader/src/main/**`, so the project's `println` / `Log.*` / `Thread.sleep` / `runBlocking` ban applies to the new reader module.
  - **Test discipline:** direct happy + error-path tests for `Aid.toBytes()` (4 new tests). Terminal-state tests for `ReaderError.PpseRejected` (malformed FCI + no-applications), `ReaderError.GpoRejected`, plus a `SocketTimeoutException → IoReason.Timeout` mapping. `read silently skips a non-9000 READ RECORD and continues with the next` pins the AFL silent-skip contract. Cancellation test now uses `cancelAndJoin()` against a structured-coroutine path (was `take(2).toList()` which only triggers normal terminal completion). Property-fuzz tests in `AflTest` / `GpoTest` / `PpseTest` now assert structural invariants on the `Ok` branch (SFI range, AIP byte-count, priority nibble bounds) instead of the tautological sealed-type check. Multi-AID test renamed to clarify "lowest priority value wins". Three new `:android:reader` tests for `ApduCommands.readRecord` boundary + defensive-copy.
  - **ABI gate:** `:shared:checkKotlinAbi` regenerated for the `Gpo` shape change (componentN/copy removed; hand-rolled equals/hashCode/toString preserved). Determinism verified.

### Added — Dokka API site (#12)
- Dokka 2.0.0 Gradle plugin applied to `:shared`. Output at `docs/api/kotlin/` (gitignored — regenerated by CI on each release tag).
- New `.github/workflows/dokka.yml` publishes to GitHub Pages on every `v*` tag push and on manual `workflow_dispatch`. Source: "GitHub Actions" (the modern Pages mode; not `gh-pages` branch).
- Site URL: `https://a7asoft.github.io/nfc-emv-toolkit/`.
- README "API Docs" section links to the published site and documents local generation (`./gradlew :shared:dokkaGenerate`).
- Implementation note: Dokka 2 ships in V1-compatibility mode by default; opted into the V2 DSL via `org.jetbrains.dokka.experimental.gradle.pluginMode=V2Enabled` in `gradle.properties` so the modern `dokka { dokkaPublications.html { ... } }` extension is available. composeApp is intentionally excluded — sample app, not a public library surface. Multi-module aggregation can be added later if needed.
- Manual settings to flip post-merge (NOT in this PR — UI-only):
  - Settings → Pages → **Source: GitHub Actions** (the modern artifact-based mode).
  - First deploy: trigger via Actions tab → Dokka workflow → "Run workflow" (since no `v*` tags exist yet).

### Added — APDU replay fixtures (#9)
- `extract/fixtures/Fixtures.kt` (test-only): three sanitized synthetic APDU `READ RECORD` response fixtures — `VISA_CLASSIC`, `MASTERCARD_PAYPASS`, `AMEX_EXPRESSPAY`. Each carries the six contactless tags (`4F`, `5A`, `5F24`, `5F20`, `50`, `57`) wrapped in a `70` template per EMV Book 3 §10.5.4. Test PANs from public test ranges (Stripe / Adyen / industry-standard) — Luhn-valid but not real accounts. ARQC, IAD, and other cryptographic fields are absent.
- `extract/fixtures/FixtureExpectation.kt`: `data class` capturing expected per-fixture parse outcome (PAN, brand, AID, expiry, cardholder, label, Track 2 components).
- `extract/fixtures/EmvParserFixturesTest.kt`: 3 integration-pin tests, one per brand, each driving the fixture through `EmvParser.parse` and asserting every field of the resulting `EmvCard` (PAN, expiry, cardholder, brand, application label, AID + Track 2 PAN/expiry/service code) in a single integration pin. Mirrors the pre-existing per-fixture integration pattern in `EmvParserTest`.
- Implementation note: chose constants-in-source over `.apdu` text files (the issue's literal request) to avoid KMP `commonTest` resource-loading friction (no new dep, single source of truth, matches existing project test pattern). Rich KDoc on each constant carries the human-legible content an `.apdu` file would have provided.
- Amex AID note: the fixture targets `A000000025010701` (Amex ExpressPay, the registered EMVCo contactless application — 8 bytes), not the 6-byte `A00000002501` stub originally listed in the plan. The 6-byte stub was explicitly dropped from `AidDirectory` in PR #28; ExpressPay is the contactless EMVCo-registered AID and the right brand-detection path for this corpus.

### Added — Security disclosure path + Dependabot (#36)
- `SECURITY.md` at repo root documents the responsible-disclosure path: GitHub Private Vulnerability Reporting primary, `a7asoft@gmail.com` fallback. Supported versions table covers the latest minor on `main`. SLO: first-touch in 5 business days, triage in 10, fix-and-advisory within 90 days.
- `.github/dependabot.yml` configures weekly version updates on `gradle` (root, picks up `libs.versions.toml`, `shared`, and `composeApp`) and `github-actions` ecosystems. Minor + patch grouped per ecosystem; majors open as individual PRs. Security updates grouped separately. Maintainer auto-assigned per CODEOWNERS.
- `CONTRIBUTING.md` cross-references `SECURITY.md` so contributors know where to route a sensitive finding.
- `README.md` adds a "Security" section linking to `SECURITY.md` and `docs/threat-model.md`, and lists the CI gates that protect PCI-sensitive surfaces (ABI gate, `ForbiddenMethodCall`, PCI-safety regressions).
- Repository settings to flip post-merge (NOT in this PR — UI-only):
  - Settings → Code security → **Private vulnerability reporting** → Enable.
  - Settings → Code security → **Dependabot alerts** → Enable.
  - Settings → Code security → **Dependabot security updates** → Enable.
  - Settings → Code security → Code scanning is intentionally deferred to issue **#37**.

### Added — ABI validation gate (#11)
- Kotlin's built-in `AbiValidationExtension` (since Kotlin 2.1) is enabled on `:shared`. Reference dumps live under `shared/api/android/shared.api` (Android target) and `shared/api/shared.klib.api` (KLIB ABI; the dump tool unifies Native targets when their ABIs match — per-target files appear under `shared/api/klib/<target>/` if they diverge).
- CI runs `./gradlew :shared:checkKotlinAbi` on every PR via the macOS `kmp` job. Public-API drift fails the build.
- `CONTRIBUTING.md` documents the workflow: any PR touching public symbols on `commonMain`, `androidMain`, or `iosMain` must run `./gradlew :shared:updateKotlinAbi` and commit the regenerated dump.
- Implementation note: chose the built-in `AbiValidationExtension` over the standalone `org.jetbrains.kotlinx.binary-compatibility-validator` named in issue #11 because the built-in handles KMP KLIB ABI per target without experimental flags and is already on the Kotlin Gradle plugin classpath. Functionally equivalent for the issue's intent.

### Added — EmvCard model + EmvParser entry point (#8)
- `EmvCard` data class composing every parser shipped so far: `pan: Pan`, `expiry: YearMonth` (kotlinx.datetime), `cardholderName: String?`, `brand: EmvBrand`, `applicationLabel: String?`, `track2: Track2?`, `aid: Aid`.
- `EmvParser.parse(apduResponses: List<ByteArray>): EmvCardResult` (sealed `Ok` / `Err(EmvCardError)`) and `EmvParser.parseOrThrow` for the throw form. Mirrors `Pan.parse` / `parseOrThrow` and `Track2.parse` / `parseOrThrow`.
- `EmvCardError` sealed catalogue: `EmptyInput`, `TlvDecodeFailed(cause: TlvError)`, `MissingRequiredTag(tagHex)`, `PanRejected(cause: PanError)`, `Track2Rejected(cause: Track2Error)`, `InvalidExpiryFormat(nibbleCount)`, `InvalidExpiryMonth(month)`, `InvalidAid(byteCount)`. Errors carry only structural metadata; raw bytes never appear.
- Required tags (`4F`, `5A`, `5F24`) and optional tags (`5F20`, `50`, `57`) are extracted via per-format helpers in `extract/internal/`; PAN segment delegates to `Pan.parse`, Track 2 delegates to `Track2.parse`, brand resolution delegates to `BrandResolver.resolveBrand`. TLV decoding uses `Strictness.Lenient` so cards that emit non-minimal length encodings still parse.
- `EmvCard.toString` overrides the data class default to mask sensitive fields: PAN through `Pan.toString`, Track 2 through `Track2.toString`, and cardholder name as a length-only placeholder (`<N chars>`). Direct `cardholderName` accessor still returns the raw String — caller MUST NOT log the EmvCard whole. A future `CardholderName` value-class wrapper is tracked separately.
- Tag `5A` PAN BCD validation: malformed nibbles (`0xA..0xE` in any position; `0xF` in a non-trailing position) surface as the typed `EmvCardError.MalformedPanNibble(offset)` BEFORE reaching `Pan.parse`, so the diagnostic reports the actual offending nibble offset rather than a derived character index.
- Tags `5F20` and `50` decode as ISO-8859-1 (was UTF-8); cardholder names like `MÜLLER` (Latin-1 `0xDC`) round-trip correctly.
- `extractAid` validates byte length BEFORE copying the value, eliminating the wasted defensive copy on the error path.
- 1,000-iteration deterministic property fuzz pinning the parser invariant: every `List<ByteArray>` resolves to a typed `EmvCardResult`, with no other exception escaping (mirrors `TlvDecoderFuzzTest` and `TrackEncoderFuzzTest`).
- Multi-APDU integration test: `EmvParser.parse` correctly merges fields across multiple `ByteArray` responses.
- `CENTURY_OFFSET = 2000` two-digit-year mapping is documented as a deliberate v0.1.x deviation from EMV (which leaves century interpretation to the kernel) and pinned by regression tests at YY=00 and YY=99.
- `EmvParser` KDoc explicitly enumerates out-of-scope behaviors: PSE/PPSE flow, multi-AID with `87` priority, `9F6B` Mastercard Track 2 fallback, PAN agreement between `5A` and `57`, caller-overridable `Strictness`. Each is tracked separately for v0.2.x or later.

### Added — BER-TLV decoder (#1)
- `Tag` value class (`@JvmInline`) backed by a packed `Long`. Supports 1–4 byte tags per ISO/IEC 8825-1.
- `TagClass` enum (universal / application / context-specific / private).
- `Tlv` sealed type with `Primitive` and `Constructed` variants (regular classes, not data classes — auto-generated members cannot accidentally expose value bytes). Hand-rolled `equals` / `hashCode` / `toString`. `toString` omits value bytes (tag + length / child count only) for PCI-safe logging.
- `TlvOptions` with `Strictness` (Strict / Lenient sealed) and `PaddingPolicy` (Tolerated / Rejected sealed) instead of boolean flags, plus `maxTagBytes` and `maxDepth` bounds.
- `TlvError` sealed catalogue: `UnexpectedEof`, `IndefiniteLengthForbidden`, `InvalidLengthOctet`, `IncompleteTag`, `TagTooLong`, `NonMinimalTagEncoding`, `NonMinimalLengthEncoding`, `ChildrenLengthMismatch`, `MaxDepthExceeded`. Every variant carries an offset; none embed value bytes.
- `TlvParseResult` (sealed `Ok` / `Err`) and `TlvParseException` for the two API styles.
- `TlvDecoder.parse` (returns sealed result) and `TlvDecoder.parseOrThrow` (throws on first violation). Both honor the same option set.
- 116 tests on `commonMain`: happy paths for primitive / constructed / nested, every error variant, EMV padding behavior, documented X.690 deviation cases (e.g. `9F02`, `BF0C`), 10,000-iteration deterministic fuzz, OOM-resistance regression with pinned `UnexpectedEof`, PCI-safety regressions for tags `5A` / `57` / `9F26` with exact-form `toString` assertions.

### Added — BER-TLV encoder (#2)
- `TlvEncoder.encode(node: Tlv): ByteArray` and `TlvEncoder.encode(nodes: List<Tlv>): ByteArray` — the only public surface, mirrors `TlvDecoder`.
- DER-canonical output: definite length, minimal length octets. No options.
- Two-pass exact-allocation strategy: one pass computes the size, second pass fills a single pre-allocated `ByteArray`. No intermediate buffers.
- Tag bytes are preserved verbatim from the source `Tlv` tree, so EMV deviations like `9F02` and `BF0C` round-trip byte-for-byte at the tag level.
- Defense in depth: hardcoded `MAX_DEPTH = 64` guard mirrors `TlvOptions.maxDepth` upper bound; trees deeper than that surface as `IllegalStateException`.
- Round-trip invariant: for every input accepted by the decoder, `decode(encode(parsed.tlvs)) == parsed.tlvs`. Pinned by deterministic 5,000-iteration fuzz and a fixture suite (FCI Visa, Track2, ARQC, nested depth 3, multi-primitive).

#### Removed before release
- An earlier draft included a `rejectTrailingBytes` option intended to catch APDU responses passed in with SW1 SW2 still attached. Removed because at the BER-TLV layer `90 00` decodes as a valid empty primitive, not as trailing bytes — SW detection belongs to the transport layer.
- Wizard-generated scaffold (`Greeting.kt`, `Platform.kt`, `SharedCommonTest.example`) cleaned up.

### Added — Luhn validation (#7)
- `String.isValidLuhn()` extension in `commonMain` package `io.github.a7asoft.nfcemv.validation` per ISO/IEC 7812-1 Annex B.
- Predicate semantics: empty input, non-digit characters, embedded whitespace, and any non-`'0'..'9'` codepoint all return `false`. Length-agnostic (PAN length bounds belong to `Pan` per #5).
- Property-tested against a textbook reference implementation across 1,000 random digit strings.

### Added — Pan value class (#5)
- `@JvmInline value class Pan` in `commonMain` package `io.github.a7asoft.nfcemv.extract`.
- Construction goes through typed factories: `Pan.parse(raw)` returns a sealed `PanResult` (`Ok(Pan)` or `Err(PanError)`), and `Pan.parseOrThrow(raw)` throws `IllegalArgumentException` — mirroring the `TlvDecoder.parse` / `parseOrThrow` pattern. The primary constructor is `internal`, so external consumers cannot bypass validation.
- `PanError` sealed catalogue: `LengthOutOfRange(length)`, `NonDigitCharacters` (no position reported, to avoid leaking corrupted-PAN structure), `LuhnCheckFailed`. Errors carry only structural metadata; raw digits never appear.
- Validation order: length (12–19, ISO/IEC 7812-1 modern range) → ASCII-digits-only → Luhn / mod-10 (#7).
- `toString` returns the PCI DSS Req 3.4 masked form `BBBBBB*…NNNN` (first 6 BIN + middle stars + last 4) on every length 12 through 19. Confirmed by exact-form snapshot tests at lengths 12, 13, 15, 16, 19, plus an explicit "raw never embedded" regression and an all-zeros sweep over 12..19.
- `unmasked()` is the only path back to the raw digit string and is documented as the PCI scope boundary.
- `equals`/`hashCode` are auto-generated by the value class (Kotlin reserves overrides), use the raw form internally, and never expose it via stringification.

### Added — Track2 parser (#6)
- `Track2` regular class in `commonMain` package `io.github.a7asoft.nfcemv.extract`. Decodes EMV tag 57 / ISO 7813 Track 2 Equivalent Data from a BCD-packed `ByteArray`.
- Construction goes through `Track2.parse(raw): Track2Result` (sealed `Ok` / `Err(Track2Error)`) or `Track2.parseOrThrow(raw): Track2`. Mirrors `Pan.parse` / `parseOrThrow` and `TlvDecoder.parse` / `parseOrThrow`.
- `Track2Error` sealed catalogue: `EmptyInput`, `MissingSeparator`, `PanRejected(cause: PanError)`, `ExpiryTooShort`, `InvalidExpiryMonth`, `ServiceCodeTooShort`, `MalformedBcdNibble(offset)`, `MalformedFPadding`. Errors carry only structural metadata; raw nibbles never appear. `MalformedBcdNibble` reports the offset of the actual offending nibble (after a single full-input pre-validation pass), and `MalformedFPadding` fires whenever an `F` nibble appears anywhere other than the single trailing position.
- 5,000-iteration deterministic property fuzz pinning the parser invariant: every input resolves to a typed `Track2Result`, with no other exception escaping (mirrors `TlvDecoderFuzzTest`).
- Fields: `pan: Pan`, `expiry: YearMonth` (2-digit year interpreted as 21st century), `serviceCode: ServiceCode`, plus a private discretionary segment exposed only via `unmaskedDiscretionary(): String` and `discretionaryLength: Int`.
- `toString` masks the PAN (via `Pan.toString`) and the discretionary (size only). `equals`/`hashCode` are hand-rolled (NOT a `data class`) so auto-generated `componentN`/`copy` cannot leak the discretionary by destructuring.
- `ServiceCode` is a `@JvmInline value class` validating exactly three ASCII digits; service codes are categorical metadata, not PCI data, so `toString` returns the raw form.
- New dependency: `org.jetbrains.kotlinx:kotlinx-datetime` (commonMain).

### Added — AID directory + brand resolution (#4)
- New package `io.github.a7asoft.nfcemv.brand` with `Aid` value type, `EmvBrand` enum (10 variants — Visa, Mastercard, Maestro, American Express, Discover, Diners Club, JCB, UnionPay, Interac, Unknown), `AidDirectory` static lookup, internal `BinMatcher` sealed type (`Prefix` / `DigitRange`), internal `BIN_TABLE`, and the public `BrandResolver`.
- `Aid.fromHex(...)` and `Aid.fromBytes(...)` factories validate length (5..16 bytes per ISO/IEC 7816-5) and hex content; case is normalised to uppercase. AIDs are public application metadata, not PCI data.
- `AidDirectory` registers 23 EMVCo-published AIDs across 9 brands, paraphrased; no third-party listing copied verbatim. O(1) lookup via a precomputed `Map<Aid, EmvBrand>`.
- `BinMatcher.Prefix(prefix)` matches by leading-digit prefix; `BinMatcher.DigitRange(length, lo, hi)` matches by numeric range over the leading `length` digits (covers Mastercard's 2221..2720 second series, Discover's 622126..622925, JCB's 3528..3589, Diners' 300..305, etc.).
- `BIN_TABLE` is order-sensitive: more specific matchers come first so Discover's 622126..622925 sub-range resolves to Discover before UnionPay's broader `62` prefix would catch it.
- `BrandResolver.resolveBrand(aid: Aid?, pan: Pan?): EmvBrand` is the public layered entry point: AID lookup first, then BIN fallback against the PAN's raw digits (via `Pan.unmasked()`), then `EmvBrand.UNKNOWN`.
- Out of scope for this milestone: country-specific debit AIDs, BIN-database issuer-name resolution, kernel-scoped AID disambiguation, and exhaustive Maestro overlap coverage.

### Added — EMV tag dictionary (#3)
- New package `io.github.a7asoft.nfcemv.emv` with `EmvTagFormat`, `EmvTagLength` (sealed `Fixed` / `Variable`), `TagSensitivity` (`PCI` / `PUBLIC`), `EmvTagInfo`, and the `EmvTags` lookup object.
- 27 entries covering EMV Book 3 / Book 4 staples plus contactless-kernel additions: AID, App Label, Track 2, PAN, Cardholder Name, Expiration / Effective Date, Country / Currency / Language, PAN Sequence, AIP, DF Name, CDOL1 / CDOL2, AFL, Amount, IAD, Preferred Name, ARQC, CID, ATC, Unpredictable Number, Signed Dynamic, Track 2 (Mastercard), CTQ, FCI Issuer Discretionary Data.
- Each entry carries human-readable name, EMV format code (`N` / `AN` / `B` / `CN`), `Fixed(n)` or `Variable(maxBytes)` length, and a binary `PCI` / `PUBLIC` sensitivity flag. Names are paraphrased from EMV specs; no third-party listing is copied verbatim.
- O(1) lookup via `EmvTags.lookup(tag)` (returns `null` for unknown tags); `EmvTags.all` returns entries in source order.

### Added — engineering setup
- `CLAUDE.md` engineering rules (architecture, SOLID, code style, testing discipline §6.1).
- `.claude/agents/` project-scoped reviewers: `emv-nfc-expert`, `pci-security-reviewer`.
- `.claude/skills/` slash commands: `/review`, `/review-pr`, `/review-emv`, `/review-arch`.
- KMP scaffold from kmp.jetbrains.com wizard with Android (Compose sample) and iOS (SwiftUI sample) targets.

### Documentation
- Top-level `README.md` with terminal-style header, quickstart for both platforms, threat model summary.
- `docs/threat-model.md` covering scope, defaults, what the lib does *not* protect against.
- `docs/recipes/parse-tlv.md` worked example.
- `shared/README.md` API reference for the KMP core module.
- `CONTRIBUTING.md` covering scope guarantees, branching, commit format, PR checklist.

## Notes on versioning

The project is pre-1.0; minor releases may break API. The 1.0 cut will accompany the binary-compatibility-validator API dump and a SemVer guarantee.
