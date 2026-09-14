package dev.kinetic.core.model

import dev.kinetic.core.agent.AgentMessage
import dev.kinetic.core.memory.MemoryContext
import dev.kinetic.core.context.ContextPlan
import dev.kinetic.core.context.SessionSummary
import dev.kinetic.core.tools.ToolCall
import dev.kinetic.core.tools.ToolDefinition
import dev.kinetic.core.tools.ToolResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ModelRequest(
    val requestId: String,
    val turnId: String,
    val sessionId: String,
    val messages: List<AgentMessage>,
    val availableTools: List<ToolDefinition>,
    val memoryContext: MemoryContext = MemoryContext(),
    val sessionSummary: SessionSummary? = null,
    val contextPlan: ContextPlan? = null,
)

data class ModelResponse(
    val responseId: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
)

sealed interface ModelToolSupport {
    data object Unavailable : ModelToolSupport

    /** Only these stable registry IDs may be disclosed to, or accepted from, this provider. */
    data class Structured(val exposedToolIds: Set<String>) : ModelToolSupport {
        init {
            require(exposedToolIds.isNotEmpty()) { "Structured tool support needs an allowlist" }
        }
    }
}

data class ModelContinuationRequest(
    val originalRequest: ModelRequest,
    val proposal: ModelResponse,
    val toolResult: ToolResult,
)

interface ModelProvider {
    val providerId: String

    /** Pin provider metadata before context planning. Legacy/manual providers need no setup. */
    suspend fun prepareTurn(turnId: String, requirements: RoutingRequirements) = Unit

    fun capabilities(): ModelCapabilities = ModelCapabilities(ProviderKind.FAKE)

    /** Discard transient transport continuation material at every terminal turn, including Reject. */
    fun finishTurn(turnId: String) = Unit

    fun toolSupport(): ModelToolSupport = ModelToolSupport.Unavailable

    fun permitsToolProposals(): Boolean = toolSupport() is ModelToolSupport.Structured

    suspend fun generate(request: ModelRequest): ModelResponse

    /** Real providers override this with transport streaming; the default preserves old providers. */
    fun stream(request: ModelRequest): Flow<ModelStreamEvent> = flow {
        val response = generate(request)
        if (response.content.isNotEmpty()) emit(ModelStreamEvent.TextDelta(response.content))
        emit(ModelStreamEvent.Completed(response))
    }

    /** A second provider turn associated with one completed, identity-bound tool call. */
    fun streamContinuation(request: ModelContinuationRequest): Flow<ModelStreamEvent> = flow {
        throw ModelProviderException(
            ModelProviderFailureKind.CONFIGURATION,
            "This model provider cannot continue a structured tool turn.",
        )
    }
}

sealed interface ModelStreamEvent {
    data object Started : ModelStreamEvent
    data class ToolCallStarted(val callId: String, val toolId: String) : ModelStreamEvent
    data class ToolArgumentDelta(val callId: String, val fragment: String) : ModelStreamEvent
    data class ToolCallCompleted(val call: ToolCall) : ModelStreamEvent
    data class TextDelta(val text: String) : ModelStreamEvent
    data class Completed(val response: ModelResponse) : ModelStreamEvent
}

enum class ModelProviderFailureKind {
    NO_ELIGIBLE_PROVIDER,
    CONFIGURATION,
    AUTHENTICATION,
    NETWORK,
    TIMEOUT,
    RATE_LIMIT,
    SERVER,
    MALFORMED_RESPONSE,
    LOCAL_UNAVAILABLE,
    CONTEXT_LIMIT,
    UNEXPECTED,
}

class ModelProviderException(
    val kind: ModelProviderFailureKind = ModelProviderFailureKind.UNEXPECTED,
    val safeMessage: String = "The model provider could not complete the request.",
    cause: Throwable? = null,
) : RuntimeException(safeMessage, cause) {
    constructor(message: String) : this(
        kind = ModelProviderFailureKind.UNEXPECTED,
        safeMessage = message,
    )
}
