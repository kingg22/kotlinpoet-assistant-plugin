package io.github.kingg22.kotlinpoet.assistant.infrastructure.analysis

import com.intellij.openapi.application.ApplicationInfo
import io.github.kingg22.kotlinpoet.assistant.k2.KotlinAnalysisBridge
import io.github.kingg22.kotlinpoet.assistant.k2.LegacyKotlinAnalysisBridge
import io.github.kingg22.kotlinpoet.assistant.k2.ModernKotlinAnalysisBridge

/**
 * Entry point to the K2 Analysis API.
 *
 * Selects, once, the [KotlinAnalysisBridge] implementation that matches the running IDE. The two
 * implementations live in separate modules compiled against different IDE versions, so the one that
 * does not match is never loaded or linked.
 *
 * Keep [MODERN_SINCE_BUILD] in sync with `k2ModernSinceBuild` in `gradle.properties`.
 */
object KotlinAnalysisBridges {
    /** First IDE baseline where `KaSession` members use context parameters. */
    private const val MODERN_SINCE_BUILD = 263

    @JvmStatic
    val instance: KotlinAnalysisBridge by lazy {
        // Direct references are safe: the JVM resolves a class only when the branch that
        // instantiates it executes, so the non-matching flavour is never loaded or linked.
        if (ApplicationInfo.getInstance().build.baselineVersion >= MODERN_SINCE_BUILD) {
            ModernKotlinAnalysisBridge()
        } else {
            LegacyKotlinAnalysisBridge()
        }
    }
}
