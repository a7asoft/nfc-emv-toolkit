<!-- ──────────────  HEADER  ────────────── -->

```diff
╭──────────────────────────────────────────────────────────────────────╮
│                                                                      │
!     ███╗   ██╗███████╗ ██████╗      ███████╗███╗   ███╗██╗   ██╗     │
!     ████╗  ██║██╔════╝██╔════╝      ██╔════╝████╗ ████║██║   ██║     │
!     ██╔██╗ ██║█████╗  ██║     █████╗█████╗  ██╔████╔██║██║   ██║     │
!     ██║╚██╗██║██╔══╝  ██║     ╚════╝██╔══╝  ██║╚██╔╝██║╚██╗ ██╔╝     │
!     ██║ ╚████║██║     ╚██████╗      ███████╗██║ ╚═╝ ██║ ╚████╔╝      │
!     ╚═╝  ╚═══╝╚═╝      ╚═════╝      ╚══════╝╚═╝     ╚═╝  ╚═══╝       │
│                                                                      │
+   ❯ what                                                             │
    Modern toolkit for reading EMV contactless cards over NFC          │
    Native Android (Kotlin) + iOS (Swift) · KMP-shared parser          │
│                                                                      │
+   ❯ status                                                           │
    v0.3.0 · real-card support shipped · pre-1.0 (API may break)       │
│                                                                      │
+   ❯ license                                                          │
    Apache 2.0                                                         │
│                                                                      │
╰──────────────────────────────────────────────────────────────────────╯
```

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/iOS-000000?style=flat-square&logo=apple&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Swift-F05138?style=flat-square&logo=swift&logoColor=white" />
  <img src="https://img.shields.io/badge/KMP-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square" />
  <img src="https://img.shields.io/badge/Release-v0.3.0-blue?style=flat-square" />
</p>

---

## What it is

A **read-only**, **PCI-safe** toolkit for parsing EMV contactless card data on mobile. Native Android (Kotlin) + native iOS (Swift), with the BER-TLV parser, AID directory, and brand detection logic shared via Kotlin Multiplatform.

Built by a developer who shipped a production Tap-to-Phone payment app and got tired of the existing options:

- `devnied/EMV-NFC-Paycard-Enrollment` — Java, blocking, logs PAN, no KMP
- iOS — no decent OSS option, period

## Why

| Problem | This toolkit |
|---------|--------------|
| Java-only, blocking I/O | Kotlin-first, coroutines + Flow / Swift `async/await` + `AsyncSequence` |
| Logs PAN + sensitive data | PCI-safe defaults: `Pan.toString()` masks always, no track2 in logs |
| Two ecosystems = two bugs | KMP-shared parser, identical models on both platforms |
| Outdated brand detection | AID + modern BIN ranges (MC 2221–2720, etc.) |
| No iOS | First-class CoreNFC reader with whitelisted AIDs |

## What it is **not**

> ⚠️ This is **not** a payment terminal SDK.

- ❌ No EMV kernel certification (C-2 / C-3 / etc.)
- ❌ No online authorization
- ❌ No ARQC generation / verification
- ❌ No terminal cryptography (DDA / CDA)
- ❌ No contact (chip-and-pin) reading
- ❌ Not for processing real transactions

If you need any of the above, talk to a payment processor, not a GitHub library.

---

## API Docs

