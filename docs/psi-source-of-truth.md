# PSI como Fuente de Verdad

Este proyecto ahora modela los strings de formato a partir del PSI, sin depender de un `rawString`
como fuente de verdad. El objetivo es conservar rangos exactos y permitir anotaciones, referencias
y validaciones con precisión.

## Resumen del Cambio

1. El parser ya no recibe `String`, recibe `FormatText`.
2. `FormatText` contiene segmentos con rangos absolutos del PSI.
3. Los placeholders y errores usan `TextSpan` (uno o varios rangos).
4. Las capas de infraestructura convierten `TextSpan` a `TextRange` del IDE.

## Nuevos Modelos

`FormatText`
- Lista de segmentos (`FormatTextSegment`)
- Cada segmento incluye texto + rango absoluto en archivo
- Puede marcarse como `LITERAL` o `DYNAMIC`

`TextSpan`
- Rango(s) absolutos en archivo
- Permite soportar concatenaciones sin perder el rango original

## Reglas de Extraccion (PSI)

Se soporta:
- `KtStringTemplateExpression` (strings normales y raw)
- Concatenacion con `+` (solo si ambos lados son strings soportados)
- Parentesis alrededor de strings

Se rechaza:
- Transformaciones de string (`trimIndent`, `trimMargin`, etc.)
- Referencias a variables o constantes fuera del string literal

Si hay entradas dinamicas (`$name`, `${expr}`), se marcan como segmentos `DYNAMIC` y el parser
no intenta parsear placeholders dentro de esas zonas.

## Efecto en Validaciones

- El parser produce spans absolutos, no offsets relativos.
- El binding/validator opera igual, pero usa spans directos del PSI.
- Las referencias solo se generan si el placeholder tiene un rango unico y valido.

## Plan de Trabajo (resumen)

1. Modelar `FormatText`/`TextSpan` y adaptar parser.
2. Ajustar extractores y providers a rangos absolutos.
3. Documentar reglas y limitaciones de extraccion.
