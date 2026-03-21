package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil.findChildOfType
import com.intellij.util.ExceptionUtil
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtPsiFactory

class KotlinPoetDocumentationLinkHandler : DocumentationLinkHandler {
    private val logger = thisLogger()

    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (target !is KotlinPoetDocumentationTarget) return null

        if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
            val names = url.substring(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL.length)
            val target = resolveKDocLink(names, target.contextElement) ?: return null
            val psiDocumentationTarget = wrapAsDocumentationTarget(target, target) ?: return null
            return LinkResolveResult.resolvedTarget(psiDocumentationTarget)
        }
        logger.warn("Unsupported documentation link protocol: $url")

        return null
    }

    /** Copied from [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.resolveKDocLink] to prevent usage of Kotlin K1 package */
    private fun resolveKDocLink(fqn: String, element: PsiElement): PsiElement? {
        val ktPsiFactory = KtPsiFactory(element.project)
        val fragment = ktPsiFactory.createBlockCodeFragment("/** [$fqn] */ val __p = 42", element)
        return findChildOfType(fragment, KDocName::class.java)?.reference?.resolve()
    }

    /**
     * Wraps a resolved [PsiElement] as a [DocumentationTarget] using the
     * [PsiDocumentationTargetProvider] extension point chain — the public API
     * equivalent of the internal [com.intellij.lang.documentation.psi.psiDocumentationTargets] function.
     *
     * Iterates all registered providers. The Kotlin plugin registers one that handles
     * `KtElement`, and IntelliJ's core registers one for Java `PsiClass`. The first
     * non-empty result wins, matching the internal function's semantics.
     *
     * Returns `null` if no provider produces a target (extremely unlikely for a successfully resolved KotlinPoet class).
     */
    @Suppress("UnstableApiUsage", "OverrideOnly")
    private fun wrapAsDocumentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? =
        try {
            PsiDocumentationTargetProvider.EP_NAME
                .extensionList
                .firstNotNullOfOrNull { provider ->
                    provider?.documentationTargets(element, originalElement)?.firstOrNull()
                }
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            logger.warn("Error wrapping resolved PsiElement as DocumentationTarget", e)
            null
        }
}
