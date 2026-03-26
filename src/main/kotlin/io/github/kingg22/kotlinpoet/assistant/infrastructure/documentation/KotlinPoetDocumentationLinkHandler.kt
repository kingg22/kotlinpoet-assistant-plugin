package io.github.kingg22.kotlinpoet.assistant.infrastructure.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
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
     * Wraps a resolved [PsiElement] as a [DocumentationTarget] by querying the
     * [PsiDocumentationTargetProvider] extension point chain via its public EP name string,
     * avoiding the use of the [Internal] [PsiDocumentationTargetProvider.EP_NAME] field.
     *
     * [PsiDocumentationTargetProvider] is annotated [@OverrideOnly][ApiStatus.OverrideOnly]:
     * it is designed to be *implemented*, not consumed directly. However, there is no public
     * API that replicates the internal `psiDocumentationTargets()` function, so calling the
     * providers is the only correct option. The suppression is intentional and minimal.
     *
     * Returns `null` if no provider produces a target (extremely unlikely for a successfully
     * resolved KotlinPoet class).
     */
    @Suppress("OverrideOnly")
    private fun wrapAsDocumentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? =
        try {
            ExtensionPointName.create<PsiDocumentationTargetProvider>(
                "com.intellij.platform.backend.documentation.psiTargetProvider",
            )
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
