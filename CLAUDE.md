# CLAUDE.md

Engineering rules for this repository. Applies to humans and AI agents alike. Read before writing or reviewing code.

---

## 1. Mission and non-goals

**Mission**: a read-only, PCI-safe toolkit to parse contactless EMV card data on Android and iOS. KMP-shared parser, native readers per platform.

**Non-goals** (closed scope, do not breach):
- No EMV kernel certification
- No online authorization
- No ARQC verification or terminal cryptography
- No contact (chip-and-pin) reading
- Not a payment terminal SDK

Any change pushing past these boundaries must be rejected at PR review.

---

## 2. Architecture

```
┌───────────────────────────────────────────────────────┐
│   shared (KMP)                                        │
│   ┌───────────────────────────────────────────────┐   │
│   │ commonMain  — pure Kotlin, no I/O, no platform│   │
│   │   tlv/        BER-TLV codec                   │   │
│   │   emv/        tag dictionary, AID directory   │   │
│   │   brand/      brand detection                 │   │
│   │   extract/    PAN, track2, expiry, holder     │   │
│   │   validation/ Luhn, format checks             │   │
│   │   model/      EmvCard, Pan, Aid, Track2 ...   │   │
│   └───────────────────────────────────────────────┘   │
│   ┌────────────────────┐  ┌────────────────────────┐  │
│   │ androidMain        │  │ iosMain                │  │
│   │ (only if platform  │  │ @ObjCName, Swift sugar │  │
│   │  primitive needed) │  │                        │  │
│   └────────────────────┘  └────────────────────────┘  │
└───────────────────────────────────────────────────────┘
        ▲                                  ▲
        │                                  │
┌───────┴──────────┐              ┌────────┴────────────┐
│ android/reader   │              │ ios/Sources/        │
│ IsoDep wrapper   │              │ NFCTagReaderSession │
│ Flow API         │              │ AsyncSequence       │
└──────────────────┘              └─────────────────────┘
```

**Hard rules**:
- `commonMain` has **zero** I/O, threads, time, randomness, logging, or platform deps. Pure functions only.
- Side effects (NFC, logging, persistence) live in platform modules.
- Domain types live in `model/`. They never reach across into reader code; readers depend on `model/`, not the other way.
- Inner layers do not know outer layers exist.

---

## 3. Design patterns

### 3.1 Result over exceptions (for caller-facing APIs)
Public functions return `Result<T>` or a domain-specific sealed result. Internal helpers may throw; the boundary catches and wraps.

```kotlin
fun decode(input: ByteArray): Result<List<TlvNode>>      // public
private fun readTag(buf: ByteBuffer): Tag                // internal, may throw EofException
```

### 3.2 Sealed types for domain alternatives
No boolean flags, no string discriminators, no nullable trees. Use sealed interfaces.

```kotlin
sealed interface TlvNode {
    val tag: Tag
    data class Primitive(override val tag: Tag, val value: ByteArray) : TlvNode
    data class Constructed(override val tag: Tag, val children: List<TlvNode>) : TlvNode
}

sealed interface TlvError {
    data object UnexpectedEof : TlvError
    data class InvalidLength(val byte: Byte) : TlvError
    data class InvalidTag(val bytes: ByteArray) : TlvError
}
```

### 3.3 Value classes for primitives
Wrap raw types whenever they carry meaning. No `String pan`, no `ByteArray aid`.

```kotlin
@JvmInline value class Pan internal constructor(private val raw: String) {
    val masked: String get() = "${raw.take(6)}${"*".repeat(raw.length - 10)}${raw.takeLast(4)}"
    override fun toString() = masked
    fun unmasked(): String = raw
}
```

### 3.4 Composition over inheritance
No abstract base classes for code reuse. If two parsers share logic, extract a function or a small interface — do not parent them.

### 3.5 Single Abstract Method = function type
Don't introduce an interface when `(Input) -> Output` works.

### 3.6 No statics, no globals
Constants live in `companion object` only when truly invariant (e.g. EMV tag bytes). No global mutable state, ever. No singletons that hold state. `object` types only for stateless dispatchers.

---

## 4. SOLID — operationalised

| Principle | Concrete rule here |
|-----------|-------------------|
| **S**RP | A class has one reason to change. If you write "and" in the class doc, split it. |
| **O**CP | Add new card brands by adding entries to `AidDirectory`, not by editing `BrandDetector`. New parsers via new `TlvNode` subtypes, not flags. |
| **L**SP | Avoid inheritance entirely outside sealed hierarchies. If you do extend, the subtype must accept every input the supertype accepts. |
| **I**SP | Interfaces stay small. `Reader { suspend fun read(): Result<EmvCard> }` is fine. A 12-method interface is not. |
| **D**IP | Depend on interfaces in `model/`. Construct concrete types at the edge (Activity, ViewController, sample app). No service locators. |

---

## 5. Code style — non-negotiable

### 5.1 Function size
- Default: **≤ 15 lines**.
- Hard cap: **≤ 25 lines**. Past that, extract.
- A function does one named thing. The name is a verb; the body is the verb.

### 5.2 Cyclomatic
- Max 4 branches per function (if/when/loops combined).
- No nested `when` blocks. Lift inner ones into private functions.
- Early return over nested `if`. No "arrow code" pyramids.

