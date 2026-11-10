import com.diffplug.spotless.LineEnding
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.ChangelogPluginConstants
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    java
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.jetbrains.changelog)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-XXexplicit-return-types=strict",
            "-jvm-default=no-compatibility",
            "-Xcontext-parameters",
            // The compiler (2.4) deprecates language 2.1, but it is the stdlib level of the oldest supported IDE.
            "-Xsuppress-version-warnings",
        )
        optIn.addAll("kotlin.contracts.ExperimentalContracts")
        // The stdlib is NOT bundled: the IDE provides it. `apiVersion` (the stdlib API the code may call) must not
        // exceed the stdlib of the oldest supported IDE: since-build 252 (2025.2) bundles 2.1.20.
        // https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#bundled-stdlib-versions
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        // `languageVersion` only enables compiler features and needs no newer runtime. 2.2 is the minimum for the
        // context parameters this project uses (`-Xcontext-parameters`).
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        extraWarnings.set(true)
        allWarningsAsErrors.set(true)
    }
}

// Pin javac and kotlinc to JDK 21 regardless of the JDK that runs Gradle (the default target follows it otherwise).
// 21 is the minimum runtime of every supported IDE (JBR 21+), and bytecode 21 runs on newer ones.
kotlin {
    jvmToolchain(21)
}

// IDE versions to launch the plugin on by hand: `./gradlew runIde262` etc. The plugin is built once against the
// compile baseline (`platformVersion`) and runs unchanged on each of them, which is the real end-to-end check that
// the version-specific K2 module is picked and loaded. Automated checks: `verifyPlugin` and the `-PplatformVersion`
// test matrix in CI.
val runIdeVersions = mapOf(
    "253" to "253.33813.99",
    "261" to "261.27258.136",
    "262" to "262.11879.100",
    "263" to "2026.3",
)

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/version_catalogs.html
dependencies {
    // https://youtrack.jetbrains.com/issue/IJPL-159134
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html?from=jetbrains.org#junit5-test-framework-refers-to-junit4
    testCompileOnly(libs.junit4)
    testImplementation(platform(libs.junit6.bom))
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.parametrized)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform)
    testRuntimeOnly(libs.junit.vintage.engine)

    // K2 Analysis API facade: one implementation per API flavour, selected at runtime by IDE build number.
    // Both are referenced directly; the JVM links lazily, so only the matching one is ever loaded.
    implementation(project(":k2-api"))
    implementation(project(":k2-legacy"))
    implementation(project(":k2-modern"))

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Bundled)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        id = "io.github.kingg22.kotlinpoet_assistant"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        vendor {
            email = "89825425+kingg22@users.noreply.github.com"
            url = "https://github.com/kingg22"
            name = "Rey Acosta (Kingg22)"
        }

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map { text ->
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            val lines = text.lines()
            val sections = mutableListOf<String>()

            var i = 0
            while (i < lines.size) {
                if (lines[i] == start) {
                    val startIndex = i + 1
                    val endIndex = lines.subList(startIndex, lines.size).indexOf(end)

                    if (endIndex == -1) {
                        throw GradleException("Closing tag not found for a section starting at line $i")
                    }

                    val section = lines.subList(startIndex, startIndex + endIndex)
                        .toMutableList()
                        .apply {
                            remove("> [!NOTE]")
                            remove("> [!WARNING]")
                            remove("> [!TIP]")
                            remove("> [!IMPORTANT]")
                            remove("> [!CAUTION]")
                        }
                        .joinToString("\n")

                    sections.add(markdownToHTML(section))

                    i = startIndex + endIndex
                }
                i++
            }

            if (sections.isEmpty()) {
                throw GradleException("No plugin description sections found in README.md")
            }

            sections.joinToString("\n\n")
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // Explicitly open-ended: never cap the plugin to the IDE it was built against.
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { version ->
            listOf(version.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    versionPrefix = ""
    groups = ChangelogPluginConstants.GROUPS + "Technical"
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://kotlin.github.io/kotlinx-kover/gradle-plugin/#configuration-details
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
            verify {
                rule("Basic Line Coverage") {
                    minBound(60, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
                }

                rule("Basic Branch Coverage") {
                    minBound(20, CoverageUnit.BRANCH, AggregationType.COVERED_PERCENTAGE)
                }
            }
        }
    }
}

intellijPlatformTesting {
    runIde {
        runIdeVersions.forEach { (build, version) ->
            register("runIde$build") {
                this.version = version
                type = IntelliJPlatformType.IntellijIdea
                plugins {
                    bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
                }
            }
        }
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }
        }
    }
}

spotless {
    encoding = Charsets.UTF_8
    lineEndings = LineEnding.PRESERVE
    // enable when use Compose to build UI
    // ktlint(libs.ktlint.compose)
    kotlin {
        ktlint(libs.versions.ktlint.pinterest.get())
    }
    kotlinGradle {
        ktlint(libs.versions.ktlint.pinterest.get())
    }
    java {
        removeUnusedImports()
        palantirJavaFormat(libs.versions.palantir.get()).formatJavadoc(true)
        importOrder("", "java", "javax", "\\#")
        formatAnnotations()
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.publishPlugin {
    dependsOn(tasks.patchChangelog)
}
