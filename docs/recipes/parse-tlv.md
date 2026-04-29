# Recipe: parse a BER-TLV response

## Goal

Take the data field of an APDU response from a contactless EMV card and decode it into a tree of `Tlv` nodes.

## What you need

- A `ByteArray` containing the response **data field only**. SW1 SW2 must already be stripped by your transport layer.
- The `nfc-emv-core` artifact on the classpath:

  ```kotlin
  implementation("io.github.a7asoft:nfcemv-core:0.1.0-SNAPSHOT")
  ```

## Minimal example

```kotlin
import io.github.a7asoft.nfcemv.tlv.*

fun main() {
    // FCI Template returned after a SELECT AID for a Visa card (sanitized fixture).
    val response = byteArrayOf(
        0x6F, 0x16,
        0x84.toByte(), 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
        0xA5.toByte(), 0x0B,
        0x50, 0x06, 0x56, 0x49, 0x53, 0x41, 0x30, 0x31,
        0x87.toByte(), 0x01, 0x01,
    )

    when (val result = TlvDecoder.parse(response)) {
        is TlvParseResult.Ok  -> result.tlvs.forEach(::printTree)
        is TlvParseResult.Err -> System.err.println("Decode failed: ${result.error}")
    }
}

fun printTree(tlv: Tlv, depth: Int = 0) {
    val indent = "  ".repeat(depth)
    when (tlv) {
        is Tlv.Primitive   -> println("$indent${tlv.tag}: ${tlv.length} bytes")
        is Tlv.Constructed -> {
            println("$indent${tlv.tag}: (constructed, ${tlv.children.size} children)")
            tlv.children.forEach { printTree(it, depth + 1) }
        }
    }
}
```

> **PCI safety:** never write `println(tlv.copyValue().toHexString())` or pass
> `copyValue()` to a logger / persistence layer. Tags `5A` (PAN), `57` (Track 2),
> and `9F26` (ARQC) carry sensitive bytes. The only safe sink is a typed
> extractor (`Pan`, `Track2`, …) that masks on `toString`. The `tlv.length`
> read above is structural and PCI-neutral.

Output:

```
6F: (constructed, 2 children)
  84: 7 bytes
  A5: (constructed, 2 children)
    50: 6 bytes
    87: 1 bytes
```

Note that the printed value never includes raw bytes — `Tlv.Primitive.toString()` masks by design.

## Try / catch style

```kotlin
val tlvs: List<Tlv> = try {
    TlvDecoder.parseOrThrow(response)
} catch (e: TlvParseException) {
    System.err.println("Decode failed at offset ${e.error.offset}: ${e.error::class.simpleName}")
    return
}
```

## Lenient mode

If you observe a card that emits non-minimal tag continuations or non-minimal length encodings (uncommon but possible with older issuers), switch strictness:

```kotlin
val tlvs = TlvDecoder.parseOrThrow(response, TlvOptions(strictness = Strictness.Lenient))
```

EMV zero-padding tolerance stays on — switch padding policy only for diagnostic dumps where every byte must be parsed literally:

```kotlin
val tlvs = TlvDecoder.parseOrThrow(response, TlvOptions(paddingPolicy = PaddingPolicy.Rejected))
```

## Common pitfall: forgot to strip SW1 SW2

If you pass `... 90 00` to the decoder, it will succeed and add a fake trailing `Primitive(tag=0x90, value=[])` to the result. The decoder cannot detect this — `90 00` is a structurally valid TLV.

Strip status words at the transport layer:

```kotlin
val full: ByteArray = isoDep.transceive(apdu)
require(full.size >= 2)
val sw1 = full[full.size - 2].toInt() and 0xFF
val sw2 = full[full.size - 1].toInt() and 0xFF
if (sw1 != 0x90 || sw2 != 0x00) throw ApduError(sw1, sw2)
val data = full.copyOfRange(0, full.size - 2)

val tlvs = TlvDecoder.parseOrThrow(data)
```

## What this recipe does not cover

- Interpreting EMV tags semantically (PAN, expiry, brand) — see future recipes once the `emv` and `extract` modules ship.
- Building APDUs and chaining `61 xx` / `6C xx` responses — that's transport-layer concern.
- Handling cards that return token PANs (`9F6B` Mastercard PayPass etc.) vs raw PANs (`5A`) — extraction module will handle that.

## Re-emit a parsed tree

If you need to forward the same TLV stream you decoded — for example, to a downstream service that expects raw EMV bytes — use `TlvEncoder`:

```kotlin
import io.github.a7asoft.nfcemv.tlv.*

val parsed = TlvDecoder.parse(response)
if (parsed is TlvParseResult.Ok) {
    val reEmitted: ByteArray = TlvEncoder.encode(parsed.tlvs)
    forwardToBackend(reEmitted)
}
```

Output is always DER-canonical (minimal length, definite-only). If the original card response used non-minimal length octets (uncommon), the re-emitted bytes will be shorter but **semantically identical** — a second `TlvDecoder.parse` produces the same `Tlv` tree.

> **PCI safety:** the encoder writes value bytes for sensitive tags (`5A`, `57`, `9F26`) into the output `ByteArray` as required to be useful — that is the encoder's job. The constraint is the same as for `Tlv.Primitive.copyValue()`: never log the result, never persist it, never send it anywhere except a typed extractor or a transport whose endpoint is itself PCI-scoped.
