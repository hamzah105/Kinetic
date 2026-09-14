package dev.kinetic.core.session

import dev.kinetic.core.agent.AgentError
import dev.kinetic.core.policy.ApprovalDecision
import dev.kinetic.core.policy.ApprovalRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

enum class DurableTurnStatus {
    THINKING,
    WAITING_FOR_APPROVAL,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class DurableApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
}

enum class DurableEffectStatus {
    PENDING,
    EXECUTING,
    COMPLETED,
    FAILED,
}

data class DurableTurnRecord(
    val turnId: String,
    val sessionId: String,
    val requestId: String,
    val requestSummary: String,
    val status: DurableTurnStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completionSummary: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

data class DurableApprovalRecord(
    val request: ApprovalRequest,
    val status: DurableApprovalStatus,
    val resolvedAt: Instant? = null,
)

data class DurableEffectRecord(
    val callId: String,
    val sessionId: String,
    val turnId: String,
    val toolId: String,
    val inputBinding: String = LEGACY_INPUT_BINDING,
    val status: DurableEffectStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val outputSummary: String? = null,
    val errorCode: String? = null,
)

data class DurableRunSnapshot(
    val turn: DurableTurnRecord,
    val approval: DurableApprovalRecord? = null,
    val effect: DurableEffectRecord? = null,
)

/**
 * Pure kernel port for the minimum process-death ledger. Implementations must make each method
 * atomic. In particular, marking an effect executing must happen before the tool is invoked, and
 * completing an effect and its owning turn must be one transaction.
 */
interface RunLedger {
    suspend fun beginTurn(turn: DurableTurnRecord)

    suspend fun prepareEffect(
        sessionId: String,
        turnId: String,
        callId: String,
        toolId: String,
        at: Instant,
        inputBinding: String = LEGACY_INPUT_BINDING,
    ): Boolean

    suspend fun requestApproval(request: ApprovalRequest)

    suspend fun resolveApproval(
        approvalId: String,
        turnId: String,
        callId: String,
        decision: ApprovalDecision,
        at: Instant,
    ): Boolean

    suspend fun markExecuting(
        turnId: String,
        callId: String,
        at: Instant,
        inputBinding: String = LEGACY_INPUT_BINDING,
    ): Boolean

    suspend fun completeTurn(turnId: String, summary: String, at: Instant)

    suspend fun completeEffectAndTurn(
        turnId: String,
        callId: String,
        outputSummary: String,
        at: Instant,
    )

    /** Marks the effect complete while the owning turn awaits its provider continuation. */
    suspend fun completeEffect(turnId: String, callId: String, outputSummary: String, at: Instant)

    suspend fun failTurn(turnId: String, error: AgentError, at: Instant)

    suspend fun cancelTurn(turnId: String, error: AgentError, at: Instant)

    suspend fun latestRun(sessionId: String): DurableRunSnapshot?
}

/** Deterministic test/default implementation; Android production wiring uses Room. */
class InMemoryRunLedger : RunLedger {
    private val mutex = Mutex()
    private val turns = linkedMapOf<String, DurableTurnRecord>()
    private val approvals = linkedMapOf<String, DurableApprovalRecord>()
    private val effects = linkedMapOf<String, DurableEffectRecord>()

    override suspend fun beginTurn(turn: DurableTurnRecord) = mutex.withLock {
        check(turn.turnId !in turns) { "Turn ${turn.turnId} already exists" }
        turns[turn.turnId] = turn
    }

    override suspend fun prepareEffect(
        sessionId: String,
        turnId: String,
        callId: String,
        toolId: String,
        at: Instant,
        inputBinding: String,
    ): Boolean = mutex.withLock {
        val turn = requireTurn(turnId)
        require(turn.sessionId == sessionId) { "Effect session does not own turn" }
        if (callId in effects) return@withLock false
        effects[callId] = DurableEffectRecord(
            callId = callId,
            sessionId = sessionId,
            turnId = turnId,
            toolId = toolId,
            inputBinding = inputBinding,
            status = DurableEffectStatus.PENDING,
            createdAt = at,
            updatedAt = at,
        )
        true
    }

    override suspend fun requestApproval(request: ApprovalRequest) = mutex.withLock {
        val turn = requireTurn(request.turnId)
        require(turn.sessionId == request.sessionId) { "Approval session does not own turn" }
        val effect = requireNotNull(effects[request.toolCall.callId]) {
            "Effect must be prepared before approval"
        }
        require(
            effect.turnId == request.turnId &&
                effect.toolId == request.toolCall.toolId &&
                (effect.inputBinding == LEGACY_INPUT_BINDING ||
                    effect.inputBinding == request.toolCall.input.authorizationBinding()),
        ) {
            "Approval is not bound to the prepared effect"
        }
        check(request.approvalId !in approvals) { "Approval ${request.approvalId} already exists" }
        approvals[request.approvalId] = DurableApprovalRecord(
            request = request,
            status = DurableApprovalStatus.PENDING,
        )
        turns[request.turnId] = turn.copy(
            status = DurableTurnStatus.WAITING_FOR_APPROVAL,
            updatedAt = request.requestedAt,
        )
    }

    override suspend fun resolveApproval(
        approvalId: String,
        turnId: String,
        callId: String,
        decision: ApprovalDecision,
        at: Instant,
    ): Boolean = mutex.withLock {
        val current = approvals[approvalId] ?: return@withLock false
        if (current.request.turnId != turnId ||
            current.request.toolCall.callId != callId
        ) {
            return@withLock false
        }
        val target = when (decision) {
            ApprovalDecision.APPROVE -> DurableApprovalStatus.APPROVED
            ApprovalDecision.REJECT -> DurableApprovalStatus.REJECTED
        }
        if (current.status == target) return@withLock true
        if (current.status != DurableApprovalStatus.PENDING) return@withLock false
        approvals[approvalId] = current.copy(
            status = target,
            resolvedAt = at,
        )
        true
    }

    override suspend fun markExecuting(
        turnId: String,
        callId: String,
        at: Instant,
        inputBinding: String,
    ): Boolean =
        mutex.withLock {
            val turn = turns[turnId] ?: return@withLock false
            val effect = effects[callId] ?: return@withLock false
            if (effect.turnId != turnId ||
                effect.status != DurableEffectStatus.PENDING ||
                effect.inputBinding != inputBinding
            ) {
                return@withLock false
            }
            val approval = approvals.values.singleOrNull { it.request.turnId == turnId }
            if (approval != null && approval.status != DurableApprovalStatus.APPROVED) {
                return@withLock false
            }
            effects[callId] = effect.copy(
                status = DurableEffectStatus.EXECUTING,
                updatedAt = at,
            )
            turns[turnId] = turn.copy(
                status = DurableTurnStatus.EXECUTING,
                updatedAt = at,
            )
            true
        }

    override suspend fun completeTurn(turnId: String, summary: String, at: Instant) =
        mutex.withLock {
            val turn = requireTurn(turnId)
            turns[turnId] = turn.copy(
                status = DurableTurnStatus.COMPLETED,
                updatedAt = at,
                completionSummary = summary,
                errorCode = null,
                errorMessage = null,
            )
        }

    override suspend fun completeEffectAndTurn(
        turnId: String,
        callId: String,
        outputSummary: String,
        at: Instant,
    ) = mutex.withLock {
        val turn = requireTurn(turnId)
        val effect = requireNotNull(effects[callId]) { "Unknown effect $callId" }
        require(effect.turnId == turnId && effect.status == DurableEffectStatus.EXECUTING) {
            "Only the executing bound effect can complete"
        }
        effects[callId] = effect.copy(
            status = DurableEffectStatus.COMPLETED,
            updatedAt = at,
            outputSummary = outputSummary,
            errorCode = null,
        )
        turns[turnId] = turn.copy(
            status = DurableTurnStatus.COMPLETED,
            updatedAt = at,
            completionSummary = outputSummary,
            errorCode = null,
            errorMessage = null,
        )
    }

    override suspend fun completeEffect(
        turnId: String,
        callId: String,
        outputSummary: String,
        at: Instant,
    ) = mutex.withLock {
        val effect = requireNotNull(effects[callId]) { "Unknown effect $callId" }
        require(effect.turnId == turnId && effect.status == DurableEffectStatus.EXECUTING) {
            "Only the executing bound effect can complete"
        }
        effects[callId] = effect.copy(
            status = DurableEffectStatus.COMPLETED,
            updatedAt = at,
            outputSummary = outputSummary,
            errorCode = null,
        )
    }

    override suspend fun failTurn(turnId: String, error: AgentError, at: Instant) =
        finishTurn(turnId, DurableTurnStatus.FAILED, error, at)

    override suspend fun cancelTurn(turnId: String, error: AgentError, at: Instant) =
        finishTurn(turnId, DurableTurnStatus.CANCELLED, error, at)

    override suspend fun latestRun(sessionId: String): DurableRunSnapshot? = mutex.withLock {
        val turn = turns.values
            .filter { it.sessionId == sessionId }
            .maxWithOrNull(compareBy<DurableTurnRecord> { it.createdAt }.thenBy { it.turnId })
            ?: return@withLock null
        DurableRunSnapshot(
            turn = turn,
            approval = approvals.values.singleOrNull { it.request.turnId == turn.turnId },
            effect = effects.values.singleOrNull { it.turnId == turn.turnId },
        )
    }

    private suspend fun finishTurn(
        turnId: String,
        status: DurableTurnStatus,
        error: AgentError,
        at: Instant,
    ) = mutex.withLock {
        val turn = requireTurn(turnId)
        turns[turnId] = turn.copy(
            status = status,
            updatedAt = at,
            errorCode = error.code,
            errorMessage = error.userMessage,
        )
        approvals.entries
            .singleOrNull { it.value.request.turnId == turnId && it.value.status == DurableApprovalStatus.PENDING }
            ?.let { (id, approval) ->
                approvals[id] = approval.copy(
                    status = DurableApprovalStatus.CANCELLED,
                    resolvedAt = at,
                )
            }
        effects.entries
            .singleOrNull { it.value.turnId == turnId && it.value.status != DurableEffectStatus.COMPLETED }
            ?.let { (id, effect) ->
                effects[id] = effect.copy(
                    status = DurableEffectStatus.FAILED,
                    updatedAt = at,
                    errorCode = error.code,
                )
            }
        Unit
    }

    private fun requireTurn(turnId: String): DurableTurnRecord =
        requireNotNull(turns[turnId]) { "Unknown turn $turnId" }
}

const val LEGACY_INPUT_BINDING = "legacy-unbound-input"
