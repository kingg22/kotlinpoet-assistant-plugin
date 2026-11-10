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

// The platform 'base' plugin does not register this configuration, but its `intellijPlatformDoctor` task requires it.
// IDE Gradle sync realizes every task, so without it the project fails to import. It is intentionally empty.
configurations.maybeCreate("intellijPlatformComposedJar")

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("k2ModernPlatformVersion"))
        bundledPlugin("org.jetbrains.kotlin")
    }
    compileOnly(project(":k2-api"))
}
