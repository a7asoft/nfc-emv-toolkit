# Issue #48 — `android/reader` IsoDep wrapper with Flow-based read API plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Plan stays untracked. Big-PR scope per user decision (Option A — single PR with full PPSE→GPO→READ flow).

**Goal:** Stand up a new `:android:reader` Gradle subproject that wraps `android.nfc.tech.IsoDep` and exposes `ContactlessReader.read(tag: Tag): Flow<ReaderState>`. The reader orchestrates the full EMV contactless application-selection + record-read flow per EMV Book 1 §11–12, ending with a typed `EmvCardResult` derived from `EmvParser.parse`. Plus three new pure-function helpers in `:shared:commonMain` (`Ppse`, `Gpo`, `Afl`) that the reader consumes — same parsers will be reused by the iOS reader in a follow-up issue.

**Architecture:**

```
┌──────────────────────────────────────────────────┐
│ :android:reader (NEW Android library)            │
│   ContactlessReader (public Flow API)            │
│   ReaderState sealed                             │
│   ReaderError sealed                             │
│   ApduTransport interface (internal, testable)   │
│   IsoDepTransport (internal IsoDep wrapper)      │
└────────────────────┬─────────────────────────────┘
                     │ depends on
                     ▼
┌──────────────────────────────────────────────────┐
│ :shared (commonMain, additive public surface)    │
│   extract/Ppse.kt — PpseDirectory, Ppse.parse    │
│   extract/Gpo.kt — Gpo, Gpo.parse                │
│   extract/Afl.kt — Afl, AflEntry, Afl.parse      │
│   (existing parsers from v0.1.0 unchanged)       │
└──────────────────────────────────────────────────┘
```

The Flow emits state transitions during the read; terminal state is `Done(EmvCard)` or `Failed(ReaderError)`. Cancellation closes the `IsoDep` channel via `onCompletion`.

The three new `:shared` parsers are pure TLV navigation — zero I/O, no platform deps. They live in `commonMain` so the iOS reader (follow-up issue) consumes the same code. This expands `:shared`'s public surface (and the ABI gate), but the addition is intentional and reviewable.

**Tech Stack:**
- Kotlin: 2.3.21 (existing)
- Gradle: 9.5.0 (existing)
- AGP: 8.11.2 (existing)
- New dep: `kotlinx-coroutines-core 1.9.0` for `Flow` — added to `:android:reader` only (not `:shared`).
- Android: minSdk 24 (already pinned), IsoDep available since API 10
- No new GitHub Actions; existing `kmp` job builds the new module via `./gradlew :shared:build` chain (need to verify this triggers `:android:reader:build`; if not, add explicit step).

