# Issue #50 — `ios/Sources/EmvReader` `NFCTagReaderSession` wrapper plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Plan stays untracked.

**Goal:** Stand up a Swift Package at `ios/` with `EmvReader` target wrapping CoreNFC's `NFCTagReaderSession` + `NFCISO7816Tag`. Public API: `EmvReader().read() -> AsyncStream<ReaderState>`. Mirrors the v0.2.0 Android reader (#48) — same six-stage flow (PPSE → SELECT AID → GPO → READ RECORD → `EmvParser.parse`), same conceptual `ReaderState`/`ReaderError`/`IoReason` shapes — but **expressed as Swift-idiomatic enums** (decision: parallel types, NOT promotion to commonMain). Manual mapping at the boundary lives in a single `Mapping.swift` file.

**Architecture:**

```
ios/                                               (NEW SPM-managed Swift package root)
├─ Package.swift                                   (binaryTarget → XCFramework, EmvReader target, EmvReaderTests)
└─ Sources/EmvReader/
   ├─ EmvReader.swift                              (public class with read() -> AsyncStream<ReaderState>)
   ├─ ReaderState.swift                            (public Swift enum mirroring Kotlin sealed)
   ├─ ReaderError.swift                            (public Swift enum + IoReason)
   ├─ ApduCommands.swift                           (internal command builders + status-word helpers)
   ├─ Iso7816Transport.swift                       (internal protocol abstracting CoreNFC for testability)
   ├─ NFCISO7816TagTransport.swift                 (internal CoreNFC production impl — ONLY file importing CoreNFC)
   └─ Mapping.swift                                (internal Kotlin sealed ↔ Swift enum bridges)

ios/Tests/EmvReaderTests/
   ├─ EmvReaderTests.swift                         (happy paths × 3 brands + error paths + cancellation)
   ├─ FakeIso7816Transport.swift                   (test double recording transcripts)
   └─ Transcripts.swift                            (synthetic byte arrays — copies from Kotlin Transcripts.kt)
```

The KMP `:shared` module already exposes `Ppse.parse`, `Gpo.parse`, `Afl.parse`, `EmvParser.parse` through the iOS framework. This PR consumes them via the XCFramework — does NOT re-implement.

**Tech Stack:**
- Swift Package Manager (Package.swift, Swift 5.9+)
- Platform: iOS 14+ (CoreNFC ISO7816 support since iOS 13; iOS 14 baseline gives async/await + AsyncStream cleanly)
- KMP: `:shared` exports XCFramework (Gradle task `assembleSharedXCFramework`)
- CoreNFC: `NFCTagReaderSession`, `NFCISO7816Tag`, `NFCReaderError`
- No new Gradle dep; no new SwiftPM dep beyond local binaryTarget

