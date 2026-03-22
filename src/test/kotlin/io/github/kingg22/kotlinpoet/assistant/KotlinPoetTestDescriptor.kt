package io.github.kingg22.kotlinpoet.assistant

import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil

/**
 * Centralized [DefaultLightProjectDescriptor] for all KotlinPoet Assistant light tests.
 *
 * Adds the real KotlinPoet library from Maven so K2 Analysis API can resolve
 * `com.squareup.kotlinpoet.*` types correctly — replacing the hand-written stubs that
 * were insufficient for full semantic resolution (e.g., FunSpec.Builder, FileSpec.Builder).
 *
 * ## Usage
 *
 * Override [com.intellij.testFramework.fixtures.BasePlatformTestCase.getProjectDescriptor]:
 *
 * ```kotlin
 * override fun getProjectDescriptor() = KotlinPoetTestDescriptor.projectDescriptor
 * ```
 *
 * ## Why not stubs?
 *
 * Hand-written stubs in `testData/stubs/KotlinPoet.kt` only cover the surface used in
 * individual test files. K2 Analysis API needs the full type hierarchy (supertypes,
 * companions, extension functions) to resolve calls like `FunSpec.Builder.addStatement`
 * or to walk delegating methods. The real artifact provides all of this.
 *
 * ## Kotlin stdlib
 *
 * The IntelliJ Platform test framework bundles the Kotlin plugin and its stdlib, so we
 * don't need to add `kotlin-stdlib` explicitly — it is already on the module classpath
 * via the bundled Kotlin plugin.
 */
object KotlinPoetTestDescriptor {

    /** KotlinPoet version used across all tests. Bump here to update all tests at once. */
    const val KOTLINPOET_VERSION: String = "2.2.0"

    private const val KOTLINPOET_COORDINATES = "com.squareup:kotlinpoet-jvm:$KOTLINPOET_VERSION"

    /**
     * Descriptor that adds KotlinPoet to the module classpath.
     * Use this for any test that exercises K2 Analysis API against KotlinPoet types.
     */
    val projectDescriptor: DefaultLightProjectDescriptor = object : DefaultLightProjectDescriptor() {
        override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)
            MavenDependencyUtil.addFromMaven(
                model,
                KOTLINPOET_COORDINATES,
                true,
                DependencyScope.COMPILE,
                listOf(RemoteRepositoryDescription.MAVEN_CENTRAL),
            )
        }
    }
}
