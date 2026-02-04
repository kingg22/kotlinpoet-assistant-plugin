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
* el **contexto PSI**
* la **lógica de ayuda IDE**

Todo eso se **orquesta**, no se mezcla.

---

## Capas Principales

```
┌────────────────────────────┐
│        IDE / IntelliJ      │
│  (PSI, References, Gutter) │
└────────────┬───────────────┘
             │
┌────────────▼───────────────┐
│     Integration Layer      │  ← PSI adapters
└────────────┬───────────────┘
             │
┌────────────▼───────────────┐
│   String Assistance Core   │  ← dominio reusable
└────────────┬───────────────┘
             │
┌────────────▼───────────────┐
│     KotlinPoet Adapters    │  ← implementación concreta
└────────────────────────────┘
```

---

## Core del Sistema (Dominio)

### 1. `FormatStringModel`

Representa **un string con intención semántica**.

```kotlin
data class FormatStringModel(val rawText: String, val style: FormatStyle, val placeholders: List<PlaceholderSpec>)
```

No sabe nada de PSI ni de KotlinPoet.

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
data class PlaceholderSpec(val kind: FormatKind, val binding: PlaceholderBinding, val textRange: IntRange)

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

Esto permite:

* referencias precisas
* highlighting
* validación granular

---

### 4. `StringFormatParser`

Responsable de **convertir raw string → segmentos**.

```kotlin
sealed interface StringFormatParser {
  fun parse(rawString: String, arguments: List<ArgumentValue>): FormatStringModel
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

Aquí **NO se parsea formato**.

Solo se traduce:

```
FormatSegment → PsiReference
```

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

* Mantén los parsers **puros**
* Modela el dominio antes del PSI
* Usa `sealed interface` para formatos y contextos
* Prefiere **composición sobre herencia**

### ❌ No hagas

* No accedas a PSI desde el dominio
* No mezcles validación con parsing
* No codifiques formatos “especiales” inline

---

## Señales de que vas bien

* Agregar un nuevo format **no rompe nada**
* Puedes testear el core **sin IntelliJ**
* El PSI layer es delgado y aburrido
* Un agente IA puede seguir el flujo sin contexto previo
