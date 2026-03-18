package io.github.kingg22.kotlinpoet.assistant.infrastructure.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.ExceptionUtil
import com.intellij.util.ThreeState
import io.github.kingg22.kotlinpoet.assistant.infrastructure.looksLikeKotlinPoetCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinPoetCompletionConfidence : CompletionConfidence() {
    private val logger = thisLogger()

    override fun shouldSkipAutopopup(
        editor: Editor,
        contextElement: PsiElement,
        psiFile: PsiFile,
        offset: Int,
    ): ThreeState {
        try {
            val element = psiFile.findElementAt(offset - 1) ?: return ThreeState.UNSURE

            val stringExpr = element.parentOfType<KtStringTemplateExpression>() ?: return ThreeState.UNSURE

            val call = stringExpr.parentOfType<KtCallExpression>() ?: return ThreeState.UNSURE

            // PSI ONLY — nada de Analysis API aquí
            if (!call.looksLikeKotlinPoetCall()) return ThreeState.UNSURE

            // CONFÍO en este contexto
            return ThreeState.NO
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            logger.error("Error trying to determine if KotlinPoet completion popup should be skipped", e)
            return ThreeState.UNSURE
        }
    }
}
