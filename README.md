# KotlinPoet Assistant

> **IDE assistance for [KotlinPoet](https://square.github.io/kotlinpoet/) format strings — catch mistakes before your tests do.**

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Marketplace-orange?logo=jetbrains)](https://plugins.jetbrains.com/plugin/TODO)
[![Build](https://github.com/kingg22/kotlinpoet-assistant-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/kingg22/kotlinpoet-assistant-plugin/actions/workflows/build.yml)
[![Version](https://img.shields.io/github/v/release/kingg22/kotlinpoet-assistant-plugin)](https://github.com/kingg22/kotlinpoet-assistant-plugin/releases)
[![License](https://img.shields.io/github/license/kingg22/kotlinpoet-assistant-plugin)](LICENSE)

<!-- Plugin description -->
Brings first-class IDE support for CodeBlock format strings directly into IntelliJ IDEA.
If you use KotlinPoet to generate Kotlin code, this plugin helps you write format strings confidently —
with real-time validation, smart navigation, inline documentation, and a live preview of what your builder chain will produce.

> [!NOTE]
> Results are **approximations**. This plugin is a development aid, not a code generator.
> Some previews may differ from the actual KotlinPoet runtime output (e.g., import resolution, indent context, variables).
> When in doubt, trust your tests.

## Features at a Glance

- 🔴 **Inspections** — Missing arguments, extra arguments, type mismatches, invalid named keys, and format syntax errors,
all reported inline with quick fixes.
- 🎨 **Syntax Highlighting** — Placeholders (`%L`, `%S`, `%T`, …) and control symbols (`«`, `»`, `⇥`, `⇤`, …) are
highlighted in a distinct color so they stand out from surrounding string content.
- 🔗 **Navigate to Argument** — `Ctrl+Click` (or `Cmd+Click`) on a placeholder jumps straight to the argument it is
bound to.
- 📖 **Inline Documentation** — Hover over any placeholder or control symbol for a quick-doc popup with a description
and a link to the official KotlinPoet docs.
- ✅ **Completion** — Typing `%` inside a CodeBlock format string pops up a completion list for all supported
placeholders (`%L`, `%S`, `%T`, `%N`, `%M`, `%P`, `%%`).
- 🔍 **Find Usages** — Find all format-string positions that reference a particular argument, alongside the argument
declaration itself.
- 🪟 **Chain Preview Tool Window** — A bottom tool window that shows the approximated output of the entire CodeBlock
builder chain at the current caret, complete with per-call metadata, state-machine violations, and an indented text preview.
- 💡 **Inlay Hints** — *Optional* per-call end-of-line hints showing the approximate emission of each builder call,
plus a collapsible block preview above the terminal `build()` call.
<!-- Plugin description end -->

---

## Screenshots

<details>
<summary>Click to expand</summary>

1. Inspections overview

    ![A file open in the editor with red/yellow underlines on a add("val %N = %S", name) call that is missing one argument.
The Problems panel is visible at the bottom showing the inspection message.](docs/screenshots/inspections-overview.png)

2. Syntax highlighting

    ![A addStatement("val %N: %T = %L") call with the three placeholders highlighted in a contrasting color against the
string background.](docs/screenshots/syntax-highlighting.png)

3. Navigate to argument

    ![The cursor is on %L and the tooltip shows "Navigate to argument", or the corresponding argument is highlighted,
demonstrating the bidirectional reference between placeholder and argument.](docs/screenshots/navigate-to-argument.png)

4. Quick documentation

    ![The Quick Documentation popup is open on %T, displaying the KotlinPoet description along with a "See Also" link
to the official documentation.](docs/screenshots/quick-doc.png)

5. Completion popup

    ![The % completion popup is visible, listing %L, %S, %T, %N, %M, %P, and %% along with their respective type
labels.](docs/screenshots/completion-popup.png)

6. Chain tool window

    ![The "KotlinPoet Chain" tool window is open at the bottom, showing a three-call chain with individual rows per call
and an indented preview of the generated code.](docs/screenshots/chain-tool-window.png)

7. Inlay hints

    ![An addStatement call with a faint end-of-line inlay hint showing the approximate emitted code, along with a
collapsible block hint displayed above the build() call.](docs/screenshots/inlay-hints.png)

8. Quick fix (named case)

    ![A %Food format string where the quick fix "Rename 'Food' to 'food'" has been applied, correcting the case of the
argument name.](docs/screenshots/quick-fix-named-case.png)

9. Quick fix (mixed style)

    ![A mixed-style format string error is highlighted, with three available quick-fix options shown to convert the
format style consistently.](docs/screenshots/quick-fix-mixed-style.png)

</details>

---

## Installation

### From JetBrains Marketplace *(recommended)*

1. Open **Settings / Preferences → Plugins → Marketplace**.
2. Search for **KotlinPoet Assistant**.
3. Click **Install** and restart the IDE.

### From disk

1. Download the latest `.zip` from [Releases](https://github.com/kingg22/kotlinpoet-assistant-plugin/releases).
2. Open **Settings / Preferences → Plugins → ⚙️ → Install Plugin from Disk…**
3. Select the downloaded file and restart.

**Requirements:**
- IntelliJ IDEA 2025.2+ (build 252+)
- Kotlin plugin enabled with K2 (bundled)
- Your project uses [KotlinPoet](https://square.github.io/kotlinpoet/) ≥ 1.x

---

## Feature Details

### Inspections

All inspections are grouped under **KotlinPoet** in *Settings → Editor → Inspections*.

| Inspection                 | Default Level  | What it detects                                                                                                  |
|----------------------------|----------------|------------------------------------------------------------------------------------------------------------------|
| **Missing argument**       | Error          | A placeholder like `%L` with no corresponding argument — causes `IllegalArgumentException` at runtime            |
| **Extra argument**         | Error / Info   | An argument with no matching placeholder — runtime crash for relative/positional, informational for named maps   |
| **Argument type mismatch** | Warning / Info | Passing a `Boolean` where `%T` expects a `TypeName`, etc.                                                        |
| **Named argument case**    | Warning        | A key in `addNamed(…, mapOf(…))` that doesn't start with a lowercase letter — KotlinPoet enforces `[a-z]+[\w_]*` |
| **Format syntax**          | Error          | Dangling `%`, unknown placeholder type (`%Z`), invalid positional index (`%0L`), or mixed argument styles        |

Each inspection comes with one or more **quick fixes**:
- *Remove extra argument* — deletes the surplus argument from the call site.
- *Rename 'X' to 'x'* — renames an uppercase named-argument key in both the format string and the map literal simultaneously.
- *Escape as `%%`* — replaces a dangling `%` with the correct literal-percent escape.
- *Remove invalid format token* — deletes an unknown placeholder type like `%Z`.
- *Fix index to `%1X`* — repairs a zero-based positional index to the 1-based form KotlinPoet expects.
- *Convert to named / relative / positional style* — resolves mixed-style errors with a live template that lets you name
each placeholder interactively.

### Syntax Highlighting

Placeholders (`%L`, `%2S`, `%food:T`, …) and control symbols (`«`, `»`, `⇥`, `⇤`, `%%`, `·`, `♢`) receive their own color
key so they are visually distinct from the rest of the string. Colors respect the active theme.

<!--TODO
and can be customized in *Settings → Editor → Color Scheme → KotlinPoet*.-->

### Navigate to Argument (`Ctrl+Click` / `Cmd+Click`)

Every placeholder in a format string is a navigable reference. `Ctrl+Click` on `%L` jumps to the expression passed as
that argument. **Find Usages** on an argument expression highlights every placeholder that references it across the file.

Supports relative, positional, and named (`addNamed`) styles inline or variable.

### Inline Documentation (`Ctrl+Q` / `F1`)

Hover the cursor over any placeholder or control symbol to see:
- A short description of what it emits.
- The accepted argument types.
- A direct link to the corresponding section of the KotlinPoet documentation.

### Completion

Typing `%` inside a KotlinPoet format string argument immediately opens a completion list.
Each item shows the placeholder letter, a human-readable type label, and a short description.
Works in `add`, `addStatement`, `addCode`, `addNamed`, `beginControlFlow`, `nextControlFlow`, and `CodeBlock.of`.

### Chain Preview Tool Window

Open via **View → Tool Windows → KotlinPoet Chain** (or the bottom bar button).

Move the caret into any KotlinPoet builder call to see:

- A **per-call row** for every call in the chain, with the method name, semantic tag (`[stmt]`, `[begin]`, `[end]`, …),
and an approximation of what that call emits.
- **State machine violations** highlighted in red — e.g., an `endControlFlow()` with no matching `beginControlFlow()`,
or an unclosed `«` statement.
- A **text preview** section showing the indented, rendered approximation of the full CodeBlock.
- Clickable call names that **navigate** back to that call in the editor.

> [!IMPORTANT]
> **Approximation notice:** The preview is a best-effort static analysis. Dynamic arguments (variables, function calls)
> may appear as `[%L]`. Import handling and runtime indent context are not simulated.
>
> **Limitation notice**: Nested CodeBlocks are supported with one depth limit.
> Only the current call of the caret is shown and updated on change.
> Violations can't be detected in all cases or can be a false positive.

### Inlay Hints

Enable in *Settings → Editor → Inlay Hints → Others → Kotlin → KotlinPoet code preview*.

- **Per-call hints** — a faint end-of-line annotation showing the approximate output of each builder call.
- **Chain preview hint** — a collapsible block element above the terminal `build()` (or `of()`) call showing the full
indented preview. Click to expand/collapse.

Both hints are configurable: max preview lines, max line length, and individual on/off toggles.

---

## Supported Call Styles

| Style                                                     | Example                                               | Supported                   |
|-----------------------------------------------------------|-------------------------------------------------------|-----------------------------|
| Relative                                                  | `add("I ate %L %L", 3, "tacos")`                      | ✅                           |
| Positional                                                | `add("I ate %2L %1L", "tacos", 3)`                    | ✅                           |
| Named                                                     | `addNamed("I ate %count:L %food:L", map)`             | ✅                           |
| `addStatement`                                            | `addStatement("val x = %L", 42)`                      | ✅                           |
| `beginControlFlow` / `nextControlFlow` / `endControlFlow` | `beginControlFlow("if (%L)", cond)`                   | ✅                           |
| `CodeBlock.of` / `buildCodeBlock { }`                     | `CodeBlock.of("%T()", MyClass::class)`                | ✅                           |
| `addKdoc`                                                 | `addKdoc("@param %L the value", "name")`              | ✅ (without preview yet)     |
| Custom delegating wrappers                                | Your own extension or method with `CodeBlock.Builder` | ✅ (detected automatically*) |

---

## Limitations

- **Dynamic format strings** (variables, string templates `"$var"`) are not analyzed —
the plugin only processes string literals and literal concatenations.
- `trimIndent()`, `trimMargin()`, and similar transformations applied to the format string are not supported.
_(stay tuned for a future feature!)_
- The chain preview is an **approximation** — import resolution, runtime indent context, and `%N` collision avoidance are not simulated.
- Named argument validation is skipped when the map is passed as an external variable (not an inline `mapOf(…)` literal),
to avoid false positives or can't resolve the variable.
- Requires **K2 compiler** mode (IntelliJ 2025.2+, Kotlin plugin with K2 enabled). **K1 is not supported**.
- Not all KotlinPoet methods aren’t supported yet.
- Not all custom wrappers aren’t supported yet.

---

## Reporting Issues

If the IDE captures an exception from this plugin, a **Report to GitHub** button will appear in the error balloon.
Clicking it:

1. Copies the full stack trace to your clipboard.
2. Opens a pre-filled GitHub issue with your IDE version, OS, and JVM info.

**Please paste the stack trace into the issue before submitting** — _it is in your clipboard_.

You can also open an issue manually in the [issue tracker](https://github.com/kingg22/kotlinpoet-assistant-plugin/issues).

---

## Contributing

Contributions are welcome! A few areas where help is especially appreciated:

- **Translations** — The plugin UI is currently in English and Spanish. If you'd like to add a language,
open a PR adding a `messages/MyBundle_XX.properties` file.
- **IntelliJ version support** — If you need support for an older or newer IntelliJ build range,
please open an issue and we'll check compatibility.
- **Bug reports and feature requests** — Use the
[issue tracker](https://github.com/kingg22/kotlinpoet-assistant-plugin/issues).

---

## Building from Source

```bash
git clone https://github.com/kingg22/kotlinpoet-assistant-plugin.git
cd kotlinpoet-assistant-plugin

# Run the IDE with the plugin loaded
./gradlew runIde

# Run all tests
./gradlew check

# Build the distributable zip
./gradlew buildPlugin
```

---

## License

[Apache 2.0 License](LICENSE) — © 2025 Rey Acosta (Kingg22)

---

<p style="text-align:center;">
  Made with ❤️ by <a href="https://github.com/kingg22">Rey A.</a> with help from Claude &nbsp;·&nbsp;
  <a href="https://github.com/kingg22/kotlinpoet-assistant-plugin/issues">Report an issue</a> &nbsp;·&nbsp;
  <a href="https://square.github.io/kotlinpoet/">KotlinPoet Official docs</a>
</p>
