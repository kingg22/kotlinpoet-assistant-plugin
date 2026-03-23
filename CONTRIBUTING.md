# Contributing to KotlinPoet Assistant Plugin

## Requirements
- JDK 21 (auto-downloaded by Gradle)
- IntelliJ IDEA 2025.3+

## Architecture

- KotlinPoet Assistant Plugin is a [Gradle](https://gradle.org/) project.
- The plugin is built using [Kotlin](https://kotlinlang.org/), [Java] and [Gradle](https://gradle.org/).
- The plugin depends on Kotlin Plugin.
- The plugin is tested using [JUnit 5](https://junit.org/junit5/) and
[IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html) that use Junit3 and
JUnit4 compatibility layers.

### Contributing Screenshots

Good screenshots make the plugin easier to discover. To contribute:

1. Install the plugin from source (`./gradlew runIde`).
2. Open a file that uses KotlinPoet.
3. Take a screenshot at **1×** scale (no HiDPI scaling artifacts) showing the feature clearly.
4. Name the file as suggested in the [Screenshots](README#screenshots) section above.
5. Open a PR adding the images to `docs/screenshots/` and update this README to reference them.

### Guidelines

1. Must not be invasive and needs to be configurable
2. Must not be a plugin that is not a part of the KotlinPoet
3. Must have a confidence level or disable it by default
4. Must have test, documentation and examples
5. Must use the latest IntelliJ Platform APIs or justificated exceptions

### Structure

```text
.
├── .github/                         GitHub Actions workflows configuration files
├── .run/                            Predefined Run/Debug Configurations
├── build/                           Output build directory
├── docs                             Documentation of the plugin
│ ├── kotlinpoet/                    KotlinPoet Official Documentation and some source codes
│ └── screenshots/                   Screenshots of the plugin
├ gradle
│ ├── wrapper/                       Gradle Wrapper
│ └── gradle-daemon-jvm.properties   Gradle Daemon JVM
│ └── libs.versions.toml             Gradle version catalog
├ src                                Plugin sources
│ ├── main
│ │ ├── java/                        Java production sources (for static and final fields)
│ │ ├── kotlin/                      Kotlin production sources
│ │ ├── io/github/kingg22/kotlinpoet/assistant
│ │ │ ├── adapters                   Bridge between IntelliJ Platform and Domain
│ │ │ │   └── types
│ │ │ ├── domain                     Domain layer (must not contain any IntelliJ Platform API)
│ │ │ │   ├── binding                Binding between placeholder and arguments
│ │ │ │   ├── chain                  API specific to the method chain
│ │ │ │   ├── extractor              Extract the data of PSI elements (K2 Analysis API)
│ │ │ │   ├── model                  Data model for call methods
│ │ │ │   ├── parser                 String parser of CodeBlock string format
│ │ │ │   ├── text                   Domain layer text representation of Text Range of PSI elements
│ │ │ │   └── validation             Validation of the model
│ │ │ │   │   └── validators         Rules of CodeBlock for Inspections
│ │ │ │   └── infrastructure         Integration with IntelliJ Platform
│ │ │ │     ├── analysis             Analysis cached data for method call
│ │ │ │     ├── annotator            Annotate the PSI elements (highlighting)
│ │ │ │     ├── chain                PSI Navigator for method chain
│ │ │ │     ├── completion           Completion for placeholders with confidence
│ │ │ │     ├── documentation        Documentation for placeholders and control symbols
│ │ │ │     ├── hints                Inlay Hints for method call and method chain
│ │ │ │     ├── inspection
│ │ │ │     │ ├── inspections        Inspections for method call
│ │ │ │     │ └── quickfixes         Quickfixes for inspections
│ │ │ │     ├── references           Usages of the placeholders reference to arguments
│ │ │ │     ├── reporting            Error handling reporting
│ │ │ │     └── toolwindow           Toolwindow for method call and method chain
│ │ └── resources/                   Resources - plugin.xml, descriptions, messages
│ └── test
│   ├── kotlin/                      Kotlin test sources
│   └── testData/                    Test data used by tests classify by infrastructure
├── .editorconfig                    Editor style and format rules
├── .gitignore                       Git ignoring rules
├── .gitattributes                   Git attributes rules
├── build.gradle(.kts)               Gradle configuration
├── CHANGELOG.md                     Full change history
├── codecov.yml                      Coverage report configuration used in CI
├── gradle.properties                Gradle configuration properties
├── gradlew                          *nix Gradle Wrapper script
├── gradlew.bat                      Windows Gradle Wrapper script
├── LICENSE                          License
├── README.md                        README
├── Template.md                      README of the plugin template
└── settings.gradle(.kts)            Gradle project settings
```