**Project rules (HARD):**
- No `Co-Authored-By` trailer.
- Plan stays untracked.
- The ONLY file importing `CoreNFC` is `NFCISO7816TagTransport.swift` (mirrors `:android:reader`'s "only `IsoDepTransport.kt` imports `android.nfc.*`" rule).
- `ReaderState` and `ReaderError` in Swift are PARALLEL to the Kotlin types in `:android:reader` — accept the duplication for Swift idiom. Mapping lives in `Mapping.swift`.
- Tests are the contract — never weaken (CLAUDE.md §6.1).
- DocC on every public symbol (Swift's KDoc equivalent).
- iOS Simulator does NOT support CoreNFC. Tests run against `FakeIso7816Transport`, never against the real session.
- ABI gate (`:shared:checkKotlinAbi`) MUST report zero diff. This PR does not change `:shared`'s public surface — only consumes it.
- `iosApp/` (the existing Xcode app) is NOT modified. Sample integration is a separate scope (CLAUDE.md §2 `ios/Sources/EmvToolkitUI`).

---

## File Structure

**Modify:**
- `shared/build.gradle.kts` — add `XCFramework("Shared")` block so `assembleSharedXCFramework` produces the iOS-consumable artifact.
- `.github/workflows/ci.yml` `ios` job — add a step to build the XCFramework + run `swift test`.
- `README.md` — add `ios/Sources/EmvReader` to the architecture diagram + a "Try it on iOS" section pointing to the SPM package.
- `CHANGELOG.md` — `### Added — iOS contactless reader (#50)` block under `[Unreleased]`.
- `CONTRIBUTING.md` — note the new module in any module-overview section.

**Create:**
- `ios/Package.swift`
- `ios/Sources/EmvReader/EmvReader.swift`
- `ios/Sources/EmvReader/ReaderState.swift`
- `ios/Sources/EmvReader/ReaderError.swift`
- `ios/Sources/EmvReader/ApduCommands.swift`
- `ios/Sources/EmvReader/Iso7816Transport.swift`
- `ios/Sources/EmvReader/NFCISO7816TagTransport.swift`
- `ios/Sources/EmvReader/Mapping.swift`
- `ios/Tests/EmvReaderTests/EmvReaderTests.swift`
- `ios/Tests/EmvReaderTests/FakeIso7816Transport.swift`
- `ios/Tests/EmvReaderTests/Transcripts.swift`

**Don't modify:**
- `:shared` source files (only consume the existing public surface).
- `:android:reader` (untouched — Kotlin types stay where they are).
- `iosApp/` Xcode project (sample app integration is a separate issue).
- `shared/api/*` ABI dumps — no changes.

---

## Reference: hand-verified iOS API shape decisions

### Public Swift API surface (final)

```swift
// EmvReader.swift
public final class EmvReader {
    public init()
    public func read() -> AsyncStream<ReaderState>
}

// ReaderState.swift
public enum ReaderState: Sendable {
    case tagDetected
    case selectingPpse
    case selectingAid(Aid)        // Aid is the Kotlin type from :shared, exposed via XCFramework
    case readingRecords
    case done(EmvCard)            // EmvCard is the Kotlin type from :shared
    case failed(ReaderError)
}

// ReaderError.swift
public enum ReaderError: Sendable {
    case ioFailure(IoReason)
    case ppseUnsupported
    case noApplicationSelected
    case apduStatusError(sw1: UInt8, sw2: UInt8)
    case ppseRejected(PpseError)
    case gpoRejected(GpoError)
    case parseFailed(EmvCardError)
}

public enum IoReason: Sendable {
    case tagLost
    case timeout
    case generic
}
```

`Aid`, `EmvCard`, `EmvCardError`, `PpseError`, `GpoError` come from the XCFramework as ObjC-bridged Kotlin classes. The Swift consumer holds them by reference; equality works via `==` operator because Kotlin generates `equals`/`hashCode`.

### Sendable conformance

`AsyncStream<ReaderState>` requires `ReaderState: Sendable`. Swift enums whose associated values are all `Sendable` are auto-derived. `Aid`, `EmvCard`, `EmvCardError` etc. are bridged Kotlin classes — verify they're marked `Sendable` in the XCFramework export. If not, conditional `@unchecked Sendable` annotations needed (these Kotlin types are immutable so it's safe).

### iOS deployment target

- **iOS 14.0** — covers `async`/`await`, `AsyncStream`, `Task`, `withCheckedThrowingContinuation`, `NFCTagReaderSession.begin()`.
- All target devices (iPhone 7+) support CoreNFC reader sessions.

---

## Task 1: Verify branch + audit current state

```bash
git checkout develop
git pull --ff-only origin develop
git checkout -b feat/50-ios-reader
git status -sb
```

Expected: `## feat/50-ios-reader` plus `?? docs/superpowers/`.

```bash
ls -la ios/ 2>&1 | head -3       # Should: not exist
ls -la iosApp/ 2>&1 | head -3    # Should: existing scaffold
cat shared/build.gradle.kts | grep -A5 "iosArm64"
```

Confirm the audit findings: no `ios/` directory, `iosApp/` is the existing scaffold, `:shared` exports per-target frameworks (no XCFramework yet).

---

## Task 2: Add XCFramework export to `:shared`

**File:** `shared/build.gradle.kts`

Find the existing `kotlin { ... }` block. Locate the `listOf(iosArm64(), iosSimulatorArm64()).forEach { ... }` portion. Wrap it with `XCFramework("Shared")`:

```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

@OptIn(ExperimentalAbiValidation::class)
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    val xcf = XCFramework("Shared")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        // ... existing
    }

    abiValidation { enabled.set(true) }
}
```

The `XCFramework("Shared")` builder receives each per-target framework via `xcf.add(this)`. The Gradle plugin auto-creates the `assembleSharedXCFramework` (release) and `assembleSharedDebugXCFramework` tasks.

Add the missing import at top of file: `import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework`.

Verify task creation:
```bash
./gradlew :shared:tasks --all 2>&1 | grep -i xcframework | head -10
```

Expected output includes:
- `assembleSharedXCFramework`
- `assembleSharedDebugXCFramework`
- `assembleSharedReleaseXCFramework`

If absent, the KMP plugin version's API may differ; check `org.jetbrains.kotlin.multiplatform` documentation for 2.3.21.

Build to verify:
```bash
./gradlew :shared:assembleSharedReleaseXCFramework --stacktrace 2>&1 | tail -5
ls -la shared/build/XCFrameworks/release/Shared.xcframework 2>&1
```

Expected: `Shared.xcframework` directory with `Info.plist` + `ios-arm64/` + `ios-arm64_x86_64-simulator/` subdirectories.

If the path differs (some KMP versions output to `shared/build/xcframeworks/`), record the exact path — Task 4's `Package.swift` references it.

---

## Task 3: Create `ios/Package.swift`

**File:** `ios/Package.swift` (new)

```swift
// swift-tools-version:5.9
//
// EmvReader Swift package — wraps CoreNFC NFCTagReaderSession against the
// shared KMP parser exported as Shared.xcframework. Built and tested
// independently of iosApp/ (the sample SwiftUI app); iosApp consumes this
// package only when sample integration lands as a separate issue.
//
// Local development:
//   1. From repo root: ./gradlew :shared:assembleSharedReleaseXCFramework
//      (or :shared:assembleSharedDebugXCFramework for faster iteration).
//   2. Open ios/Package.swift in Xcode; the binaryTarget below resolves
//      to the freshly-built XCFramework.
//   3. Run tests: swift test --package-path ios
//      (or use Xcode's test runner against the EmvReaderTests target).

import PackageDescription

let package = Package(
    name: "EmvReader",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "EmvReader", targets: ["EmvReader"]),
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            path: "../shared/build/XCFrameworks/release/Shared.xcframework"
        ),
        .target(
            name: "EmvReader",
            dependencies: ["Shared"],
            path: "Sources/EmvReader"
        ),
        .testTarget(
            name: "EmvReaderTests",
            dependencies: ["EmvReader"],
            path: "Tests/EmvReaderTests"
        ),
    ]
)
```

Notes:
- `binaryTarget` path is relative to `Package.swift` location (`ios/`).
- If Task 2 emitted XCFrameworks at a different path (e.g., lowercase `xcframeworks/`), update accordingly. Same if you choose debug vs release default.
- The `Shared` target name MUST match the XCFramework's framework name. If KMP names it differently (verify by inspecting the .xcframework's Info.plist), update.

Smoke test:
```bash
swift package --package-path ios resolve 2>&1 | tail -5
```

Expected: `Shared` resolves to local binaryTarget, no network fetch.

---

## Task 4: Create `Sources/EmvReader/` directory + Swift files

### Task 4a — `ReaderState.swift`

**File:** `ios/Sources/EmvReader/ReaderState.swift`

```swift
import Shared

/// Discrete states emitted by ``EmvReader/read()-AsyncStream`` during a
/// contactless EMV read.
///
/// Mirrors the Kotlin `io.github.a7asoft.nfcemv.reader.ReaderState`
/// catalogue from the Android reader (#48). Variants are duplicated as
/// Swift `enum` cases for idiomatic `switch` exhaustiveness; the
/// associated Kotlin types (``Aid``, ``EmvCard``) come from the shared
/// XCFramework.
public enum ReaderState: Sendable {
    /// The Android equivalent of `TagDetected` — emitted right after the
    /// transport's `connect()` succeeds.
    case tagDetected

    /// The reader is sending the PPSE SELECT command
    /// (`2PAY.SYS.DDF01`) to the card. Emitted before the APDU goes out.
    case selectingPpse

    /// PPSE returned a list of applications and the reader has chosen
    /// the lowest-priority entry; about to send SELECT-by-AID.
    case selectingAid(Aid)

    /// SELECT-AID succeeded. The reader is sending GPO and the
    /// subsequent READ RECORD APDUs derived from the AFL.
    case readingRecords

    /// Terminal state on success. The accumulated READ RECORD payloads
    /// were composed into an ``EmvCard`` by ``EmvParser/parse(_:)``.
    case done(EmvCard)

    /// Terminal state on failure. ``ReaderError`` carries a structured
    /// reason for the failure.
    case failed(ReaderError)
}
```

