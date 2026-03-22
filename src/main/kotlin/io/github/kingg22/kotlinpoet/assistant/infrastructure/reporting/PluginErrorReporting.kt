package io.github.kingg22.kotlinpoet.assistant.infrastructure.reporting

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.diagnostic.SubmittedReportInfo.SubmissionStatus.FAILED
import com.intellij.openapi.diagnostic.SubmittedReportInfo.SubmissionStatus.NEW_ISSUE
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import com.intellij.util.ExceptionUtil
import io.github.kingg22.kotlinpoet.assistant.KPoetAssistantBundle
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val GITHUB_ISSUE_BASE_URL = "https://github.com/kingg22/kotlinpoet-assistant-plugin/issues/new"

private val logger = Logger.getInstance(PluginErrorReporting::class.java)

class PluginErrorReporting : ErrorReportSubmitter() {

    override fun getReportActionText(): String = KPoetAssistantBundle.getMessage("error.handler.action")

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>,
    ): Boolean {
        val event = events.firstOrNull()
        val throwable = event?.throwable

        val title = buildTitle(event)
        val stacktrace = buildStacktrace(throwable)
        val copied = copyToClipboard(stacktrace)
        val body = buildBody(
            stacktrace.take(4_000),
            pluginDescriptor.version,
            additionalInfo,
            events.drop(1).count(),
            copied,
        )

        val issueUrl = buildGitHubIssueUrl(
            title = title,
            body = body,
            labels = listOf("IDE Exception Pool", "bug"),
        )

        val openedBrowser = openBrowser(issueUrl)

        consumer.consume(
            SubmittedReportInfo(
                issueUrl,
                "GitHub",
                if (openedBrowser) NEW_ISSUE else FAILED,
            ),
        )

        return true
    }
}

@VisibleForTesting
fun openBrowser(url: String): Boolean {
    if (Desktop.isDesktopSupported() && !GraphicsEnvironment.isHeadless()) {
        try {
            Desktop.getDesktop().browse(URI(url))
            return true
        } catch (e: Exception) {
            if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
            logger.warn("Error opening browser to report fatal exception", e)
            return false
        }
    }
    return false
}

@VisibleForTesting
fun copyToClipboard(text: String): Boolean {
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        return true
    } catch (e: Exception) {
        if (Logger.shouldRethrow(e)) ExceptionUtil.rethrow(e)
        logger.warn("Error copying text to clipboard", e)
        return false
    }
}

@VisibleForTesting
fun buildTitle(event: IdeaLoggingEvent?): String {
    val exceptionName = event?.throwable?.javaClass?.simpleName ?: "UnknownException"
    val message = event?.throwable?.message?.take(80)

    return buildString {
        append("[IDE Exception] ")
        append(exceptionName)
        if (!message.isNullOrBlank()) {
            append(": ")
            append(message)
        }
    }
}

@VisibleForTesting
fun buildStacktrace(throwable: Throwable?): String = throwable?.let { ExceptionUtil.getThrowableText(it) }
    ?: "No stacktrace available"

@VisibleForTesting
fun buildBody(
    shortStackTrace: String,
    pluginVersion: String?,
    additionalInfo: String?,
    additionalEvents: Int,
    copiedClipboard: Boolean,
): String {
    val ideInfo = ApplicationInfo.getInstance()
    val osInfo = "${SystemInfo.getOsNameAndVersion()} (${SystemInfo.OS_ARCH})"
    val jvmInfo = buildString {
        append(SystemInfo.JAVA_VENDOR)
        append(" ")
        append(SystemInfo.JAVA_VERSION)
        append(" Runtime Version: ")
        append(SystemInfo.JAVA_RUNTIME_VERSION)
    }

    // language=Markdown
    return """
        ## Environment
        - Plugin version: $pluginVersion
        - IDE: ${ideInfo.fullApplicationName}
        - Build: ${ideInfo.build.asString()}
        - OS: $osInfo
        - JVM: $jvmInfo

        ## User description
        ${additionalInfo ?: "_No additional info provided_"}

        _Additional events_: $additionalEvents

        ${if (additionalEvents > 0) "⚠️ Please report each event as an issue comment!" else ""}

        ## Step to reproduce

        ## Stacktrace

        ⚠️ The full stacktrace was **${if (!copiedClipboard) "Failed" else ""} copied to your clipboard**.
        Please paste it below before submitting this issue.

        _Full stacktrace_
        ```java
        PASTE STACKTRACE HERE
        ```

        <details>
        <summary>Short stacktrace can remove this</summary>

        ```java
    """.trimIndent() + "\n" + shortStackTrace + "\n" + """
        ```

        </details>
    """.trimIndent()
}

@VisibleForTesting
fun buildGitHubIssueUrl(title: String, body: String, labels: List<String>): String {
    fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    val params = buildString {
        append("?title=${enc(title)}")
        append("&body=${enc(body)}")
        append("&labels=${enc(labels.joinToString(","))}")
    }

    return GITHUB_ISSUE_BASE_URL + params
}
