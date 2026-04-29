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
    [WIP] v0.1.0 in development · API not stable yet                   │
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
  <img src="https://img.shields.io/badge/Status-WIP-orange?style=flat-square" />
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

```swift
// Package.swift
.package(url: "https://github.com/a7asoft/nfc-emv-toolkit", from: "0.1.0")
```

```swift
import EmvToolkit
import EmvReader

// Pure parser (KMP core, swift-bridged)
let card = try EmvParser.parse(apduResponses: responses)
print(card.pan.masked)   // "4111********1111"
print(card.brand)        // .visa

// Reader (async)
let reader = EmvReader()
let card = try await reader.read()

// AsyncSequence
for try await event in reader.events {
    switch event {
    case .detected:        print("card detected")
    case .read(let card):  print(card.pan.masked)
    case .failed(let err): print("error: \(err)")
    }
}

// SwiftUI
struct PayView: View {
    @StateObject var reader = EmvReaderState()
    var body: some View {
        EmvReaderView(state: reader)
    }
}
```

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
     <string>A000000025010801</string><!-- Amex -->
   </array>
   ```
3. Real device required (NFC unavailable on simulator).

---

## Project layout

```
nfc-emv-toolkit/
├── shared/         ← KMP module: TLV parser, AID dir, brand detection (commonMain)
│   └── src/{commonMain, androidMain, iosMain, commonTest}
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

## Threat model

See [`docs/threat-model.md`](./docs/threat-model.md). TL;DR: assume the device is **not** a certified EMV terminal. Never ship this lib as a payment authorizer.

## Contributing

Issues and PRs welcome. See [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## License

[Apache 2.0](./LICENSE) — chosen over MIT for its explicit patent grant, which matters in payments-adjacent code.

---

<p align="center"><sub><code>$ exit 0  ·  read carefully, ship carefully</code></sub></p>
