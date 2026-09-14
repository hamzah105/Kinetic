package dev.kinetic.core.agent

import dev.kinetic.core.model.ModelResponse
import dev.kinetic.core.tools.ToolResult
import java.time.Instant

/** A user request admitted to one agent turn. */
data class AgentRequest(
    val requestId: String,
    val sessionId: String,
    val content: String,
    val createdAt: Instant,
) {
    init {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(content.isNotBlank()) { "content must not be blank" }
    }
}

enum class MessageRole {
    USER,
    ASSISTANT,
    TOOL,
}

/** Ordered conversation content. Credentials must never be placed in metadata. */
data class AgentMessage(
    val messageId: String,
    val turnId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant,
    val sequence: Long = 0,
)

sealed interface AgentTurnResult {
    val turnId: String

    data class Completed(
        override val turnId: String,
        val modelResponse: ModelResponse,
        val toolResult: ToolResult? = null,
    ) : AgentTurnResult

    data class Failed(
        override val turnId: String,
        val error: AgentError,
    ) : AgentTurnResult

    data class Cancelled(
        override val turnId: String,
        val error: AgentError,
    ) : AgentTurnResult
}
