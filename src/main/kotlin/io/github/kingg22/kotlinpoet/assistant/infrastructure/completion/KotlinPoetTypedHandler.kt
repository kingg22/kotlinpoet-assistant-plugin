package io.github.kingg22.kotlinpoet.assistant.infrastructure.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis.getCachedAnalysis
import io.github.kingg22.kotlinpoet.assistant.infrastructure.looksLikeKotlinPoetCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class KotlinPoetTypedHandler : TypedHandlerDelegate() {
    private val logger = thisLogger()

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        try {
            if (charTyped != '%') return Result.CONTINUE

            val psiFile = file as? KtFile ?: return Result.CONTINUE
            val offset = editor.caretModel.offset

            val element = psiFile.findElementAt(offset - 1) ?: return Result.CONTINUE

            val stringExpr = PsiTreeUtil.getParentOfType(
                element,
                KtStringTemplateExpression::class.java,
            ) ?: return Result.CONTINUE

            val call = PsiTreeUtil.getParentOfType(
                stringExpr,
                KtCallExpression::class.java,
            ) ?: return Result.CONTINUE

            val kotlinPoetCallAnalysis = getCachedAnalysis(call)
            if (kotlinPoetCallAnalysis != null) return Result.CONTINUE
            if (!call.looksLikeKotlinPoetCall()) return Result.CONTINUE

            // Disparar popup de completion
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)

            return Result.STOP
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            logger.error("Error trying to trigger KotlinPoet completion popup", e)
            return Result.CONTINUE
        }
    }
}
