package io.github.kingg22.kotlinpoet.assistant.infrastructure

import com.intellij.openapi.diagnostic.ControlFlowException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Rethrows [throwable] when it is used by the platform for control flow (cancellation of
 * read actions, `ProcessCanceledException`, coroutine cancellation). Swallowing those breaks the platform.
 *
 * Own implementation of the platform's `rethrowControlFlowException`/`Logger.shouldRethrow`:
 * the former does not exist before IDE 2026.x and the latter is deprecated in newer ones, so
 * depending on either breaks the binary compatibility with part of the supported range.
 */
fun rethrowControlFlowException(throwable: Throwable) {
    if (throwable is ControlFlowException || throwable is CancellationException) throw throwable
}
