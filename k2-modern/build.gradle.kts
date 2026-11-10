import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform.base)
}

kotlin {
    compilerOptions {
        // Only loaded on IDE builds >= k2ModernSinceBuild, whose bundled stdlib is >= 2.4, so 2.2 (required to call
        // context-parameter declarations) is safe here.
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(languageVersion)
        freeCompilerArgs.addAll("-XXexplicit-return-types=strict", "-Xcontext-parameters")
        allWarningsAsErrors.set(true)
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("k2ModernPlatformVersion"))
        bundledPlugin("org.jetbrains.kotlin")
    }
    compileOnly(project(":k2-api"))
}