`@unchecked Sendable` may be needed on the `Aid`/`EmvCard` associated values if Kotlin's bridged classes don't conform to `Sendable` in the XCFramework export. Verify when running the build; add the annotation if Swift errors. The Kotlin types are immutable so the `@unchecked` is sound.

### Task 4b — `ReaderError.swift`

**File:** `ios/Sources/EmvReader/ReaderError.swift`

```swift
import Shared

/// Reasons ``EmvReader/read()-AsyncStream`` can terminate with
/// ``ReaderState/failed(_:)``.
///
/// Mirrors the Kotlin `io.github.a7asoft.nfcemv.reader.ReaderError`
/// catalogue. Structural metadata only — never embeds raw card bytes.
/// (CLAUDE.md §5.8.)
public enum ReaderError: Sendable {
    /// CoreNFC's `NFCISO7816Tag` connection or transceive raised an
    /// I/O error. ``IoReason`` distinguishes tag-loss vs timeout vs
    /// generic failure without surfacing the underlying `Error`'s
    /// `localizedDescription` (which can carry implementation noise).
    case ioFailure(IoReason)

    /// PPSE returned `6A 82` (file not found) — the card has no
    /// contactless application directory. PSE (`1PAY.SYS.DDF01`)
    /// fallback is out of scope for v0.2.x.
    case ppseUnsupported

    /// PPSE was structurally valid but listed zero applications.
    case noApplicationSelected

    /// A card APDU returned a non-`90 00` status word at a stage
    /// other than PPSE-not-found. The status-word bytes are
    /// non-sensitive (public protocol).
    case apduStatusError(sw1: UInt8, sw2: UInt8)

    /// PPSE response body was syntactically a `90 00` success but the
    /// FCI structure failed ``Ppse/parse(_:)``.
    case ppseRejected(PpseError)

    /// GPO response body was `90 00` but failed ``Gpo/parse(_:)``.
    case gpoRejected(GpoError)

    /// READ RECORD records were collected but ``EmvParser/parse(_:)``
    /// surfaced an error. The wrapped ``EmvCardError`` distinguishes
    /// PAN/Track 2/expiry/cardholder failures.
    case parseFailed(EmvCardError)
}

/// Categorical I/O failure reason for ``ReaderError/ioFailure(_:)``.
///
/// Mirrors the Kotlin `IoReason` enum from the Android reader. Maps
/// CoreNFC's `NFCReaderError` codes to a small, stable set of
/// platform-neutral categories so consumers don't have to depend on
/// CoreNFC error code semantics.
public enum IoReason: Sendable {
    /// Maps to `NFCReaderError.readerTransceiveErrorTagConnectionLost`
    /// or any session invalidation caused by the tag leaving the
    /// reader's RF field.
    case tagLost

    /// Maps to session timeout invalidation
    /// (`NFCReaderError.readerSessionInvalidationErrorSessionTimeout`).
    case timeout

    /// Catch-all for any other CoreNFC error.
    case generic
}
```

### Task 4c — `Iso7816Transport.swift`

**File:** `ios/Sources/EmvReader/Iso7816Transport.swift`

```swift
import Foundation

/// Stateful APDU exchange channel.
///
/// Mirrors the Kotlin `ApduTransport` interface. Implementations wrap
/// `NFCISO7816Tag` in production and a fake recorder in tests.
///
/// Thread-safety contract:
/// - ``connect()`` and ``transceive(_:)`` are called sequentially from
///   the same `Task` (the read flow's orchestration coroutine).
/// - ``close()`` may be called from a different `Task` than the one
///   doing the I/O, e.g. during `AsyncStream` cancellation.
///   Implementations MUST tolerate this; CoreNFC's
///   `NFCTagReaderSession.invalidate()` is documented as thread-safe.
internal protocol Iso7816Transport: Sendable {
    func connect() async throws
    func transceive(_ command: Data) async throws -> Data
    func close() async
}
```

### Task 4d — `ApduCommands.swift`

**File:** `ios/Sources/EmvReader/ApduCommands.swift`

```swift
import Foundation
import Shared

/// APDU command builders + status-word helpers per ISO/IEC 7816-4 §5.
///
/// Mirrors the Kotlin `ApduCommands` object. All commands target
/// contactless EMV applications; values are spec-mandated bit patterns,
/// not magic numbers.
internal enum ApduCommands {
    /// SELECT 2PAY.SYS.DDF01 — discovers the contactless application
    /// directory (PPSE) per EMV Book 1 §11.3.4.
    ///
    /// `00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00`
    ///
    /// Fresh `Data` per access; callers cannot mutate the underlying
    /// bytes (mirrors Kotlin's `get() = byteArrayOf(...)` defensive-copy
    /// pattern).
    static var ppseSelect: Data {
        Data([
            0x00, 0xA4, 0x04, 0x00, 0x0E,
            0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
            0x00,
        ])
    }

    /// GET PROCESSING OPTIONS with empty PDOL data (`83 00`).
    ///
    /// `80 A8 00 00 02 83 00 00`
    static var gpoDefault: Data {
        Data([0x80, 0xA8, 0x00, 0x00, 0x02, 0x83, 0x00, 0x00])
    }

    /// Build a SELECT-by-AID command per ISO/IEC 7816-4 §5.4.1.
    static func selectAid(_ aid: Aid) -> Data {
        let aidBytes = Mapping.aidBytes(aid)
        var command = Data(count: 5 + aidBytes.count + 1)
        command[0] = 0x00
        command[1] = 0xA4
        command[2] = 0x04
        command[3] = 0x00
        command[4] = UInt8(aidBytes.count)
        command.replaceSubrange(5..<(5 + aidBytes.count), with: aidBytes)
        // command[last] = 0x00 by Data(count:) default — Le=00.
        return command
    }

    /// Build a READ RECORD command per ISO/IEC 7816-4 §7.3.3.
    ///
    /// `00 B2 [recordNumber] [(sfi << 3) | 0x04] 00`
    /// Valid `recordNumber` range: 1..254 (per ISO 7816-4 §7.3.3, `0xFF`
    /// is RFU). Valid `sfi` range: 1..30.
    static func readRecord(recordNumber: UInt8, sfi: UInt8) -> Data {
        precondition(recordNumber >= 1 && recordNumber <= 254,
                     "recordNumber out of range: \(recordNumber)")
        precondition(sfi >= 1 && sfi <= 30,
                     "sfi out of range: \(sfi)")
        let p2: UInt8 = (sfi << 3) | 0x04
        return Data([0x00, 0xB2, recordNumber, p2, 0x00])
    }

    /// Returns true if the response ends with `90 00` (success).
    static func isSuccess(_ response: Data) -> Bool {
        guard response.count >= 2 else { return false }
        return response[response.count - 2] == 0x90 && response[response.count - 1] == 0x00
    }

    /// Strip the 2-byte status word from the response.
    static func dataField(_ response: Data) -> Data {
        precondition(response.count >= 2)
        return response.prefix(response.count - 2)
    }

    /// Returns the (sw1, sw2) byte pair from the tail of the response.
    static func statusWord(_ response: Data) -> (UInt8, UInt8) {
        precondition(response.count >= 2)
        return (response[response.count - 2], response[response.count - 1])
    }
}
```

