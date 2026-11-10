package io.github.kingg22.kotlinpoet.assistant.k2

import com.intellij.psi.PsiElement

/** @property callableName simple name of the resolved callable. */
data class ResolvedCallInfo(
    val callableName: String?,
    /** Dispatch receiver type, falling back to the extension receiver type. */
    val receiverType: TypeInfo?,
    /** PSI declaration of the resolved symbol, if it has source. */
    val symbolPsi: PsiElement?,
)
