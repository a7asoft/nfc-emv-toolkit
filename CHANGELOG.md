# Changelog

All notable changes to this project will be documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added — BER-TLV decoder (#1)
- `Tag` value class (`@JvmInline`) backed by a packed `Long`. Supports 1–4 byte tags per ISO/IEC 8825-1.
- `TagClass` enum (universal / application / context-specific / private).
- `Tlv` sealed type with `Primitive` and `Constructed` variants (regular classes, not data classes — auto-generated members cannot accidentally expose value bytes). Hand-rolled `equals` / `hashCode` / `toString`. `toString` omits value bytes (tag + length / child count only) for PCI-safe logging.
- `TlvOptions` with `Strictness` (Strict / Lenient sealed) and `PaddingPolicy` (Tolerated / Rejected sealed) instead of boolean flags, plus `maxTagBytes` and `maxDepth` bounds.
- `TlvError` sealed catalogue: `UnexpectedEof`, `IndefiniteLengthForbidden`, `InvalidLengthOctet`, `IncompleteTag`, `TagTooLong`, `NonMinimalTagEncoding`, `NonMinimalLengthEncoding`, `ChildrenLengthMismatch`, `MaxDepthExceeded`. Every variant carries an offset; none embed value bytes.
- `TlvParseResult` (sealed `Ok` / `Err`) and `TlvParseException` for the two API styles.
- `TlvDecoder.parse` (returns sealed result) and `TlvDecoder.parseOrThrow` (throws on first violation). Both honor the same option set.
- 116 tests on `commonMain`: happy paths for primitive / constructed / nested, every error variant, EMV padding behavior, documented X.690 deviation cases (e.g. `9F02`, `BF0C`), 10,000-iteration deterministic fuzz, OOM-resistance regression with pinned `UnexpectedEof`, PCI-safety regressions for tags `5A` / `57` / `9F26` with exact-form `toString` assertions.

#### Removed before release
- An earlier draft included a `rejectTrailingBytes` option intended to catch APDU responses passed in with SW1 SW2 still attached. Removed because at the BER-TLV layer `90 00` decodes as a valid empty primitive, not as trailing bytes — SW detection belongs to the transport layer.
- Wizard-generated scaffold (`Greeting.kt`, `Platform.kt`, `SharedCommonTest.example`) cleaned up.

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
