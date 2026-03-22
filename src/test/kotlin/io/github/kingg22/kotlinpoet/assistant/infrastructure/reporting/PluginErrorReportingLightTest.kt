package io.github.kingg22.kotlinpoet.assistant.infrastructure.reporting

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.fail as failKt

/**
 * Light-platform integration tests for [PluginErrorReporting].
 *
 * ## What requires the platform
 *
 * - [buildBody] calls [com.intellij.openapi.application.ApplicationInfo.getInstance] and
 *   [com.intellij.openapi.util.SystemInfo] — both require an initialized IntelliJ application.
 * - [PluginErrorReporting.submit] requires [com.intellij.openapi.diagnostic.ErrorReportSubmitter.pluginDescriptor]
 *   to be set (non-null) so it can read `pluginDescriptor.version`.
 *
 * ## submit() testing strategy
 *
 * `ErrorReportSubmitter.setPluginDescriptor()` is public. We obtain a real
 * [com.intellij.openapi.extensions.PluginDescriptor] from [PluginManagerCore] for the
 * Kotlin plugin (always present in tests) and inject it, then call `submit()` directly,
 * capturing the [SubmittedReportInfo] via the consumer.
 *
 * We assert on the [SubmittedReportInfo] and on the generated URL — the browser open
 * and clipboard calls are no-ops in headless (both return false/no-op gracefully).
 */
class PluginErrorReportingLightTest : BasePlatformTestCase() {

    private val reporting = PluginErrorReporting()

    override fun setUp() {
        super.setUp()
        reporting.disableBrowserOpening()
        // Inject a real PluginDescriptor so pluginDescriptor.version is non-null.
        // We use the Kotlin plugin descriptor which is always available in IJ test platform.
        val kotlinPluginDescriptor = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.kotlin"))
            ?: PluginManagerCore.getPlugin(PluginId.getId("com.intellij")) // fallback to platform
        assertNotNull("A real PluginDescriptor must be available for tests", kotlinPluginDescriptor)
        reporting.pluginDescriptor = kotlinPluginDescriptor!!
    }

    // ── buildBody with real ApplicationInfo ────────────────────────────────────

    fun testBuildBodyContainsRealIdeInfo() {
        val body = buildBody("stacktrace here", "1.0.0", null, 0, true)
        // ApplicationInfo.getInstance() is available in light tests
        assertTrue("Body should contain '## Environment' section", body.contains("## Environment"))
        assertTrue("Body should contain IDE build info", body.contains("Build:"))
        assertTrue("Body should contain OS info", body.contains("OS:"))
        assertTrue("Body should contain JVM info", body.contains("JVM:"))
    }

    fun testBuildBodyPluginVersionIsIncluded() {
        val body = buildBody("trace", "2.5.1-beta", null, 0, true)
        assertTrue("Body should contain the plugin version", body.contains("2.5.1-beta"))
    }

    fun testBuildBodyWithAdditionalInfoAndEvents() {
        val body = buildBody("at Test.run(Test.kt:1)", "1.0.0", "Reproducible by clicking X", 2, false)
        assertTrue(body.contains("Reproducible by clicking X"))
        assertTrue(body.contains("2"))
        assertTrue(body.contains("Please report each event"))
        assertTrue(body.contains("Failed")) // copiedClipboard = false
    }

    fun testBuildBodyResultingUrlIsWithinBrowserLimit() {
        val stacktrace = "at com.example.Deep.method(Deep.kt:42)\n".repeat(80).take(4_000)
        val body = buildBody(stacktrace, "0.0.1", "User description here", 1, true)
        val url = buildGitHubIssueUrl(
            "[IDE Exception] RuntimeException: test",
            body,
            listOf("IDE Exception Pool", "bug"),
        )
        assertTrue(
            "URL built with real ApplicationInfo body (${url.length} chars) exceeds 8192 limit",
            url.length <= 8192,
        )
    }

    // ── submit() — full integration via Consumer ───────────────────────────────

    fun testSubmitReturnsTrueAndInvokesConsumer() {
        val events = arrayOf(fakeEvent(RuntimeException("test error from submit")))
        var capturedInfo: SubmittedReportInfo? = null

        val result = reporting.submit(
            events = events,
            additionalInfo = "Test additional info",
            parentComponent = javax.swing.JLabel(), // dummy, not used in submit body
            consumer = { info -> capturedInfo = info },
        )

        assertTrue("submit() should return true", result)
        assertNotNull("Consumer should have been called with a SubmittedReportInfo", capturedInfo)
    }