### 5.3 Parameters
- Max 4 parameters. Past that, introduce a `data class` for the input.
- No boolean flags as parameters. Replace with two functions or a sealed type.

### 5.4 Variables
- `val` everywhere. `var` is a code smell that requires justification in the PR description.
- Mutable collections never escape a function.
- `lateinit` is forbidden in `commonMain` and discouraged elsewhere.

### 5.5 Null
- Public APIs return `T` or `T?`, never both via overloads.
- `!!` is forbidden. If a value cannot be null, prove it with the type system.
- `requireNotNull` is allowed at boundary checks with a clear error message.

### 5.6 Naming
- Domain words win over abbreviations: `pan`, `aid`, `tag` — fine, those are the spec terms. `pn`, `ad`, `t` — no.
- Functions are verbs (`decode`, `extract`, `resolve`). Nouns are types or properties.
- No `Manager`, `Helper`, `Util`, `Service` suffixes. They mean "I don't know what this does."
- No Hungarian notation. No `_underscore_prefix`.

### 5.7 Comments
- Default: **no comments**. Names and types do the talking.
- Allowed: a one-line `// why:` when the code looks wrong but is right (workaround for a spec quirk, hardware bug, EMV oddity).
- Forbidden: comments restating the code, commit-message-style commentary, TODO without an issue link.
- KDoc / DocC required on every public symbol. Document **why** and **contract**, never **how**.

### 5.8 Error messages
- Errors targeted at developers (TLV parse failure, etc.) include offset and context.
- Errors that could leak sensitive bytes never embed the value. Reference position only.

### 5.9 Logging
- No `println`, no `Log.d`, no `print`. Only via the injected `Logger` interface (added in v0.4.0).
- Sensitive types' `toString()` always masks. There is no "debug-only" override.

---

## 6. Testing rules

- Every public function: ≥ 1 happy-path test, ≥ 1 error-path test.
- Parser code: ≥ 1 property test (round-trip, idempotence, or invariant).
- Sensitive types: a test that constructs the value, runs `toString()`, and asserts the raw form does **not** appear.
- Fixtures live in `fixtures/`, hex-encoded, sanitized. Never commit a real PAN.
- Test names are sentences: `decode returns UnexpectedEof when length byte is missing`.
- One assertion concept per test. Group with `kotest`'s `should` blocks if needed.

---

## 7. Module boundaries

| Module | May depend on | May NOT depend on |
|--------|---------------|-------------------|
| `shared/commonMain` | stdlib, kotlinx (datetime, serialization, coroutines.core) | Android SDK, CoreNFC, JVM-only APIs, log frameworks |
| `shared/androidMain` | commonMain, Android SDK | iosMain, jvm-desktop |
| `shared/iosMain` | commonMain, platform.* (Foundation, CoreNFC) | androidMain |
| `android/reader` | shared, androidx.nfc | Compose, sample apps |
| `android/compose` | shared, android/reader, Compose | sample apps |
| `android/sample` | everything Android | nothing else depends on it |
| `ios/Sources/EmvToolkit` | shared (XCFramework), Foundation | UIKit, SwiftUI |
| `ios/Sources/EmvReader` | EmvToolkit, CoreNFC | SwiftUI |
| `ios/Sources/EmvToolkitUI` | EmvToolkit, EmvReader, SwiftUI | UIKit-specific |

A PR that crosses these boundaries is rejected.

---

## 8. Cross-platform parity

When adding to `commonMain`:
- Run the iOS framework export and verify Swift consumers see idiomatic names.
- Annotate Swift-facing types with `@ObjCName` where needed.
- Sealed classes exposed to Swift become enums in DocC — verify the exported header.
- Never expose `KotlinThrowable`, `KotlinArray`, raw `Continuation` — wrap in the iOS Swift module.

---

## 9. Forbidden

- `try { ... } catch (e: Exception) { throw e }` (the empty rethrow).
- `Thread.sleep`, `runBlocking` outside tests.
- Reflection (`KClass<*>::members`, etc.) in production paths.
- Annotation processors beyond `kotlinx-serialization` and `androidx.room` (we don't use Room here, so basically: just serialization).
- Service locators (`Koin.get<X>()` from inside the lib). DI happens at the edge.
- Mutable singletons.
- Magic numbers in hot paths. Hex tag constants belong in `EmvTag` companion.

---

## 10. PR checklist (auto-fail any unchecked)

- [ ] Public APIs documented (KDoc / DocC).
- [ ] No function > 25 lines without justification in PR.
- [ ] No `var` introduced without justification in PR.
- [ ] PCI-safety tests pass for any change touching `Pan`, `Track2`, or logging.
- [ ] `./gradlew ktlintCheck detekt :shared:allTests` clean.
- [ ] If new public API on commonMain → binary-compat dump updated.
- [ ] If new public API exposed to iOS → manual smoke test on Swift consumer.
- [ ] No new dependency without an entry in PR description justifying it.

---

## 11. When in doubt

> **Read the spec, not the room.**

EMV is precise. Defer to ISO/IEC 7816-4, EMV Books 3 and 4, and the contactless kernel specs. Do not infer behavior from a single card or a single existing library.
