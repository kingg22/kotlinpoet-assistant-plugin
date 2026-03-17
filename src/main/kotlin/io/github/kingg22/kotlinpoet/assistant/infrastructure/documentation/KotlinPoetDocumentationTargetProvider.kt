package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class KotlinPoetDocumentationTargetProvider : DocumentationTargetProvider {
    private val logger = thisLogger()

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (file.language != KotlinLanguage.INSTANCE) return emptyList()

        val element = file.findElementAt(offset) ?: return emptyList()
        val template = element.getParentOfType<KtStringTemplateExpression>(false) ?: return emptyList()
        val call = template.getParentOfType<KtCallExpression>(false) ?: return emptyList()

        val format = getCachedAnalysis(call)?.format ?: return emptyList()

        val placeholder = format.placeholders.firstOrNull { it.span.contains(offset) }
        if (placeholder != null) {
            return listOf(KotlinPoetDocumentationTarget(DocToken.Placeholder(placeholder.kind)))
        }

        val control = format.controlSymbols.firstOrNull { it.span.contains(offset) }
        if (control != null) {
            return listOf(KotlinPoetDocumentationTarget(DocToken.Control(control.type)))
        }

        val fallback = resolveControlSymbolFromSource(file, template, offset)
        if (fallback != null) {
            logger.warn("Control symbol not found in analysis, but found fallback of $fallback")
            return listOf(KotlinPoetDocumentationTarget(DocToken.Control(fallback)))
        }

        return emptyList()
    }

    @Suppress("UnstableApiUsage")
    private class KotlinPoetDocumentationTarget(private val token: DocToken) : DocumentationTarget {

        override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

        override fun computePresentation(): TargetPresentation = TargetPresentation
            .builder(token.presentationTitle())
            .presentation()

        override fun computeDocumentation(): DocumentationResult = DocumentationResult
            .documentation(token.html())
            .externalUrl("https://square.github.io/kotlinpoet/")
    }
}

private sealed interface DocToken {
    data class Placeholder(val kind: PlaceholderSpec.FormatKind) : DocToken
    data class Control(val type: ControlSymbol.SymbolType) : DocToken
}

private fun DocToken.presentationTitle(): String = when (this) {
    is DocToken.Placeholder -> KPoetAssistantBundle.getMessage("doc.placeholder.title", kind.value)
    is DocToken.Control -> KPoetAssistantBundle.getMessage("doc.control.title", type.value)
}

private fun DocToken.html(): String = when (this) {
    is DocToken.Placeholder -> {
        val key = "doc.placeholder.${kind.value}"
        val descKey = "$key.desc"

        docHtml(
            KPoetAssistantBundle.getMessage(key),
            KPoetAssistantBundle.getMessage(descKey),
        )
    }

    is DocToken.Control -> {
        val key = controlKey(type)
        val descKey = "$key.desc"

        docHtml(
            KPoetAssistantBundle.getMessage(key),
            KPoetAssistantBundle.getMessage(descKey),
        )
    }
}

private fun controlKey(type: ControlSymbol.SymbolType): String = when (type) {
    ControlSymbol.SymbolType.LITERAL_PERCENT -> "doc.control.percent"
    ControlSymbol.SymbolType.SPACE_OR_NEW_LINE -> "doc.control.wrap"
    ControlSymbol.SymbolType.SPACE -> "doc.control.space"
    ControlSymbol.SymbolType.INDENT -> "doc.control.indent"
    ControlSymbol.SymbolType.OUTDENT -> "doc.control.outdent"
    ControlSymbol.SymbolType.STATEMENT_BEGIN -> "doc.control.statement.begin"
    ControlSymbol.SymbolType.STATEMENT_END -> "doc.control.statement.end"
    else -> "doc.unknown.control"
}

private fun resolveControlSymbolFromSource(
    file: PsiFile,
    template: KtStringTemplateExpression,
    offset: Int,
): ControlSymbol.SymbolType? {
    if (!template.textRange.contains(offset)) return null

    val text = file.text
    val current = text.getOrNull(offset) ?: return null

    if (current == '%' && text.getOrNull(offset + 1) == '%') {
        return ControlSymbol.SymbolType.LITERAL_PERCENT
    }

    return CONTROL_SYMBOLS[current]
}

private val CONTROL_SYMBOLS: Map<Char, ControlSymbol.SymbolType> = mapOf(
    '♢' to ControlSymbol.SymbolType.SPACE_OR_NEW_LINE,
    '·' to ControlSymbol.SymbolType.SPACE,
    '⇥' to ControlSymbol.SymbolType.INDENT,
    '⇤' to ControlSymbol.SymbolType.OUTDENT,
    '«' to ControlSymbol.SymbolType.STATEMENT_BEGIN,
    '»' to ControlSymbol.SymbolType.STATEMENT_END,
)

private fun docHtml(title: String, body: String): String = """
${DocumentationMarkup.DEFINITION_START}$title${DocumentationMarkup.DEFINITION_END}
${DocumentationMarkup.CONTENT_START}$body${DocumentationMarkup.CONTENT_END}
""".trimIndent()