**Project rules (HARD):**
- No `Co-Authored-By` trailer.
- Plan stays untracked.
- TDD where new behavior lands. Test fixtures = hand-built synthetic transcripts (same approach as #9).
- ABI gate: `:shared:checkKotlinAbi` will FAIL after adding the new public types — implementer MUST run `./gradlew :shared:updateKotlinAbi` and commit the regenerated `shared/api/` files. This is the gate working as designed.
- CLAUDE.md §7 module boundaries: `:android:reader` MUST NOT depend on Compose, sample apps, or iOS code. Verify after wiring.
- Function ≤ 25 lines, ≤ 4 branches per CLAUDE.md §5.1/§5.2 (use `@Suppress("CyclomaticComplexMethod") // why:` for exhaustive sealed `when` ladders per project pattern from PR #34).
- KDoc on every public symbol (CLAUDE.md §5.7).
- Sensitive types' error variants carry only structural metadata (CLAUDE.md §5.8 — already enforced by detekt's `ForbiddenMethodCall`).

---

## File Structure

**Create (new files):**

`:shared:commonMain` (additive public surface):
- `shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Ppse.kt` — `data class Ppse internal constructor(val applications: List<PpseEntry>)`, `data class PpseEntry(val aid: Aid, val priority: Int?)`, sealed `PpseError`, sealed `PpseResult`, `Ppse.Companion.parse(fciBytes: ByteArray): PpseResult`.
- `shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Gpo.kt` — `data class Gpo internal constructor(val applicationInterchangeProfile: ByteArray, val afl: Afl)`, sealed `GpoError`, sealed `GpoResult`, `Gpo.Companion.parse(responseBytes: ByteArray): GpoResult`. Handles both response formats (tag 80 format-1 and tag 77 format-2).
- `shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Afl.kt` — `data class Afl internal constructor(val entries: List<AflEntry>)`, `data class AflEntry(val sfi: Int, val firstRecord: Int, val lastRecord: Int, val odaCount: Int)`, sealed `AflError`, sealed `AflResult`, `Afl.Companion.parse(aflBytes: ByteArray): AflResult`.

`:shared:commonTest` (parser tests):
- `shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/PpseTest.kt`
- `shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/GpoTest.kt`
- `shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/AflTest.kt`

`:android:reader` (new module — under `android/reader/`):
- `android/reader/build.gradle.kts` — `com.android.library` + `org.jetbrains.kotlin.android` plugins, depends on `:shared` + `kotlinx-coroutines-core`.
- `android/reader/src/main/AndroidManifest.xml` — minimal manifest (no permissions; library doesn't request NFC at install time).
- `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/ContactlessReader.kt` — public class with `fun read(tag: android.nfc.Tag): Flow<ReaderState>`. Internal constructor accepts `ApduTransport` for testability; public factory `ContactlessReader.fromTag(tag): ContactlessReader`.
- `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/ReaderState.kt` — sealed catalogue (TagDetected, SelectingPpse, SelectingAid, ReadingRecords, Done, Failed).
- `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/ReaderError.kt` — sealed catalogue (TagLost, IoFailure, PpseUnsupported, NoApplicationSelected, ApduStatusError, ParseFailed).
- `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/internal/ApduTransport.kt` — `internal interface` with `connect`, `transceive(ByteArray): ByteArray`, `close`.
- `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/internal/IsoDepTransport.kt` — `internal class` wrapping `IsoDep`. The ONLY file in this module that imports `android.nfc.*`.
- `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/internal/ApduCommands.kt` — `internal object` with `PPSE_SELECT: ByteArray`, `selectAid(aid: Aid): ByteArray`, `GPO_DEFAULT: ByteArray`, `readRecord(recordNumber: Int, sfi: Int): ByteArray`, `extractStatusWord(response: ByteArray): Pair<Byte, Byte>`, `stripStatusWord(response: ByteArray): ByteArray`. Hand-built per ISO/IEC 7816-4 §5.

`:android:reader` (tests):
- `android/reader/src/test/kotlin/io/github/a7asoft/nfcemv/reader/ContactlessReaderTest.kt` — Flow-based tests against `FakeApduTransport`.
- `android/reader/src/test/kotlin/io/github/a7asoft/nfcemv/reader/internal/FakeApduTransport.kt` — test helper that returns canned APDU responses by command-prefix matching.
- `android/reader/src/test/kotlin/io/github/a7asoft/nfcemv/reader/internal/Transcripts.kt` — synthetic transcripts: PPSE response, SELECT AID FCI response, GPO response, READ RECORD responses for Visa/MC/Amex.

**Modify:**
- `gradle/libs.versions.toml` — add `kotlinx-coroutines = "1.9.0"` and `kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }`.
- `settings.gradle.kts` — add `include(":android:reader")` (Gradle nested project syntax automatically resolves to `android/reader/`).
- `shared/api/android/shared.api` + `shared/api/shared.klib.api` — REGENERATED via `./gradlew :shared:updateKotlinAbi` after adding new public types. Commit the regenerated dumps.
- `README.md` — add `:android:reader` to the architecture diagram / module list.
- `CHANGELOG.md` — `### Added — Android contactless reader (#48)` block under `[Unreleased]`.
- `CONTRIBUTING.md` — note the new module in any module-overview section.

**Don't modify:**
- `composeApp/AndroidManifest.xml` — sample app integrates the reader in a future issue, not this PR.
- Existing `:shared` source files (only ADD new ones).
- `:composeApp` — unrelated.

---

## Reference: EMV contactless read flow (Book 1 §11–12)

The reader orchestrates this sequence per state:

1. **TagDetected** — `transport.connect()` succeeds.
2. **SelectingPpse** — send `PPSE_SELECT` (`00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00`), receive PPSE FCI (`6F` template), parse via `Ppse.parse` → list of `PpseEntry(aid, priority?)`. Pick highest-priority AID (lowest priority value wins; absent priority = lowest).
3. **SelectingAid** — send `selectAid(chosen.aid)`, receive AID FCI (informational; we discard the body — only success/fail status matters).
4. **ReadingRecords** — send `GPO_DEFAULT` (`80 A8 00 00 02 83 00 00`), parse response via `Gpo.parse` → `(aip, afl)`. For each `AflEntry` in `afl.entries`: for each record number in `firstRecord..lastRecord`, send `readRecord(recordNumber, sfi)`, accumulate the response data field (status word stripped).
5. **Done** — feed accumulated records into `EmvParser.parse(records)`. Return `Done(card)` or `Failed(ParseFailed(cause))`.

Per CLAUDE.md (mission), this is read-only. NO ARQC verification, NO online authorization, NO crypto.

Reference APDUs:
- PPSE select: `00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00` (CLA=00, INS=A4 SELECT, P1=04 by name, P2=00 first/only occurrence, Lc=0E, data=`2PAY.SYS.DDF01`, Le=00).
- AID select: `00 A4 04 00 [Lc] [aid bytes] 00`. Lc = aid byte length.
- GPO with empty PDOL: `80 A8 00 00 02 83 00 00`. The `83 00` is a constructed tag-83 with empty value, signaling "I have no PDOL data for you, give me defaults".
- READ RECORD: `00 B2 [recordNumber] [(sfi << 3) | 0x04] 00`. SFI 1–10 for typical EMV applications.

Status word interpretation:
- `90 00` — success
- `61 XX` / `6C XX` — chained / wrong-Le; spec allows resend with adjusted Le. Handle in v0.2.0 by either retrying with the suggested length or treating as `ApduStatusError` (simpler — log as future-work for kernel-grade retry semantics).
- `6A 82` — file/application not found (PPSE absent → `PpseUnsupported`).
- `6A 86` / `6A 88` — wrong P1/P2; treat as `ApduStatusError`.
- Anything else non-9000 → `ApduStatusError(sw1, sw2)`.

---

## Reference: hand-verified test transcripts

The transcripts mirror real-card READ flows but are 100% synthetic — same approach as `Fixtures.kt` from #9. Each transcript = list of `(commandPrefix, response)` pairs the FakeApduTransport matches against.

### PPSE response (Visa-only example, single AID listed)

PPSE FCI structure:
```
6F [outerLen]
  84 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31  -- DF Name (PPSE)
  A5 [a5Len]
    BF 0C [bf0cLen]
      61 [61Len]
        4F 07 A0 00 00 00 03 10 10  -- Visa AID
        87 01 01                    -- priority 1
[SW=9000]
```

Hand-counted byte breakdown:
- `4F` entry: 1+1+7 = 9 bytes
- `87` entry: 1+1+1 = 3 bytes
- `61` template: 1+1+(9+3) = 14 bytes (outer 1+1, inner 12)
- `BF 0C` template: 2+1+14 = 17 bytes
- `A5` template: 1+1+17 = 19 bytes
- `84` entry: 1+1+14 = 16 bytes (DF Name "2PAY.SYS.DDF01" = 14 ASCII bytes)
- `6F` outer inner = 16 + 19 = 35 = 0x23. Outer envelope `6F 23 ...` = 37 bytes total + SW2 90 00.

For multi-AID transcripts (Mastercard / Amex), repeat the `61 ... 4F ... 87 ...` pattern inside the same `BF 0C`. Implementer hand-verifies each.

### SELECT AID FCI response

Real Visa SELECT AID returns a `6F` envelope with `84` (AID), `A5` containing `50` (label), `5F2D` (preferred languages), `9F38` (PDOL), etc. For the reader's purposes, ALL we need is `90 00` status. We discard the FCI body. Transcript can be minimal:

```
6F 0E
  84 07 A0 00 00 00 03 10 10
  A5 03 50 01 56  -- minimal "V" label
[SW=9000]
```

### GPO response (format 1 — tag 80)

```
80 [len]
  [aip 2 bytes] [afl bytes...]
[SW=9000]
```

Visa example with 1 AFL entry covering SFI 1, records 1..1:
- AIP: `00 80` (2 bytes)
- AFL: `08 01 01 00` (4 bytes — SFI=1<<3=08, first=01, last=01, oda=00)
- 80 entry: 1+1+6 = 8 bytes

### READ RECORD response

This is exactly the `Fixtures.VISA_CLASSIC` etc. from #9 — `70 [len] [tags]` template. Reuse those constants verbatim by exposing them as `internal` in `:android:reader`'s test source set, OR re-importing from the existing :shared `commonTest` corpus (preferred — DRY).

Issue: `:android:reader` test source set can't import from `:shared:commonTest` directly. Easiest: copy the byte arrays into the `:android:reader/src/test/.../Transcripts.kt` with a comment citing `shared/.../fixtures/Fixtures.kt` as source.

OR: promote `Fixtures` from `internal` to a `testFixtures` source set in `:shared` so other modules' tests can consume it. Adds Gradle complexity. Defer — copy bytes for v0.2.0.

---

## Task 1: Verify branch + sync state

```bash
git checkout develop
git pull --ff-only origin develop
git checkout -b feat/48-android-reader
git status -sb
```

Expected: `## feat/48-android-reader` plus `?? docs/superpowers/`.

---

## Task 2: Add `kotlinx-coroutines` to version catalog

**File:** `gradle/libs.versions.toml`

In `[versions]`:
```toml
kotlinx-coroutines = "1.9.0"
```

In `[libraries]`:
```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
```

Verify resolution:
```bash
./gradlew help 2>&1 | tail -3
```

If `1.9.0` doesn't resolve, fall back to the latest 1.x patch on Maven Central. Don't jump to a 2.x preview.

---

## Task 3: Create the new `:android:reader` Gradle module

### Step 3a — `settings.gradle.kts`

Append:
```kotlin
include(":android:reader")
```

Verify:
```bash
./gradlew projects 2>&1 | grep android
```

Expected output includes `Project ':android:reader'`. If it errors with "no build file", that's expected at this point — we haven't created `android/reader/build.gradle.kts` yet.

### Step 3b — `android/reader/build.gradle.kts`

Create with:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "io.github.a7asoft.nfcemv.reader"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
```

Notes:
- `alias(libs.plugins.kotlinAndroid)` — verify this exists. If `libs.versions.toml` only has `kotlinMultiplatform`, add a sibling: `kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }`.
- `projects.shared` — uses Gradle's typesafe project accessors. settings.gradle.kts already has `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` (verified earlier). If accessor doesn't resolve, fall back to `project(":shared")`.
- `consumerProguardFiles("consumer-rules.pro")` — empty file for now; placeholder so consumers don't strip our coroutine flow types if they enable R8.

### Step 3c — `android/reader/consumer-rules.pro`

Create empty file with a single comment:
```
# nfc-emv-toolkit android/reader: consumer ProGuard rules.
# Empty for v0.2.0 — Reader's public API uses kotlinx.coroutines.flow types
# which kotlinx-coroutines ships its own consumer rules for. Add here only
# if downstream R8 strips a public symbol from this module.
```

### Step 3d — `android/reader/src/main/AndroidManifest.xml`

Create:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

Empty manifest. Library does NOT request NFC permission — that's the consuming app's responsibility.

### Step 3e — Verify the module configures cleanly

```bash
./gradlew :android:reader:tasks 2>&1 | head -20
```

Expected: standard library task list (`assembleDebug`, `compileDebugKotlin`, `testDebugUnitTest`, etc.). If errors, fix before proceeding to Task 4.

---

## Task 4: Add three new pure-fn parsers to `:shared:commonMain`

### Task 4a — `Afl.kt`

**File:** `shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Afl.kt`

```kotlin
package io.github.a7asoft.nfcemv.extract

/**
 * Application File Locator (EMV Book 3 §6.5.8 / Annex A tag `94`).
 *
 * The AFL is a flat list of 4-byte entries describing which records the
 * terminal must read from which Short File Identifiers. A reader walks
 * this list and issues one `READ RECORD` APDU per record.
 *
 * Each [AflEntry] specifies:
 * - [AflEntry.sfi] — Short File Identifier (1..30); occupies the high
 *   5 bits of the first byte (`firstByte ushr 3`).
 * - [AflEntry.firstRecord] — first record number to read (inclusive).
 * - [AflEntry.lastRecord] — last record number to read (inclusive).
 * - [AflEntry.odaCount] — count of records used for offline data
 *   authentication; reader doesn't act on this in v0.2.0.
 */
public data class Afl internal constructor(public val entries: List<AflEntry>)

public data class AflEntry(
    public val sfi: Int,
    public val firstRecord: Int,
    public val lastRecord: Int,
    public val odaCount: Int,
)

public sealed interface AflError {
    public data object EmptyInput : AflError
    public data class InvalidLength(val byteCount: Int) : AflError
    public data class InvalidSfi(val offset: Int, val sfi: Int) : AflError
    public data class InvalidRecordRange(val offset: Int, val first: Int, val last: Int) : AflError
}

public sealed interface AflResult {
    public data class Ok(val afl: Afl) : AflResult
    public data class Err(val error: AflError) : AflResult
}

public fun Afl.Companion.parse(bytes: ByteArray): AflResult { ... }
public fun Afl.Companion.parseOrThrow(bytes: ByteArray): Afl = ...
```

The `parse` body validates: bytes non-empty, length divisible by 4, each entry's SFI in 1..30, firstRecord ≤ lastRecord, both ≥ 1.

Implementation sketch (use `@Suppress("ReturnCount", "CyclomaticComplexMethod")` per project pattern from PR #34):
```kotlin
public companion object

public fun Afl.Companion.parse(bytes: ByteArray): AflResult {
    if (bytes.isEmpty()) return AflResult.Err(AflError.EmptyInput)
    if (bytes.size % AFL_ENTRY_BYTES != 0) return AflResult.Err(AflError.InvalidLength(bytes.size))
    val entries = mutableListOf<AflEntry>()
    var offset = 0
    while (offset < bytes.size) {
        val sfi = (bytes[offset].toInt() and 0xFF) ushr 3
        if (sfi !in 1..30) return AflResult.Err(AflError.InvalidSfi(offset, sfi))
        val first = bytes[offset + 1].toInt() and 0xFF
        val last = bytes[offset + 2].toInt() and 0xFF
        if (first < 1 || first > last) {
            return AflResult.Err(AflError.InvalidRecordRange(offset, first, last))
        }
        val oda = bytes[offset + 3].toInt() and 0xFF
        entries.add(AflEntry(sfi, first, last, oda))
        offset += AFL_ENTRY_BYTES
    }
    return AflResult.Ok(Afl(entries))
}

private const val AFL_ENTRY_BYTES = 4
```

The `parseOrThrow` mirror-pattern (per `Pan`, `Track2`, `ServiceCode`):
```kotlin
public fun Afl.Companion.parseOrThrow(bytes: ByteArray): Afl =
    when (val result = parse(bytes)) {
        is AflResult.Ok -> result.afl
        is AflResult.Err -> throw IllegalArgumentException(messageFor(result.error))
    }
```

### Task 4b — `Gpo.kt`

**File:** `shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Gpo.kt`

GPO response has TWO formats per EMV Book 3 §6.5.8:

- **Format 1** (tag `80`): outer = `80 [len] [aip 2 bytes] [afl bytes...]`. Most common.
- **Format 2** (tag `77`): outer = `77 [len] ...mixed TLVs containing tag 82 (AIP) and tag 94 (AFL) inside...`. Used by Visa quick / contactless kernels.

```kotlin
public data class Gpo internal constructor(
    public val applicationInterchangeProfile: ByteArray,
    public val afl: Afl,
)

public sealed interface GpoError {
    public data object EmptyInput : GpoError
    public data object UnknownTemplate : GpoError
    public data class TlvDecodeFailed(val cause: TlvError) : GpoError
    public data object MissingAip : GpoError
    public data object MissingAfl : GpoError
    public data class AflRejected(val cause: AflError) : GpoError
    public data class InvalidAipLength(val byteCount: Int) : GpoError
}

public sealed interface GpoResult {
    public data class Ok(val gpo: Gpo) : GpoResult
    public data class Err(val error: GpoError) : GpoResult
}

public fun Gpo.Companion.parse(bytes: ByteArray): GpoResult { ... }
public fun Gpo.Companion.parseOrThrow(bytes: ByteArray): Gpo = ...
```

Implementation walks the input via `TlvDecoder.parse(bytes, TlvOptions(strictness = Strictness.Lenient))`, then:
- If first node is `Tlv.Primitive` with tag `0x80` → format 1: bytes split at offset 2 into AIP (2 bytes) + AFL (rest).
- If first node is `Tlv.Constructed` with tag `0x77` → format 2: walk children, find tag `0x82` for AIP, tag `0x94` for AFL.
- Else → `UnknownTemplate`.

### Task 4c — `Ppse.kt`

**File:** `shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Ppse.kt`

```kotlin
public data class Ppse internal constructor(public val applications: List<PpseEntry>)

public data class PpseEntry(
    public val aid: Aid,
    public val priority: Int?,
)

public sealed interface PpseError {
    public data object EmptyInput : PpseError
    public data class TlvDecodeFailed(val cause: TlvError) : PpseError
    public data object UnknownTemplate : PpseError
    public data object NoApplicationsFound : PpseError
    public data class InvalidAid(val byteCount: Int) : PpseError
}

public sealed interface PpseResult {
    public data class Ok(val ppse: Ppse) : PpseResult
    public data class Err(val error: PpseError) : PpseResult
}

public fun Ppse.Companion.parse(bytes: ByteArray): PpseResult { ... }
public fun Ppse.Companion.parseOrThrow(bytes: ByteArray): Ppse = ...
```

Implementation walks the input:
- TlvDecoder.parse → expect outer `0x6F` (FCI Template) → child `0xA5` (FCI Proprietary Template) → child `0xBF0C` (FCI Issuer Discretionary Data) → list of `0x61` (Application Template) entries.
- For each `0x61`: extract `0x4F` (AID) and optional `0x87` (Application Priority Indicator, 1 byte 0..15).
- Return ordered list (priority ascending; nulls last). Or return as-found and let caller pick.

Decision: return as-found (no sort). Caller picks via `applications.minByOrNull { it.priority ?: Int.MAX_VALUE }` or first.

### Task 4d — Tests for the three parsers

Add exhaustive tests in `:shared:commonTest`. Each parser:
- Happy path with hand-built bytes (one happy path with 1 AID, one with multiple AIDs, one with priority absent).
- Each error variant (`EmptyInput`, `InvalidLength`, `UnknownTemplate`, etc.).
- Property fuzz (random ByteArray → must yield typed Result, never throws non-typed exception). Mirror `EmvParserTest.parser-never-crashes` pattern from #8.
- `parseOrThrow` happy + IAE.

Approximate test counts: 12 per parser × 3 parsers = 36 tests in :shared:commonTest. Plus property fuzz × 3 = 39 total.

### Task 4e — Update `shared/api/`

```bash
./gradlew :shared:updateKotlinAbi
git diff --stat shared/api/
```

Expected: large diff adding entries for `Ppse`, `PpseEntry`, `PpseError$*`, `PpseResult$*`, `Gpo`, `GpoError$*`, `GpoResult$*`, `Afl`, `AflEntry`, `AflError$*`, `AflResult$*`, plus their factory methods. ~50–100 new ABI lines per dump file.

Run determinism check (two consecutive `updateKotlinAbi` runs produce zero diff):
```bash
./gradlew :shared:updateKotlinAbi
git diff --stat shared/api/
```
Expected: no further diff after the first run.

### Task 4f — Verify all gates green on :shared

```bash
./gradlew :shared:allTests :shared:checkKotlinAbi ktlintCheck detekt
```

If anything fails, fix before moving to Task 5.

Commit at this point (sub-commit for narrative clarity):
```bash
git add shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Afl.kt \
        shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Gpo.kt \
        shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Ppse.kt \
        shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/AflTest.kt \
        shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/GpoTest.kt \
        shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/PpseTest.kt \
        shared/api/
git commit -m "feat(extract): Ppse Gpo and Afl parsers in commonMain (#48)"
```

---

## Task 5: Build the `:android:reader` module — internal layer first

Order: ApduTransport → IsoDepTransport → ApduCommands → ReaderError → ReaderState → ContactlessReader.

### Task 5a — `ApduTransport.kt` interface

**File:** `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/internal/ApduTransport.kt`

```kotlin
package io.github.a7asoft.nfcemv.reader.internal

/**
 * Stateful APDU exchange channel. Implementations wrap `IsoDep` in
 * production and a fake byte-array recorder in unit tests.
 *
 * Implementations are NOT thread-safe; the reader's flow uses a single
 * IO-bound coroutine and closes the transport on completion.
 */
internal interface ApduTransport {
    @Throws(java.io.IOException::class)
    fun connect()

    /**
     * Send [command] to the card, return the response (data field +
     * 2-byte status word). Throws [java.io.IOException] on RF errors,
     * including the special [android.nfc.TagLostException] wrapper.
     */
    @Throws(java.io.IOException::class)
    fun transceive(command: ByteArray): ByteArray

    fun close()
}
```

### Task 5b — `IsoDepTransport.kt` implementation

**File:** `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/internal/IsoDepTransport.kt`

```kotlin
package io.github.a7asoft.nfcemv.reader.internal

import android.nfc.tech.IsoDep

/**
 * Production [ApduTransport] backed by Android's [IsoDep] tech.
 *
 * The ONLY file in this module that imports from `android.nfc.*`.
 * All other reader logic operates against the [ApduTransport] abstraction
 * for testability.
 */
internal class IsoDepTransport(private val isoDep: IsoDep) : ApduTransport {
    override fun connect() {
        isoDep.connect()
    }
    override fun transceive(command: ByteArray): ByteArray = isoDep.transceive(command)
    override fun close() {
        isoDep.close()
    }
}
```

### Task 5c — `ApduCommands.kt`

**File:** `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/internal/ApduCommands.kt`

```kotlin
package io.github.a7asoft.nfcemv.reader.internal

import io.github.a7asoft.nfcemv.brand.Aid

/**
 * APDU command builders + status-word helpers per ISO/IEC 7816-4 §5.
 * All commands target contactless EMV applications; values are
 * spec-mandated bit patterns, not magic numbers.
 */
internal object ApduCommands {

    /**
     * SELECT 2PAY.SYS.DDF01 — discovers the contactless application
     * directory (PPSE) per EMV Book 1 §11.3.4.
     *
     * `00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00`
     */
    val PPSE_SELECT: ByteArray = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
        // "2PAY.SYS.DDF01"
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
        0x00, // Le=00 → "give me up to 256 bytes"
    )

    /**
     * GET PROCESSING OPTIONS with empty PDOL data (`83 00`).
     *
     * `80 A8 00 00 02 83 00 00`
     */
    val GPO_DEFAULT: ByteArray = byteArrayOf(
        0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00, 0x00,
    )

    /**
     * Build a SELECT-by-AID command per ISO/IEC 7816-4 §5.4.1.
     */
    fun selectAid(aid: Aid): ByteArray {
        val aidBytes = aid.toBytes()
        val command = ByteArray(5 + aidBytes.size + 1)
        command[0] = 0x00
        command[1] = 0xA4.toByte()
        command[2] = 0x04
        command[3] = 0x00
        command[4] = aidBytes.size.toByte()
        aidBytes.copyInto(command, destinationOffset = 5)
        // command[last] = 0x00 by ByteArray default — Le=00.
        return command
    }

    /**
     * Build a READ RECORD command per ISO/IEC 7816-4 §7.3.3.
     *
     * `00 B2 [recordNumber] [(sfi << 3) | 0x04] 00`
     */
    fun readRecord(recordNumber: Int, sfi: Int): ByteArray {
        require(recordNumber in 1..255) { "recordNumber out of range: $recordNumber" }
        require(sfi in 1..30) { "sfi out of range: $sfi" }
        val p2 = ((sfi shl 3) or 0x04).toByte()
        return byteArrayOf(0x00, 0xB2.toByte(), recordNumber.toByte(), p2, 0x00)
    }

    /**
     * Returns true if the APDU response ends with `90 00` (SW indicating success).
     */
    fun isSuccess(response: ByteArray): Boolean {
        if (response.size < 2) return false
        return response[response.size - 2] == 0x90.toByte() &&
            response[response.size - 1] == 0x00.toByte()
    }

    /**
     * Strip the 2-byte status word from the response. Caller must ensure
     * the response has length ≥ 2 (use [isSuccess] first).
     */
    fun dataField(response: ByteArray): ByteArray = response.copyOfRange(0, response.size - 2)

    fun statusWord(response: ByteArray): Pair<Byte, Byte> {
        require(response.size >= 2)
        return response[response.size - 2] to response[response.size - 1]
    }
}
```

Notes:
- `Aid.toBytes()` — verify this exists on the existing `Aid` value class. If only `Aid.bytes` (a property) exists, use that. If neither, the implementer may need to add a helper to `Aid` (small ABI change).

### Task 5d — `ReaderError.kt`

**File:** `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/ReaderError.kt`

```kotlin
package io.github.a7asoft.nfcemv.reader

