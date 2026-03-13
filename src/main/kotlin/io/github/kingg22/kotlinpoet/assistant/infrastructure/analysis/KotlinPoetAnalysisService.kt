package io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import io.github.kingg22.kotlinpoet.assistant.domain.extractor.FormatContextExtractorRegistry
import org.jetbrains.kotlin.psi.KtCallExpression

@Suppress("UnstableApiUsage")
private val logger = fileLogger()

internal val KEY_CONTEXT: Key<CachedValue<KotlinPoetAnalysis>> = Key.create("kotlinpoet.context")

fun putCachedAnalysis(call: KtCallExpression, kotlinPoetAnalysis: KotlinPoetAnalysis) {
    val project = call.project
    val manager = CachedValuesManager.getManager(project) ?: return
    manager.createCachedValue {
        CachedValueProvider.Result.create(kotlinPoetAnalysis, call, call.containingFile)
    }.also {
        call.putUserData(KEY_CONTEXT, it)
    }
}

fun getCachedAnalysis(call: KtCallExpression?): KotlinPoetAnalysis? {
    if (call == null) return null
    val project = call.project
    val manager = CachedValuesManager.getManager(project) ?: return null

    val cached = call.getUserData(KEY_CONTEXT)?.takeIf { it.hasUpToDateValue() }
        ?: manager.createCachedValue {
            val context = if (DumbService.isDumb(project)) {
                logger.info("Skipping extract kotlinpoet call in dumb mode")
                null
            } else {
                FormatContextExtractorRegistry.extract(call)
            }

            CachedValueProvider.Result.create(
                context?.let { KotlinPoetAnalysis(context, isBound = false, isValidated = false) },
                call,
                call.containingFile,
            )
        }.also {
            call.putUserData(KEY_CONTEXT, it)
        }

    return cached.value
}
