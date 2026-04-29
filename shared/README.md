# shared — KMP core

Pure-Kotlin core for the nfc-emv-toolkit. Lives in `commonMain` and ships with no platform dependencies, so the same code runs on Android, iOS (via XCFramework), and the JVM.

## Modules in this folder

| Package | Purpose |
|---------|---------|
| `io.github.a7asoft.nfcemv.tlv` | BER-TLV decoder + encoder (this milestone) |
| `io.github.a7asoft.nfcemv.tlv.internal` | Reader cursor, tag/length/node decoders, padding skipper. Internal — do not depend on these symbols. |

Future packages (later issues): `emv` (tag dictionary), `brand` (AID + BIN brand resolution), `extract` (PAN, Track2, expiry), `validation` (Luhn, format checks).

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

## Tests

179 tests on `commonMain` cover happy paths, all error variants, the EMV padding behavior, the documented X.690 deviation, fuzz over 10,000 random buffers (strict + lenient), OOM-resistance regression, PCI-safety regressions for tags `5A`, `57`, and `9F26`, encoder round-trip fixtures, 5,000-iteration encoder fuzz, encoder PCI-safety regressions. Run with:

```bash
./gradlew :shared:testDebugUnitTest
```