import io.github.a7asoft.nfcemv.extract.EmvCardError

public sealed interface ReaderError {
    public data object TagLost : ReaderError

    public data class IoFailure(val cause: Throwable) : ReaderError

    public data object PpseUnsupported : ReaderError

    public data object NoApplicationSelected : ReaderError

    public data class ApduStatusError(val sw1: Byte, val sw2: Byte) : ReaderError

    public data class ParseFailed(val cause: EmvCardError) : ReaderError
}
```

### Task 5e — `ReaderState.kt`

**File:** `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/ReaderState.kt`

```kotlin
package io.github.a7asoft.nfcemv.reader

import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.EmvCard

public sealed interface ReaderState {
    public data object TagDetected : ReaderState

    public data object SelectingPpse : ReaderState

    public data class SelectingAid(val aid: Aid) : ReaderState

    public data object ReadingRecords : ReaderState

    public data class Done(val card: EmvCard) : ReaderState

    public data class Failed(val error: ReaderError) : ReaderState
}
```

### Task 5f — `ContactlessReader.kt`

**File:** `android/reader/src/main/kotlin/io/github/a7asoft/nfcemv/reader/ContactlessReader.kt`

```kotlin
package io.github.a7asoft.nfcemv.reader

import android.nfc.Tag
import android.nfc.tech.IsoDep
import io.github.a7asoft.nfcemv.brand.Aid
import io.github.a7asoft.nfcemv.extract.Afl
import io.github.a7asoft.nfcemv.extract.AflEntry
import io.github.a7asoft.nfcemv.extract.EmvCardResult
import io.github.a7asoft.nfcemv.extract.EmvParser
import io.github.a7asoft.nfcemv.extract.Gpo
import io.github.a7asoft.nfcemv.extract.GpoResult
import io.github.a7asoft.nfcemv.extract.Ppse
import io.github.a7asoft.nfcemv.extract.PpseResult
import io.github.a7asoft.nfcemv.reader.internal.ApduCommands
import io.github.a7asoft.nfcemv.reader.internal.ApduTransport
import io.github.a7asoft.nfcemv.reader.internal.IsoDepTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import java.io.IOException

