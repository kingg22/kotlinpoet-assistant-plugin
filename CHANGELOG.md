# Changelog

## [Unreleased]

### Added

#### Inspections

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

#### Syntax Highlighting

- Placeholders (`%L`, `%2S`, `%food:T`, …) highlighted with `KPOET_PLACEHOLDER`
(`DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE` base).
- Control symbols (`«`, `»`, `⇥`, `⇤`, `%%`, `·`, `♢`) highlighted with `KPOET_CONTROL`
(`DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL` base).

#### Navigation & References

- **Navigate to argument** — `Ctrl+Click` (or `Cmd+Click`) on any placeholder in a format string navigates to the bound
argument expression. Supports relative, positional, and named styles.
- **Find Usages** — `Alt+F7` on an argument expression highlights all placeholder references to it in the format string,
plus the declaration itself.
- Bidirectional: references are implemented via `PsiSymbolReferenceProvider` + `UsageSearcher` using the new IntelliJ
Platform API (`com.intellij.model.psi`).

#### Inline Documentation

- `Ctrl+Q` (or hover) on any placeholder or control symbol shows a documentation popup with:
  - A short description of the token's semantics.
  - The accepted argument types (with links to KotlinPoet classes).
  - A direct external link to the relevant section of the KotlinPoet documentation.
- `psi_element://` links in the popup resolve to KotlinPoet class documentation via `DocumentationLinkHandler`.

#### Code Completion

- Typing `%` inside a KotlinPoet format string argument triggers a completion popup listing all supported placeholder
types and `%%`.
- Each item includes a type label (e.g., "Literal", "Type") and a short description.
- `KotlinPoetCompletionConfidence` suppresses the default auto-popup inhibition so the list appears immediately
after `%`.
- `KotlinPoetTypedHandler` fires `AutoPopupController` to ensure the popup appears even on the very first `%` keystroke.

#### Chain Preview Tool Window

- New bottom tool window **KotlinPoet Chain** (`View → Tool Windows → KotlinPoet Chain`).
- Activates on caret movement into any KotlinPoet builder call (dot-chains and `buildCodeBlock { }` lambdas).
- Displays a per-call row for every method in the chain with:
  - Method name (clickable — navigates to the source location with a brief highlight).
  - Semantic tag (`[stmt]`, `[begin]`, `[end]`, `[indent]`, `[build]`, …).
  - Approximate emitted text (resolved scalar arguments shown inline; unresolved shown as `[%L]`).
  - Row background color: red for state-machine violations, amber for inspection problems.
- State-machine validation: it detects double-open statement, close-without-open, and negative indent violations at
static analysis time.
- Text preview section: indented, rendered approximation of the full `CodeBlock` output.
- Warnings for unclosed statement scopes and unbalanced indent levels at the end of a chain.
- Updates with a 400 ms debounced on caret movement and document changes.

#### Inlay Hints

- New inlay hints provider **KotlinPoet code preview**
(*Settings → Editor → Inlay Hints → Other → Kotlin → KotlinPoet code preview*).
- **Per-call EOL hint** (default: on) — a faint annotation at the end of each builder call line showing the approximate
emission of that call alone.
- **Chain preview hint** (default: on) — a collapsible block element placed above the terminal call showing the full
indented preview. Starts collapsed; click to expand.
- Settings: max preview lines (1–100, default 12), max line length (20–200 chars, default 80), toggle per-call and chain
preview independently, toggle expanded-by-default.

#### Error Reporting

- Custom `ErrorReportSubmitter` with a **Report to GitHub** button on any IDE error captured from this plugin.
- Copies the full stack trace to the clipboard automatically.
- Opens a pre-filled GitHub issue with IDE version, OS, JVM, plugin version, and a paste placeholder for the stack trace.

#### Supported KotlinPoet APIs

- `CodeBlock.Builder.add` / `addCode` / `addNamed` / `addStatement`
- `CodeBlock.Builder.beginControlFlow` / `nextControlFlow` / `endControlFlow`
- `CodeBlock.Builder.indent` / `unindent`
- `CodeBlock.of` / `buildCodeBlock { }`
- `FunSpec.Builder`, `TypeSpec.Builder`, `PropertySpec.Builder`, `FileSpec.Builder` (any `.Builder` in
`com.squareup.kotlinpoet.*` package)
- `addKdoc`
- Custom extension functions on `CodeBlock.Builder` that delegate to a known KotlinPoet format method
(detected automatically via K2 body analysis)

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

[Unreleased]: https://github.com/kingg22/kotlinpoet-assistant-plugin/commits
