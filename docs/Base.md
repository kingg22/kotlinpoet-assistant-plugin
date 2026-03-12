# Guía de Arquitectura

**Asistencia de Strings y Formats en KotlinPoet (Presente y Futuro)**

## Objetivo

Diseñar un sistema **extensible, desacoplado y reusable** para:

* Analizar strings usados en KotlinPoet (`CodeBlock`, `FileSpec.Builder`, etc.)
* Proveer **asistencia IDE** (referencias, navegación, validación)
* Soportar **múltiples tipos de format** (relativo, posicional, nombrado)
* Facilitar la expansión futura (nuevas APIs, nuevos DSLs, otros frameworks)

El sistema debe funcionar igual de bien para:

* **Agentes IA** (razonamiento estructurado, reglas claras)
* **Desarrolladores humanos** (clases pequeñas, responsabilidades obvias)

---

## Principio Rector

> **El string es solo datos; el formato define el significado.**

Nunca acoples:

* el **string**
* el **formato**

Todo eso se **orquesta**, no se mezcla.

---

## Core del Sistema (Dominio)

### 1. `FormatStringModel`

Representa **un string con intención semántica**, pero el texto viene del PSI.

```kotlin
data class FormatStringModel(val text: FormatText, val style: FormatStyle, val placeholders: List<PlaceholderSpec>)
```

---

### 2. `FormatStyle`

Define **cómo interpretar el string**.

```kotlin
sealed interface FormatStyle {
   data object Relative : FormatStyle
   data object Positional : FormatStyle
   data object Named : FormatStyle
}
```

⚠️ Regla clave:

> El formato **no interpreta**, solo **define reglas**.

---

### 3. `FormatSegment`

Unidad mínima del análisis.

```kotlin
data class PlaceholderSpec(val kind: FormatKind, val binding: PlaceholderBinding, val span: TextSpan)

value class FormatKind private constructor(val value: String) {
  companion object {
    val LITERAL: FormatKind = FormatKind("L")
    val STRING: FormatKind = FormatKind("S")
    val TYPE: FormatKind = FormatKind("T")
    val MEMBER: FormatKind = FormatKind("M")
    val NAME: FormatKind = FormatKind("N")
    val STRING_TEMPLATE: FormatKind = FormatKind("P")
  }
}

sealed interface PlaceholderBinding {
  /** %L */
  data object Relative : PlaceholderBinding
  /** %2L */
  data class Positional(val index1Based: Int) : PlaceholderBinding
  /** %count:L */
  data class Named(val name: String) : PlaceholderBinding
}
```

### 4. `FormatText` y `TextSpan`

`FormatText` es un string segmentado con rangos absolutos del PSI.  
`TextSpan` permite uno o varios rangos (concatenaciones).

```kotlin
data class FormatText(val segments: List<FormatTextSegment>)
data class FormatTextSegment(val text: String, val range: IntRange, val kind: SegmentKind)
data class TextSpan(val ranges: List<IntRange>)
```

Esto permite:

* referencias precisas
* highlighting
* validación granular

---

### 5. `StringFormatParser`

Responsable de **convertir raw string → segmentos**.

```kotlin
sealed interface StringFormatParser {
  fun parse(text: FormatText, isNamedStyle: Boolean = false): FormatStringModel
}
```

Implementaciones:

* `RelativeFormatParser`
* `PositionalFormatParser`
* `NamedFormatParser`

➡️ **Extensible sin tocar el resto del sistema**

---

## Integración con IntelliJ

### 5. `PsiStringReferenceProvider`

```kotlin
class FormatPlaceholderReferenceProvider(
    private val resolver: PlaceholderResolver
)
```

El IDE:

* resuelve navegación
* muestra errores
* maneja refactor

Tú solo entregas:

* rango
* símbolo lógico

---

## Flujo Completo

```text
PSI Element
   ↓
KotlinPoetStringSource
   ↓
StringFormatParser
   ↓
FormattedString
   ↓
FormatSegment
   ↓
PsiReference
   ↓
IDE Magic ✨
```

---

## Reglas de Oro (para humanos y agentes IA)

### ✅ Haz

* Mantén los parsers **puros** lo más posible
* Modela el dominio antes del PSI
* Usa `sealed interface` para formatos y contextos
* Prefiere **composición sobre herencia**

### ❌ No hagas

* No mezcles validación con parsing
* No codifiques formatos “especiales” hardcoded

---

## Señales de que vas bien

* Agregar un nuevo format **no rompe nada**
* Puedes testear el core **sin IntelliJ** o mocks mínimos.
* El PSI layer es delgado y aburrido