public class ContactlessReader internal constructor(
    private val transport: ApduTransport,
) {
    public companion object {
        /**
         * Build a reader from an Android [Tag] handed in by `NfcAdapter`.
         * Caller must hold a valid Tag for the lifetime of the returned
         * reader's collected Flow.
         */
        public fun fromTag(tag: Tag): ContactlessReader {
            val isoDep = IsoDep.get(tag)
                ?: throw IllegalArgumentException("Tag does not support IsoDep")
            return ContactlessReader(IsoDepTransport(isoDep))
        }
    }

    public fun read(): Flow<ReaderState> = flow {
        emit(ReaderState.TagDetected)
        try {
            transport.connect()
            val ppse = selectPpse() ?: run {
                emit(ReaderState.Failed(ReaderError.PpseUnsupported))
                return@flow
            }
            val chosen = ppse.applications.minByOrNull { it.priority ?: Int.MAX_VALUE }
                ?: run {
                    emit(ReaderState.Failed(ReaderError.NoApplicationSelected))
                    return@flow
                }
            emit(ReaderState.SelectingAid(chosen.aid))
            if (!selectAid(chosen.aid)) return@flow
            emit(ReaderState.ReadingRecords)
            val gpo = readGpo() ?: return@flow
            val records = readAllRecords(gpo.afl)
            when (val parsed = EmvParser.parse(records)) {
                is EmvCardResult.Ok -> emit(ReaderState.Done(parsed.card))
                is EmvCardResult.Err -> emit(ReaderState.Failed(ReaderError.ParseFailed(parsed.error)))
            }
        } catch (e: IOException) {
            emit(ReaderState.Failed(translateIo(e)))
        }
    }
        .flowOn(Dispatchers.IO)
        .onCompletion { runCatching { transport.close() } }

    // ... private helpers selectPpse, selectAid, readGpo, readAllRecords,
    // translateIo — each ≤ 25 lines, ≤ 4 branches.
}
```

Sketch of private helpers (full bodies the implementer fills in):

```kotlin
private suspend fun kotlinx.coroutines.flow.FlowCollector<ReaderState>.selectPpse(): Ppse? {
    emit(ReaderState.SelectingPpse)
    val response = transport.transceive(ApduCommands.PPSE_SELECT)
    if (!ApduCommands.isSuccess(response)) {
        val (sw1, sw2) = ApduCommands.statusWord(response)
        // 6A 82 = file not found = PPSE absent → PpseUnsupported
        if (sw1 == 0x6A.toByte() && sw2 == 0x82.toByte()) return null
        emit(ReaderState.Failed(ReaderError.ApduStatusError(sw1, sw2)))
        return null
    }
    return when (val parsed = Ppse.parse(ApduCommands.dataField(response))) {
        is PpseResult.Ok -> parsed.ppse
        is PpseResult.Err -> { /* emit Failed; return null */ null }
    }
}

