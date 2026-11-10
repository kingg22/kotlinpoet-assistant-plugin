import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform.base)
}

kotlin {
    compilerOptions {
        // Same rule as the root project: not above the stdlib bundled by the oldest supported IDE (2025.2 -> 2.1.20).
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
        apiVersion.set(languageVersion)
        freeCompilerArgs.addAll("-XXexplicit-return-types=strict", "-Xsuppress-version-warnings")
        allWarningsAsErrors.set(true)
    }
}

kotlin {
    jvmToolchain(21)
}

// The platform 'base' plugin does not register this configuration, but its `intellijPlatformDoctor` task requires it.
// IDE Gradle sync realizes every task, so without it the project fails to import. It is intentionally empty.
configurations.maybeCreate("intellijPlatformComposedJar")

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("k2LegacyPlatformVersion"))
        bundledPlugin("org.jetbrains.kotlin")
    }
}
