package io.github.kingg22.kotlinpoet.assistant.infrastructure.inspection.quickfixes

import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/*
 * Plain-PSI replacements for `KtValueArgumentList.addArgument/removeValueArgumentCompat`. Those moved between packages
 * (`org.jetbrains.kotlin.idea.base.psi`) across IDE generations, so relying on them breaks the
 * binary compatibility with older/newer IDEs. These helpers only use stable PSI APIs.
 */

/** Appends [argument] at the end of this list, inserting the separating comma when needed. */
internal fun KtValueArgumentList.appendValueArgumentCompat(argument: KtValueArgument): KtValueArgument {
    val closing = rightParenthesis
    if (arguments.isNotEmpty()) {
        addBefore(KtPsiFactory(project).createComma(), closing)
        addBefore(KtPsiFactory(project).createWhiteSpace(), closing)
    }
    return addBefore(argument, closing) as KtValueArgument
}

/** Removes [argument] from this list together with its adjacent comma and whitespace. */
internal fun KtValueArgumentList.removeValueArgumentCompat(argument: KtValueArgument) {
    val nextComma = PsiTreeUtil.skipSiblingsForward(argument, PsiWhiteSpace::class.java)
        ?.takeIf { it.node.elementType == KtTokens.COMMA }
    val previousComma = PsiTreeUtil.skipSiblingsBackward(argument, PsiWhiteSpace::class.java)
        ?.takeIf { it.node.elementType == KtTokens.COMMA }

    // Prefer dropping the trailing comma; for the last argument drop the leading one.
    val comma = nextComma ?: previousComma
    if (comma != null) {
        val whitespaceAfter = comma.nextSibling as? PsiWhiteSpace
        if (comma === nextComma) whitespaceAfter?.delete()
        comma.delete()
    }
    argument.delete()
}
