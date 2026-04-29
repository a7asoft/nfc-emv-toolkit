# shared — KMP core

Pure-Kotlin core for the nfc-emv-toolkit. Lives in `commonMain` and ships with no platform dependencies, so the same code runs on Android, iOS (via XCFramework), and the JVM.

## Modules in this folder

| Package | Purpose |
|---------|---------|
| `io.github.a7asoft.nfcemv.tlv` | BER-TLV decoder + encoder (this milestone) |
| `io.github.a7asoft.nfcemv.tlv.internal` | Reader cursor, tag/length/node decoders, padding skipper. Internal — do not depend on these symbols. |
| `io.github.a7asoft.nfcemv.validation` | Luhn check (this milestone) |
| `io.github.a7asoft.nfcemv.extract` | PAN, Track 2, ServiceCode (this milestone) |
| `io.github.a7asoft.nfcemv.emv` | EMV tag dictionary (this milestone) |

Future packages (later issues): `brand` (AID + BIN brand resolution), `extract` (PAN, Track2, expiry), `validation` (Luhn, format checks).

## BER-TLV decoder — quickstart

```kotlin
import io.github.a7asoft.nfcemv.tlv.*

val response: ByteArray = /* APDU response data, SW1 SW2 already stripped */

when (val result = TlvDecoder.parse(response)) {
    is TlvParseResult.Ok  -> result.tlvs.forEach(::printTlv)
    is TlvParseResult.Err -> println("Parse failed at offset ${result.error.offset}: ${result.error}")
}

fun printTlv(tlv: Tlv): Unit = when (tlv) {
    is Tlv.Primitive   -> println("${tlv.tag}: ${tlv.length} bytes")
    is Tlv.Constructed -> { println("${tlv.tag}: ${tlv.children.size} children"); tlv.children.forEach(::printTlv) }
}
```

If you prefer try / catch:

```kotlin
val tlvs: List<Tlv> = TlvDecoder.parseOrThrow(response)
```

Both APIs share the same error catalogue (`TlvError`).

## Options

Defaults are tuned for real EMV cards:

| Option | Default | Effect |
|--------|---------|--------|
| `strictness` | `Strictness.Strict` | Reject non-minimal multi-byte tag continuation (first byte = `0x80`) and non-minimal long-form length encodings |
| `paddingPolicy` | `PaddingPolicy.Tolerated` | Skip `0x00` bytes between top-level / constructed children, per EMV Specification Update Bulletin 69 |
| `maxTagBytes` | `4` | Hard cap on multi-byte tag length (EMV uses ≤ 2 in practice) |
| `maxDepth` | `16` | Hard cap on constructed-tag nesting |

Override via `TlvOptions(...)`:

```kotlin
val tlvs = TlvDecoder.parseOrThrow(input, TlvOptions(strictness = Strictness.Lenient))
```

## Error catalogue

All variants implement `TlvError` and carry the byte offset where the problem was detected. None embed value bytes — error reporting is PCI-safe by construction.

| Variant | Cause |
|---------|-------|
| `UnexpectedEof` | Input ended before a tag, length, or value finished |
| `IndefiniteLengthForbidden` | Length octet was `0x80` (forbidden in EMV) |
| `InvalidLengthOctet` | First length octet was reserved or unsupported (`0x85`–`0xFF`) |
| `LengthOverflow` | Long-form length parsed structurally but decoded value exceeds `Int.MAX_VALUE` |
| `IncompleteTag` | Multi-byte tag started but not terminated before EOF |
| `TagTooLong` | Multi-byte tag exceeded `maxTagBytes` |
| `NonMinimalTagEncoding` | Strict mode: first continuation byte's seven low bits were all zero (`0x00` or `0x80`) per ISO/IEC 8825-1 §8.1.2.4.2(c) |
| `NonMinimalLengthEncoding` | Strict mode: long-form length used more octets than necessary |
| `ChildrenLengthMismatch` | Constructed value's children consumed a different number of bytes than declared |
| `MaxDepthExceeded` | Constructed nesting exceeded `maxDepth` |

## Important contract

The decoder operates on **the data field of a card response, with SW1 SW2 already stripped**.

Passing raw APDU output (e.g. `... 90 00`) does **not** raise an error — `90 00` decodes as a valid empty primitive (`Primitive(tag=0x90, value=[])`). Detection of unstripped status words must happen at the transport / APDU layer.

## EMV deviation from X.690

ISO/IEC 8825-1 §8.1.2.4 mandates that tag numbers ≤ 30 use the short single-byte form. EMV deliberately violates this (e.g. `9F02` is tag number 2 in long form for namespace separation across card schemes). This decoder follows EMV practice and accepts those tags even in strict mode.

The X.690 leading-zero rule (first continuation byte ≠ `0x80`) **is** enforced in strict mode — that one is universal.

## BER-TLV encoder — quickstart

Re-emit a parsed `Tlv` tree (or a list of trees) as DER-canonical BER-TLV bytes:

```kotlin
import io.github.a7asoft.nfcemv.tlv.*

val parsed = TlvDecoder.parse(response)
if (parsed is TlvParseResult.Ok) {
    val reEmitted: ByteArray = TlvEncoder.encode(parsed.tlvs)
    forwardToBackend(reEmitted)
}
```

The encoder takes no options. Output is always X.690 DER-canonical (definite length, minimal length octets). If the original card response used non-minimal length octets (uncommon), the re-emitted bytes will be shorter but **semantically identical** — a second `TlvDecoder.parse` produces the same `Tlv` tree.

Defense-in-depth: a hardcoded `MAX_DEPTH = 64` guard fires `IllegalStateException` on caller-built trees that exceed it (the decoder's `TlvOptions.maxDepth` defaults to 16; the encoder's higher cap accommodates any tree the decoder accepts).

## Validation

`String.isValidLuhn(): Boolean` — Luhn / mod-10 checksum per ISO/IEC 7812-1 Annex B. Predicate; never throws. Use to gate untrusted PAN inputs before constructing `Pan` (#5 once it ships).

```kotlin
import io.github.a7asoft.nfcemv.validation.isValidLuhn

if ("4111111111111111".isValidLuhn()) {
    // proceed
}
```

## Extract

`Pan` is a `@JvmInline value class` that wraps a primary account number and keeps raw digits off `toString()`, stack traces, and string interpolation. Construction goes through a typed factory; the primary constructor is `internal`.

```kotlin
import io.github.a7asoft.nfcemv.extract.Pan
import io.github.a7asoft.nfcemv.extract.PanResult

// Result-driven control flow
when (val result = Pan.parse(input)) {
    is PanResult.Ok  -> use(result.pan)
    is PanResult.Err -> reportRefusal(result.error)
}

// Or, throw-on-error
val pan = Pan.parseOrThrow("4111111111111111")
println(pan)              // 411111******1111
println("Card $pan ok")   // Card 411111******1111 ok
val raw: String = pan.unmasked()  // explicit opt-in to the raw form
```

`Pan.parse` returns a sealed `PanResult` with a typed `PanError` reason:

| Variant                  | When                                                              |
|--------------------------|-------------------------------------------------------------------|
| `LengthOutOfRange`       | Input is not 12 to 19 characters long                             |
| `NonDigitCharacters`     | Input contains anything other than `'0'..'9'`                     |
| `LuhnCheckFailed`        | Input is well-formed digits but fails the mod-10 check (#7)       |

### Track 2

Decode the BCD-packed Track 2 Equivalent Data carried in EMV tag `57`:

```kotlin
import io.github.a7asoft.nfcemv.extract.Track2
import io.github.a7asoft.nfcemv.extract.Track2Result

when (val result = Track2.parse(tag57Bytes)) {
    is Track2Result.Ok -> {
        val t2 = result.track2
        // t2.pan       — io.github.a7asoft.nfcemv.extract.Pan (masked toString)
        // t2.expiry    — kotlinx.datetime.YearMonth
        // t2.serviceCode  — io.github.a7asoft.nfcemv.extract.ServiceCode
        // t2.unmaskedDiscretionary()  — PCI-scoped raw discretionary digits
    }
    is Track2Result.Err -> reportRefusal(result.error)
}
```

`Track2.toString` masks the PAN and reports only the discretionary length. Two-digit expiry years are interpreted as 21st century (`YY` ⇒ `20YY`).

## EMV tag dictionary

Look up human-readable metadata for an EMV tag:

```kotlin
import io.github.a7asoft.nfcemv.emv.EmvTags
import io.github.a7asoft.nfcemv.tlv.Tag

val info = EmvTags.lookup(Tag.fromHex("9F26"))
// info.name           — "Application Cryptogram"
// info.format         — EmvTagFormat.B
// info.length         — EmvTagLength.Fixed(8)
// info.sensitivity    — TagSensitivity.PCI
```

`EmvTags.lookup` returns `null` for tags not in the dictionary. Use `EmvTags.all` to enumerate all 27 registered entries in source order. Sensitivity is a binary `PCI` / `PUBLIC` flag; `PCI` covers PAN, Track 2, cryptograms, signed dynamic data, IAD, and the cardholder-data trio (name, expiry, sequence number).

Names and metadata are hand-curated from EMV Book 3 / Book 4 / contactless kernels C-2..C-7. Descriptions are paraphrased; no third-party listing has been copied verbatim.

## Tests

179 tests on `commonMain` cover happy paths, all error variants, the EMV padding behavior, the documented X.690 deviation, fuzz over 10,000 random buffers (strict + lenient), OOM-resistance regression, PCI-safety regressions for tags `5A`, `57`, and `9F26`, encoder round-trip fixtures, 5,000-iteration encoder fuzz, encoder PCI-safety regressions. Run with:

```bash
./gradlew :shared:testDebugUnitTest
```
