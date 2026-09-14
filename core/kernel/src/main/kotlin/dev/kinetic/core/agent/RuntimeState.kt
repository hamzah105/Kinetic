package dev.kinetic.core.agent

import dev.kinetic.core.policy.ApprovalRequest
import dev.kinetic.core.tools.ToolCall
import dev.kinetic.core.tools.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RuntimePhase {
    IDLE,
    THINKING,
    WAITING_FOR_APPROVAL,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

sealed interface RuntimeState {
    val phase: RuntimePhase

    data object Idle : RuntimeState {
        override val phase: RuntimePhase = RuntimePhase.IDLE
    }

    data class Thinking(val turnId: String) : RuntimeState {
        override val phase: RuntimePhase = RuntimePhase.THINKING
    }

    data class WaitingForApproval(
        val turnId: String,
        val request: ApprovalRequest,
    ) : RuntimeState {
        override val phase: RuntimePhase = RuntimePhase.WAITING_FOR_APPROVAL
    }

    data class Executing(
        val turnId: String,
        val toolCall: ToolCall,
    ) : RuntimeState {
        override val phase: RuntimePhase = RuntimePhase.EXECUTING
    }

    data class Completed(
        val turnId: String,
        val summary: String,
        val toolResult: ToolResult? = null,
    ) : RuntimeState {
        override val phase: RuntimePhase = RuntimePhase.COMPLETED
    }

    data class Failed(
        val turnId: String,
        val error: AgentError,
    ) : RuntimeState {
        override val phase: RuntimePhase = RuntimePhase.FAILED
    }

    data class Cancelled(
        val turnId: String,
        val error: AgentError,
    ) : RuntimeState {
        override val phase: RuntimePhase = RuntimePhase.CANCELLED
    }
}

class IllegalStateTransitionException(
    val from: RuntimePhase,
    val to: RuntimePhase,
) : IllegalStateException("Illegal agent runtime transition: $from -> $to")

/** Small deterministic state machine; every state change must pass through [transition]. */
class AgentStateMachine {
    private val mutableState = MutableStateFlow<RuntimeState>(RuntimeState.Idle)
    val state: StateFlow<RuntimeState> = mutableState.asStateFlow()

    @Synchronized
    fun transition(next: RuntimeState): RuntimeState {
        val previous = mutableState.value
        if (next.phase !in allowedTargets.getValue(previous.phase)) {
            throw IllegalStateTransitionException(previous.phase, next.phase)
        }
        mutableState.value = next
        return previous
    }

    /** Restores one durable state before new work is admitted; it is not a runtime transition. */
    @Synchronized
    fun restore(recovered: RuntimeState) {
        check(mutableState.value == RuntimeState.Idle) { "Runtime state was already initialized" }
        mutableState.value = recovered
    }

    companion object {
        private val allowedTargets: Map<RuntimePhase, Set<RuntimePhase>> = mapOf(
            RuntimePhase.IDLE to setOf(RuntimePhase.THINKING),
            RuntimePhase.THINKING to setOf(
                RuntimePhase.WAITING_FOR_APPROVAL,
                RuntimePhase.EXECUTING,
                RuntimePhase.COMPLETED,
                RuntimePhase.FAILED,
                RuntimePhase.CANCELLED,
            ),
            RuntimePhase.WAITING_FOR_APPROVAL to setOf(
                RuntimePhase.EXECUTING,
                RuntimePhase.FAILED,
                RuntimePhase.CANCELLED,
            ),
            RuntimePhase.EXECUTING to setOf(
                RuntimePhase.COMPLETED,
                RuntimePhase.FAILED,
                RuntimePhase.CANCELLED,
            ),
            RuntimePhase.COMPLETED to setOf(RuntimePhase.IDLE),
            RuntimePhase.FAILED to setOf(RuntimePhase.IDLE),
            RuntimePhase.CANCELLED to setOf(RuntimePhase.IDLE),
        )
    }
}
