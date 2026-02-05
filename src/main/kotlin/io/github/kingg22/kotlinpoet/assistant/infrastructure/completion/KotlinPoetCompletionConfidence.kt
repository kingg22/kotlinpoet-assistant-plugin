package io.github.kingg22.kotlinpoet.assistant.infrastructure.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import io.github.kingg22.kotlinpoet.assistant.infrastructure.looksLikeKotlinPoetCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinPoetCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(
        editor: Editor,
        contextElement: PsiElement,
        psiFile: PsiFile,
        offset: Int,
    ): ThreeState {
        val element = psiFile.findElementAt(offset - 1) ?: return ThreeState.UNSURE

        val stringExpr = PsiTreeUtil.getParentOfType(
            element,
            KtStringTemplateExpression::class.java,
        ) ?: return ThreeState.UNSURE

        val call = PsiTreeUtil.getParentOfType(
            stringExpr,
            KtCallExpression::class.java,
        ) ?: return ThreeState.UNSURE

        // PSI ONLY — nada de Analysis API aquí
        if (!call.looksLikeKotlinPoetCall()) return ThreeState.UNSURE

        // CONFÍO en este contexto
        return ThreeState.NO
    }
}