`Mapping.aidBytes(_:)` is defined in Task 4f.

### Task 4e — `NFCISO7816TagTransport.swift` (production)

**File:** `ios/Sources/EmvReader/NFCISO7816TagTransport.swift`

```swift
import CoreNFC
import Foundation

/// Production ``Iso7816Transport`` backed by CoreNFC's
/// `NFCTagReaderSession` + `NFCISO7816Tag`.
///
/// The ONLY file in this module that imports `CoreNFC`. All other
/// reader logic operates against the protocol abstraction for
/// testability.
///
/// Lifecycle:
/// 1. ``connect()`` opens an `NFCTagReaderSession`, waits for
///    `tagReaderSession(_:didDetect:)` delegate, picks the first
///    `NFCISO7816Tag`, and connects.
/// 2. ``transceive(_:)`` sends an APDU via `sendCommand(apdu:)` and
///    awaits the response continuation.
/// 3. ``close()`` invalidates the session.
///
/// The session can be initiated only from a foreground app context
/// triggered by user gesture per Apple's NFC reader rules. Library
/// consumers must ensure ``EmvReader/read()-AsyncStream`` is invoked
/// in response to a UI button / similar.
internal final class NFCISO7816TagTransport: NSObject, Iso7816Transport {
    private var session: NFCTagReaderSession?
    private var connectedTag: NFCISO7816Tag?
    private var connectContinuation: CheckedContinuation<Void, Error>?
    private let lock = NSLock()

    func connect() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            lock.lock()
            self.connectContinuation = continuation
            lock.unlock()
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                let session = NFCTagReaderSession(
                    pollingOption: [.iso14443],
                    delegate: self,
                    queue: nil
                )
                session?.alertMessage = "Hold your card near the device"
                self.session = session
                session?.begin()
            }
        }
    }

    func transceive(_ command: Data) async throws -> Data {
        guard let tag = connectedTag else {
            throw NFCReaderError(.readerSessionInvalidationErrorSessionTimeout)
        }
        guard let apdu = NFCISO7816APDU(data: command) else {
            throw NFCReaderError(.readerErrorInvalidParameter)
        }
        return try await withCheckedThrowingContinuation { continuation in
            tag.sendCommand(apdu: apdu) { responseData, sw1, sw2, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                var response = Data(responseData)
                response.append(sw1)
                response.append(sw2)
                continuation.resume(returning: response)
            }
        }
    }

    func close() async {
        lock.lock()
        let s = self.session
        self.session = nil
        self.connectedTag = nil
        lock.unlock()
        s?.invalidate()
    }

    private func resumeConnect(_ result: Result<Void, Error>) {
        lock.lock()
        let cont = self.connectContinuation
        self.connectContinuation = nil
        lock.unlock()
        cont?.resume(with: result)
    }
}

// MARK: - NFCTagReaderSessionDelegate

extension NFCISO7816TagTransport: NFCTagReaderSessionDelegate {
    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let first = tags.first, case let .iso7816(tag) = first else {
            session.invalidate(errorMessage: "Tag is not ISO 7816 — only contactless EMV cards are supported.")
            return
        }
        session.connect(to: first) { [weak self] error in
            guard let self = self else { return }
            if let error = error {
                self.resumeConnect(.failure(error))
                return
            }
            self.connectedTag = tag
            self.resumeConnect(.success(()))
        }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        // Only resume the connect continuation if it's still pending.
        // If the session invalidated AFTER connect succeeded, the
        // transceive continuation will surface the error.
        resumeConnect(.failure(error))
    }
}
```

Notes:
- `NSLock` guards `connectContinuation` against multiple delegate-thread callbacks.
- `connect()` resumes ONCE — guaranteed by `lock` + `connectContinuation = nil` immediately on resume.
- `close()` invalidates the session and clears references — safe to call after success or during cancellation.

### Task 4f — `Mapping.swift`

**File:** `ios/Sources/EmvReader/Mapping.swift`

```swift
import Foundation
import Shared

/// Bridges between Kotlin sealed types (exposed via `Shared.xcframework`)
/// and Swift enums in this module. Centralising the mapping here keeps
/// runtime `is` checks against ObjC-bridged Kotlin classes out of
/// `EmvReader.swift`'s read flow.
internal enum Mapping {

    /// Convert an `Aid` value class (Kotlin `@JvmInline value class`)
    /// into raw bytes for APDU command building. The Kotlin side
    /// already exposes `Aid.toBytes(): ByteArray` (added in #48 as a
    /// public helper); we adapt it to Swift `Data`.
    static func aidBytes(_ aid: Aid) -> Data {
        let kotlinBytes = aid.toBytes()
        return kotlinBytes.toData()
    }

    /// Run `Ppse.parse` on response bytes and adapt the result.
    static func parsePpse(_ bytes: Data) -> Result<Ppse, PpseError> {
        let kotlinBytes = bytes.toKotlinByteArray()
        let result = PpseCompanion.shared.parse(bytes: kotlinBytes)
        if let ok = result as? PpseResultOk {
            return .success(ok.ppse)
        }
        if let err = result as? PpseResultErr {
            return .failure(err.error)
        }
        fatalError("Unhandled PpseResult variant — XCFramework export likely changed")
    }

    /// Run `Gpo.parse` on response bytes and adapt the result.
    static func parseGpo(_ bytes: Data) -> Result<Gpo, GpoError> {
        let kotlinBytes = bytes.toKotlinByteArray()
        let result = GpoCompanion.shared.parse(bytes: kotlinBytes)
        if let ok = result as? GpoResultOk {
            return .success(ok.gpo)
        }
        if let err = result as? GpoResultErr {
            return .failure(err.error)
        }
        fatalError("Unhandled GpoResult variant — XCFramework export likely changed")
    }

    /// Run `EmvParser.parse` on accumulated READ RECORD bytes.
    static func parseEmvCard(_ records: [Data]) -> Result<EmvCard, EmvCardError> {
        let kotlinList = records.map { $0.toKotlinByteArray() } as NSArray
        let result = EmvParser.shared.parse(apduResponses: kotlinList as! [Any])
        if let ok = result as? EmvCardResultOk {
            return .success(ok.card)
        }
        if let err = result as? EmvCardResultErr {
            return .failure(err.error)
        }
        fatalError("Unhandled EmvCardResult variant — XCFramework export likely changed")
    }

    /// Map a CoreNFC `Error` to ``IoReason``.
    static func ioReason(from error: Error) -> IoReason {
        guard let nfcError = error as? NFCReaderError else { return .generic }
        switch nfcError.code {
        case .readerTransceiveErrorTagConnectionLost:
            return .tagLost
        case .readerSessionInvalidationErrorSessionTimeout:
            return .timeout
        default:
            return .generic
        }
    }
}

// MARK: - Bridges between Foundation Data and Kotlin ByteArray

extension Data {
    /// `Data` → `KotlinByteArray` for APDU input to bridged Kotlin parsers.
    func toKotlinByteArray() -> KotlinByteArray {
        let bytes = KotlinByteArray(size: Int32(count))
        for (i, byte) in self.enumerated() {
            bytes.set(index: Int32(i), value: Int8(bitPattern: byte))
        }
        return bytes
    }
}

extension KotlinByteArray {
    /// `KotlinByteArray` → Foundation `Data`.
    func toData() -> Data {
        var data = Data(count: Int(self.size))
        for i in 0..<Int(self.size) {
            data[i] = UInt8(bitPattern: self.get(index: Int32(i)))
        }
        return data
    }
}
```