private suspend fun kotlinx.coroutines.flow.FlowCollector<ReaderState>.selectAid(aid: Aid): Boolean {
    val response = transport.transceive(ApduCommands.selectAid(aid))
    if (ApduCommands.isSuccess(response)) return true
    val (sw1, sw2) = ApduCommands.statusWord(response)
    emit(ReaderState.Failed(ReaderError.ApduStatusError(sw1, sw2)))
    return false
}

private suspend fun kotlinx.coroutines.flow.FlowCollector<ReaderState>.readGpo(): Gpo? {
    val response = transport.transceive(ApduCommands.GPO_DEFAULT)
    if (!ApduCommands.isSuccess(response)) {
        val (sw1, sw2) = ApduCommands.statusWord(response)
        emit(ReaderState.Failed(ReaderError.ApduStatusError(sw1, sw2)))
        return null
    }
    return when (val parsed = Gpo.parse(ApduCommands.dataField(response))) {
        is GpoResult.Ok -> parsed.gpo
        is GpoResult.Err -> { /* emit Failed; return null */ null }
    }
}

private fun readAllRecords(afl: Afl): List<ByteArray> {
    val collected = mutableListOf<ByteArray>()
    afl.entries.forEach { entry ->
        for (record in entry.firstRecord..entry.lastRecord) {
            val response = transport.transceive(ApduCommands.readRecord(record, entry.sfi))
            if (ApduCommands.isSuccess(response)) {
                collected.add(ApduCommands.dataField(response))
            }
            // Else: silently skip; partial reads are tolerated by EmvParser
            // which will surface MissingRequiredTag if essential data is absent.
        }
    }
    return collected
}

