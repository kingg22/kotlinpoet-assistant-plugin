package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.DocumentationMarkup
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import io.github.kingg22.kotlinpoet.assistant.domain.model.ControlSymbol.SymbolType
import io.github.kingg22.kotlinpoet.assistant.domain.model.PlaceholderSpec

sealed interface DocToken {
    fun presentationTitle(): String
    fun html(): String

    data class Placeholder(val kind: PlaceholderSpec.FormatKind) : DocToken {
        override fun presentationTitle(): String = KPoetAssistantBundle.getMessage("doc.placeholder.title", kind.value)
        override fun html(): String {
            val key = "doc.placeholder.${kind.value}"
            val descKey = "$key.desc"

            return docHtml(
                KPoetAssistantBundle.getMessage(key),
                KPoetAssistantBundle.getMessage(descKey),
            )
        }
    }

    data class Control(val type: SymbolType) : DocToken {
        override fun presentationTitle(): String = KPoetAssistantBundle.getMessage("doc.control.title", type.value)
        override fun html(): String {
            val key = controlKey(type)
            val descKey = "$key.desc"

            return docHtml(
                KPoetAssistantBundle.getMessage(key),
                KPoetAssistantBundle.getMessage(descKey),
                DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + "com.squareup.kotlinpoet.CodeBlock",
                "CodeBlock KDoc",
            )
        }
    }
}

private fun controlKey(type: SymbolType): String = when (type) {
    SymbolType.LITERAL_PERCENT -> "doc.control.percent"
    SymbolType.SPACE_OR_NEW_LINE -> "doc.control.wrap"
    SymbolType.SPACE -> "doc.control.space"
    SymbolType.INDENT -> "doc.control.indent"
    SymbolType.OUTDENT -> "doc.control.outdent"
    SymbolType.STATEMENT_BEGIN -> "doc.control.statement.begin"
    SymbolType.STATEMENT_END -> "doc.control.statement.end"
    else -> "doc.unknown.control"
}

private fun docHtml(title: String, body: String, externalUrl: String? = null, externalUrlName: String? = null): String {
    val linksSection = if (externalUrl != null && externalUrlName != null) {
        """
        ${DocumentationMarkup.SECTIONS_START}
        ${DocumentationMarkup.SECTION_HEADER_START}See also${DocumentationMarkup.SECTION_SEPARATOR}
        <a href="$externalUrl">$externalUrlName</a>
        ${DocumentationMarkup.SECTION_END}
        ${DocumentationMarkup.SECTIONS_END}
        """.trimIndent()
    } else {
        ""
    }

    return """
        ${DocumentationMarkup.DEFINITION_START}$title${DocumentationMarkup.DEFINITION_END}
        ${DocumentationMarkup.CONTENT_START}$body${DocumentationMarkup.CONTENT_END}
        $linksSection
    """.trimIndent()
}