Notes:
- The exact Swift import names (`PpseCompanion.shared.parse`, `EmvParser.shared.parse`) depend on the XCFramework's ObjC bridging. KMP renames Kotlin `companion object` members to `XxxCompanion.shared` when bridged. The `apduResponses: NSArray` cast may need adjustment based on the actual generated header.
- Verify by inspecting `Shared.xcframework/.../Headers/Shared.h` after Task 2 — find the actual generated method signatures and adjust if the prediction here is off.
- The `fatalError` on unhandled sealed variants is the Swift idiom for "type system says this is exhaustive". Kotlin sealed types bridged to ObjC don't give Swift exhaustive checking; runtime `is` plus `fatalError` is the established pattern.

### Task 4g — `EmvReader.swift`

**File:** `ios/Sources/EmvReader/EmvReader.swift`

```swift
import Foundation
import Shared

/// Top-level Swift entry point for reading a contactless EMV card on iOS.
///
/// Wraps CoreNFC's `NFCTagReaderSession` and orchestrates the
/// EMV Book 1 §11–12 contactless flow:
/// PPSE → SELECT AID → GPO → READ RECORD → ``EmvParser/parse(_:)``.
/// Each stage emits a ``ReaderState`` over the returned `AsyncStream`.
/// Terminal states are ``ReaderState/done(_:)`` and
/// ``ReaderState/failed(_:)``.
///
/// Cancellation: cancelling the consuming `Task` cancels the stream.
/// The `onTermination` handler invalidates the underlying NFC session.
/// Note that cancellation is observed only BETWEEN APDU exchanges
/// (CoreNFC `sendCommand(apdu:)` callbacks are not cancellable).
public final class EmvReader {

    private let transportFactory: () -> Iso7816Transport

    /// Build an `EmvReader` backed by a fresh
    /// ``NFCISO7816TagTransport`` per `read()` call.
    public init() {
        self.transportFactory = { NFCISO7816TagTransport() }
    }

    /// Internal initializer for testing — caller supplies the
    /// transport directly.
    internal init(transportFactory: @escaping () -> Iso7816Transport) {
        self.transportFactory = transportFactory
    }

    /// Begin a contactless read. Returns an `AsyncStream` that emits
    /// state transitions as the read progresses, ending with either
    /// ``ReaderState/done(_:)`` or ``ReaderState/failed(_:)``.
    ///
    /// Apple's NFC reader rules require this to be invoked in response
    /// to a user gesture; library cannot enforce.
    public func read() -> AsyncStream<ReaderState> {
        let transport = transportFactory()
        return AsyncStream { continuation in
            let task = Task {
                await drive(transport: transport, continuation: continuation)
            }
            continuation.onTermination = { _ in
                task.cancel()
                Task { await transport.close() }
            }
        }
    }

    private func drive(
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async {
        do {
            try await transport.connect()
            continuation.yield(.tagDetected)

            guard let ppse = try await runPpse(transport: transport, continuation: continuation) else { return }
            guard let chosen = chooseApplication(ppse: ppse, continuation: continuation) else { return }
            continuation.yield(.selectingAid(chosen.aid))
            guard try await runSelectAid(aid: chosen.aid, transport: transport, continuation: continuation) else { return }

            continuation.yield(.readingRecords)
            guard let gpo = try await runGpo(transport: transport, continuation: continuation) else { return }
            let records = try await readAllRecords(afl: gpo.afl, transport: transport)
            yieldParseOutcome(records: records, continuation: continuation)
        } catch {
            continuation.yield(.failed(.ioFailure(Mapping.ioReason(from: error))))
        }
        continuation.finish()
        await transport.close()
    }

    private func runPpse(
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws -> Ppse? {
        continuation.yield(.selectingPpse)
        let response = try await transport.transceive(ApduCommands.ppseSelect)
        guard ApduCommands.isSuccess(response) else {
            let (sw1, sw2) = ApduCommands.statusWord(response)
            if sw1 == 0x6A && sw2 == 0x82 {
                continuation.yield(.failed(.ppseUnsupported))
            } else {
                continuation.yield(.failed(.apduStatusError(sw1: sw1, sw2: sw2)))
            }
            return nil
        }
        switch Mapping.parsePpse(ApduCommands.dataField(response)) {
        case .success(let ppse):
            return ppse
        case .failure(let error):
            continuation.yield(.failed(.ppseRejected(error)))
            return nil
        }
    }

    private func chooseApplication(
        ppse: Ppse,
        continuation: AsyncStream<ReaderState>.Continuation
    ) -> PpseEntry? {
        let entries = ppse.applications
        guard !entries.isEmpty else {
            continuation.yield(.failed(.noApplicationSelected))
            return nil
        }
        // Lowest priority value wins; nil priority sorts last.
        return entries.min(by: { ($0.priority?.intValue ?? Int.max) < ($1.priority?.intValue ?? Int.max) })
    }

    private func runSelectAid(
        aid: Aid,
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws -> Bool {
        let response = try await transport.transceive(ApduCommands.selectAid(aid))
        if ApduCommands.isSuccess(response) { return true }
        let (sw1, sw2) = ApduCommands.statusWord(response)
        continuation.yield(.failed(.apduStatusError(sw1: sw1, sw2: sw2)))
        return false
    }

    private func runGpo(
        transport: Iso7816Transport,
        continuation: AsyncStream<ReaderState>.Continuation
    ) async throws -> Gpo? {
        let response = try await transport.transceive(ApduCommands.gpoDefault)
        guard ApduCommands.isSuccess(response) else {
            let (sw1, sw2) = ApduCommands.statusWord(response)
            continuation.yield(.failed(.apduStatusError(sw1: sw1, sw2: sw2)))
            return nil
        }
        switch Mapping.parseGpo(ApduCommands.dataField(response)) {
        case .success(let gpo):
            return gpo
        case .failure(let error):
            continuation.yield(.failed(.gpoRejected(error)))
            return nil
        }
    }

    private func readAllRecords(afl: Afl, transport: Iso7816Transport) async throws -> [Data] {
        var collected: [Data] = []
        for entry in afl.entries {
            let first = entry.firstRecord.intValue
            let last = entry.lastRecord.intValue
            let sfi = entry.sfi.intValue
            for record in first...last {
                let cmd = ApduCommands.readRecord(recordNumber: UInt8(record), sfi: UInt8(sfi))
                let response = try await transport.transceive(cmd)
                // why: a non-9000 READ RECORD is silently skipped rather than aborting
                // the whole flow. Real cards sometimes advertise records that aren't
                // readable. EmvParser surfaces MissingRequiredTag downstream if essential
                // data was in a skipped record. Mirrors the Android reader's #48 contract.
                if ApduCommands.isSuccess(response) {
                    collected.append(ApduCommands.dataField(response))
                }
            }
        }
        return collected
    }

    private func yieldParseOutcome(
        records: [Data],
        continuation: AsyncStream<ReaderState>.Continuation
    ) {
        switch Mapping.parseEmvCard(records) {
        case .success(let card):
            continuation.yield(.done(card))
        case .failure(let error):
            continuation.yield(.failed(.parseFailed(error)))
        }
    }
}
```