private fun translateIo(e: IOException): ReaderError =
    if (e is android.nfc.TagLostException) ReaderError.TagLost
    else ReaderError.IoFailure(e)
```

Each helper ≤ 25 lines per CLAUDE.md §5.1. The `read()` body is the orchestrator — verify it stays ≤ 25 lines after extraction; if not, decompose further (e.g., `runRead()` private fn).

---

## Task 6: Tests for `:android:reader`

### Task 6a — `FakeApduTransport.kt`

**File:** `android/reader/src/test/kotlin/io/github/a7asoft/nfcemv/reader/internal/FakeApduTransport.kt`

```kotlin
package io.github.a7asoft.nfcemv.reader.internal

internal class FakeApduTransport(
    private val script: List<Pair<ByteArray, ByteArray>>,
) : ApduTransport {
    private var connected = false
    private var index = 0
    var closed: Boolean = false ; private set

    override fun connect() { connected = true }

    override fun transceive(command: ByteArray): ByteArray {
        check(connected) { "transport not connected" }
        check(index < script.size) { "no more scripted responses (received ${command.size}-byte command)" }
        val (expectedPrefix, response) = script[index]
        check(command.startsWithBytes(expectedPrefix)) {
            "command #$index does not start with expected prefix"
        }
        index++
        return response
    }

    override fun close() { closed = true }

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean =
        size >= prefix.size && (0 until prefix.size).all { i -> this[i] == prefix[i] }
}
```

### Task 6b — `Transcripts.kt`

**File:** `android/reader/src/test/kotlin/io/github/a7asoft/nfcemv/reader/internal/Transcripts.kt`

A test-only object holding hand-built byte arrays for:
- `VISA_PPSE_RESPONSE` — synthetic PPSE FCI listing one Visa AID with priority 1, ending in `90 00` SW.
- `VISA_SELECT_FCI_RESPONSE` — minimal valid SELECT response, `90 00`.
- `VISA_GPO_RESPONSE` — format-1 (tag 80) with synthetic AIP + AFL covering SFI=1 records 1..1.
- `VISA_RECORD_1_RESPONSE` — same byte sequence as `Fixtures.VISA_CLASSIC` from #9 (copied with citation).
- Same triplet for `MASTERCARD_*` and `AMEX_*`.

Hand-verify outer template lengths AND status-word terminators per the same discipline as #9 fixtures.

### Task 6c — `ContactlessReaderTest.kt`

**File:** `android/reader/src/test/kotlin/io/github/a7asoft/nfcemv/reader/ContactlessReaderTest.kt`

Tests (organize by concept, kotlin.test framework):

1. `read emits Visa Done state with the expected EmvCard` — script: `[(PPSE_SELECT, VISA_PPSE), (selectAid(visaAid), VISA_SELECT_FCI), (GPO_DEFAULT, VISA_GPO), (readRecord(1,1), VISA_RECORD_1)]`. Collect Flow to list, assert `[TagDetected, SelectingPpse, SelectingAid(visa), ReadingRecords, Done(card)]`. Card matches expected.
2. Same for Mastercard.
3. Same for Amex.
4. `read emits PpseUnsupported when PPSE returns 6A 82` — script: PPSE returns `6A 82`. Last state is `Failed(PpseUnsupported)`.
5. `read emits TagLost when transport throws TagLostException` — fake transport throws `android.nfc.TagLostException` on connect. Last state is `Failed(TagLost)`.
6. `read emits IoFailure when transport throws generic IOException` — fake throws plain IOException. Last state is `Failed(IoFailure(cause))`.
7. `read emits ApduStatusError on a non-9000 status from SELECT AID` — PPSE OK, SELECT AID returns `6A 86`. Last state is `Failed(ApduStatusError(0x6A, 0x86))`.
8. `read emits ApduStatusError on a non-9000 status from GPO` — same pattern.
9. `read emits ParseFailed when records produce a malformed EmvCard` — script returns success bytes that omit `5A` or `5F24`; last state is `Failed(ParseFailed(MissingRequiredTag(...)))`.
10. `read closes the transport after Flow completion (success path)` — assert `fake.closed == true`.
11. `read closes the transport after Flow cancellation` — collect with `take(2)` to cancel mid-flow; assert `fake.closed == true`.
12. `read selects highest-priority application from a multi-AID PPSE` — PPSE returns 2 AIDs (Visa priority 1, MC priority 5); reader picks Visa. `SelectingAid(visa)` emitted, not Mastercard.

12 tests minimum. Each test ≤ 30 lines (Test functions are exempt from §5.1's 25-line cap when scaffolding dominates).

Add a property-fuzz test that sends 1000 random scripts and asserts the Flow always terminates with `Done` or `Failed` — never with a non-terminal state, never with an unhandled exception.

---

## Task 7: Verify the new module's `:check` task passes

```bash
./gradlew :android:reader:check :android:reader:assembleDebug --stacktrace
```

Expected: BUILD SUCCESSFUL. If any test fails, diagnose. Common issues:
- **`ChildrenLengthMismatch` from a transcript** — outer length wrong; recount per the Reference section.
- **`PpseError.NoApplicationsFound`** — synthetic PPSE doesn't contain `0x61` template correctly.
- **`AflError.InvalidLength`** — AFL bytes not divisible by 4.
- **Transcript prefix mismatch in FakeApduTransport** — command format wrong; verify PPSE_SELECT, GPO_DEFAULT, selectAid, readRecord byte sequences against the spec.

If a transcript fixture fails, the FIXTURE is wrong (not the code under test). Recompute; do NOT relax the assertion.

---

## Task 8: Verify all existing gates green

```bash
./gradlew :shared:allTests :shared:checkKotlinAbi :shared:dokkaGenerate :android:reader:check ktlintCheck detekt --stacktrace
```

Expected: all green. The ABI gate now reflects the new public types from Task 4.

If the existing macOS `kmp` CI job doesn't include `:android:reader:assembleDebug`, modify `.github/workflows/ci.yml` to add a step (or rely on `:shared:build` to chain it via dependency — verify by reading the existing ci.yml). Most likely we need an explicit `./gradlew :android:reader:assembleDebug :android:reader:check` step in the `kmp` job after `:shared:allTests`.

---

## Task 9: Documentation updates

### Task 9a — README

Add to architecture diagram:
```
android/reader   — IsoDep wrapper, Flow API for Android consumers (NEW v0.2.0)
```

### Task 9b — CHANGELOG `[Unreleased]` block

```markdown
### Added — Android contactless reader (#48)
- New `:android:reader` Gradle module providing `ContactlessReader.fromTag(tag).read()` returning a `Flow<ReaderState>`. Wraps `android.nfc.tech.IsoDep` and orchestrates the full EMV contactless read flow (PPSE → SELECT AID → GPO → READ RECORD → `EmvParser.parse`).
- `ReaderState` sealed catalogue: `TagDetected`, `SelectingPpse`, `SelectingAid(aid)`, `ReadingRecords`, `Done(card)`, `Failed(error)`.
- `ReaderError` sealed catalogue: `TagLost`, `IoFailure(cause)`, `PpseUnsupported`, `NoApplicationSelected`, `ApduStatusError(sw1, sw2)`, `ParseFailed(cause)`.
- Three new pure-fn parsers in `:shared:commonMain` consumed by the reader (and the future iOS reader): `Ppse.parse`, `Gpo.parse`, `Afl.parse`. All follow the established `parse` / `parseOrThrow` mirror pattern with sealed `*Result` and `*Error` types.
- Module boundary verified per CLAUDE.md §7: `:android:reader` depends on `:shared` + `kotlinx-coroutines-core` only. Does NOT depend on Compose or any sample app.
- Cancellation: collector cancellation closes the `IsoDep` channel via `onCompletion`. APDU exchanges run on `Dispatchers.IO`; collector can run on Main.
- Testability: `ContactlessReader`'s public constructor is internal-only; `fromTag(Tag)` factory builds the production reader, while tests inject a `FakeApduTransport` against an internal `ApduTransport` interface.
- Test coverage: 12 integration tests in `ContactlessReaderTest` (Visa / MC / Amex happy paths plus every `ReaderError` variant plus cancellation), 36+ unit tests across the three new `:shared` parsers, plus property fuzz on each.
- Public ABI surface change: `:shared:checkKotlinAbi` regenerated with the new types. ABI gate now pins the additive surface.
```

### Task 9c — CONTRIBUTING

If the file has a "Modules" section, add `:android:reader`. Otherwise skip.

---

## Task 10: Final commit + push + open PR

Stage files explicitly:
```bash
git add gradle/libs.versions.toml \
        settings.gradle.kts \
        shared/api/ \
        shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Afl.kt \
        shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Gpo.kt \
        shared/src/commonMain/kotlin/io/github/a7asoft/nfcemv/extract/Ppse.kt \
        shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/AflTest.kt \
        shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/GpoTest.kt \
        shared/src/commonTest/kotlin/io/github/a7asoft/nfcemv/extract/PpseTest.kt \
        android/ \
        README.md \
        CHANGELOG.md
