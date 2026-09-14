package dev.kinetic.core.agent

import dev.kinetic.core.model.ModelProviderFailureKind

/** Typed, user-safe failures. Raw exceptions and stack traces do not cross this boundary. */
sealed interface AgentError {
    val code: String
    val userMessage: String
}

data class ModelFailure(
    val providerId: String,
    val kind: ModelProviderFailureKind = ModelProviderFailureKind.UNEXPECTED,
    override val userMessage: String = "The model could not complete the request.",
) : AgentError {
    override val code: String = when (kind) {
        ModelProviderFailureKind.NO_ELIGIBLE_PROVIDER -> "model_no_eligible_provider"
        ModelProviderFailureKind.CONFIGURATION -> "model_configuration_missing"
        ModelProviderFailureKind.AUTHENTICATION -> "model_authentication_failure"
        ModelProviderFailureKind.NETWORK -> "model_network_unavailable"
        ModelProviderFailureKind.TIMEOUT -> "model_timeout"
        ModelProviderFailureKind.RATE_LIMIT -> "model_rate_limited"
        ModelProviderFailureKind.SERVER -> "model_server_failure"
        ModelProviderFailureKind.MALFORMED_RESPONSE -> "model_malformed_response"
        ModelProviderFailureKind.LOCAL_UNAVAILABLE -> "model_local_unavailable"
        ModelProviderFailureKind.CONTEXT_LIMIT -> "model_context_limit"
        ModelProviderFailureKind.UNEXPECTED -> "model_failure"
    }
}

data class ToolFailure(
    val toolId: String,
    override val userMessage: String = "The tool could not complete the request.",
) : AgentError {
    override val code: String = "tool_failure"
}

data class PermissionFailure(
    val toolId: String,
    override val userMessage: String = "This capability is not available in the current profile.",
) : AgentError {
    override val code: String = "permission_failure"
}

data class ApprovalRejected(
    val approvalId: String,
    override val userMessage: String = "The requested action was rejected.",
) : AgentError {
    override val code: String = "approval_rejected"
}

data class InvalidToolCall(
    val toolId: String,
    override val userMessage: String = "The model requested an unavailable or invalid tool.",
) : AgentError {
    override val code: String = "invalid_tool_call"
}

data object Cancelled : AgentError {
    override val code: String = "cancelled"
    override val userMessage: String = "The agent turn was cancelled."
}

data class InternalFailure(
    val incidentId: String,
    override val userMessage: String = "Kinetic encountered an internal error.",
) : AgentError {
    override val code: String = "internal_failure"
}

data class RecoveryInterrupted(
    val interruptedPhase: RuntimePhase,
    override val userMessage: String =
        "The previous agent turn was interrupted and was not resumed automatically.",
) : AgentError {
    override val code: String = "recovery_interrupted"
}

data class RestoredAgentError(
    override val code: String,
    override val userMessage: String,
) : AgentError