Function size discipline: each `private func` ≤ ~25 lines. `drive()` is the orchestrator at ~20 lines; helpers further decomposed.

Verify: `swift build --package-path ios` should succeed. Compile errors most likely from the `Mapping.swift` Kotlin-bridge predictions — fix per actual XCFramework header.

---

## Task 5: Tests

### Task 5a — `FakeIso7816Transport.swift`

**File:** `ios/Tests/EmvReaderTests/FakeIso7816Transport.swift`

```swift
import Foundation
@testable import EmvReader

internal final class FakeIso7816Transport: Iso7816Transport, @unchecked Sendable {
    private let script: [(prefix: Data, response: Data)]
    private let lock = NSLock()
    private var index = 0
    private var connected = false
    var closed: Bool = false

    init(script: [(prefix: Data, response: Data)]) {
        self.script = script
    }

    func connect() async throws {
        lock.lock(); defer { lock.unlock() }
        connected = true
    }

    func transceive(_ command: Data) async throws -> Data {
        lock.lock(); defer { lock.unlock() }
        precondition(connected, "transport not connected")
        precondition(index < script.count, "no more scripted responses")
        let (expectedPrefix, response) = script[index]
        precondition(command.starts(with: expectedPrefix),
                     "command #\(index) does not start with expected prefix")
        index += 1
        return response
    }

    func close() async {
        lock.lock(); defer { lock.unlock() }
        closed = true
    }
}
```

### Task 5b — `Transcripts.swift`

**File:** `ios/Tests/EmvReaderTests/Transcripts.swift`

```swift
import Foundation

/// Hand-built synthetic byte sequences for the FakeIso7816Transport scripts.
///
/// Sources:
/// - PPSE / SELECT FCI / GPO byte arrays mirror those in the Android
///   reader's `Transcripts.kt` (#48). Keep in sync if either side changes.
/// - READ RECORD response bodies are byte-identical to the
///   `Fixtures.VISA_CLASSIC` / `MASTERCARD_PAYPASS` / `AMEX_EXPRESSPAY`
///   constants from `:shared:commonTest:Fixtures.kt` (#9), with `90 00`
///   appended to simulate the wire-level status word.
///
/// All PANs are public test ranges (Stripe / Adyen). Synthetic discretionary
/// fields. No ARQC / IAD / ATC tags. See CONTRIBUTING.md "Test fixture PANs".
internal enum Transcripts {
    static let visaPpseResponse: Data = Data([
        // ... copy verbatim from Android Transcripts.kt VISA_PPSE_RESPONSE
        // and append 0x90, 0x00 to terminate the wire APDU.
    ])
    // ... (same for visaSelectFciResponse, visaGpoResponse, visaRecord1Response)
    // ... (same for mastercardPpseResponse, etc.)
    // ... (same for amexPpseResponse, etc.)
}
```

The implementer copies the BYTE ARRAYS from `:android:reader/src/test/.../Transcripts.kt` verbatim. Same byte-count discipline as #45's `Fixtures.kt` — recompute outer template lengths if anything looks off.

### Task 5c — `EmvReaderTests.swift`

**File:** `ios/Tests/EmvReaderTests/EmvReaderTests.swift`

```swift
import XCTest
import Shared
@testable import EmvReader

final class EmvReaderTests: XCTestCase {
    func testReadEmitsVisaDoneStateWithExpectedEmvCard() async {
        let transport = FakeIso7816Transport(script: visaScript())
        let reader = EmvReader(transportFactory: { transport })
        let states = await collectStates(from: reader)
        XCTAssertTrue(transport.closed)
        guard case .done(let card) = states.last else {
            return XCTFail("Expected .done as terminal state, got \(String(describing: states.last))")
        }
        XCTAssertEqual(card.pan.unmasked(), "4111111111111111")
    }

    // ... + 11 more tests mirroring the Android ContactlessReaderTest cases
    //     (Mastercard, Amex, IoFailure(TagLost), IoFailure(Generic),
    //      ApduStatusError, PpseRejected, GpoRejected, NoApplicationSelected,
    //      multi-AID priority selection, silent-skip, real cancellation)

    private func collectStates(from reader: EmvReader) async -> [ReaderState] {
        var collected: [ReaderState] = []
        for await state in reader.read() {
            collected.append(state)
        }
        return collected
    }

    private func visaScript() -> [(Data, Data)] {
        return [
            (ApduCommands.ppseSelect, Transcripts.visaPpseResponse),
            // ... select AID, GPO, READ RECORD
        ]
    }
    // ... mastercardScript(), amexScript(), multiAidScript(), etc.
}
```