git status -s
```

Verify NOTHING under `docs/superpowers/plans/` is staged.

If `.github/workflows/ci.yml` was modified per Task 8, include it.

Commit. If you split the work as recommended (sub-commit after Task 4 for the :shared parsers), this is the second commit:
```bash
git commit -m "feat(reader): android ContactlessReader with Flow API (#48)"
```

NO `Co-Authored-By` trailer.

Push and open PR:
```bash
git push -u origin feat/48-android-reader
gh pr create --base develop --head feat/48-android-reader \
  --title "feat(reader): :android:reader module with Flow-based contactless read API (#48)" \
  --body "$(cat <<'EOF'
## Summary

- New `:android:reader` Gradle module wrapping `android.nfc.tech.IsoDep` with a `ContactlessReader.fromTag(tag).read(): Flow<ReaderState>` API. Orchestrates the full EMV contactless read flow per Book 1 §11–12: PPSE → SELECT AID → GPO → READ RECORD → `EmvParser.parse`.
- Three new pure-fn parsers added to `:shared:commonMain`: `Ppse`, `Gpo`, `Afl` (with `parse` / `parseOrThrow` and sealed `*Result` / `*Error` per project pattern). Reused by the upcoming iOS reader.
- `ReaderState` sealed: `TagDetected`, `SelectingPpse`, `SelectingAid(aid)`, `ReadingRecords`, `Done(card)`, `Failed(error)`.
- `ReaderError` sealed: `TagLost`, `IoFailure(cause)`, `PpseUnsupported`, `NoApplicationSelected`, `ApduStatusError(sw1, sw2)`, `ParseFailed(cause)`.
- Internal `ApduTransport` interface lets the reader be unit-tested against a `FakeApduTransport` recording synthetic transcripts; production wiring through `IsoDepTransport`.

