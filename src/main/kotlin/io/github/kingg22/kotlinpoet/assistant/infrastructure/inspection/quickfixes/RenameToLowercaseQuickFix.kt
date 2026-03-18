package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Renames a named-argument key that violates KotlinPoet's lowercase requirement.
 *
 * Two edits are applied atomically inside the same write-command action:
 * 1. **Format string** – every occurrence of `%<OldName>:` is replaced by `%<newName>:`.
 * 2. **Map key** – the string-literal key `"<OldName>"` inside the `mapOf(...)` / `"<OldName>" to`
 *    expression in the second argument is renamed to `"<newName>"`.
 *
 * Limitation: only handles simple inline maps whose entries are written as binary infix expressions
 * (`"key" to value`) or `Pair("key", value)` calls (the common case produced by `mapOf`). External
 * variable maps are not modified.
 */
class RenameToLowercaseQuickFix(private val originalName: String) : LocalQuickFix {

    private val lowercaseName: String = originalName.lowercase()

    @Nls
    override fun getName(): String =
        KPoetAssistantBundle.getMessage("quickfix.rename.to.lowercase", originalName, lowercaseName)

    @Nls
    override fun getFamilyName(): String = KPoetAssistantBundle.getMessage("inspection.group.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? KtCallExpression ?: return
        val args = call.valueArguments
        if (args.size < 2) return

        val factory = KtPsiFactory(project)

        // 1. Rename in the format string: replace %OriginalName: → %lowercaseName:
        val formatExpr = args[0].getArgumentExpression() as? KtStringTemplateExpression
        if (formatExpr != null) {
            val oldText = formatExpr.text
            val newText = oldText.replace(Regex("%$originalName:"), "%$lowercaseName:")
            if (oldText != newText) {
                val newExpr = factory.createExpression(newText)
                formatExpr.replace(newExpr)
            }
        }

        // 2. Rename the map key inside the second argument.
        //    We re-fetch call.valueArguments because the format-string replacement above may have
        //    shifted PSI, so always work from a fresh reference.
        val callAfterFormatFix = descriptor.psiElement as? KtCallExpression ?: return
        val mapArgExpr = callAfterFormatFix.valueArguments
            .getOrNull(1)
            ?.getArgumentExpression() as? KtCallExpression ?: return

        for (entry in mapArgExpr.valueArguments) {
            val entryExpr = entry.getArgumentExpression() as? KtBinaryExpression ?: continue
            val keyLiteral = entryExpr.left as? KtStringTemplateExpression ?: continue

            // KtStringTemplateExpression.text includes the surrounding quotes, e.g. "\"Food\""
            val keyContent = keyLiteral.text.removeSurrounding("\"")
            if (keyContent == originalName) {
                val newKey = factory.createExpression("\"$lowercaseName\"")
                keyLiteral.replace(newKey)
                break // each key should appear at most once in a valid map
            }
        }
    }
}
