# KotlinPoet Format Parsing (Reference)

This section summarizes KotlinPoet's parsing behavior and argument typing rules (based on the
actual implementation).

## Named Arguments (`addNamed`)

Rules:
- Named arguments must match `^[a-z][a-zA-Z0-9_]*$`.
- The format uses `%name:X` where `X` is the format kind.
- Missing names in the map are errors.

Parsing flow:
- Scan the string and locate the next `%`.
- If `%name:X` matches, validate name and resolve the argument.
- If `%` is followed by a no-arg placeholder, consume it.
- Otherwise treat `%` with unknown or dangling format as error.

## Positional / Relative (`add`)

Rules:
- Relative: placeholders consume arguments in order.
- Positional: `%3L` uses 1-based index.
- Mixing positional and relative is invalid.
- Unused arguments are errors (relative: args > placeholders; positional: any index never used).

Parsing flow:
- Scan tokens and parse optional digits after `%`.
- If token is no-arg placeholder, continue.
- If digits present, treat as positional; otherwise relative.
- Validate index in range and mixing rules.

## Argument Type Mapping (KotlinPoet)

Mapping used by `addArgument`:
- `%N` → `argToName(...)` (ParameterSpec, PropertySpec, FunSpec, TypeSpec, MemberName, etc.)
- `%L` → `argToLiteral(...)` (numbers formatted; otherwise raw)
- `%S` → `argToString(...)` (string escaping)
- `%P` → CodeBlock or String (string escaping but `$` allowed)
- `%T` → `argToType(...)` (TypeName, TypeMirror, Element, Type, KClass, etc.)
- `%M` → MemberName

## Validator Severity Guidance

When a rule can be validated with certainty:
- Use `ERROR`.

When the PSI or type system is incomplete or ambiguous:
- Use `WARNING` or `INFO`.

Examples:
- Unknown format kind → `ERROR`.
- Named key missing in map literal → `ERROR` (if map literal is resolved).
- Type mismatch for `%S` or `%T` → `WARNING` (if type inference is incomplete).
