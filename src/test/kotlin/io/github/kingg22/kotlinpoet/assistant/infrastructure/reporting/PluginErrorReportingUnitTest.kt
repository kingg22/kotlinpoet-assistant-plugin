package io.github.kingg22.kotlinpoet.assistant.infrastructure.reporting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.awt.Desktop
import java.awt.GraphicsEnvironment

/**
 * Pure JUnit 5 unit tests for the top-level `@VisibleForTesting` functions in
 * the reporting package.
 *
 * No IntelliJ Platform required — [buildBody] is tested with a hardcoded stub for
 * the IDE/OS/JVM info sections that [com.intellij.openapi.application.ApplicationInfo] would normally provide.
 * [buildGitHubIssueUrl], [buildTitle], [buildStacktrace], [copyToClipboard], and
 * [openBrowser] have no platform dependencies at all.
 */
class PluginErrorReportingUnitTest {

    // ── buildTitle ─────────────────────────────────────────────────────────────

    @Nested
    inner class BuildTitle {

        @Test
        fun `null event produces UnknownException fallback`() {
            val title = buildTitle(null)
            assertTrue(title.startsWith("[IDE Exception]"), "Title: $title")
            assertTrue(title.contains("UnknownException"), "Title: $title")
        }

        @Test
        fun `exception with message includes both class and message`() {
            val title = buildTitle(fakeEvent(RuntimeException("Something went wrong")))
            assertTrue(title.startsWith("[IDE Exception]"))
            assertTrue(title.contains("RuntimeException"))
            assertTrue(title.contains("Something went wrong"))
        }

        @Test
        fun `exception with null message omits colon-suffix`() {
            val title = buildTitle(fakeEvent(RuntimeException()))
            assertTrue(title.contains("RuntimeException"))
            assertFalse(title.trimEnd().endsWith(":"), "Title should not end with ':' when message is null: $title")
        }

        @Test
        fun `blank message is omitted`() {
            val title = buildTitle(fakeEvent(RuntimeException("   ")))
            assertFalse(title.contains(":   "), "Blank message should not appear: $title")
        }

        @Test
        fun `long message is truncated to 80 chars`() {
            val longMessage = "X".repeat(200)
            val title = buildTitle(fakeEvent(RuntimeException(longMessage)))
            val messageInTitle = title.substringAfter(": ")
            assertTrue(
                messageInTitle.length <= 80,
                "Message portion should be ≤ 80 chars, got ${messageInTitle.length}",
            )
        }
    }

    // ── buildStacktrace ────────────────────────────────────────────────────────

    @Nested
    inner class BuildStacktrace {

        @Test
        fun `null throwable returns fallback string`() {
            assertEquals("No stacktrace available", buildStacktrace(null))
        }

        @Test
        fun `real throwable contains class and message`() {
            val trace = buildStacktrace(RuntimeException("oops"))
            assertTrue(trace.contains("RuntimeException"))
            assertTrue(trace.contains("oops"))
        }

        @Test
        fun `nested cause is included`() {
            val trace = buildStacktrace(RuntimeException("wrapper", IllegalArgumentException("root")))
            assertTrue(trace.contains("IllegalArgumentException"))
            assertTrue(trace.contains("root"))
        }
    }

    // ── buildGitHubIssueUrl ────────────────────────────────────────────────────

    @Nested
    inner class BuildGitHubIssueUrl {

        private val baseUrl = "https://github.com/kingg22/kotlinpoet-assistant-plugin/issues/new"

        @Test
        fun `url starts with github base`() {
            val url = buildGitHubIssueUrl("title", "body", listOf("bug"))
            assertTrue(url.startsWith(baseUrl), "URL: $url")
        }

        @Test
        fun `url contains title param`() {
            val url = buildGitHubIssueUrl("[IDE Exception] NPE: oops", "body", listOf("bug"))
            assertTrue(url.contains("title="), "URL: $url")
        }

        @Test
        fun `url contains body param`() {
            val url = buildGitHubIssueUrl("title", "## Environment\ntest", listOf("bug"))
            assertTrue(url.contains("body="), "URL: $url")
        }

        @Test
        fun `url contains labels param`() {
            val url = buildGitHubIssueUrl("title", "body", listOf("IDE Exception Pool", "bug"))
            assertTrue(url.contains("labels="), "URL: $url")
        }

        @Test
        fun `url with typical content is within 8192 char browser limit`() {
            val stacktrace = "at com.example.Class.method(File.kt:1)\n".repeat(80).take(4_000)
            val url = buildGitHubIssueUrl(
                "[IDE Exception] RuntimeException: test error",
                stubBody(stacktrace),
                listOf("IDE Exception Pool", "bug"),
            )
            assertTrue(url.length <= 8192, "URL length ${url.length} exceeds 8192 chars")
        }

        @Test
        fun `url with max stacktrace take 4000 is within limit`() {
            val maxTrace = "X".repeat(4_000)
            val url = buildGitHubIssueUrl("[IDE Exception] Error", stubBody(maxTrace), listOf("bug"))
            assertTrue(url.length <= 8192, "URL length ${url.length} exceeds 8192 chars")
        }

        @Test
        fun `url with long additional info is within limit`() {
            val longInfo = "User wrote a description. ".repeat(50)
            val url = buildGitHubIssueUrl("[IDE Exception] Error", stubBody("trace\n", longInfo), listOf("bug"))
            assertTrue(url.length <= 8192, "URL length ${url.length} exceeds 8192 chars")
        }

        /** Minimal body that mirrors [buildBody] structure without needing ApplicationInfo. */
        private fun stubBody(stacktrace: String, additionalInfo: String? = null): String = """
            ## Environment
            - Plugin version: 0.0.1-test
            - IDE: IntelliJ IDEA test
            - OS: Linux test
            - JVM: JetBrains test

            ## User description
            ${additionalInfo ?: "_No additional info provided_"}

            ## Stacktrace
            ```java
            $stacktrace
            ```
        """.trimIndent()
    }

    // ── copyToClipboard ────────────────────────────────────────────────────────

    @Nested
    inner class CopyToClipboard {

        @Test
        fun `does not throw in any environment`() {
            // In headless CI the clipboard may fail — the function must not propagate exceptions
            assertDoesNotThrow("copyToClipboard must not throw") {
                copyToClipboard("stacktrace text for clipboard")
            }
        }
    }

    // ── openBrowser ────────────────────────────────────────────────────────────

    @Nested
    inner class OpenBrowser {

        @Test
        fun `returns false in headless environment without throwing`() {
            assumeTrue(Desktop.isDesktopSupported(), "This test requires desktop environment")
            assumeTrue(GraphicsEnvironment.isHeadless(), "This test requires headless environment")
            val result = openBrowser("https://github.com/kingg22/kotlinpoet-assistant-plugin/issues/new")
            // headless → Desktop.isDesktopSupported() == false → returns false
            assertFalse(result, "openBrowser should return false in headless CI")
        }
    }
}