The implementer fills in the 12-test body mirroring the Android `ContactlessReaderTest` test names + assertions. Tests are async (XCTest supports `async` test methods since Xcode 13).

For the cancellation test (real `Task.cancel()`):
```swift
func testReadClosesTransportWhenCollectingTaskIsCancelled() async {
    let transport = FakeIso7816Transport(script: visaScript())
    let reader = EmvReader(transportFactory: { transport })

    let collected = LockedArray<ReaderState>()
    let task = Task {
        for await state in reader.read() {
            await collected.append(state)
        }
    }
    // Wait for at least one state then cancel.
    while await collected.count == 0 { await Task.yield() }
    task.cancel()
    _ = await task.value

    // onTermination ran transport.close async; give it a tick.
    try? await Task.sleep(nanoseconds: 100_000_000)
    XCTAssertTrue(transport.closed, "transport must close when collecting Task is cancelled")
}
```

`LockedArray` is a small actor or `@unchecked Sendable` wrapper for thread-safe collection from the consuming Task.

---

## Task 6: CI workflow update

**File:** `.github/workflows/ci.yml`

Find the `ios` job. Replace the existing single "Build iOS framework" + "Build iOS sample app" steps with:

```yaml
  ios:
    name: iOS build & test
    needs: lint
    runs-on: macos-14
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v6
      - uses: maxim-lobanov/setup-xcode@v1
        with:
          xcode-version: latest-stable
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v6

      - name: Build Shared XCFramework
        run: ./gradlew :shared:assembleSharedReleaseXCFramework --stacktrace

      - name: Test EmvReader Swift package
        run: swift test --package-path ios

      - name: Build iOS sample app
        run: |
          xcodebuild -project iosApp/iosApp.xcodeproj \
            -scheme iosApp \
            -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
            -configuration Debug \
            build | xcpretty || exit ${PIPESTATUS[0]}
```