Generated from the latest released tag via [Dokka 2](https://kotlinlang.org/docs/dokka-introduction.html):

**[https://a7asoft.github.io/nfc-emv-toolkit/](https://a7asoft.github.io/nfc-emv-toolkit/)**

For unreleased `develop` snapshots, run locally:

```bash
./gradlew :shared:dokkaGenerate
open docs/api/kotlin/index.html
```

The published site is regenerated automatically by GitHub Actions on every `v*` tag.

---

## Security

This library handles PCI-class data (PAN, Track 2, ARQC). Defaults are conservative — see [`docs/threat-model.md`](docs/threat-model.md) for what we do and do not protect against.

If you found a vulnerability, please follow [`SECURITY.md`](SECURITY.md). Do not open a public issue.

CI gates that protect the surface:
- `:shared:checkKotlinAbi` — public-API drift fails the build.
- `detekt` `ForbiddenMethodCall` — no `println` / `Log.*` / `Thread.sleep` / `runBlocking` in `commonMain` or platform `*Main` source sets.
- PCI-safety regression tests pin that `toString()` masks PAN / Track 2 / ARQC.

Dependency hygiene runs weekly via Dependabot.

---

## Quickstart

### Android (Kotlin)

```kotlin
dependencies {
    implementation("io.github.a7asoft:nfcemv-core:0.1.0")
    implementation("io.github.a7asoft:nfcemv-reader-android:0.1.0")
    implementation("io.github.a7asoft:nfcemv-compose:0.1.0") // optional
}
```

```kotlin
// Pure parser
val card: EmvCard = EmvParser.parse(apduResponses)
println(card.pan.masked)   // "4111********1111"
println(card.brand)        // EmvBrand.Visa
println(card.expiry)       // YearMonth(2028, 12)

// Reader (suspend)
val reader = IsoDepReader(tag)
val result: Result<EmvCard> = reader.read()

// Flow
nfcAdapter.cardReadFlow(activity).collect { event ->
    when (event) {
        is ReaderEvent.Detected -> Unit
        is ReaderEvent.Read     -> event.card
        is ReaderEvent.Failed   -> event.error
    }
}

// Compose
@Composable
fun PayScreen() {
    val state = rememberNfcReader()
    when (state) {
        ReaderState.Idle      -> Text("Tap a card")
        ReaderState.Reading   -> CircularProgressIndicator()
        is ReaderState.Read   -> Text(state.card.pan.masked)
        is ReaderState.Error  -> Text("Failed: ${state.error.message}")
    }
}
```

### iOS (Swift)

The `EmvReader` Swift Package lives at `ios/Package.swift` and consumes the
KMP `Shared.xcframework` via a local `binaryTarget`. Build the framework
once, then open the package in Xcode (or run `xcodebuild` headlessly):

```bash
./gradlew :shared:assembleSharedReleaseXCFramework
open ios/Package.swift
```

```swift
import EmvReader

let reader = EmvReader()
for await state in reader.read() {
    switch state {
    case .tagDetected:           print("tag detected")
    case .selectingPpse:         print("PPSE")
    case .selectingAid(let aid): print("AID \(aid)")
    case .readingRecords:        print("reading records")
    case .done(let card):        print("done — brand \(card.brand), AID \(card.aid)")
    case .failed(let error):     print("error: \(error)")
    }
}
```

`EmvReader().read()` returns an `AsyncStream<ReaderState>` that drives the
EMV Book 1 §11–12 contactless flow (PPSE → SELECT AID → GPO → READ RECORD
→ `EmvParser.parse`). Each stage emits a discrete state; terminal states
are `.done(EmvCard)` and `.failed(ReaderError)`.

NFC reader sessions must be initiated by user gesture (e.g., a button
tap). Cancelling the consuming `Task` invalidates the underlying
`NFCTagReaderSession`.

#### iOS setup checklist

1. Add `Near Field Communication Tag Reading` capability in Xcode.
2. Add to `Info.plist`:
   ```xml
   <key>NFCReaderUsageDescription</key>
   <string>Used to read your contactless card.</string>
   <key>com.apple.developer.nfc.readersession.iso7816.select-identifiers</key>
   <array>
     <string>A0000000031010</string>  <!-- Visa -->
     <string>A0000000041010</string>  <!-- Mastercard -->
     <string>A000000025010701</string><!-- Amex ExpressPay -->
   </array>
   ```
3. Real device required (NFC unavailable on simulator).

---

## Project layout

```
nfc-emv-toolkit/
├── shared/         ← KMP module: TLV parser, AID dir, brand detection (commonMain)
│   └── src/{commonMain, androidMain, iosMain, commonTest}
├── android/
│   └── reader/     ← IsoDep wrapper, Flow-based contactless read API (v0.2.0)
├── ios/            ← Swift Package: EmvReader (NFCTagReaderSession + AsyncStream) (v0.2.0)
│   └── Sources/EmvReader, Tests/EmvReaderTests
├── composeApp/     ← Android sample app (Compose) consuming the lib
├── iosApp/         ← iOS sample app (SwiftUI) consuming the lib
└── docs/           ← threat model, recipes
```

## Supported targets

| Platform | Min version | Status |
|----------|------------|--------|
| Android  | API 21 (Lollipop) | WIP |
| iOS      | 14.0       | WIP |
| JVM (parser only) | 17 | WIP |

## Features (v0.1.0 milestone)

- [x] BER-TLV decoder ([details](./shared/README.md))
- [ ] BER-TLV encoder
- [ ] EMV tag dictionary (Book 3 + contactless kernels)
- [ ] AID directory (Visa, MC, Amex, JCB, Discover, UnionPay, more)
- [ ] Card brand detection (AID + BIN range)
- [ ] PAN extraction with PCI masking
- [ ] Track2-equivalent parser
- [ ] Expiry / cardholder / app label extraction
- [ ] Luhn validation
- [ ] APDU replay fixtures
- [ ] Android `IsoDepReader` (suspend + Flow)
- [ ] iOS `EmvReader` (async + AsyncSequence)
- [ ] Compose `rememberNfcReader`
- [ ] SwiftUI `EmvReaderView`
- [ ] Maven Central + SPM publish

## Roadmap

| Version | Goal |
|---------|------|
| **v0.1.0** | KMP core: TLV + AID + brand on both platforms |
| **v0.2.0** | Android reader + sample app |
| **v0.3.0** | iOS reader + sample app |
| **v0.4.0** | Compose + SwiftUI helpers + PCI-safe logging |
| **v0.5.0** | Maven Central + SPM stable release |
| **v1.0.0** | API freeze, binary compatibility, benchmark suite |

## Build

```bash
# Android sample
./gradlew :composeApp:assembleDebug

# Run KMP tests
./gradlew :shared:allTests
```

iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run on a real device (NFC requires hardware).

## iOS development

The iOS sample app (`iosApp/`) consumes the `EmvReader` Swift package via a local SPM reference (`../ios`). The `EmvReader` package's binary target points at `shared/build/XCFrameworks/release/Shared.xcframework` — produced by Gradle, not SPM.

**Branch-switch footgun:** `Shared.xcframework` is a binary artifact local to your working directory. After switching to a branch with a different Kotlin source tree (e.g. `main` ↔ `develop` ↔ feature branches), the on-disk framework may be stale. Re-run:

```bash
./gradlew :shared:assembleSharedReleaseXCFramework
```

after every branch switch before opening Xcode, or `iosApp` will link against the previous branch's symbols.

The `iosApp.xcodeproj` "Compile Kotlin Framework" build phase invokes this task automatically on Xcode build, but Xcode's incremental build detection may skip it. Manual invocation guarantees a fresh framework.

## Threat model

See [`docs/threat-model.md`](./docs/threat-model.md). TL;DR: assume the device is **not** a certified EMV terminal. Never ship this lib as a payment authorizer.

## Contributing

Issues and PRs welcome. See [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## License

[Apache 2.0](./LICENSE) — chosen over MIT for its explicit patent grant, which matters in payments-adjacent code.

---

<p align="center"><sub><code>$ exit 0  ·  read carefully, ship carefully</code></sub></p>
