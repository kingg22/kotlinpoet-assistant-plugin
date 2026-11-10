# Changelog

## [Unreleased]

### Added
- Compatibility with **IntelliJ IDEA 2026.2** (build 262). The plugin keeps working on IntelliJ IDEA 2025.2 (build 252)
  and newer.

### Changed

### Deprecated

### Removed

### Fixed

### Security

### Technical

## [0.0.1] - 2026-03-26

### Added

- **Missing argument** (`KotlinPoetMissingArgument`) — reports a placeholder with no corresponding argument;
causes `IllegalArgumentException` at runtime. Level: **Error**.
- **Extra argument** (`KotlinPoetExtraArgument`) — reports a surplus argument with no matching placeholder.
Level: **Error** for relative/positional, **Information** for named maps (shared maps are a valid pattern).
Quick fix: *Remove extra argument*.
- **Argument type mismatch** (`KotlinPoetTypeMismatch`) — reports an argument whose resolved type is incompatible with
the placeholder kind it is bound to (`%T` expects `TypeName`/`KClass`, `%M` expects `MemberName`, etc.).
Level: **Warning** / **Information** depending on kind.
- **Named argument case** (`KotlinPoetNamedCase`) — reports a named-argument key that does not match KotlinPoet's
required pattern `[a-z]+[\w_]*`. Level: **Warning**. Quick fix: *Rename 'X' to 'x'* (renames key in both the format
string and the map literal simultaneously).
- **Format syntax** (`KotlinPoetFormatSyntax`) — reports parser-level issues in format strings:
  - Dangling `%` — quick fixes: *Escape as `%%`*, *Remove invalid format token*.
  - Unknown placeholder type (e.g., `%Z`) — quick fix: *Remove invalid format token*.
  - Invalid positional index (e.g., `%0L`) — quick fix: *Fix index to `%1L`*.
  - Mixed argument styles (relative + positional + named in the same string) — quick fixes: *Convert to named style*,
  *Convert to relative style*,
  *Convert to positional style* (the latter two use a live template for interactive renaming).

### Technical

- Requires **IntelliJ IDEA 2025.2+** (build 252+) with K2 Kotlin plugin mode. K1 is not supported.
- Domain layer is PSI-free and IntelliJ-free; fully unit-testable without the platform.
- Analysis results are cached per `KtCallExpression` via `CachedValuesManager` to avoid redundant K2 sessions on every
annotator/inspection pass.
- Format string parsed from PSI (`FormatText`/`FormatTextSegment`) preserving absolute file offsets for precise range
highlighting and navigation — no raw string reparsing.
- `StringFormatParserImpl` never throws for malformed input; all issues are accumulated as `errors`/`warnings` in
`FormatStringModel`.
- Chain analysis uses an immutable state machine (`EmissionState` + `EmissionStateDelta`) safe to cache per call and
compose across the chain.

[Unreleased]: https://github.com/kingg22/kotlinpoet-assistant-plugin/compare/0.0.1...HEAD
[0.0.1]: https://github.com/kingg22/kotlinpoet-assistant-plugin/commits/0.0.1
