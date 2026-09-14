package dev.kinetic.core.model

import dev.kinetic.core.tools.EchoInput
import dev.kinetic.core.tools.ComposeEmailInput
import dev.kinetic.core.tools.CopyTextToClipboardInput
import dev.kinetic.core.tools.NoToolInput
import dev.kinetic.core.tools.OpenHttpsUrlInput
import dev.kinetic.core.tools.OpenDialerInput
import dev.kinetic.core.tools.OpenSettingsInput
import dev.kinetic.core.tools.ProtectedDemoInput
import dev.kinetic.core.tools.SettingsDestination
import dev.kinetic.core.tools.ShareTextInput
import dev.kinetic.core.tools.ToolCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import dev.kinetic.core.memory.MemoryRetentionState

/**
 * Deterministic Phase 1 provider. It performs no network or local-model work and exists only to
 * exercise kernel contracts and the developer shell.
 */
class FakeModelProvider(
    private val responseDelayMillis: Long = 0,
) : ModelProvider {
    override val providerId: String = "fake"
    override fun toolSupport(): ModelToolSupport = ModelToolSupport.Structured(
        setOf(
            "echo",
            "current_app_time",
            "protected_demo_tool",
            "failing_demo_tool",
            "open_https_url",
            "share_text",
            "open_settings",
            "copy_text_to_clipboard",
            "open_dialer",
            "compose_email",
        ),
    )

    override suspend fun generate(request: ModelRequest): ModelResponse {
        if (responseDelayMillis > 0) delay(responseDelayMillis)

        val prompt = request.messages.lastOrNull()?.content.orEmpty()
        val normalized = prompt.lowercase()
        val callIdPrefix = "fake-call-${request.turnId}"

        return when {
            "model failure" in normalized -> throw ModelProviderException("Deterministic failure")
            "what is my preferred kinetic test language" in normalized -> {
                val remembered = request.memoryContext.ordered
                    .firstNotNullOfOrNull { memory ->
                        PREFERRED_TEST_LANGUAGE.find(memory.content)?.groupValues?.get(1)
                    }
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = remembered?.let { "Your preferred Kinetic test language is $it." }
                        ?: "Kinetic has no stored preferred test language.",
                )
            }
            "what is my preferred kinetic test database" in normalized -> {
                val conflicts = request.memoryContext.ordered.filter {
                    it.retentionState == MemoryRetentionState.CONFLICTED &&
                        PREFERRED_TEST_DATABASE.containsMatchIn(it.content)
                }
                if (conflicts.size > 1) {
                    return ModelResponse(
                        responseId = "fake-response-${request.turnId}",
                        content = "Your preferred Kinetic test database has conflicting stored values. " +
                            "Please resolve the conflict in Controlled memory.",
                    )
                }
                val remembered = request.memoryContext.ordered
                    .firstNotNullOfOrNull { memory ->
                        PREFERRED_TEST_DATABASE.find(memory.content)?.groupValues?.get(1)
                    }
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = remembered?.let { "Your preferred Kinetic test database is $it." }
                        ?: "Kinetic has no stored preferred test database.",
                )
            }
            "what is my preferred kinetic test editor" in normalized -> {
                val matching = request.memoryContext.ordered.filter {
                    PREFERRED_TEST_EDITOR.containsMatchIn(it.content)
                }
                val conflicts = matching.filter {
                    it.retentionState == MemoryRetentionState.CONFLICTED
                }
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = when {
                        conflicts.size > 1 -> "Your preferred Kinetic test editor has conflicting stored values. " +
                            "Please resolve the conflict in Controlled memory."
                        matching.isNotEmpty() -> "Your preferred Kinetic test editor is " +
                            requireNotNull(PREFERRED_TEST_EDITOR.find(matching.first().content)).groupValues[1] + "."
                        else -> "Kinetic has no stored preferred test editor."
                    },
                )
            }
            "what is the kinetic test codename for this session" in normalized -> {
                val source = buildString {
                    request.sessionSummary?.let { append(it.content); append('\n') }
                    request.contextPlan?.selectedMessages?.forEach { append(it.content); append('\n') }
                }
                val codename = TEST_CODENAME.find(source)?.groupValues?.get(1)
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = codename?.let { "The Kinetic test codename for this session is $it." }
                        ?: "Kinetic has no session-context test codename.",
                )
            }
            HTTPS_URL.find(prompt)?.value != null -> {
                val url = requireNotNull(HTTPS_URL.find(prompt)).value
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = "Requesting Android HTTPS navigation.",
                    toolCalls = listOf(
                        ToolCall("$callIdPrefix-open-url", "open_https_url", OpenHttpsUrlInput(url)),
                    ),
                )
            }
            "share the text" in normalized -> {
                val marker = normalized.indexOf("share the text") + "share the text".length
                val value = prompt.substring(marker).trim().ifBlank { "KINETIC_SHARE_OK" }
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = "Requesting Android's share chooser.",
                    toolCalls = listOf(
                        ToolCall("$callIdPrefix-share", "share_text", ShareTextInput(value)),
                    ),
                )
            }
            CLIPBOARD_REQUEST.matchEntire(prompt.trim()) != null -> {
                val value = requireNotNull(CLIPBOARD_REQUEST.matchEntire(prompt.trim())).groupValues[1].trim()
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = "Requesting an approved Android clipboard write.",
                    toolCalls = listOf(
                        ToolCall(
                            "$callIdPrefix-clipboard",
                            "copy_text_to_clipboard",
                            CopyTextToClipboardInput(value),
                        ),
                    ),
                )
            }
            DIALER_REQUEST.find(prompt) != null -> {
                val number = requireNotNull(DIALER_REQUEST.find(prompt)).groupValues[1].trim()
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = "Requesting Android's user-controlled dialer.",
                    toolCalls = listOf(
                        ToolCall("$callIdPrefix-dialer", "open_dialer", OpenDialerInput(number)),
                    ),
                )
            }
            EMAIL_REQUEST.matchEntire(prompt.trim()) != null -> {
                val match = requireNotNull(EMAIL_REQUEST.matchEntire(prompt.trim()))
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = "Requesting Android's user-controlled email composer.",
                    toolCalls = listOf(
                        ToolCall(
                            "$callIdPrefix-email",
                            "compose_email",
                            ComposeEmailInput(
                                recipient = match.groupValues[1].trim(),
                                subject = match.groupValues[2].trim(),
                                body = match.groupValues[3].trim().removeSuffix("."),
                            ),
                        ),
                    ),
                )
            }
            "wi-fi settings" in normalized || "wifi settings" in normalized -> ModelResponse(
                responseId = "fake-response-${request.turnId}",
                content = "Requesting the allowlisted Wi-Fi Settings page.",
                toolCalls = listOf(
                    ToolCall(
                        "$callIdPrefix-settings-wifi",
                        "open_settings",
                        OpenSettingsInput(SettingsDestination.WIFI),
                    ),
                ),
            )
            normalized.trim() == "open settings" -> ModelResponse(
                responseId = "fake-response-${request.turnId}",
                content = "Requesting the allowlisted general Settings page.",
                toolCalls = listOf(
                    ToolCall(
                        "$callIdPrefix-settings-general",
                        "open_settings",
                        OpenSettingsInput(SettingsDestination.GENERAL),
                    ),
                ),
            )
            "unknown tool" in normalized -> ModelResponse(
                responseId = "fake-response-${request.turnId}",
                content = "Requesting an unknown tool for validation.",
                toolCalls = listOf(ToolCall("$callIdPrefix-unknown", "unknown_demo_tool", NoToolInput)),
            )
            "protected demo" in normalized -> ModelResponse(
                responseId = "fake-response-${request.turnId}",
                content = "The protected demo action requires approval.",
                toolCalls = listOf(
                    ToolCall(
                        callId = "$callIdPrefix-protected",
                        toolId = "protected_demo_tool",
                        input = ProtectedDemoInput("protected demo"),
                    ),
                ),
            )
            "failing demo" in normalized -> ModelResponse(
                responseId = "fake-response-${request.turnId}",
                content = "Requesting the deterministic failing tool.",
                toolCalls = listOf(
                    ToolCall("$callIdPrefix-failing", "failing_demo_tool", NoToolInput),
                ),
            )
            "current app time" in normalized || normalized.trim() == "time" -> ModelResponse(
                responseId = "fake-response-${request.turnId}",
                content = "Requesting the current app time.",
                toolCalls = listOf(
                    ToolCall("$callIdPrefix-time", "current_app_time", NoToolInput),
                ),
            )
            normalized.startsWith("echo") -> {
                val value = prompt.substringAfter(':', prompt.removePrefix("echo")).trim()
                ModelResponse(
                    responseId = "fake-response-${request.turnId}",
                    content = "Requesting the echo tool.",
                    toolCalls = listOf(
                        ToolCall("$callIdPrefix-echo", "echo", EchoInput(value)),
                    ),
                )
            }
            else -> ModelResponse(
                responseId = "fake-response-${request.turnId}",
                content = "Fake response: $prompt",
            )
        }
    }

    override fun streamContinuation(request: ModelContinuationRequest) = flow {
        if (responseDelayMillis > 0) delay(responseDelayMillis)
        val resultText = when (val result = request.toolResult) {
            is dev.kinetic.core.tools.ToolResult.Success -> result.output.displayText()
            is dev.kinetic.core.tools.ToolResult.Failure -> result.error.userMessage
        }
        val content = "Fake continuation: tool returned $resultText"
        emit(ModelStreamEvent.TextDelta(content))
        emit(
            ModelStreamEvent.Completed(
                ModelResponse(
                    responseId = "fake-continuation-${request.originalRequest.turnId}",
                    content = content,
                ),
            ),
        )
    }

    private companion object {
        val HTTPS_URL = Regex("https://[^\\s]+")
        val CLIPBOARD_REQUEST = Regex(
            "(?i)^copy\\s+(.+?)\\s+to\\s+the\\s+clipboard[.!]?$",
        )
        val DIALER_REQUEST = Regex(
            "(?i)(?:open\\s+the\\s+)?dialer\\s+with\\s+(\\+?[0-9][0-9 -]*)",
        )
        val EMAIL_REQUEST = Regex(
            "(?is)^compose\\s+an\\s+email\\s+to\\s+(\\S+)\\s+with\\s+subject\\s+(.+?)" +
                "\\s+and\\s+body\\s+(.+)$",
        )
        val PREFERRED_TEST_LANGUAGE = Regex(
            "(?i)preferred\\s+Kinetic\\s+test\\s+language\\s+is\\s+([A-Za-z0-9_+#-]{1,40})",
        )
        val PREFERRED_TEST_DATABASE = Regex(
            "(?i)preferred\\s+Kinetic\\s+test\\s+database\\s+is\\s+([A-Za-z0-9_+#-]{1,40})",
        )
        val PREFERRED_TEST_EDITOR = Regex(
            "(?i)preferred\\s+Kinetic\\s+test\\s+editor\\s+is\\s+([A-Za-z0-9_+# -]{1,40})",
        )
        val TEST_CODENAME = Regex(
            "(?i)Kinetic\\s+test\\s+codename\\s+(?:for\\s+this\\s+session\\s+)?is\\s+" +
                "([A-Za-z0-9_-]{1,80})",
        )
    }
}