## Module boundaries (CLAUDE.md §7)

- `:android:reader` → `:shared` + `kotlinx-coroutines-core 1.9.0`. NO dependency on Compose, sample apps, or iOS code.
- The ONLY file importing `android.nfc.*` is `IsoDepTransport.kt`.

## ABI gate

`:shared:checkKotlinAbi` regenerated to reflect the additive `Ppse` / `Gpo` / `Afl` public types. New ABI surface pinned by the gate.

## Test coverage

- 36+ unit tests across the three new `:shared` parsers (happy paths + every error variant + property fuzz).
- 12 integration tests in `ContactlessReaderTest` driving the Flow against `FakeApduTransport` with hand-built synthetic transcripts (Visa Classic / Mastercard PayPass / Amex ExpressPay).
- Cancellation, IO error mapping, multi-AID priority selection, every `ReaderError` variant covered.
- All transcripts hand-verified per the same discipline as the #9 fixture corpus.

## Test plan

- [x] `./gradlew :shared:allTests :shared:checkKotlinAbi :shared:dokkaGenerate ktlintCheck detekt` green.
- [x] `./gradlew :android:reader:check :android:reader:assembleDebug` green.
- [x] No `Co-Authored-By` trailer.
- [x] Plan files (`docs/superpowers/plans/`) untracked.
- [x] Module boundary verified: no Compose / iOS / sample-app deps.

Closes #48.
EOF
)"
```

---

## Self-Review

| Issue requirement | Task |
|---|---|
| New `:android:reader` module under `android/reader/` | Task 3 |
| Public API ≤ ~5 types with KDoc | Tasks 5d–5f (`ContactlessReader`, `ReaderState`, `ReaderError`, plus internal helpers) |
| `:shared:checkKotlinAbi` consistent | Task 4e (regenerated) |
| Unit tests with fake APDU transport | Tasks 6a–6c |
| CI: new module builds | Task 8 |
| README / CONTRIBUTING / CHANGELOG | Task 9 |

**Placeholders:** the Task 5f `read()` body has a `// ...` mark for private helper bodies — implementer fills in per the sketch. No literal placeholder strings in production code.

**Type consistency:**
- `Aid.toBytes()` referenced in Task 5c — verify exists or use `Aid.bytes` accessor; if neither, small ABI addition needed (flagged before commit).
- `EmvCardError` referenced in `ReaderError.ParseFailed` — type from #8.
- `Flow<ReaderState>` is `kotlinx.coroutines.flow.Flow` — pulled in via Task 2.
- `Aid.fromHex(...)` factory used in tests — exists per #28.
- `EmvParser.parse(List<ByteArray>): EmvCardResult` — sig verified in #8.

**Risks accepted:**
- `IsoDep` retry semantics on `61 XX` / `6C XX` not implemented; v0.2.0 treats those as `ApduStatusError`. Future-work issue if real cards demand it.
- Multi-AID priority handling implemented as "lowest priority value wins"; this is per EMV Book 1 §12.4 but not tied to the Application Priority Indicator's full nibble semantics. Acceptable for v0.2.0 — flag for review.
- AFL records that fail (non-9000 SW) are silently skipped. EmvParser will surface `MissingRequiredTag` if essential data is absent. Acceptable degradation; documented in `readAllRecords` KDoc.

**Skips:**
- iOS reader (separate issue, same milestone v0.2.0).
- Compose UI surface for the reader (separate milestone).
- Real-device instrumented tests (separate follow-up).
- HCE / online auth / ARQC verification (CLAUDE.md §1 non-goals).
- Application Priority Indicator's full nibble semantics (defer to a refinement issue if needed).

**Commit count:** 2 (sub-commit after Task 4 for the :shared parsers, final commit after Task 10 for the reader module). Both with the same `Co-Authored-By: NONE` rule.

**Risk flagged:** This is a ~2000-line PR spanning 3 new :shared parsers + a full new module + 50+ tests. Reviewer load is high. If during implementation it becomes clear the scope is unworkable in one PR, FALL BACK to Option B (phased) by stopping after Task 4f's commit and shipping just the :shared parsers as a smaller PR (#48a), then opening #48b for the reader module. User explicitly chose Option A; only fall back if a concrete blocker (not a "feels big" instinct) appears.
