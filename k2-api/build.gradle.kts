import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform.module)
}

kotlin {
    compilerOptions {
        // Keep the language level of the oldest supported IDE (252 bundles Kotlin 2.2).
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(languageVersion)
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-XXexplicit-return-types=strict")
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("k2LegacyPlatformVersion"))
        bundledPlugin("org.jetbrains.kotlin")
    }
}
