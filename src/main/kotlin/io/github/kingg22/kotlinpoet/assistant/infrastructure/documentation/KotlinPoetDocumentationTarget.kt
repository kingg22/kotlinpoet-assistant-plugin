package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation.DocToken.Control
import io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation.DocToken.Placeholder
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Documentation target for a KotlinPoet placeholder or control symbol.
 *
 * [contextElement] is the `KtStringTemplateExpression` that contains the placeholder.
 * It is exposed as a public property so [KotlinPoetDocumentationLinkHandler] can use
 * it as the resolution anchor for synthetic KDoc fragments — without embedding link
 * handling logic inside this class or taking a `PsiFile` in the constructor.
 */
@Suppress("UnstableApiUsage")
class KotlinPoetDocumentationTarget(
    private val token: DocToken,
    /** The PSI element providing the resolution scope for `psi_element://` links. */
    val contextElement: KtStringTemplateExpression,
) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation = TargetPresentation
        .builder(token.presentationTitle())
        .presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult
        .documentation(token.html())
        .externalUrl(token.externalUrl() ?: "https://square.github.io/kotlinpoet/")
}

private fun DocToken.externalUrl(): String? = when (this) {
    is Placeholder -> PLACEHOLDER_DOC_URLS[kind.value]
    is Control -> "https://square.github.io/kotlinpoet/1.x/kotlinpoet/kotlinpoet/com.squareup.kotlinpoet/-code-block/"
}

private val PLACEHOLDER_DOC_URLS = mapOf(
    'L' to "https://square.github.io/kotlinpoet/l-for-literals/",
    'S' to "https://square.github.io/kotlinpoet/s-for-strings/",
    'T' to "https://square.github.io/kotlinpoet/t-for-types/",
    'N' to "https://square.github.io/kotlinpoet/n-for-names/",
    'M' to "https://square.github.io/kotlinpoet/m-for-members/",
    'P' to "https://square.github.io/kotlinpoet/p-for-string-templates/",
)
