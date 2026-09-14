package dev.kinetic.core.policy

import dev.kinetic.core.tools.ToolCall
import dev.kinetic.core.session.RunLedger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.Clock

data class ApprovalRequest(
    val approvalId: String,
    val sessionId: String,
    val turnId: String,
    val toolCall: ToolCall,
    val capability: CapabilityMetadata,
    val requestedAt: Instant,
)

enum class ApprovalDecision {
    APPROVE,
    REJECT,
}

interface ApprovalGate {
    val pendingRequest: StateFlow<ApprovalRequest?>

    suspend fun awaitDecision(request: ApprovalRequest): ApprovalDecision
    suspend fun resolve(approvalId: String, decision: ApprovalDecision): Boolean
    suspend fun cancelPending(turnId: String)
}

/** One active, identity-bound approval suitable for the single-turn Phase 1 runtime. */
class InteractiveApprovalGate : ApprovalGate {
    private data class Pending(
        val request: ApprovalRequest,
        val deferred: CompletableDeferred<ApprovalDecision>,
    )

    private val mutex = Mutex()
    private var pending: Pending? = null
    private val mutablePendingRequest = MutableStateFlow<ApprovalRequest?>(null)
    override val pendingRequest: StateFlow<ApprovalRequest?> = mutablePendingRequest.asStateFlow()

    override suspend fun awaitDecision(request: ApprovalRequest): ApprovalDecision {
        val deferred = CompletableDeferred<ApprovalDecision>()
        mutex.withLock {
            check(pending == null) { "Another approval request is already pending" }
            pending = Pending(request, deferred)
            mutablePendingRequest.value = request
        }
        return try {
            deferred.await()
        } finally {
            mutex.withLock {
                if (pending?.deferred === deferred) {
                    pending = null
                    mutablePendingRequest.value = null
                }
            }
        }
    }

    override suspend fun resolve(approvalId: String, decision: ApprovalDecision): Boolean =
        mutex.withLock {
            val current = pending ?: return@withLock false
            if (current.request.approvalId != approvalId) return@withLock false
            current.deferred.complete(decision)
        }

    override suspend fun cancelPending(turnId: String) {
        mutex.withLock {
            val current = pending ?: return
            if (current.request.turnId == turnId) current.deferred.cancel()
        }
    }
}

/** Persists an identity-bound decision before releasing the in-process waiter. */
class LedgerBackedApprovalGate(
    private val ledger: RunLedger,
    private val clock: Clock,
    private val delegate: InteractiveApprovalGate = InteractiveApprovalGate(),
) : ApprovalGate {
    override val pendingRequest: StateFlow<ApprovalRequest?> = delegate.pendingRequest

    override suspend fun awaitDecision(request: ApprovalRequest): ApprovalDecision =
        delegate.awaitDecision(request)

    override suspend fun resolve(approvalId: String, decision: ApprovalDecision): Boolean {
        val request = pendingRequest.value ?: return false
        if (request.approvalId != approvalId) return false
        val persisted = ledger.resolveApproval(
            approvalId = approvalId,
            turnId = request.turnId,
            callId = request.toolCall.callId,
            decision = decision,
            at = clock.instant(),
        )
        return persisted && delegate.resolve(approvalId, decision)
    }

    override suspend fun cancelPending(turnId: String) {
        delegate.cancelPending(turnId)
    }
}