Notes:
- `swift test --package-path ios` runs the EmvReaderTests target. CoreNFC linker hits at link time but tests don't invoke real CoreNFC paths (FakeIso7816Transport is the only path tested) so the link succeeds.
- The XCFramework build must precede the swift test — `Package.swift`'s binaryTarget is a local file path.
- Sample app build remains unchanged (it doesn't yet consume EmvReader).

---

## Task 7: Documentation

### Task 7a — README

Add to architecture diagram and module list:
```
ios/Sources/EmvReader  — NFCTagReaderSession wrapper, AsyncStream API for iOS consumers (NEW v0.2.0)
```

Add a "Try it on iOS" section near the existing Android section:
```markdown
## Try it on iOS

Build the Shared XCFramework, then open the Swift package in Xcode:

```bash
./gradlew :shared:assembleSharedReleaseXCFramework
open ios/Package.swift
```

The `EmvReader` library exposes:

```swift
import EmvReader

let reader = EmvReader()
for await state in reader.read() {
    switch state {
    case .tagDetected: print("tag detected")
    case .selectingPpse: print("PPSE")
    case .selectingAid(let aid): print("AID \(aid)")
    case .readingRecords: print("reading records")
    case .done(let card): print("PAN: \(card.pan)")
    case .failed(let error): print("error: \(error)")
    }
}
```

NFC reader sessions must be initiated by user gesture (e.g., a button tap). The consuming app must declare `NFCReaderUsageDescription` in `Info.plist` and include the `com.apple.developer.nfc.readersession.formats` entitlement with `TAG`.
```

### Task 7b — CHANGELOG

Under `## [Unreleased]`:

```markdown
### Added — iOS contactless reader (#50)
- New `ios/Sources/EmvReader` Swift Package providing `EmvReader.read() -> AsyncStream<ReaderState>`. Wraps CoreNFC's `NFCTagReaderSession` + `NFCISO7816Tag` and orchestrates the full EMV contactless read flow per Book 1 §11–12: PPSE → SELECT AID → GPO → READ RECORD → `EmvParser.parse`.
- Swift `ReaderState` and `ReaderError` enums mirror the Kotlin sealed types from the Android reader (#48). Parallel Swift idiom chosen over commonMain promotion to keep `:shared:commonMain` platform-neutral and give iOS consumers idiomatic `switch` exhaustiveness; manual mapping in `Mapping.swift`.
- New `XCFramework("Shared")` Gradle wiring in `:shared:build.gradle.kts` produces `Shared.xcframework` for the Swift package's `binaryTarget` to consume.
- CI: `ios` job now builds the XCFramework and runs `swift test --package-path ios` before the sample-app xcodebuild.
- Sample-app integration (Compose for Android / SwiftUI for iOS) remains out of scope; tracked separately as future-work issues per CLAUDE.md §2 architecture.
- Module boundary verified per CLAUDE.md §7: `EmvReader` depends only on `Shared` (XCFramework) and `CoreNFC`. The ONLY file importing `CoreNFC` is `NFCISO7816TagTransport.swift`. Does NOT depend on UIKit / SwiftUI / `iosApp/`.
```

### Task 7c — CONTRIBUTING.md

Add `ios/Sources/EmvReader` to any module-overview section, with a one-line note about the XCFramework prerequisite for local development.

---

## Task 8: Verify everything end-to-end

```bash
# Verify ABI gate unchanged (we only consume :shared, don't modify it)
./gradlew :shared:checkKotlinAbi 2>&1 | tail -5
# Expected: BUILD SUCCESSFUL with zero diff

# Verify XCFramework builds
./gradlew :shared:assembleSharedReleaseXCFramework --stacktrace 2>&1 | tail -5
ls -la shared/build/XCFrameworks/release/Shared.xcframework
# Expected: directory with Info.plist + per-target subdirs

# Verify Swift package compiles
swift build --package-path ios 2>&1 | tail -10
# Expected: Build complete!

# Verify tests pass
swift test --package-path ios 2>&1 | tail -20
# Expected: 12 tests passed

# Verify Android still green (cross-module sanity)
./gradlew :android:reader:check :shared:allTests ktlintCheck detekt 2>&1 | tail -5
# Expected: BUILD SUCCESSFUL on all
```

If `swift build` fails on Mapping.swift bridges, inspect the actual generated header:
```bash
find shared/build/XCFrameworks/release/Shared.xcframework -name "Shared.h" | head -1 | xargs head -50
```
Adjust `Mapping.swift` Kotlin-bridge calls to match the actual ObjC names.

If `swift test` fails on a transcript byte-count, recount per the established discipline (#9, #45). Fix the bytes; do NOT relax the assertion.

---

## Task 9: Commit + push + open PR

Stage explicitly:
```bash
git add shared/build.gradle.kts \
        ios/ \
        .github/workflows/ci.yml \
        README.md \
        CHANGELOG.md \
        CONTRIBUTING.md
git status -s
```

Verify NOTHING under `docs/superpowers/plans/` is staged. Verify NOTHING under `iosApp/` is staged.

Commit:
```bash
git commit -m "feat(ios-reader): EmvReader Swift package with AsyncStream read API (#50)"
```
NO `Co-Authored-By` trailer.

Push and open PR:
```bash
git push -u origin feat/50-ios-reader
gh pr create --base develop --head feat/50-ios-reader \
  --title "feat(ios-reader): EmvReader Swift package with AsyncStream read API (#50)" \
  --body "$(cat <<'EOF'
## Summary

- New `ios/Sources/EmvReader` Swift Package wrapping `CoreNFC.NFCTagReaderSession` + `NFCISO7816Tag` with `EmvReader().read() -> AsyncStream<ReaderState>`. Mirrors the v0.2.0 Android reader (#48) — same six-stage flow, same conceptual sealed catalogues — but as Swift-idiomatic `enum`s.
- `ReaderState`, `ReaderError`, `IoReason` defined as parallel Swift enums (decision: NOT promoted to commonMain). Manual mapping at the Kotlin/Swift boundary lives in `Mapping.swift`.
- New `XCFramework("Shared")` Gradle wiring in `:shared:build.gradle.kts` produces `Shared.xcframework` for the Swift package's `binaryTarget`.
- CI: `ios` job builds the XCFramework + runs `swift test`.
- `iosApp/` (the existing Xcode sample) is NOT modified — sample integration is a separate scope per CLAUDE.md §2 / §7.

## Module boundaries (CLAUDE.md §7)

- `ios/Sources/EmvReader` → `Shared` (XCFramework) + `CoreNFC`. NO dependency on UIKit, SwiftUI, `iosApp/`, or `:android:reader`.
- The ONLY file importing `CoreNFC` is `NFCISO7816TagTransport.swift`.

## ABI gate

`:shared:checkKotlinAbi` UNCHANGED — this PR consumes `:shared`'s public surface, does not modify it.

## Test coverage

- 12 integration tests in `EmvReaderTests` mirroring the Android `ContactlessReaderTest` cases (Visa / Mastercard / Amex happy paths, every `ReaderError` variant, real-cancellation, multi-AID priority, silent-skip on non-9000 READ RECORD).
- Tests run against `FakeIso7816Transport`; iOS Simulator does NOT support CoreNFC, so production `NFCISO7816TagTransport` is not exercised on CI.
- All transcripts hand-built per the same byte-count discipline as #9 / #45.

## Test plan

- [x] `./gradlew :shared:assembleSharedReleaseXCFramework` produces `Shared.xcframework`.
- [x] `swift build --package-path ios` succeeds.
- [x] `swift test --package-path ios` — 12 tests pass.
- [x] `./gradlew :shared:checkKotlinAbi` zero diff.
- [x] `./gradlew :android:reader:check :shared:allTests ktlintCheck detekt` green.
- [x] No `Co-Authored-By` trailer.
- [x] Plan files (`docs/superpowers/plans/`) untracked.

Closes #50.
EOF
)"
```

---

## Self-Review

| Issue requirement | Task |
|---|---|
| New `ios/Sources/EmvReader` Swift target | Tasks 3, 4 |
| Public API ≤ 5 types with DocC | Tasks 4a, 4b, 4g |
| `:shared:checkKotlinAbi` unaffected | Task 8 (verify zero diff) |
| Unit tests against protocol fake | Task 5 |
| CI: builds in existing iOS job | Task 6 |
| README / CHANGELOG / CONTRIBUTING | Task 7 |

**Decisions reified:**
- **Parallel Swift enums** (Option A from investigation) for `ReaderState` / `ReaderError` / `IoReason`. Kotlin sealed types stay in `:android:reader`.
- **SPM Package** with local `binaryTarget` pointing to Gradle-built XCFramework. Modern, friction-free.
- **Minimal scope** — `iosApp/` not modified. Sample integration deferred.

**Placeholders:** the Mapping.swift Kotlin-bridge calls are a best-prediction shape (`PpseCompanion.shared.parse(bytes:)`, `EmvParser.shared.parse(apduResponses:)`). Implementer must verify against the ACTUAL generated `Shared.h` and adjust before tests pass. Marked explicitly in Task 8.

**Type consistency:**
- `ReaderState` cases match Kotlin variant names lower-cased per Swift convention.
- `ReaderError` cases match exactly the post-#49-fix Kotlin variants (no `tagLost` top-level — folded into `ioFailure(.tagLost)`).
- Bridge helpers (`Data.toKotlinByteArray()`, `KotlinByteArray.toData()`) named consistently.

**Skips (justified, separate issues):**
- 1PAY.SYS.DDF01 (legacy PSE) fallback when PPSE absent.
- `61 XX` / `6C XX` retry semantics.
- AIP as typed value class.
- iOS sample app integration (`iosApp/` consuming EmvReader).
- DocC catalog generation (Dokka-equivalent for Swift) — defer.
- iOS UI layer (`ios/Sources/EmvToolkitUI` per CLAUDE.md §2 architecture).

**Risks accepted:**
- KMP/ObjC bridge predictions in `Mapping.swift` may need adjustment per actual generated header. Implementer fixes by inspection.
- iOS Simulator's lack of CoreNFC means production `NFCISO7816TagTransport` isn't exercised by automated tests. Real-device validation deferred to manual QA / sample-app issue.
- `Aid`, `EmvCard`, `EmvCardError` etc. crossing the `Sendable` boundary — `@unchecked Sendable` annotations may be needed where Kotlin's bridged classes don't auto-conform.
- Local `binaryTarget` path means contributors must run Gradle BEFORE opening the SPM package in Xcode. Documented in Package.swift comments + README.

**Commit count:** 1.