    fun testSubmitConsumerReceivesGitHubUrl() {
        val events = arrayOf(fakeEvent(IllegalStateException("bad state")))
        var capturedInfo: SubmittedReportInfo? = null

        reporting.submit(
            events = events,
            additionalInfo = null,
            parentComponent = javax.swing.JLabel(),
            consumer = { info -> capturedInfo = info },
        )

        val url = capturedInfo?.url
        assertNotNull("SubmittedReportInfo should have a URL", url)
        assertTrue(
            "URL should point to GitHub issues, got: $url",
            url!!.startsWith("https://github.com/kingg22/kotlinpoet-assistant-plugin/issues/new"),
        )
    }

    fun testSubmitConsumerUrlContainsExceptionClass() {
        val events = arrayOf(fakeEvent(NullPointerException("null ref")))
        var capturedInfo: SubmittedReportInfo? = null

        reporting.submit(
            events = events,
            additionalInfo = null,
            parentComponent = javax.swing.JLabel(),
            consumer = { info -> capturedInfo = info },
        )

        val url = capturedInfo?.url ?: failKt("Consumer was not called")
        assertTrue(
            "URL should contain exception class in title param, got: $url",
            url.contains("NullPointerException"),
        )
    }

    fun testSubmitWithNullThrowableDoesNotCrash() {
        // events with no throwable — should still call consumer and return true
        val events = arrayOf(fakeEvent(null))
        var consumerCalled = false

        val result = reporting.submit(
            events = events,
            additionalInfo = "No throwable in this event",
            parentComponent = javax.swing.JLabel(),
            consumer = { consumerCalled = true },
        )

        assertTrue("submit() should return true even with null throwable", result)
        assertTrue("Consumer should be called even with null throwable", consumerCalled)
    }

    fun testSubmitWithEmptyEventsDoesNotCrash() {
        var consumerCalled = false

        val result = reporting.submit(
            events = emptyArray(),
            additionalInfo = null,
            parentComponent = javax.swing.JLabel(),
            consumer = { consumerCalled = true },
        )

        assertTrue("submit() should return true for empty events", result)
        assertTrue("Consumer should be called for empty events", consumerCalled)
    }

    fun testSubmitUrlIsWithinBrowserLimit() {
        val longTrace = "at com.example.VeryLongClassName.veryLongMethodName(VeryLongFileName.kt:999)\n"
            .repeat(200)
        val events = arrayOf(fakeEvent(RuntimeException(longTrace)))
        var capturedInfo: SubmittedReportInfo? = null

        reporting.submit(
            events = events,
            additionalInfo = "A".repeat(500),
            parentComponent = javax.swing.JLabel(),
            consumer = { capturedInfo = it },
        )

        val url = capturedInfo?.url ?: failKt("Consumer was not called")
        assertTrue(
            "Generated URL (${url.length} chars) exceeds 8192 safe browser limit",
            url.length <= 8192,
        )
    }

    fun testSubmitConsumerStatusIsNewIssueInHeadless() {
        // In headless, openBrowser returns false → status is FAILED
        // We just assert the consumer was called — status depends on environment
        val events = arrayOf(fakeEvent(RuntimeException("status test")))
        var capturedInfo: SubmittedReportInfo? = null

        reporting.submit(
            events = events,
            additionalInfo = null,
            parentComponent = javax.swing.JLabel(),
            consumer = { capturedInfo = it },
        )

        assertNotNull(capturedInfo)
        // In headless: openBrowser = false → FAILED; in desktop: NEW_ISSUE
        // Both are valid — we just verify the status is one of the known values
        val status = capturedInfo!!.status
        assertTrue(
            "Status should be NEW_ISSUE or FAILED, got: $status",
            status == SubmittedReportInfo.SubmissionStatus.NEW_ISSUE ||
                status == SubmittedReportInfo.SubmissionStatus.FAILED,
        )
    }

    fun testSubmitMultipleEventsPassesFirstToTitle() {
        val events = arrayOf(
            fakeEvent(RuntimeException("first")),
            fakeEvent(IllegalArgumentException("second")),
            fakeEvent(NullPointerException("third")),
        )
        var capturedInfo: SubmittedReportInfo? = null

        reporting.submit(
            events = events,
            additionalInfo = null,
            parentComponent = javax.swing.JLabel(),
            consumer = { capturedInfo = it },
        )

        val url = capturedInfo?.url ?: failKt("Consumer was not called")
        // Title is built from events.firstOrNull() → RuntimeException
        assertTrue(
            "URL title should reflect first event exception class: $url",
            url.contains("RuntimeException"),
        )
    }
}

// ── Test helpers ───────────────────────────────────────────────────────────────

fun fakeEvent(throwable: Throwable?): IdeaLoggingEvent = object : IdeaLoggingEvent("", throwable) {}
