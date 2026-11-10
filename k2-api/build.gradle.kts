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

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("k2LegacyPlatformVersion"))
        bundledPlugin("org.jetbrains.kotlin")
    }
}
