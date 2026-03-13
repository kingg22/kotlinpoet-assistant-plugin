# Converter de Kotlin → KotlinPoet

# 1️⃣ Qué significa realmente “Kotlin → KotlinPoet”

Ejemplo de entrada:

```kotlin
fun hello() {
    println("Hello")
}
```

Salida generada:

```kotlin
FunSpec
    .builder("hello")
    .addStatement("println(%S)", "Hello")
    .build()
```

El problema real es:

```
Kotlin PSI
   ↓
Semantic Model
   ↓
KotlinPoet AST
   ↓
CodeBlock / FileSpec
```

Es básicamente un **transpiler parcial**.

---

# 2️⃣ APIs principales que necesitas

## 1. PSI (Parsear el código Kotlin)

La API principal es:

```kotlin
KtPsiFactory
```

y el modelo PSI de Kotlin:

```kotlin
org.jetbrains.kotlin.psi.*
```

Ejemplo:

```kotlin
KtFile
KtFunction
KtCallExpression
KtProperty
KtClass
```

Esto te permite recorrer código como árbol.

---

## 2. Visitors del PSI

La forma correcta de recorrer Kotlin es:

```kotlin
KtVisitorVoid
```

o

```kotlin
KtTreeVisitorVoid
```

Ejemplo:

```kotlin
class KotlinToPoetVisitor : KtVisitorVoid() {

    override fun visitNamedFunction(function: KtNamedFunction) {
        val name = function.name
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val callee = expression.calleeExpression?.text
    }
}
```

Esto te permite reconstruir el modelo.

---

## 3. Resolution semántica (muy importante)

Si quieres saber:

```
println → kotlin.io.println
List → kotlin.collections.List
```

necesitas el **análisis semántico** del compilador Kotlin.

La API es:

```kotlin
org.jetbrains.kotlin.analysis.api.* // Kotlin Analysis API (K2)
```

Ejemplo:

```kotlin
analyze(psiElement) {
    val symbol = psi.resolveToSymbol()
}
```

Esto es clave para generar:

```
ClassName("kotlin.io", "println")
```

---

# 3️⃣ API de generación KotlinPoet

Luego generas:

Ejemplo:

```kotlin
FunSpec
  .builder("hello")
  .addStatement("println(%S)", "Hello")
```

Tu converter básicamente produce:

```
KotlinPoet AST
```

---

# 4️⃣ Feature ideal dentro de IntelliJ

La UX ideal sería:

### 1️⃣ Acción en el editor

```
Right click
→ Convert to KotlinPoet
```

API:

```kotlin
AnAction
```

---

### 2️⃣ Quick fix

Ejemplo:

```
Alt+Enter
Convert this function to KotlinPoet
```

API:

```
IntentionAction
```

---

### 3️⃣ Tool Window (muy poderoso)

Mostrar:

```
Kotlin code
↓
Generated KotlinPoet preview
```

API:

```
ToolWindowFactory
```

---

# 5️⃣ Arquitectura recomendada

Te recomiendo separar en capas.

```
converter
 ├─ psi
 │   ├─ KotlinElementMapper
 │   └─ KotlinVisitor
 │
 ├─ model
 │   ├─ PoetModel
 │   └─ PoetNode
 │
 ├─ generator
 │   └─ KotlinPoetGenerator
 │
 └─ renderer
     └─ KotlinPoetRenderer
```

Pipeline:

```
Kotlin PSI
   ↓
Intermediate Model
   ↓
KotlinPoet AST
   ↓
Kotlin code
```

---

# 6️⃣ Estrategia incremental (muy importante)

No intentes soportar **todo Kotlin**.

Empieza con:

### MVP

Soportar:

```
functions
calls
literals
parameters
```

Ejemplo soportado:

```kotlin
fun hello() {
    println("hi")
}
```

---

### v2

Agregar:

```
if
loops
properties
```

---

### v3

Agregar:

```
classes
annotations
generics
```

---

# 7️⃣ Dificultades reales

Las partes difíciles:

### 1️⃣ Control flow

```
if
when
for
try
```

→ mapear a `beginControlFlow`.

---

### 2️⃣ Imports

Resolver:

```
List
Map
String
```

→ `ClassName`, `ParameterizedTypeName`, etc.

---

### 3️⃣ Strings complejos

```
"hello $name"
```

→ `%P`.

---

# 8️⃣ APIs del IntelliJ que te ayudan mucho

## `PsiElementFactory`

Crear código PSI nuevo.

---

## `CodeStyleManager`

Formatear salida.

---

## `SmartPsiElementPointer`

Mantener referencias vivas.

---

## `PsiTreeUtil`

Buscar nodos fácilmente.

---

# 9️⃣ Feature muy potente para tu plugin

Podrías hacer algo **casi único**:

### Inline preview

Mostrar sobre el código:

```
fun hello() { ... }

↓ preview

FunSpec.builder("hello")
```

Usando:

```
InlayHintsProvider
```
